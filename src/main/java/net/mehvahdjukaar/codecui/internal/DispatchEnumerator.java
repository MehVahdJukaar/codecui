package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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

/**
 * Enumerates the variants of a {@link KeyDispatchCodec} into a {@link Schema.OneOf}. Split out
 * from {@link SchemaResolver}: the dispatch-key machinery (reading the private decoder/keyCodec,
 * probing hooks, registry-backed fallback) is a self-contained subsystem that only touches
 * dispatch codecs and calls back into the resolver for inner-codec resolution.
 */
final class DispatchEnumerator {

    private final SchemaResolver resolver;

    DispatchEnumerator(SchemaResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Resolves a {@code KeyDispatchCodec} to a {@link Schema.OneOf} of its variants, or a raw-JSON
     * {@link Schema.Opaque} when no variants can be recovered (an empty OneOf renders as a dead
     * picker, so raw JSON is strictly more useful and keeps tier 3 from mis-guessing on the key
     * codec field). Assumes {@code KEY_DISPATCH_KEYCODEC} is non-null (guarded by the caller).
     */
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

        // Generic fallback: if no registered hook matched, check whether the keyCodec is a
        // registry-backed codec (tagged with a single-field Record carrying a ResourceId).
        // If so, populate variants directly from that registry. Bodies stay Opaque since
        // resolving 1000+ per-variant codecs (e.g. every Block) is impractical.
        if (variants.isEmpty()) {
            variants = enumerateFromRegistryTag(keyCodec, dispatch, cache);
        }
        if (variants.isEmpty()) {
            return new Schema.Opaque<>(fullCodec.codec(), null);
        }
        return new Schema.OneOf<>(typeKey, variants);
    }
    /**
     * The JSON field name driving a dispatch. DFU 8 exposes it as a {@code typeKey} String field;
     * DFU 9 folds it into the fieldOf-wrapped keyCodec, recovered via {@code keys()}. Defaults to
     * {@code "type"}.
     */
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

