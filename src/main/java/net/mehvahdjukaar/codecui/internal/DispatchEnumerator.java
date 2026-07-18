package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.EnumerableCodec;
import net.mehvahdjukaar.codecui.Schema;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

import static net.mehvahdjukaar.codecui.internal.CodecFieldHandles.KEY_DISPATCH_DECODER;
import static net.mehvahdjukaar.codecui.internal.CodecFieldHandles.KEY_DISPATCH_KEYCODEC;
//? <1.21.11
//import static net.mehvahdjukaar.codecui.internal.CodecFieldHandles.KEY_DISPATCH_TYPEKEY;

// Enumerates the variants of a KeyDispatchCodec into a Schema.OneOf. Split out from
// SchemaResolver; calls back into the resolver for inner-codec resolution.
final class DispatchEnumerator {

    private final SchemaResolver resolver;

    DispatchEnumerator(SchemaResolver resolver) {
        this.resolver = resolver;
    }

    // Falls back to raw-JSON Opaque when no variants can be recovered: an empty OneOf renders
    // as a dead picker. Assumes KEY_DISPATCH_KEYCODEC is non-null (guarded by the caller).
    Schema<?> resolve(KeyDispatchCodec<?, ?> dispatch, MapCodec<?> fullCodec,
                      IdentityHashMap<Object, Schema<?>> cache) {
        //? >=1.21.11
        MapCodec<?> keyCodec = (MapCodec<?>) KEY_DISPATCH_KEYCODEC.get(dispatch);
        //? <1.21.11
        //Object keyCodec = KEY_DISPATCH_KEYCODEC.get(dispatch);
        // typeKey is the JSON field name driving the dispatch. On DFU 8 it's a plain field;
        // on DFU 9 it lives inside the fieldOf-wrapped keyCodec, read via keys(). Else "type".
        String typeKey = /*? >=1.21.11 {*/extractFirstKey(keyCodec)/*?} <1.21.11 {*//*dispatchTypeKey(dispatch, keyCodec)*//*?}*/;
        LinkedHashMap<String, Schema<?>> variants = enumerateDispatchVariants(dispatch, cache);
        if (variants.isEmpty()) {
            variants = enumerateFromRegistryTag(keyCodec, dispatch, cache);
        }
        if (variants.isEmpty()) {
            return new Schema.Opaque<>(fullCodec.codec(), null);
        }
        return new Schema.OneOf<>(typeKey, variants);
    }

    // The JSON field name driving the dispatch. DFU 8 exposes it as a typeKey String field;
    // DFU 9 folds it into the fieldOf-wrapped keyCodec, recovered via keys(). Defaults to "type".
    //? <1.21.11 {
    /*private String dispatchTypeKey(KeyDispatchCodec<?, ?> dispatch, @Nullable Object keyCodec) {
        if (KEY_DISPATCH_TYPEKEY != null) {
            try {
                if (KEY_DISPATCH_TYPEKEY.get(dispatch) instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
        }
        if (keyCodec instanceof MapCodec<?> mc) return extractFirstKey(mc);
        return "type";
    }
    *///?}

    // The dispatch's raw key Codec: DFU 9 stores it fieldOf-wrapped as a MapCodec (unwrapped
    // through FieldOfTags), DFU 8 stores the raw Codec directly.
    private static @Nullable Codec<?> innerKeyCodec(@Nullable Object keyCodec) {
        if (keyCodec instanceof MapCodec<?> mc) {
            FieldOfTags.Entry foe = FieldOfTags.get(mc);
            return foe != null ? foe.innerCodec() : null;
        }
        if (keyCodec instanceof Codec<?> c) return c;
        return null;
    }

    // Applies the dispatch's private decoder function to candidate keys; any successful
    // DataResult contributes a variant. Every hook is tried (rather than picked by K's class)
    // because closures hide K's runtime type - decoder.apply(K) fails fast for a wrong K and
    // succeeds only on a match.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LinkedHashMap<String, Schema<?>> enumerateDispatchVariants(KeyDispatchCodec<?, ?> dispatch,
                                                                       IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();
        CodecUI.LOGGER.debug("enumerateDispatchVariants called for {}", dispatch.getClass().getName());

        if (KEY_DISPATCH_DECODER == null) {
            CodecUI.LOGGER.warn("KEY_DISPATCH_DECODER VarHandle is null - field lookup failed at init");
            return variants;
        }
        CuratedSchemas.bootstrap();
        CodecUI.LOGGER.debug("DispatchRegistry has {} hooks", DispatchRegistry.all().size());

        Object decoderFn = KEY_DISPATCH_DECODER.get(dispatch);
        if (!(decoderFn instanceof Function<?, ?> fn)) {
            CodecUI.LOGGER.warn("decoder field is not a Function (got {})",
                    decoderFn == null ? "null" : decoderFn.getClass().getName());
            return variants;
        }

