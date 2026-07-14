package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;


final class CodecReflection {


    record ScannedInner(Object value, String fieldName) {}


    record FieldOfEntry(String name, Codec<?> elementCodec) {}

    static List<ScannedInner> scanInnerCodecs(Object codec) {
        ArrayList<ScannedInner> inners = new ArrayList<>();
        try {
            for (Class<?> cls = codec.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> t = f.getType();
                    boolean single = Codec.class.isAssignableFrom(t) || MapCodec.class.isAssignableFrom(t);
                    boolean array = t.isArray() && Codec.class.isAssignableFrom(t.getComponentType());
                    if (!single && !array) continue;
                    Object value = getFieldValue(f, codec);
                    if (value == null || value == codec) continue;
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    if (array) {
                        for (Object o : (Object[]) value) {
                            if (o != null && o != codec) inners.add(new ScannedInner(o, name));
                        }
                    } else {
                        inners.add(new ScannedInner(value, name));
                    }
                }
            }
        } catch (Throwable ignored) {
            return List.of();
        }
        return inners;
    }

    static @Nullable FieldOfEntry unwrapFieldOf(MapCodec<?> codec) {
        Object fieldDec = findFieldValueByClassName(codec, "FieldDecoder");
        if (fieldDec == null) return null;
        Object nameObj = readField(fieldDec, "name");
        Object elem = readField(fieldDec, "elementCodec");
        if (!(nameObj instanceof String name)) return null;
        Codec<?> inner = decoderAsCodec(elem);
        return inner == null ? null : new FieldOfEntry(name, inner);
    }

    /**
     * Best-effort recovery of {@code Codec.intRange/floatRange/doubleRange} bounds when the
     * construction mixin didn't apply. DFU builds these as {@code PRIMITIVE.flatXmap(checker, checker)}
     * where {@code checker = checkRange(min, max)} is a lambda capturing the two bounds as its only
     * fields (Codec.java checkRange). We walk the captured graph of the flatXmapped wrapper for the
     * single object that captures exactly two {@link Number}s and return them ordered [min, max].
     * Caller gates this on the wrapper being a flatXmap over a primitive number codec, so the only
     * two-number capture in the graph is the range checker. Returns null (→ unbounded) if unsure.
     */
    static Number @Nullable [] recoverRangeBounds(Object flatXmappedCodec) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Number[] pair = findNumberPairCapture(flatXmappedCodec, visited, 0);
        if (pair == null) return null;
        return pair[0].doubleValue() <= pair[1].doubleValue()
                ? pair
                : new Number[]{pair[1], pair[0]};
    }

    private static Number @Nullable [] findNumberPairCapture(Object obj, Set<Object> visited, int depth) {
        if (obj == null || depth > 8 || !visited.add(obj)) return null;

        Number[] direct = twoCapturedNumbers(obj);
        if (direct != null) return direct;

        for (Class<?> cls = obj.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                // Bounds live inside object-typed captures (Encoder/Decoder/Function); skip
                // primitives, boxed numbers and strings so we don't recurse into leaf data.
                if (t.isPrimitive() || t == String.class || Number.class.isAssignableFrom(t)
                        || t == Boolean.class || t == Character.class) continue;
                Object v = getFieldValue(f, obj);
                if (v == null || v == obj) continue;
                Number[] found = findNumberPairCapture(v, visited, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** An object whose only two non-static declared fields both hold {@link Number}s — the shape of
     *  the {@code checkRange} lambda's captured (min, max). */
    private static Number @Nullable [] twoCapturedNumbers(Object obj) {
        List<Field> fields = new ArrayList<>(2);
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            fields.add(f);
            if (fields.size() > 2) return null;
        }
        if (fields.size() != 2) return null;
        Object a = getFieldValue(fields.get(0), obj);
        Object b = getFieldValue(fields.get(1), obj);
        return (a instanceof Number na && b instanceof Number nb) ? new Number[]{na, nb} : null;
    }

    static @Nullable Object singleCapturedInner(Object codec) {
        Set<Object> inners = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectCapturedInners(codec, codec, inners, visited, 0);
        return inners.size() == 1 ? inners.iterator().next() : null;
    }

    static @Nullable List<RecordFieldTags.Entry> extractRecordFields(MapCodec<?> codec) {
        if (!looksLikeRecordCodec(codec)) return null;

        RecordCodecBuilder<?, ?> builder = findCapturedBuilder(codec);
        if (builder == null) return null;

        Object decoder = readField(builder, "decoder");
        if (decoder == null) return null;

        ArrayList<RecordFieldTags.Entry> fields = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRecordDecoderFields(decoder, fields, visited, 0);
        return fields.isEmpty() ? null : List.copyOf(fields);
    }


    static @Nullable Codec<?> decoderAsCodec(@Nullable Object decoder) {
        return decoder instanceof Codec<?> c ? c : null;
    }

    static @Nullable Object readField(Object obj, String name) {
        for (Class<?> cls = obj.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                return getFieldValue(cls.getDeclaredField(name), obj);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    static @Nullable Object findFieldValueByClassName(Object obj, String simpleNameSuffix) {
        for (Class<?> cls = obj.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object v = getFieldValue(f, obj);
                if (v != null && v.getClass().getSimpleName().endsWith(simpleNameSuffix)) return v;
            }
        }
        return null;
    }

    private static @Nullable Object getFieldValue(Field f, Object obj) {
        try {
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable inaccessible) {
            return null;
        }
    }

    private static void collectCapturedInners(Object root, Object current, Set<Object> inners,
                                              Set<Object> visited, int depth) {
        if (current == null || depth > 6 || !visited.add(current)) return;
        for (Class<?> cls = current.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                boolean wrapperTyped = Encoder.class.isAssignableFrom(t)
                        || Decoder.class.isAssignableFrom(t)
                        || MapEncoder.class.isAssignableFrom(t)
                        || MapDecoder.class.isAssignableFrom(t);
                if (!wrapperTyped) continue;
                Object v = getFieldValue(f, current);
                if (v == null || v == root || v == current) continue;
                if (v instanceof Codec<?> || v instanceof MapCodec<?>) {
                    inners.add(v);
                } else {
                    collectCapturedInners(root, v, inners, visited, depth + 1);
                }
            }
        }
    }


    private static boolean looksLikeRecordCodec(MapCodec<?> codec) {
        String cn = codec.getClass().getName();
        if (cn.contains("RecordCodecBuilder")) return true;
        return codec.toString().startsWith("RecordCodec[");
    }

    private static @Nullable RecordCodecBuilder<?, ?> findCapturedBuilder(MapCodec<?> codec) {
        for (Class<?> cls = codec.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (!RecordCodecBuilder.class.isAssignableFrom(f.getType())) continue;
                Object v = getFieldValue(f, codec);
                if (v instanceof RecordCodecBuilder<?, ?> rcb) return rcb;
            }
        }
        return null;
    }

    private static void collectRecordDecoderFields(Object node, List<RecordFieldTags.Entry> out,
                                                   Set<Object> visited, int depth) {
        if (node == null || depth > 12 || !visited.add(node)) return;

        RecordFieldTags.Entry direct = entryFromDecoderNode(node);
        if (direct != null) {
            if (out.stream().noneMatch(e -> e.name().equals(direct.name()))) {
                out.add(direct);
            }
            return;
        }

        if (node instanceof MapDecoder<?> md && md.toString().endsWith("[mapped]")) {
            Object inner = unwrapMappedDecoder(md);
            if (inner != null) collectRecordDecoderFields(inner, out, visited, depth + 1);
            return;
        }

        for (Class<?> cls = node.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object v = getFieldValue(f, node);
                if (v == null || v == node) continue;
                Class<?> t = f.getType();

                if (RecordCodecBuilder.class.isAssignableFrom(t)) {
                    RecordCodecBuilder<?, ?> rcb = (RecordCodecBuilder<?, ?>) v;
                    if (!isApplicativeFunction(rcb)) {
                        collectRecordDecoderFields(readField(rcb, "decoder"), out, visited, depth + 1);
                    }
                } else if (MapDecoder.class.isAssignableFrom(t) || MapCodec.class.isAssignableFrom(t)) {
                    collectRecordDecoderFields(v, out, visited, depth + 1);
                }
            }
        }
    }

    private static RecordFieldTags.@Nullable Entry entryFromDecoderNode(Object node) {
        if (node instanceof OptionalFieldCodec<?> opt) {
            return entryFromOptional(opt);
        }
        if (node instanceof MapCodec<?> mc) {
            if (mc instanceof OptionalFieldCodec<?> opt) {
                RecordFieldTags.Entry fromOpt = entryFromOptional(opt);
                if (fromOpt != null) return fromOpt;
            }
            Object fieldDec = findFieldDecoder(mc);
            if (fieldDec != null) return entryFromFieldDecoder(fieldDec);

            Object inner = singleCapturedInner(mc);
            if (inner != null && inner != mc) return entryFromDecoderNode(inner);
        }
        if (isFieldDecoder(node)) return entryFromFieldDecoder(node);
        return null;
    }

    private static RecordFieldTags.@Nullable Entry entryFromOptional(OptionalFieldCodec<?> opt) {
        String name = (String) readField(opt, "name");
        Codec<?> elem = (Codec<?>) readField(opt, "elementCodec");
        if (name == null || elem == null) return null;
        return new RecordFieldTags.Entry(name, null, opt);
    }

    private static RecordFieldTags.@Nullable Entry entryFromFieldDecoder(Object fieldDec) {
        Object nameObj = readField(fieldDec, "name");
        Object elem = readField(fieldDec, "elementCodec");
        if (!(nameObj instanceof String name)) return null;
        Codec<?> codec = decoderAsCodec(elem);
        return codec == null ? null : new RecordFieldTags.Entry(name, codec, null);
    }

    private static boolean isFieldDecoder(Object obj) {
        return obj != null && obj.getClass().getName().endsWith("FieldDecoder");
    }

    private static @Nullable Object findFieldDecoder(Object mapCodec) {
        for (Class<?> cls = mapCodec.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object v = getFieldValue(f, mapCodec);
                if (isFieldDecoder(v)) return v;
            }
        }
        return null;
    }

    private static @Nullable Object unwrapMappedDecoder(MapDecoder<?> mapped) {
        for (Class<?> cls = mapped.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : cls.getDeclaredFields()) {
                if (!MapDecoder.class.isAssignableFrom(f.getType())) continue;
                Object v = getFieldValue(f, mapped);
                if (v != null && v != mapped) return v;
            }
        }
        return null;
    }

    private static boolean isApplicativeFunction(RecordCodecBuilder<?, ?> rcb) {
        Object decoder = readField(rcb, "decoder");
        if (decoder == null) return true;
        String s = decoder.toString().toLowerCase(Locale.ROOT);
        return s.contains("unit") || s.contains("empty");
    }
}
