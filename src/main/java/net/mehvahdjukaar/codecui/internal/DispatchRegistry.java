package net.mehvahdjukaar.codecui.internal;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

// Known key sets for KeyDispatchCodec families. The dispatch itself only holds a black-box
// Function<K, MapCodec>, so K's values can't be enumerated from it alone.
public final class DispatchRegistry {

    // keys is a Supplier so hooks can register at class-load time and iterate at editor-open
    // time, when registries are populated.
    public record Hook<K>(Class<K> keyType,
                          Supplier<List<K>> keys,
                          Function<K, String> nameOf) {}

    private static final Map<Class<?>, Hook<?>> HOOKS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public static <K> void register(Class<K> keyType,
                                    Supplier<List<K>> keys,
                                    Function<K, String> nameOf) {
        HOOKS.put(keyType, new Hook<>(keyType, keys, nameOf));
    }

    @SuppressWarnings("unchecked")
    public static <K> @Nullable Hook<K> get(Class<K> keyType) {
        return (Hook<K>) HOOKS.get(keyType);
    }

    public static List<Hook<?>> all() {
        synchronized (HOOKS) {
            return List.copyOf(HOOKS.values());
        }
    }
}