        // Path 0: ask the key codec itself. If it implements EnumerableCodec or resolves to a
        // Schema.Enum, we know the exact key set of THIS dispatch - feed each key through the
        // decoder for real variant bodies.
        if (KEY_DISPATCH_KEYCODEC != null) {
            Codec<?> innerKey = innerKeyCodec(KEY_DISPATCH_KEYCODEC.get(dispatch));
            if (innerKey instanceof EnumerableCodec en) {
                for (var e : en.codecUiValues().entrySet()) {
                    MapCodec<?> variantCodec = applyDecoder(fn, e.getValue());
                    if (variantCodec == null) continue;
                    variants.put(e.getKey(), resolver.resolveMapCodec(variantCodec, cache));
                }
            } else if (innerKey != null
                    && resolver.resolveCodec((Codec) innerKey, cache) instanceof Schema.Enum keyEnum) {
                for (Object k : keyEnum.options()) {
                    MapCodec<?> variantCodec = applyDecoder(fn, k);
                    if (variantCodec == null) continue;
                    String name = ((Function<Object, String>) keyEnum.label()).apply(k);
                    variants.put(name, resolver.resolveMapCodec(variantCodec, cache));
                }
            }
            if (!variants.isEmpty()) {
                CodecUI.LOGGER.debug("key-codec enumeration produced {} variants", variants.size());
                return variants;
            }
        }

        // Probe each registered hook. A hook for the wrong registry fails on EVERY key (foreign K
        // -> ClassCastException -> null), so after MAX_MISS early misses we reject it cheaply
        // instead of iterating its whole registry. Trade-off: a hook whose first entries all
        // legitimately fail to decode would be skipped - not a concern for the vanilla registries.
        final int MAX_MISS = 4;
        for (DispatchRegistry.Hook<?> hook : DispatchRegistry.all()) {
            List<?> keys = hook.keys().get();
            LinkedHashMap<String, Schema<?>> local = new LinkedHashMap<>();
            int miss = 0;
            for (Object k : keys) {
                MapCodec<?> variantCodec = applyDecoder(fn, k);
                if (variantCodec == null) {
                    if (local.isEmpty() && ++miss >= MAX_MISS) break; // wrong registry - bail cheaply
                    continue;
                }
                String name = ((Function<Object, String>) hook.nameOf()).apply(k);
                local.put(name, resolver.resolveMapCodec(variantCodec, cache));
            }
            if (!local.isEmpty()) {
                CodecUI.LOGGER.debug("hook {} produced {} variants", hook.keyType().getSimpleName(), local.size());
                variants.putAll(local);
                break;
            }
        }

        // Deliberately no name-only fallback: a per-hook lookup succeeds for any registered K
        // regardless of the dispatch's actual K (BlockState once got IntProvider variants), so the
        // dispatch's own decoder is the sole way a variant body is recovered.

