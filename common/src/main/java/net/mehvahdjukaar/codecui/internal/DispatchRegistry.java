package net.mehvahdjukaar.codecui.internal;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

// Registry of variant enumerators for KeyDispatchCodec-backed codecs.
//
// KeyDispatchCodec stores its variants as a Function<K, MapCodec<? extends V>>, a black-box
// closure. There's no way to enumerate K's known values from the dispatch alone,
// because the keyCodec is the output of Registry.byNameCodec() which itself is a
// flatComapMap wrapper that hides the underlying registry inside a lambda closure.
public final class DispatchRegistry {

    // A single dispatch hook: the known keys for some K, plus how to name each one. The
    // SchemaResolver recovers each variant's body by feeding these keys through the dispatch's
    // own internal decoder function - so no variant-codec lookup is needed here.
    //
    // The key supplier is lazy - each call re-resolves the keys, so dispatches register at
    // class-load time but the actual iteration happens at editor-open time when registries are
    // fully populated (and, for dynamic registries, when a level / registryAccess is available).
    public record Hook<K>(Class<K> keyType,
                          Supplier<List<K>> keys,
                          Function<K, String> nameOf) {}

    // IdentityHashMap on Class keys (Class identity is fine).
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

    // Snapshot of all registered hooks. Used by the resolver to try each hook against an
    // unknown dispatch's decoder function.
    public static List<Hook<?>> all() {
        synchronized (HOOKS) {
            return List.copyOf(HOOKS.values());
        }
    }
}
