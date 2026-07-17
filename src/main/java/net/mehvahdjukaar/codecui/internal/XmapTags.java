package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// Lazy side-channel for Codec.xmap/flatXmap/validate/stable/... (and the MapCodec mirrors):
// wrapper → innerCodec, recorded at construction. The resolver resolves the inner FRESH at
// lookup time, so a companion registered after construction still wins (eager resolution used
// to capture stale schemas during MC bootstrap).
public final class XmapTags {

    private static final Map<Codec<?>, Codec<?>> CODEC_INNER =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MapCodec<?>, MapCodec<?>> MAP_INNER =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void putCodec(Codec<?> wrapped, Codec<?> inner) {
        if (wrapped == null || inner == null || wrapped == inner) return;
        CODEC_INNER.put(wrapped, inner);
    }

    public static void putMap(MapCodec<?> wrapped, MapCodec<?> inner) {
        if (wrapped == null || inner == null || wrapped == inner) return;
        MAP_INNER.put(wrapped, inner);
    }

    public static @Nullable Codec<?> getCodec(Codec<?> wrapped) { return CODEC_INNER.get(wrapped); }

    public static @Nullable MapCodec<?> getMap(MapCodec<?> wrapped) { return MAP_INNER.get(wrapped); }
}
