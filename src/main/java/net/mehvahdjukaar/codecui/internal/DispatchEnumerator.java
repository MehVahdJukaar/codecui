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

// Enumerates the variants of a KeyDispatchCodec into a Schema.OneOf.
final class DispatchEnumerator {

    private final SchemaResolver resolver;

    DispatchEnumerator(SchemaResolver resolver) {
        this.resolver = resolver;
    }

    // Opaque when no variants can be recovered: an empty OneOf is a dead picker.
    // Caller guarantees KEY_DISPATCH_KEYCODEC is non-null.
    Schema<?> resolve(KeyDispatchCodec<?, ?> dispatch, MapCodec<?> fullCodec,
                      IdentityHashMap<Object, Schema<?>> cache) {
        //? >=1.21.11
        MapCodec<?> keyCodec = (MapCodec<?>) KEY_DISPATCH_KEYCODEC.get(dispatch);
        //? <1.21.11
        //Object keyCodec = KEY_DISPATCH_KEYCODEC.get(dispatch);
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

    // The JSON field name driving the dispatch. DFU 8 has a typeKey String field; DFU 9 folds
    // it into the fieldOf-wrapped keyCodec, read back via keys(). Defaults to "type".
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

    // DFU 9 stores the key codec fieldOf-wrapped as a MapCodec (unwrapped via FieldOfTags),
    // DFU 8 stores the raw Codec.
    private static @Nullable Codec<?> innerKeyCodec(@Nullable Object keyCodec) {
        if (keyCodec instanceof MapCodec<?> mc) {
            FieldOfTags.Entry fieldOfEntry = FieldOfTags.get(mc);
            return fieldOfEntry != null ? fieldOfEntry.innerCodec() : null;
        }
        if (keyCodec instanceof Codec<?> c) return c;
        return null;
    }

    // Feeds candidate keys through the dispatch's private decoder; each success is a variant.
    // Every hook is tried because closures hide K's runtime type; a wrong K just fails fast.
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

        Object decoderField = KEY_DISPATCH_DECODER.get(dispatch);
        if (!(decoderField instanceof Function<?, ?> decoder)) {
            CodecUI.LOGGER.warn("decoder field is not a Function (got {})",
                    decoderField == null ? "null" : decoderField.getClass().getName());
            return variants;
        }

        // Path 0: the key codec itself knows its key set (EnumerableCodec or resolves to Schema.Enum).
        if (KEY_DISPATCH_KEYCODEC != null) {
            Codec<?> innerKey = innerKeyCodec(KEY_DISPATCH_KEYCODEC.get(dispatch));
            if (innerKey instanceof EnumerableCodec en) {
                for (var e : en.codecUiValues().entrySet()) {
                    MapCodec<?> variantCodec = applyDecoder(decoder, e.getValue());
                    if (variantCodec == null) continue;
                    variants.put(e.getKey(), resolver.resolveMapCodec(variantCodec, cache));
                }
            } else if (innerKey != null
                    && resolver.resolveCodec((Codec) innerKey, cache) instanceof Schema.Enum keyEnum) {
                for (Object k : keyEnum.options()) {
                    MapCodec<?> variantCodec = applyDecoder(decoder, k);
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

        // A hook for the wrong registry fails on every key (foreign K -> ClassCastException), so
        // bail after a few early misses instead of walking its whole registry.
        final int MAX_EARLY_MISSES = 4;
        for (DispatchRegistry.Hook<?> hook : DispatchRegistry.all()) {
            List<?> keys = hook.keys().get();
            LinkedHashMap<String, Schema<?>> hookVariants = new LinkedHashMap<>();
            int misses = 0;
            for (Object k : keys) {
                MapCodec<?> variantCodec = applyDecoder(decoder, k);
                if (variantCodec == null) {
                    boolean noHitYet = hookVariants.isEmpty();
                    if (noHitYet && ++misses >= MAX_EARLY_MISSES) break;
                    continue;
                }
                String name = ((Function<Object, String>) hook.nameOf()).apply(k);
                hookVariants.put(name, resolver.resolveMapCodec(variantCodec, cache));
            }
            if (!hookVariants.isEmpty()) {
                CodecUI.LOGGER.debug("hook {} produced {} variants", hook.keyType().getSimpleName(), hookVariants.size());
                variants.putAll(hookVariants);
                break;
            }
        }

        // No name-only fallback on purpose: a hook lookup matches any registered K regardless of
        // the dispatch's real K (BlockState once got IntProvider variants).

        CodecUI.LOGGER.debug("final variant count: {}", variants.size());
        return variants;
    }

    // Null on any failure (wrong-K ClassCastException, error DataResult, non-MapCodec result).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable MapCodec<?> applyDecoder(Function decoder, Object key) {
        try {
            Object result = decoder.apply(key);
            if (!(result instanceof DataResult<?> dr)) return null;
            Object inner = dr.result().orElse(null);
            if (inner instanceof MapCodec<?> mc) return mc;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Registry-backed fallback: the key codec resolves to a ResourceId of a known registry, so
    // that registry's entries fill the dropdown.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LinkedHashMap<String, Schema<?>> enumerateFromRegistryTag(Object keyCodec, KeyDispatchCodec<?, ?> dispatch,
                                                                      IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();

        Schema.ResourceId registryId = null;
        Codec<?> innerKey = innerKeyCodec(keyCodec);
        if (innerKey != null) {
            IdentityHashMap<Object, Schema<?>> tmpCache = new IdentityHashMap<>();
            Schema<?> innerSchema = resolver.resolveCodec((Codec) innerKey, tmpCache);
            CodecUI.LOGGER.debug("registry-tag fallback: inner={}, schema={}",
                    innerKey.getClass().getSimpleName(), innerSchema);
            if (innerSchema instanceof Schema.ResourceId r && r.registry() != null) registryId = r;
        }
        // or a companion tagged directly on the MapCodec key codec
        if (registryId == null && keyCodec instanceof MapCodec<?> mapKey) {
            Schema<?> keyCodecSchema = SchemaTags.lookupMap(mapKey);
            CodecUI.LOGGER.debug("registry-tag fallback: SchemaTags entry={}", keyCodecSchema);
            if (keyCodecSchema instanceof Schema.Record<?> rec && rec.fields().size() == 1) {
                Schema<?> fieldSchema = rec.fields().getFirst().schema();
                if (fieldSchema instanceof Schema.ResourceId r && r.registry() != null) registryId = r;
            }
        }
        if (registryId == null) return variants;

        try {
            Registry<?> registry = McCompat.getValue(BuiltInRegistries.REGISTRY, McCompat.keyId(registryId.registry()));
            if (registry == null) {
                CodecUI.LOGGER.warn("registry {} not found in BuiltInRegistries", registryId.registry());
                return variants;
            }
            // Small registries: the values are the dispatch keys, so decode each for a real body.
            // Large ones (Block, Item) stay opaque; 1000+ variant codecs is expensive and rarely useful.
            boolean resolveBodies = registry.size() <= 128 && KEY_DISPATCH_DECODER != null;
            Object decoderField = resolveBodies ? KEY_DISPATCH_DECODER.get(dispatch) : null;
            List<Identifier> ids = new ArrayList<>(registry.keySet());
            ids.sort(Comparator.comparing(Identifier::toString));
            int bodies = 0;
            for (Identifier id : ids) {
                Schema<?> body = new Schema.Opaque<>(null, null);
                if (decoderField instanceof Function<?, ?> decoder) {
                    Object value = McCompat.getValue(registry, id);
                    MapCodec<?> variantCodec = value == null ? null : applyDecoder((Function) decoder, value);
                    if (variantCodec != null) {
                        body = resolver.resolveMapCodec(variantCodec, cache);
                        bodies++;
                    }
                }
                variants.put(id.toString(), body);
            }
            CodecUI.LOGGER.debug("registry-backed dispatch: populated {} variants ({} with real bodies) from {}",
                    variants.size(), bodies, McCompat.keyId(registryId.registry()));
        } catch (Throwable t) {
            CodecUI.LOGGER.warn("Failed to enumerate registry {}: {}", registryId.registry(), t.toString());
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

    // ExtraCodecs.dispatchOptionalValue: a OneOf with a valueField, so the variant body nests
    // under that key (a criterion's "conditions") instead of flattening. Null when empty.
    @Nullable Schema<?> resolveOptionalValueDispatch(CodecReflection.OptionalValueDispatch dispatch,
                                                     IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = enumerateOptionalValueVariants(dispatch, cache);
        if (variants.isEmpty()) return null;
        return new Schema.OneOf<>(dispatch.typeKey(), variants, dispatch.valueKey());
    }

    // Only registry-keyed dispatches (the sole vanilla shape): the key codec resolves to a
    // ResourceId and each registry value fed to the codec-getter gives that variant's body.
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

    // Of the two captured getters (keyGetter V->K, codecGetter K->Codec), the codec-getter is
    // the one that returns a Codec for a registry value. One probe per getter is enough.
    private static @Nullable Function<Object, Object> pickCodecGetter(
            List<Function<Object, Object>> getters, Registry<?> registry, List<Identifier> ids) {
        for (Function<Object, Object> getter : getters) {
            for (Identifier id : ids) {
                Object value = McCompat.getValue(registry, id);
                if (value == null) continue;
                try {
                    if (asCodec(getter.apply(value)) != null) return getter;
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