    /**
     * The dispatch's raw key {@link Codec} (the registry byNameCodec / StringRepresentable codec).
     * DFU 9 stores it fieldOf-wrapped as a {@link MapCodec} (unwrapped through {@link FieldOfTags});
     * DFU 8 stores the raw Codec directly. Returns null when it can't be recovered.
     */
    private @Nullable Codec<?> innerKeyCodec(@Nullable Object keyCodec) {
        if (keyCodec instanceof MapCodec<?> mc) {
            FieldOfTags.Entry foe = FieldOfTags.get(mc);
            return foe != null ? foe.innerCodec() : null;
        }
        if (keyCodec instanceof Codec<?> c) return c;
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LinkedHashMap<String, Schema<?>> enumerateDispatchVariants(KeyDispatchCodec<?, ?> dispatch,
                                                                       IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();
        CodecUI.LOGGER.debug("enumerateDispatchVariants called for {}", dispatch.getClass().getName());

        if (KEY_DISPATCH_DECODER == null) {
            CodecUI.LOGGER.warn("KEY_DISPATCH_DECODER VarHandle is null — field lookup failed at init");
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

        // Path 0: ask the key codec itself. The stored keyCodec is the user codec wrapped in
        // fieldOf(typeKey); FieldOfTags gives us the inner codec back. If it implements
        // EnumerableCodec (custom registries like MapRegistry) or resolves to a Schema.Enum
        // (StringRepresentable codecs), we know the exact key set of THIS dispatch — feed
        // each key through the decoder and get real variant bodies.
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

        // Probe each registered hook. A hook whose registry ISN'T this dispatch's fails on EVERY
        // key (a foreign K makes the dispatch's decoder throw ClassCastException -> null), so once
        // the first few keys all miss we reject the hook in O(1) instead of iterating its whole
        // (possibly large) registry. Only the matching hook is enumerated in full — this keeps the
        // cost independent of how many/large the other registries are, so the list can grow freely.
        // Trade-off: a hook whose first MAX_MISS entries all *legitimately* fail to decode would be
        // skipped — not a concern for the vanilla type registries registered here.
        final int MAX_MISS = 4;
        for (DispatchRegistry.Hook<?> hook : DispatchRegistry.all()) {
            List<?> keys = hook.keys().get();
            LinkedHashMap<String, Schema<?>> local = new LinkedHashMap<>();
            int miss = 0;
            for (Object k : keys) {
                MapCodec<?> variantCodec = applyDecoder(fn, k);
                if (variantCodec == null) {
                    if (local.isEmpty() && ++miss >= MAX_MISS) break; // wrong registry — bail cheaply
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

        // (Old name-only fallback removed: it picked the WRONG hook for unknown-K dispatches
        // because a per-hook variant-codec lookup succeeds for any registered K regardless of
        // whether the dispatch's actual K matches. Result: BlockState's dispatch got IntProvider
        // variants. That lookup is now gone entirely — hooks only carry keys + names, and the
        // dispatch's own decoder is the sole way a variant body is recovered.)

        CodecUI.LOGGER.debug("final variant count: {}", variants.size());
        return variants;
    }

    /**
     * Applies the dispatch's decoder function to a candidate key and unwraps the resulting
     * {@code DataResult<MapDecoder<? extends V>>} into a {@link MapCodec} (which is the concrete
     * type stored in practice by the public {@code KeyDispatchCodec} constructor).
     * Returns null on any failure: ClassCastException from a wrong-K hook, error DataResult, etc.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable MapCodec<?> applyDecoder(Function fn, Object key) {
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

    /**
     * Registry-backed dispatch fallback. When the dispatch's {@code keyCodec} is itself a
     * single-field {@code Schema.Record} whose field schema is a {@link Schema.ResourceId},
     * we know the dispatch is keyed on an identifier from a known registry. Populate the
     * variants dropdown with every entry in that registry (label = identifier), using a placeholder
     * Opaque schema for the variant body.
     *
     * <p>Bodies stay opaque on purpose — for large registries (Block, Item, etc.) resolving each
     * variant's MapCodec would be expensive and rarely useful in this MVP. The user can still
     * edit the body as raw JSON.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LinkedHashMap<String, Schema<?>> enumerateFromRegistryTag(Object keyCodec, KeyDispatchCodec<?, ?> dispatch,
                                                                      IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();

        // Resolve the dispatch's raw key Codec and check whether it's a registry id (BLOCK/RECIPE_
        // SERIALIZER/... byNameCodec, tagged ResourceId). On DFU 9 the key is fieldOf-wrapped
        // (unwrapped via FieldOfTags); on DFU 8 it's the raw tagged Codec.
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
            Registry<?> registry = BuiltInRegistries.REGISTRY./*? >1.21.1 {*/getValue/*?} <=1.21.1 {*//*get*//*?}*/(rid.registry()./*? >=1.21.11 {*/identifier/*?} <1.21.11 {*//*location*//*?}*/());
            if (registry == null) {
                CodecUI.LOGGER.warn("registry {} not found in BuiltInRegistries", rid.registry());
                return variants;
            }
            // For small registries (rule tests, height providers, ...) the registry VALUES are
            // the dispatch keys themselves — feed each through the decoder for a real variant
            // body. Large registries (Block, Item, ...) stay name-only with opaque bodies.
            boolean resolveBodies = registry.size() <= 128 && KEY_DISPATCH_DECODER != null;
            Object decoderFn = resolveBodies ? KEY_DISPATCH_DECODER.get(dispatch) : null;
            List<Identifier> ids = new ArrayList<>(registry.keySet());
            ids.sort(Comparator.comparing(Identifier::toString));
            int bodies = 0;
            for (var id : ids) {
                Schema<?> body = new Schema.Opaque<>(null, null);
                if (decoderFn instanceof Function<?, ?> fn) {
                    Object value = registry./*? >1.21.1 {*/getValue/*?} <=1.21.1 {*//*get*//*?}*/(id);
                    MapCodec<?> variantCodec = value == null ? null : applyDecoder((Function) fn, value);
                    if (variantCodec != null) {
                        body = resolver.resolveMapCodec(variantCodec, cache);
                        bodies++;
                    }
                }
                variants.put(id.toString(), body);
            }
            CodecUI.LOGGER.debug("registry-backed dispatch: populated {} variants ({} with real bodies) from {}",
                    variants.size(), bodies, rid.registry()./*? >=1.21.11 {*/identifier/*?} <1.21.11 {*//*location*//*?}*/());
        } catch (Throwable t) {
            CodecUI.LOGGER.warn("Failed to enumerate registry {}: {}", rid.registry(), t.toString());
        }
        return variants;
    }

    private String extractFirstKey(MapCodec<?> keyCodec) {
        try {
            return keyCodec.keys(com.mojang.serialization.JsonOps.INSTANCE)
                    .map(DispatchEnumerator::unwrapJsonKey)
                    .findFirst()
                    .orElse("type");
        } catch (Throwable ignored) {
            return "type";
        }
    }

    /**
     * JsonOps emits keys as {@code JsonPrimitive(String)}, whose toString() returns the
     * quoted form (e.g. {@code "predicate_type"}). Unwrap the underlying string when possible.
     */
    private static String unwrapJsonKey(Object o) {
        if (o instanceof com.google.gson.JsonPrimitive prim && prim.isString()) return prim.getAsString();
        return String.valueOf(o);
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
            registry = BuiltInRegistries.REGISTRY./*? >1.21.1 {*/getValue/*?} <=1.21.1 {*//*get*//*?}*/(rid.registry()./*? >=1.21.11 {*/identifier/*?} <1.21.11 {*//*location*//*?}*/());
        } catch (Throwable t) {
            return variants;
        }
        if (registry == null || registry.size() > 512) return variants;

        List<Identifier> ids = new ArrayList<>(registry.keySet());
        ids.sort(Comparator.comparing(Identifier::toString));

        Function<Object, Object> codecGetter = pickCodecGetter(dispatch.getters(), registry, ids);
        if (codecGetter == null) return variants;

        for (Identifier id : ids) {
            Object value = registry./*? >1.21.1 {*/getValue/*?} <=1.21.1 {*//*get*//*?}*/(id);
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
                Object value = registry./*? >1.21.1 {*/getValue/*?} <=1.21.1 {*//*get*//*?}*/(id);
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