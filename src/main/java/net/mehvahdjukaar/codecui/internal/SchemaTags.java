package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.Schema;

import java.util.Map;

// Schemas attached to codecs at construction. Best-effort: a missing tag falls back to the resolver tiers.
// The Schema value is held strongly (nothing else refs it), so a Schema that embeds its own key codec
// pins the entry forever. Fine for static bootstrap codecs, not for codecs built per instance.
public final class SchemaTags {
    private static final Map<Codec<?>, Schema<?>> CODEC_SCHEMAS = WeakTags.identityKeyed();
    private static final Map<MapCodec<?>, Schema<?>> MAP_CODEC_SCHEMAS = WeakTags.identityKeyed();

    public static <A> void tag(Codec<A> codec, Schema<A> schema) {
        if (codec == null || schema == null) return;
        CODEC_SCHEMAS.put(codec, schema);
    }

    public static <A> void tag(MapCodec<A> codec, Schema<A> schema) {
        if (codec == null || schema == null) return;
        MAP_CODEC_SCHEMAS.put(codec, schema);
    }

    @SuppressWarnings("unchecked")
    public static <A> Schema<A> lookup(Codec<A> codec) {
        if (codec == null) return null;
        return (Schema<A>) CODEC_SCHEMAS.get(codec);
    }

    @SuppressWarnings("unchecked")
    public static <A> Schema<A> lookupMap(MapCodec<A> codec) {
        if (codec == null) return null;
        return (Schema<A>) MAP_CODEC_SCHEMAS.get(codec);
    }
}
