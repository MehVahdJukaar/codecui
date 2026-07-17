package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// Side-channel storage for the ordered (fieldName, fieldCodec) pairs accumulated per
// RecordCodecBuilder, populated by the RCB-construction mixins. WeakHashMap by builder
// identity so transient builders don't leak.
public final class RecordFieldTags {

    private static final Map<RecordCodecBuilder<?, ?>, List<Entry>> TAGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Exactly one of elementCodec / mapCodec is non-null: elementCodec from the
    // of(getter, name, codec) form, mapCodec carrying the whole MapCodec from the
    // of(getter, MapCodec) form (optional / default / lenient variants).
    public record Entry(String name,
                        @Nullable Codec<?> elementCodec,
                        @Nullable MapCodec<?> mapCodec) {}

    public static void single(RecordCodecBuilder<?, ?> builder, String name, Codec<?> fieldCodec) {
        if (builder == null) return;
        TAGS.put(builder, List.of(new Entry(name, fieldCodec, null)));
    }

    // The on-disk field name comes from the MapCodec's keys(); without one we skip tagging
    // (the field just resolves Opaque).
    public static void singleMap(RecordCodecBuilder<?, ?> builder, MapCodec<?> mapCodec) {
        if (builder == null || mapCodec == null) return;
        String name = extractFirstKey(mapCodec);
        if (name == null) return;
        TAGS.put(builder, List.of(new Entry(name, null, mapCodec)));
    }

    private static @Nullable String extractFirstKey(MapCodec<?> mapCodec) {
        try {
            return mapCodec.keys(JsonOps.INSTANCE)
                    .map(CodecReflection::jsonKeyString)
                    .findFirst()
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Used by the Instance.map hook: the Applicative ap5..ap16 defaults pipe the accumulated
    // function position through map, which must not lose the fields gathered so far.
    public static void copy(RecordCodecBuilder<?, ?> from, RecordCodecBuilder<?, ?> to) {
        if (from == null || to == null || from == to) return;
        List<Entry> v = TAGS.get(from);
        if (v != null && !v.isEmpty()) TAGS.put(to, v);
    }

    public static void concat(RecordCodecBuilder<?, ?> result, RecordCodecBuilder<?, ?>... inputs) {
        if (result == null) return;
        ArrayList<Entry> merged = new ArrayList<>();
        for (RecordCodecBuilder<?, ?> in : inputs) {
            if (in == null) continue;
            List<Entry> sub = TAGS.get(in);
            if (sub != null) merged.addAll(sub);
        }
        if (!merged.isEmpty()) {
            TAGS.put(result, List.copyOf(merged));
        }
    }

    public static List<Entry> get(RecordCodecBuilder<?, ?> builder) {
        if (builder == null) return List.of();
        List<Entry> v = TAGS.get(builder);
        return v == null ? List.of() : v;
    }

    // Output side: entries re-keyed by the built MapCodec. The resolver rebuilds the
    // Schema.Record FRESH each lookup, so a companion registered after RCB.build() still wins.

    private static final Map<MapCodec<?>, List<Entry>> BUILT_TAGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void onBuilt(MapCodec<?> result, List<Entry> entries) {
        if (result == null || entries == null || entries.isEmpty()) return;
        BUILT_TAGS.put(result, List.copyOf(entries));
    }

    public static @Nullable List<Entry> getBuilt(MapCodec<?> result) {
        return BUILT_TAGS.get(result);
    }
}
