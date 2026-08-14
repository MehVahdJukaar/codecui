package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;

// Lazy side-channel for Codec.xmap/flatXmap/validate/stable/... (and the MapCodec mirrors):
// wrapper -> innerCodec, recorded at construction. The resolver resolves the inner FRESH at
// lookup time, so a companion registered after construction still wins (eager resolution used
// to capture stale schemas during MC bootstrap). See WeakTags for why the map is identity-keyed
// and why the inner codec is held weakly.
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
