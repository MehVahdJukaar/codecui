package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NeoForge fallback for {@code RecordCodecBuilder.build(...)} outputs when construction mixins
 * cannot run on DFU. Walks the {@link MapDecoder} tree captured inside the built {@link MapCodec}
 * (via its synthetic {@link RecordCodecBuilder} closure field) and synthesises the same
 * {@link RecordFieldTags.Entry} list the RCB mixins would have recorded.
 */
final class RecordCodecUnwrapper {

    private RecordCodecUnwrapper() {}

    static @Nullable List<RecordFieldTags.Entry> extractFields(MapCodec<?> codec) {
        if (!looksLikeRecordCodec(codec)) return null;

        RecordCodecBuilder<?, ?> builder = findCapturedBuilder(codec);
        if (builder == null) return null;

        Object decoder = readField(builder, "decoder");
        if (decoder == null) return null;

        ArrayList<RecordFieldTags.Entry> fields = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectFields(decoder, fields, visited, 0);
        return fields.isEmpty() ? null : List.copyOf(fields);
    }

    private static boolean looksLikeRecordCodec(MapCodec<?> codec) {
        String cn = codec.getClass().getName();
        if (cn.contains("RecordCodecBuilder")) return true;
        String ts = codec.toString();
        return ts.startsWith("RecordCodec[");
    }

    private static @Nullable RecordCodecBuilder<?, ?> findCapturedBuilder(MapCodec<?> codec) {
        for (Class<?> cls = codec.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (!RecordCodecBuilder.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    return (RecordCodecBuilder<?, ?>) f.get(codec);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static void collectFields(Object node, List<RecordFieldTags.Entry> out,
                                      Set<Object> visited, int depth) {
        if (node == null || depth > 12 || !visited.add(node)) return;

        RecordFieldTags.Entry direct = entryFromNode(node);
        if (direct != null) {
            if (out.stream().noneMatch(e -> e.name().equals(direct.name()))) {
                out.add(direct);
            }
            return;
        }

        if (node instanceof MapDecoder<?> md && md.toString().endsWith("[mapped]")) {
            Object inner = unwrapMappedDecoder(md);
            if (inner != null) collectFields(inner, out, visited, depth + 1);
            return;
        }

        // ap/lift/flatMap decoder composites — descend captured MapDecoder / RecordCodecBuilder fields.
        for (Class<?> cls = node.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(node);
                } catch (Throwable inaccessible) {
                    continue;
                }
                if (v == null || v == node) continue;

                if (RecordCodecBuilder.class.isAssignableFrom(t)) {
                    RecordCodecBuilder<?, ?> rcb = (RecordCodecBuilder<?, ?>) v;
                    if (!isApplicativeFunction(rcb)) {
                        collectFields(readField(rcb, "decoder"), out, visited, depth + 1);
                    }
                } else if (MapDecoder.class.isAssignableFrom(t) || MapCodec.class.isAssignableFrom(t)) {
                    collectFields(v, out, visited, depth + 1);
                }
            }
        }
    }

    private static @Nullable RecordFieldTags.Entry entryFromNode(Object node) {
        if (node instanceof OptionalFieldCodec<?> opt) {
            return entryFromOptional(opt);
        }
        if (node instanceof MapCodec<?> mc) {
            RecordFieldTags.Entry fromOpt = mc instanceof OptionalFieldCodec<?> opt ? entryFromOptional(opt) : null;
            if (fromOpt != null) return fromOpt;

            Object fieldDec = findFieldDecoder(mc);
            if (fieldDec != null) {
                return entryFromFieldDecoder(fieldDec);
            }

            // optionalFieldOf(default) xmap wrapper — peel to OptionalFieldCodec if reachable.
            Object inner = singleCapturedInner(mc);
            if (inner != null && inner != mc) {
                return entryFromNode(inner);
            }
        }

        if (isFieldDecoder(node)) {
            return entryFromFieldDecoder(node);
        }
        return null;
    }

    private static @Nullable RecordFieldTags.Entry entryFromOptional(OptionalFieldCodec<?> opt) {
        try {
            String name = (String) readField(opt, "name");
            Codec<?> elem = (Codec<?>) readField(opt, "elementCodec");
            if (name == null || elem == null) return null;
            return new RecordFieldTags.Entry(name, null, opt);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable RecordFieldTags.Entry entryFromFieldDecoder(Object fieldDec) {
        Object nameObj = readField(fieldDec, "name");
        Object elem = readField(fieldDec, "elementCodec");
        if (!(nameObj instanceof String name)) return null;
        Codec<?> codec = decoderAsCodec(elem);
        if (codec == null) return null;
        return new RecordFieldTags.Entry(name, codec, null);
    }

    static @Nullable Codec<?> decoderAsCodec(@Nullable Object decoder) {
        return decoder instanceof Codec<?> c ? c : null;
    }

    private static boolean isFieldDecoder(Object obj) {
        return obj != null && obj.getClass().getName().endsWith("FieldDecoder");
    }

    private static @Nullable Object findFieldDecoder(Object mapCodec) {
        for (Class<?> cls = mapCodec.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(mapCodec);
                    if (v != null && isFieldDecoder(v)) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Peel shape-preserving MapCodec/Codec wrappers (xmap over optionalField, etc.). */
    private static @Nullable Object singleCapturedInner(Object codec) {
        Set<Object> inners = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectCapturedInners(codec, codec, inners, visited, 0);
        return inners.size() == 1 ? inners.iterator().next() : null;
    }

    private static void collectCapturedInners(Object root, Object current, Set<Object> inners,
                                              Set<Object> visited, int depth) {
        if (current == null || depth > 6 || !visited.add(current)) return;
        for (Class<?> cls = current.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                boolean wrapperTyped = Encoder.class.isAssignableFrom(t)
                        || Decoder.class.isAssignableFrom(t)
                        || MapEncoder.class.isAssignableFrom(t)
                        || MapDecoder.class.isAssignableFrom(t);
                if (!wrapperTyped) continue;
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(current);
                } catch (Throwable inaccessible) {
                    continue;
                }
                if (v == null || v == root || v == current) continue;
                if (v instanceof Codec<?> || v instanceof MapCodec<?>) {
                    inners.add(v);
                } else {
                    collectCapturedInners(root, v, inners, visited, depth + 1);
                }
            }
        }
    }

    private static @Nullable Object unwrapMappedDecoder(MapDecoder<?> mapped) {
        for (Class<?> cls = mapped.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (!MapDecoder.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(mapped);
                    if (v != null && v != mapped) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Skip the curry/function position in ap/lift chains (Decoder.unit / point). */
    private static boolean isApplicativeFunction(RecordCodecBuilder<?, ?> rcb) {
        Object decoder = readField(rcb, "decoder");
        if (decoder == null) return true;
        String s = decoder.toString().toLowerCase(Locale.ROOT);
        return s.contains("unit") || s.contains("empty");
    }

    private static @Nullable Object readField(Object obj, String name) {
        for (Class<?> cls = obj.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
