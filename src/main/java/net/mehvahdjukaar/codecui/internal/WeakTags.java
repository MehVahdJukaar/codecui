package net.mehvahdjukaar.codecui.internal;

import com.google.common.collect.MapMaker;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;

// Backing store for the construction-mixin side channels. Keys compare by identity: DFU's
// OptionalFieldCodec and record codecs have structural equals, so a WeakHashMap aliases them.
// Values must not reach their key (a weak map holds values strongly), so codec refs in values are weak.
final class WeakTags {

    static <K, V> Map<K, V> identityKeyed() {
        return new MapMaker().weakKeys().makeMap();
    }

    static <T> @Nullable WeakReference<T> weakRef(@Nullable T value) {
        return value == null ? null : new WeakReference<>(value);
    }

    static <T> @Nullable T deref(@Nullable WeakReference<T> ref) {
        return ref == null ? null : ref.get();
    }
}