        CodecUI.LOGGER.debug("final variant count: {}", variants.size());
        return variants;
    }

    // Unwraps decoder.apply(key) into a MapCodec; null on any failure (wrong-K
    // ClassCastException, error DataResult, non-MapCodec decoder).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable MapCodec<?> applyDecoder(Function fn, Object key) {
        try {
            Object result = fn.apply(key);
            if (!(result instanceof DataResult<?> dr)) return null;
            Object inner = dr.result().orElse(null);
            if (inner instanceof MapCodec<?> mc) return mc;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Registry-backed fallback: when the keyCodec resolves to a ResourceId of a known registry,
    // populate the variant dropdown from that registry's entries.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LinkedHashMap<String, Schema<?>> enumerateFromRegistryTag(Object keyCodec, KeyDispatchCodec<?, ?> dispatch,
                                                                      IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();

        Schema.ResourceId rid = null;
        Codec<?> innerKey = innerKeyCodec(keyCodec);
        if (innerKey != null) {
            IdentityHashMap<Object, Schema<?>> tmpCache = new IdentityHashMap<>();
            Schema<?> innerSchema = resolver.resolveCodec((Codec) innerKey, tmpCache);
            CodecUI.LOGGER.debug("registry-tag fallback: inner={}, schema={}",
                    innerKey.getClass().getSimpleName(), innerSchema);
            if (innerSchema instanceof Schema.ResourceId r && r.registry() != null) rid = r;
        }
        // Fall back to an eager SchemaTags entry (manual companion tagging on a MapCodec keyCodec).
        if (rid == null && keyCodec instanceof MapCodec<?> mapKey) {
            Schema<?> keyCodecSchema = SchemaTags.lookupMap(mapKey);
            CodecUI.LOGGER.debug("registry-tag fallback: SchemaTags entry={}", keyCodecSchema);
            if (keyCodecSchema instanceof Schema.Record<?> rec && rec.fields().size() == 1) {
                Schema<?> fs = rec.fields().getFirst().schema();
                if (fs instanceof Schema.ResourceId r && r.registry() != null) rid = r;
            }
        }
        if (rid == null) return variants;

        try {
            Registry<?> registry = McCompat.getValue(BuiltInRegistries.REGISTRY, McCompat.keyId(rid.registry()));
            if (registry == null) {
                CodecUI.LOGGER.warn("registry {} not found in BuiltInRegistries", rid.registry());
                return variants;
            }
            // For small registries the registry VALUES are the dispatch keys themselves - feed each
            // through the decoder for a real body. Large ones (Block, Item) stay opaque: resolving
            // 1000+ variant codecs is expensive and rarely useful.
            boolean resolveBodies = registry.size() <= 128 && KEY_DISPATCH_DECODER != null;
            Object decoderFn = resolveBodies ? KEY_DISPATCH_DECODER.get(dispatch) : null;
            List<Identifier> ids = new ArrayList<>(registry.keySet());
            ids.sort(Comparator.comparing(Identifier::toString));
            int bodies = 0;
            for (Identifier id : ids) {
                Schema<?> body = new Schema.Opaque<>(null, null);
                if (decoderFn instanceof Function<?, ?> fn) {
                    Object value = McCompat.getValue(registry, id);
                    MapCodec<?> variantCodec = value == null ? null : applyDecoder((Function) fn, value);
                    if (variantCodec != null) {
                        body = resolver.resolveMapCodec(variantCodec, cache);
                        bodies++;
                    }
                }
                variants.put(id.toString(), body);
            }
            CodecUI.LOGGER.debug("registry-backed dispatch: populated {} variants ({} with real bodies) from {}",
                    variants.size(), bodies, McCompat.keyId(rid.registry()));
        } catch (Throwable t) {
            CodecUI.LOGGER.warn("Failed to enumerate registry {}: {}", rid.registry(), t.toString());
        }
        return variants;
    }

    private static String extractFirstKey(MapCodec<?> keyCodec) {
        try {
            return keyCodec.keys(JsonOps.INSTANCE)
                    .map(CodecReflection::jsonKeyString)
                    .findFirst()
                    .orElse("type");
        } catch (Throwable ignored) {
            return "type";
        }
    }

    // ExtraCodecs.dispatchOptionalValue: a OneOf carrying a valueField, so the variant body
    // nests under that key (e.g. a criterion's "conditions") instead of flattening. Null when no
    // variants can be recovered, so the caller falls through instead of committing to a dead picker.
    @Nullable Schema<?> resolveOptionalValueDispatch(CodecReflection.OptionalValueDispatch dispatch,
                                                     IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = enumerateOptionalValueVariants(dispatch, cache);
        if (variants.isEmpty()) return null;
        return new Schema.OneOf<>(dispatch.typeKey(), variants, dispatch.valueKey());
    }

    // Only registry-keyed dispatches (the sole vanilla shape) are enumerated: the key codec
    // resolves to a ResourceId, and each registry entry's VALUE fed to the codec-getter yields
    // that variant's body codec. Anything else returns empty and the caller falls back.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LinkedHashMap<String, Schema<?>> enumerateOptionalValueVariants(
            CodecReflection.OptionalValueDispatch dispatch, IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();

        Schema<?> keySchema = resolver.resolveCodec((Codec) dispatch.keyCodec(), new IdentityHashMap<>());
        if (!(keySchema instanceof Schema.ResourceId rid) || rid.registry() == null) return variants;

        Registry<?> registry;
        try {
            registry = McCompat.getValue(BuiltInRegistries.REGISTRY, McCompat.keyId(rid.registry()));
        } catch (Throwable t) {
            return variants;
        }
        if (registry == null || registry.size() > 512) return variants;

        List<Identifier> ids = new ArrayList<>(registry.keySet());
        ids.sort(Comparator.comparing(Identifier::toString));

        Function<Object, Object> codecGetter = pickCodecGetter(dispatch.getters(), registry, ids);
        if (codecGetter == null) return variants;

        for (Identifier id : ids) {
            Object value = McCompat.getValue(registry, id);
            if (value == null) continue;
            Codec<?> body;
            try {
                body = asCodec(codecGetter.apply(value));
            } catch (Throwable t) {
                body = null;
            }
            if (body == null) continue;
            variants.put(id.toString(), resolver.resolveCodec((Codec) body, cache));
        }
        return variants;
    }

    // Of the two captured getters (keyGetter V->K, codecGetter K->Codec), the codec-getter is the
    // one that returns a Codec when applied to a registry value; the other throws or returns a
    // non-codec. One probe per getter is enough.
    private static @Nullable Function<Object, Object> pickCodecGetter(
            List<Function<Object, Object>> getters, Registry<?> registry, List<Identifier> ids) {
        for (Function<Object, Object> g : getters) {
            for (Identifier id : ids) {
                Object value = McCompat.getValue(registry, id);
                if (value == null) continue;
                try {
                    if (asCodec(g.apply(value)) != null) return g;
                } catch (Throwable ignored) {
                }
                break;
            }
        }
        return null;
    }

    private static @Nullable Codec<?> asCodec(@Nullable Object o) {
        if (o instanceof Codec<?> c) return c;
        if (o instanceof DataResult<?> dr) {
            Object r = dr.result().orElse(null);
            if (r instanceof Codec<?> c) return c;
        }
        return null;
    }
}