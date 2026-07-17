package net.mehvahdjukaar.codecui.internal;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

// Registry of variant enumerators for KeyDispatchCodec-backed codecs. KeyDispatchCodec
// stores its variants as a black-box Function<K, MapCodec<? extends V>>, so K's known
// values can't be enumerated from the dispatch alone.
public final class DispatchRegistry {

    // The known keys for some K, plus how to name each one; variant bodies are recovered by
    // feeding these keys through the dispatch's own decoder. The key supplier is lazy so
    // hooks register at class-load time but iterate at editor-open time, when registries
    // are fully populated.
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
