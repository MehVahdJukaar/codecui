package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;


public final class FieldOfTags {

    public record Entry(String name, Codec<?> innerCodec, boolean optional, @Nullable Object defaultValue) {}

    private static final Map<MapCodec<?>, Entry> ENTRIES = Collections.synchronizedMap(new WeakHashMap<>());

    private FieldOfTags() {}

    public static void put(MapCodec<?> wrapped, String name, Codec<?> innerCodec, boolean optional, @Nullable Object defaultValue) {
        if (wrapped == null) return;
        ENTRIES.put(wrapped, new Entry(name, innerCodec, optional, defaultValue));
    }

    public static @Nullable Entry get(MapCodec<?> wrapped) {
        return ENTRIES.get(wrapped);
    }
}
