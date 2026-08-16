package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;

// Side channel for Codec.xmap/flatXmap/validate/... and the MapCodec mirrors: wrapper -> inner.
// The inner codec is resolved fresh at lookup so a companion registered later still wins.
public final class XmapTags {

    private static final Map<Codec<?>, WeakReference<Codec<?>>> CODEC_INNER = WeakTags.identityKeyed();
    private static final Map<MapCodec<?>, WeakReference<MapCodec<?>>> MAP_INNER = WeakTags.identityKeyed();

    public static void putCodec(Codec<?> wrapped, Codec<?> inner) {
        if (wrapped == null || inner == null || wrapped == inner) return;
        CODEC_INNER.put(wrapped, new WeakReference<>(inner));
    }

    public static void putMap(MapCodec<?> wrapped, MapCodec<?> inner) {
        if (wrapped == null || inner == null || wrapped == inner) return;
        MAP_INNER.put(wrapped, new WeakReference<>(inner));
    }

    public static @Nullable Codec<?> getCodec(Codec<?> wrapped) {
        return wrapped == null ? null : WeakTags.deref(CODEC_INNER.get(wrapped));
    }

    public static @Nullable MapCodec<?> getMap(MapCodec<?> wrapped) {
        return wrapped == null ? null : WeakTags.deref(MAP_INNER.get(wrapped));
    }
}
