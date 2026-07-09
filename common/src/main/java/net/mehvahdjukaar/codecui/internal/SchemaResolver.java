package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.CompoundListCodec;
import com.mojang.serialization.codecs.DispatchedMapCodec;
import com.mojang.serialization.codecs.EitherCodec;
import com.mojang.serialization.codecs.EitherMapCodec;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import com.mojang.serialization.codecs.ListCodec;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import com.mojang.serialization.codecs.PairCodec;
import com.mojang.serialization.codecs.PairMapCodec;
import com.mojang.serialization.codecs.SimpleMapCodec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import com.mojang.serialization.codecs.XorCodec;
import com.mojang.serialization.DataResult;
import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.EnumerableCodec;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.SchemaHandler;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * Walks a Codec graph and produces an introspected Schema. Falls back to {@link Schema.Opaque}
 * for anything we can't statically introspect (xmap, flatXmap, RecordCodecBuilder outputs, etc.).
 *
 * <p>Internal — external code goes through {@code SchemaCodec.wrap(...)} /
 * {@code SchemaResolvers}, and extends resolution via the public SPIs
 * ({@code SchemaHandler}, {@code EnumerableCodec}, companions, dispatch key hooks).</p>
 */
public final class SchemaResolver implements SchemaHandler.Resolver {

    private static final SchemaResolver INSTANCE = new SchemaResolver();

    // Registered via SchemaCodecs.registerHandler. Consulted after per-instance tags,
    // before the built-in structural tiers.
    private static final java.util.List<SchemaHandler> HANDLERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static SchemaResolver get() {
        return INSTANCE;
    }

    public static void registerHandler(SchemaHandler handler) {
        HANDLERS.add(handler);
    }

    // ---- VarHandles for private-field access on DFU codec classes ----

    private static final @Nullable VarHandle PAIR_CODEC_FIRST;
    private static final @Nullable VarHandle PAIR_CODEC_SECOND;

    private static final @Nullable VarHandle OPTIONAL_FIELD_NAME;
    private static final @Nullable VarHandle OPTIONAL_FIELD_ELEMENT;
    private static final @Nullable VarHandle OPTIONAL_FIELD_LENIENT;

    private static final @Nullable VarHandle PAIR_MAP_FIRST;
    private static final @Nullable VarHandle PAIR_MAP_SECOND;

    private static final @Nullable VarHandle KEY_DISPATCH_KEYCODEC;
    private static final @Nullable VarHandle KEY_DISPATCH_TYPE;
    private static final @Nullable VarHandle KEY_DISPATCH_DECODER;

    private static final @Nullable VarHandle SIMPLE_MAP_KEYCODEC;
    private static final @Nullable VarHandle SIMPLE_MAP_ELEMENT;
    private static final @Nullable VarHandle SIMPLE_MAP_KEYS;

    private static final @Nullable VarHandle RECURSIVE_WRAPPED;
    private static final @Nullable Class<?> RECURSIVE_MAP_CLASS;
    private static final @Nullable VarHandle RECURSIVE_MAP_WRAPPED;
    private static final @Nullable VarHandle COMPOUND_LIST_KEY;
    private static final @Nullable VarHandle COMPOUND_LIST_ELEMENT;
    private static final @Nullable VarHandle EITHER_MAP_FIRST;
    private static final @Nullable VarHandle EITHER_MAP_SECOND;

    private static final @Nullable VarHandle REGISTRY_FILE_KEY;
    private static final @Nullable VarHandle REGISTRY_FILE_ELEMENT;
    private static final @Nullable VarHandle REGISTRY_FILE_INLINE;
    private static final @Nullable VarHandle REGISTRY_FIXED_KEY;
    private static final @Nullable VarHandle HOLDER_SET_ELEMENT;

    static {
        VarHandle pf = null, ps = null;
        VarHandle ofn = null, ofe = null, ofl = null;
        VarHandle pmf = null, pms = null;
        VarHandle kdk = null, kdt = null, kdd = null;
        VarHandle smk = null, sme = null, sms = null;
        VarHandle rw = null, rmw = null;
        Class<?> rmc = null;
        VarHandle clk = null, cle = null;
        VarHandle emf = null, ems = null;
        VarHandle rfk = null, rfe = null, rfi = null;
        VarHandle rxk = null;
        VarHandle hse = null;
        try {
            var lookup = MethodHandles.privateLookupIn(PairCodec.class, MethodHandles.lookup());
            pf = lookup.findVarHandle(PairCodec.class, "first", Codec.class);
            ps = lookup.findVarHandle(PairCodec.class, "second", Codec.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(OptionalFieldCodec.class, MethodHandles.lookup());
            ofn = lookup.findVarHandle(OptionalFieldCodec.class, "name", String.class);
            ofe = lookup.findVarHandle(OptionalFieldCodec.class, "elementCodec", Codec.class);
            ofl = lookup.findVarHandle(OptionalFieldCodec.class, "lenient", boolean.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(PairMapCodec.class, MethodHandles.lookup());
            pmf = lookup.findVarHandle(PairMapCodec.class, "first", MapCodec.class);
            pms = lookup.findVarHandle(PairMapCodec.class, "second", MapCodec.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(KeyDispatchCodec.class, MethodHandles.lookup());
            kdk = lookup.findVarHandle(KeyDispatchCodec.class, "keyCodec", MapCodec.class);
            // KeyDispatchCodec field is named "type" (Function<? super V, ...>), used for the type field name.
            kdt = lookup.findVarHandle(KeyDispatchCodec.class, "type", java.util.function.Function.class);
            // "decoder" Function<? super K, DataResult<? extends MapDecoder<? extends V>>> — the
            // public constructor uses the same `codec` function as both decoder and source for the
            // (lazily-wrapped) encoder, so applying decoder to a candidate K yields the variant
            // MapCodec wrapped in DataResult. We use this for variant enumeration.
            kdd = lookup.findVarHandle(KeyDispatchCodec.class, "decoder", java.util.function.Function.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(SimpleMapCodec.class, MethodHandles.lookup());
            smk = lookup.findVarHandle(SimpleMapCodec.class, "keyCodec", Codec.class);
            sme = lookup.findVarHandle(SimpleMapCodec.class, "elementCodec", Codec.class);
            sms = lookup.findVarHandle(SimpleMapCodec.class, "keys", com.mojang.serialization.Keyable.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(Codec.RecursiveCodec.class, MethodHandles.lookup());
            rw = lookup.findVarHandle(Codec.RecursiveCodec.class, "wrapped", java.util.function.Supplier.class);
        } catch (Throwable ignored) {}
        try {
            rmc = Class.forName("com.mojang.serialization.MapCodec$RecursiveMapCodec");
            var lookup = MethodHandles.privateLookupIn(rmc, MethodHandles.lookup());
            rmw = lookup.findVarHandle(rmc, "wrapped", java.util.function.Supplier.class);
        } catch (Throwable ignored) {
            rmc = null;
        }
        try {
            var lookup = MethodHandles.privateLookupIn(CompoundListCodec.class, MethodHandles.lookup());
            clk = lookup.findVarHandle(CompoundListCodec.class, "keyCodec", Codec.class);
            cle = lookup.findVarHandle(CompoundListCodec.class, "elementCodec", Codec.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(EitherMapCodec.class, MethodHandles.lookup());
            emf = lookup.findVarHandle(EitherMapCodec.class, "first", MapCodec.class);
            ems = lookup.findVarHandle(EitherMapCodec.class, "second", MapCodec.class);
        } catch (Throwable ignored) {}

        try {
            var lookup = MethodHandles.privateLookupIn(RegistryFileCodec.class, MethodHandles.lookup());
            rfk = lookup.findVarHandle(RegistryFileCodec.class, "registryKey", ResourceKey.class);
            rfe = lookup.findVarHandle(RegistryFileCodec.class, "elementCodec", Codec.class);
            rfi = lookup.findVarHandle(RegistryFileCodec.class, "allowInline", boolean.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(RegistryFixedCodec.class, MethodHandles.lookup());
            rxk = lookup.findVarHandle(RegistryFixedCodec.class, "registryKey", ResourceKey.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(HolderSetCodec.class, MethodHandles.lookup());
            hse = lookup.findVarHandle(HolderSetCodec.class, "elementCodec", Codec.class);
        } catch (Throwable ignored) {}

        PAIR_CODEC_FIRST = pf;
        PAIR_CODEC_SECOND = ps;
        OPTIONAL_FIELD_NAME = ofn;
        OPTIONAL_FIELD_ELEMENT = ofe;
        OPTIONAL_FIELD_LENIENT = ofl;
        PAIR_MAP_FIRST = pmf;
        PAIR_MAP_SECOND = pms;
        KEY_DISPATCH_KEYCODEC = kdk;
        KEY_DISPATCH_TYPE = kdt;
        KEY_DISPATCH_DECODER = kdd;
        SIMPLE_MAP_KEYCODEC = smk;
        SIMPLE_MAP_ELEMENT = sme;
        SIMPLE_MAP_KEYS = sms;
        RECURSIVE_WRAPPED = rw;
        RECURSIVE_MAP_CLASS = rmc;
        RECURSIVE_MAP_WRAPPED = rmw;
        COMPOUND_LIST_KEY = clk;
        COMPOUND_LIST_ELEMENT = cle;
        EITHER_MAP_FIRST = emf;
        EITHER_MAP_SECOND = ems;
        REGISTRY_FILE_KEY = rfk;
        REGISTRY_FILE_ELEMENT = rfe;
        REGISTRY_FILE_INLINE = rfi;
        REGISTRY_FIXED_KEY = rxk;
        HOLDER_SET_ELEMENT = hse;
    }

    // Per-call cache; new IdentityHashMap each resolve() so it doesn't leak codecs.
    // Recursion is handled by inserting a Schema.Ref placeholder on entry; a recursive
    // lookup that hits the in-progress entry gets the Ref, which is bound to the real
    // schema on exit (lazy sub-editor in the UI).
    private static final ThreadLocal<IdentityHashMap<Object, Schema<?>>>  CACHE = ThreadLocal.withInitial(IdentityHashMap::new);

    private SchemaResolver() {}

    public <A> Schema<A> resolve(Codec<A> codec) {
        CuratedSchemas.bootstrap();
        IdentityHashMap<Object, Schema<?>> cache = CACHE.get();
        boolean owner = cache.isEmpty();
        try {
            return resolveCodec(codec, cache);
        } finally {
            if (owner) cache.clear();
        }
    }

    public <A> Schema<A> resolveMap(MapCodec<A> codec) {
        CuratedSchemas.bootstrap();
        IdentityHashMap<Object, Schema<?>> cache = CACHE.get();
        boolean owner = cache.isEmpty();
        try {
            return resolveMapCodec(codec, cache);
        } finally {
            if (owner) cache.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private <A> Schema<A> resolveCodec(Codec<A> codec, IdentityHashMap<Object, Schema<?>> cache) {
        Schema<?> cached = cache.get(codec);
        if (cached != null) return (Schema<A>) cached;

        // Tier 0: mixin-attached side-channel tag (manual companions, hand-tagged codecs).
        Schema<A> tagged = SchemaTags.lookup(codec);
        if (tagged != null) {
            cache.put(codec, tagged);
            return tagged;
        }

        // Tier 0d: lazy xmap/stable/etc. wrapper tag — delegate to inner FRESH.
        Codec<?> innerWrapped = net.mehvahdjukaar.codecui.internal.XmapTags.getCodec(codec);
        if (innerWrapped != null) {
            Schema<?> innerSchema = resolveCodec((Codec) innerWrapped, cache);
            cache.put(codec, innerSchema);
            return (Schema<A>) innerSchema;
        }

        // Insert a Ref placeholder so cycles short-circuit: a recursive lookup gets the Ref,
        // which we bind to the finished schema below — recursive fields render as lazily
        // expanded sub-editors instead of raw JSON.
        Schema.Ref<A> ref = new Schema.Ref<>();
        cache.put(codec, ref);

        Schema<A> result = (Schema<A>) tierCustomHandlers(codec, false);
        if (result == null) result = (Schema<A>) tierOnePrimitive(codec);
        if (result == null) result = (Schema<A>) tierTwoStructural(codec, cache);
        if (result == null) result = (Schema<A>) tierThreeReflective(codec, cache);
        // Tier 3.5: transform-free unwrap of DFU combinator wrappers (xmap/validate/orElse/…).
        if (result == null) result = (Schema<A>) unwrapCapturedInner(codec, cache);
        if (result == null) result = (Schema<A>) new Schema.Opaque<>(codec, null);

        ref.bind(result);
        cache.put(codec, result);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <A> Schema<A> resolveMapCodec(MapCodec<A> codec, IdentityHashMap<Object, Schema<?>> cache) {
        Schema<?> cached = cache.get(codec);
        if (cached != null) return (Schema<A>) cached;

        // Tier 0a: lazy fieldOf tag — resolve inner FRESH so companions registered after the
        // fieldOf mixin fired still take effect.
        net.mehvahdjukaar.codecui.internal.FieldOfTags.Entry foe =
                net.mehvahdjukaar.codecui.internal.FieldOfTags.get(codec);
        if (foe != null) {
            Schema<?> innerSchema = resolveCodec((Codec) foe.innerCodec(), cache);
            Schema.Field field = new Schema.Field(foe.name(), innerSchema, foe.optional(), foe.defaultValue());
            Schema rec = new Schema.Record(Object.class, java.util.List.of(field));
            cache.put(codec, rec);
            return rec;
        }

        // Tier 0b: lazy RecordCodecBuilder.build tag — rebuild the Schema.Record fresh from
        // the accumulated field entries. Cached only in the per-resolve cache (not SchemaTags),
        // so a companion registered after RCB.build() still affects the next resolve.
        java.util.List<RecordFieldTags.Entry> built = RecordFieldTags.getBuilt(codec);
        if (built != null && !built.isEmpty()) {
            Schema<?> rec = schemaFromRecordEntries(built, cache);
            cache.put(codec, rec);
            return (Schema<A>) rec;
        }

        // Tier 0c: eager side-channel tag (manual companion registrations via SchemaCodecs.registerCompanion).
        Schema<A> tagged = SchemaTags.lookupMap(codec);
        if (tagged != null) {
            cache.put(codec, tagged);
            return tagged;
        }

        // Tier 0d: lazy MapCodec xmap wrapper tag — delegate to inner FRESH.
        MapCodec<?> innerWrappedMap = net.mehvahdjukaar.codecui.internal.XmapTags.getMap(codec);
        if (innerWrappedMap != null) {
            Schema<?> innerSchema = resolveMapCodec((MapCodec) innerWrappedMap, cache);
            cache.put(codec, innerSchema);
            return (Schema<A>) innerSchema;
        }

        // Ref placeholder for cycles (see resolveCodec). Fallback is Opaque over codec() form.
        Schema.Ref<A> ref = new Schema.Ref<>();
        cache.put(codec, ref);

        Schema<A> result = (Schema<A>) tierCustomHandlers(codec, true);
        if (result == null) result = (Schema<A>) tierTwoMapStructural(codec, cache);
        if (result == null) result = (Schema<A>) tierThreeReflective(codec, cache);
        // Tier 3.5: transform-free unwrap. fieldOf first (rebuilds the named single-field record),
        // then RCB built-output recovery (NeoForge), then generic captured-inner unwrap.
        if (result == null) result = (Schema<A>) unwrapFieldOf(codec, cache);
        if (result == null) result = (Schema<A>) unwrapRecordCodec(codec, cache);
        if (result == null) result = (Schema<A>) unwrapCapturedInner(codec, cache);
        if (result == null) result = (Schema<A>) new Schema.Opaque<>(codec.codec(), null);

        ref.bind(result);
        cache.put(codec, result);
        return result;
    }

    // ---- Tier 0.5: user-registered SchemaHandlers (SchemaCodecs.registerHandler) ----
    //
    // The cache placeholder is already in place when these run, so handlers can freely
    // resolve inner codecs through the Resolver view without breaking cycle detection.

    private @Nullable Schema<?> tierCustomHandlers(Object codec, boolean isMapCodec) {
        for (SchemaHandler handler : HANDLERS) {
            try {
                Schema<?> schema = isMapCodec
                        ? handler.tryResolveMap((MapCodec<?>) codec, this)
                        : handler.tryResolve((Codec<?>) codec, this);
                if (schema != null) return schema;
            } catch (Throwable t) {
                CodecUI.LOGGER.warn("[codec_ui] SchemaHandler {} threw on {}: {}",
                        handler.getClass().getName(), codec.getClass().getName(), t.toString());
            }
        }
        return null;
    }

    // ---- Tier 1: identity match on Codec singletons ----

    private static Schema<?> tierOnePrimitive(Codec<?> codec) {
        if (codec == Codec.BOOL) return new Schema.Bool();
        if (codec == Codec.BYTE) return new Schema.IntRange(Byte.MIN_VALUE, Byte.MAX_VALUE);
        if (codec == Codec.SHORT) return new Schema.IntRange(Short.MIN_VALUE, Short.MAX_VALUE);
        if (codec == Codec.INT) return new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (codec == Codec.LONG) return new Schema.LongRange(Long.MIN_VALUE, Long.MAX_VALUE);
        if (codec == Codec.FLOAT) return new Schema.FloatRange(-Float.MAX_VALUE, Float.MAX_VALUE);
        if (codec == Codec.DOUBLE) return new Schema.DoubleRange(-Double.MAX_VALUE, Double.MAX_VALUE);
        if (codec == Codec.STRING) return new Schema.Str(0, Integer.MAX_VALUE, null);
        return null;
    }

    // ---- Tier 2: structural instanceof on concrete DFU codec classes ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Schema<?> tierTwoStructural(Codec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        // Authored schema-carrying codecs know their own schema. (Lazy variants re-enter the
        // resolver for inner codecs — the placeholder already in the cache guards cycles.)
        // A codecui SchemaCodec built by a mod WITHOUT our resolver (codecui standalone has no
        // inference engine) freezes any raw inner codec as Schema.Opaque. Since that Opaque still
        // carries the codec, we re-resolve those leaves here through the full pipeline — so a
        // third-party declaration's plain float/id/registry fields render as richly as our own.
        if (codec instanceof net.mehvahdjukaar.codecui.SchemaCodec<?> sc) {
            return enrichOpaques(sc.schema(), cache);
        }
        // MapCodec.codec() returns a MapCodecCodec record wrapping the underlying MapCodec.
        // Promote to the inner MapCodec resolution so dispatch codecs (KeyDispatchCodec, RCB.build
        // outputs) reach the MapCodec tier-2 path.
        if (codec instanceof MapCodec.MapCodecCodec<?> mcc) {
            return resolveMapCodec(mcc.codec(), cache);
        }
        if (codec instanceof ListCodec<?> list) {
            Schema<?> elem = resolveCodec(list.elementCodec(), cache);
            return new Schema.ListOf(elem, list.minSize(), list.maxSize());
        }
        if (codec instanceof EitherCodec<?, ?> either) {
            Schema<?> l = resolveCodec(either.first(), cache);
            Schema<?> r = resolveCodec(either.second(), cache);
            return Schema.anyOf(Schema.option(l), Schema.option(r));
        }
        if (codec instanceof UnboundedMapCodec<?, ?> map) {
            Schema<?> k = resolveCodec(map.keyCodec(), cache);
            Schema<?> v = resolveCodec(map.elementCodec(), cache);
            return new Schema.MapOf(k, v);
        }
        if (codec instanceof PairCodec<?, ?> pair && PAIR_CODEC_FIRST != null && PAIR_CODEC_SECOND != null) {
            Codec<?> first = (Codec<?>) PAIR_CODEC_FIRST.get(pair);
            Codec<?> second = (Codec<?>) PAIR_CODEC_SECOND.get(pair);
            Schema<?> f = resolveCodec(first, cache);
            Schema<?> s = resolveCodec(second, cache);
            return new Schema.PairOf(f, s);
        }
        if (codec instanceof XorCodec<?, ?> xor) {
            Schema<?> l = resolveCodec(xor.first(), cache);
            Schema<?> r = resolveCodec(xor.second(), cache);
            return Schema.anyOf(Schema.option(l), Schema.option(r));
        }
        // Codec.recursive / Codec.lazyInitialized. Forcing the memoized supplier is safe:
        // the placeholder already inserted for this codec short-circuits self-references,
        // which render as Opaque (raw JSON validated by the real codec).
        if (codec instanceof Codec.RecursiveCodec<?> rec && RECURSIVE_WRAPPED != null) {
            try {
                java.util.function.Supplier<?> sup = (java.util.function.Supplier<?>) RECURSIVE_WRAPPED.get(rec);
                Object inner = sup.get();
                if (inner instanceof Codec<?> c && c != codec) {
                    return resolveCodec(c, cache);
                }
            } catch (Throwable ignored) {}
        }
        // Encodes as a JSON object of key -> value entries, so a map editor is the right surface.
        if (codec instanceof CompoundListCodec<?, ?> cl && COMPOUND_LIST_KEY != null && COMPOUND_LIST_ELEMENT != null) {
            Schema<?> k = resolveCodec((Codec<?>) COMPOUND_LIST_KEY.get(cl), cache);
            Schema<?> v = resolveCodec((Codec<?>) COMPOUND_LIST_ELEMENT.get(cl), cache);
            return new Schema.MapOf(k, v);
        }
        // Value codec depends on the key via an opaque function; keys are still editable.
        if (codec instanceof DispatchedMapCodec<?, ?> dm) {
            Schema<?> k = resolveCodec(dm.keyCodec(), cache);
            return new Schema.MapOf(k, new Schema.Opaque<>(null, null));
        }
        // Holder<E> by registry id, optionally with an inline definition (SoundEvent.CODEC etc.).
        if (codec instanceof RegistryFileCodec<?> rfc && REGISTRY_FILE_KEY != null
                && REGISTRY_FILE_ELEMENT != null && REGISTRY_FILE_INLINE != null) {
            var key = (ResourceKey<? extends net.minecraft.core.Registry<?>>) REGISTRY_FILE_KEY.get(rfc);
            Schema<?> id = new Schema.ResourceId(key);
            if ((boolean) REGISTRY_FILE_INLINE.get(rfc)) {
                Schema<?> inline = resolveCodec((Codec<?>) REGISTRY_FILE_ELEMENT.get(rfc), cache);
                return Schema.anyOf(Schema.option("reference", id), Schema.option("inline", inline));
            }
            return id;
        }
        // Holder<E> strictly by registry id.
        if (codec instanceof RegistryFixedCodec<?> rfx && REGISTRY_FIXED_KEY != null) {
            return new Schema.ResourceId((ResourceKey<? extends net.minecraft.core.Registry<?>>) REGISTRY_FIXED_KEY.get(rfx));
        }
        // HolderSet<E>: a "#namespace:path" tag string, a single entry, or a list of entries.
        if (codec instanceof HolderSetCodec<?> hs && HOLDER_SET_ELEMENT != null) {
            Schema<?> element = resolveCodec((Codec<?>) HOLDER_SET_ELEMENT.get(hs), cache);
            Schema<?> tagOrId = new Schema.Str(0, Integer.MAX_VALUE, null);
            return Schema.anyOf(
                    Schema.option("#tag or id", tagOrId),
                    Schema.option("single", element),
                    Schema.option("list", new Schema.ListOf(element, 0, Integer.MAX_VALUE)));
        }
        // Custom registries etc. that expose their value set — a dropdown of registered names.
        if (codec instanceof EnumerableCodec en) {
            java.util.List<String> names = new java.util.ArrayList<>(en.codecUiValues().keySet());
            return new Schema.Enum<>(names, Function.identity());
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Schema<?> tierTwoMapStructural(MapCodec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        if (codec instanceof OptionalFieldCodec<?> opt && OPTIONAL_FIELD_NAME != null
                && OPTIONAL_FIELD_ELEMENT != null) {
            String name = (String) OPTIONAL_FIELD_NAME.get(opt);
            Codec<?> elem = (Codec<?>) OPTIONAL_FIELD_ELEMENT.get(opt);
            Schema<?> elemSchema = resolveCodec(elem, cache);
            // Represent an optional field as a single-field Record. Caller (the parent
            // RecordCodecBuilder) will normally have already produced its own Record schema;
            // this is the standalone fallback.
            Schema.Field field = new Schema.Field(name, elemSchema, true, null);
            return new Schema.Record(Object.class, java.util.List.of(field));
        }
        if (codec instanceof PairMapCodec<?, ?> pair && PAIR_MAP_FIRST != null && PAIR_MAP_SECOND != null) {
            MapCodec<?> first = (MapCodec<?>) PAIR_MAP_FIRST.get(pair);
            MapCodec<?> second = (MapCodec<?>) PAIR_MAP_SECOND.get(pair);
            Schema<?> f = resolveMapCodec(first, cache);
            Schema<?> s = resolveMapCodec(second, cache);
            return new Schema.PairOf(f, s);
        }
        if (codec instanceof KeyDispatchCodec<?, ?> dispatch && KEY_DISPATCH_KEYCODEC != null) {
            MapCodec<?> keyCodec = (MapCodec<?>) KEY_DISPATCH_KEYCODEC.get(dispatch);
            // typeKey is the JSON field name driving the dispatch. DFU stores it implicitly via
            // the keyCodec's keys(); we use the MapCodec's first key if we can, else "type".
            String typeKey = extractFirstKey(keyCodec);
            LinkedHashMap<String, Schema<?>> variants = enumerateDispatchVariants(dispatch, cache);

            // Generic fallback: if no registered hook matched, check whether the keyCodec is a
            // registry-backed codec (tagged with a single-field Record carrying a ResourceId).
            // If so, populate variants directly from that registry. Bodies stay Opaque since
            // resolving 1000+ per-variant codecs (e.g. every Block) is impractical.
            if (variants.isEmpty()) {
                variants = enumerateFromRegistryTag(keyCodec, dispatch, cache);
            }
            if (variants.isEmpty()) {
                // A OneOf with zero variants renders as a dead picker; raw JSON is strictly
                // more useful, and also keeps tier 3 from mis-guessing on the key codec field.
                return new Schema.Opaque<>(codec.codec(), null);
            }
            return new Schema.OneOf<>(typeKey, variants);
        }
        if (codec instanceof SimpleMapCodec<?, ?> simple && SIMPLE_MAP_KEYCODEC != null && SIMPLE_MAP_ELEMENT != null) {
            Codec<?> keyCodec = (Codec<?>) SIMPLE_MAP_KEYCODEC.get(simple);
            Codec<?> elemCodec = (Codec<?>) SIMPLE_MAP_ELEMENT.get(simple);
            Schema<?> k = resolveCodec(keyCodec, cache);
            Schema<?> v = resolveCodec(elemCodec, cache);
            return new Schema.MapOf(k, v);
        }
        if (codec instanceof EitherMapCodec<?, ?> em && EITHER_MAP_FIRST != null && EITHER_MAP_SECOND != null) {
            Schema<?> f = resolveMapCodec((MapCodec<?>) EITHER_MAP_FIRST.get(em), cache);
            Schema<?> s = resolveMapCodec((MapCodec<?>) EITHER_MAP_SECOND.get(em), cache);
            return Schema.anyOf(Schema.option(f), Schema.option(s));
        }
        // MapCodec.recursive — mirror of the RecursiveCodec handler above.
        if (RECURSIVE_MAP_CLASS != null && RECURSIVE_MAP_WRAPPED != null && RECURSIVE_MAP_CLASS.isInstance(codec)) {
            try {
                java.util.function.Supplier<?> sup = (java.util.function.Supplier<?>) RECURSIVE_MAP_WRAPPED.get(codec);
                Object inner = sup.get();
                if (inner instanceof MapCodec<?> mc && mc != codec) {
                    return resolveMapCodec(mc, cache);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // ---- Tier 3: reflective last resort over unknown (typically hand-rolled) codec classes ----

    /**
     * Scans the instance fields of an unknown Codec/MapCodec class for inner
     * {@code Codec}/{@code MapCodec} values (including {@code Codec[]} arrays) and guesses
     * the shape from what it finds:
     * <ul>
     *   <li>one inner codec → assume a shape-preserving wrapper, inherit its schema;</li>
     *   <li>a (key, element/value) field pair → assume a map codec, produce {@code MapOf};</li>
     *   <li>several inner codecs → assume "try each in order" alternatives (the dominant
     *       hand-rolled pattern: reference-or-inline, multi-format unions) and produce a
     *       flat {@code AnyOf} picker in declaration order.</li>
     * </ul>
     * Heuristic by design — a wrong guess is overridden by registering a tier-0 companion
     * for that codec. Only runs after every exact tier has passed.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Schema<?> tierThreeReflective(Object codec,
                                                                              IdentityHashMap<Object, Schema<?>> cache) {
        java.util.List<Object> inners = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            for (Class<?> cls = codec.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> t = f.getType();
                    boolean single = Codec.class.isAssignableFrom(t) || MapCodec.class.isAssignableFrom(t);
                    boolean array = t.isArray() && Codec.class.isAssignableFrom(t.getComponentType());
                    if (!single && !array) continue;
                    Object value;
                    try {
                        f.setAccessible(true);
                        value = f.get(codec);
                    } catch (Throwable inaccessible) {
                        continue;
                    }
                    if (value == null || value == codec) continue;
                    if (array) {
                        for (Object o : (Object[]) value) {
                            if (o != null && o != codec) {
                                inners.add(o);
                                names.add(f.getName().toLowerCase(java.util.Locale.ROOT));
                            }
                        }
                    } else {
                        inners.add(value);
                        names.add(f.getName().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        if (inners.isEmpty()) return null;

        CodecUI.LOGGER.debug("[codec_ui] tier-3 reflective guess for {} ({} inner codecs: {})",
                codec.getClass().getName(), inners.size(), names);
        if (inners.size() == 1) {
            return resolveAny(inners.get(0), cache);
        }
        if (inners.size() == 2) {
            String a = names.get(0), b = names.get(1);
            boolean aKey = a.contains("key"), bKey = b.contains("key");
            boolean aVal = a.contains("element") || a.contains("value");
            boolean bVal = b.contains("element") || b.contains("value");
            if (aKey && bVal) return new Schema.MapOf(resolveAny(inners.get(0), cache), resolveAny(inners.get(1), cache));
            if (bKey && aVal) return new Schema.MapOf(resolveAny(inners.get(1), cache), resolveAny(inners.get(0), cache));
        }
        // "Try each in order" alternatives — one flat picker, auto "#N kind" labels.
        java.util.List<Schema.AnyOf.Option> options = new java.util.ArrayList<>(inners.size());
        for (Object inner : inners) {
            options.add(Schema.option(resolveAny(inner, cache)));
        }
        return Schema.anyOf(options);
    }

    private Schema<?> resolveAny(Object inner, IdentityHashMap<Object, Schema<?>> cache) {
        if (inner instanceof Codec<?> c) return resolveCodec(c, cache);
        if (inner instanceof MapCodec<?> m) return resolveMapCodec(m, cache);
        return new Schema.Opaque<>(null, null);
    }

    // ---- Tier 3.5: transform-free unwrap of DFU combinator wrappers ----
    //
    // DFU's xmap/validate/orElse/comapFlatMap/… return a Codec.of(Encoder, Decoder) whose inner
    // codec is captured *inside* the Encoder/Decoder anonymous classes, and required fieldOf(name)
    // returns a MapCodec.of(FieldEncoder, FieldDecoder). Tier-3's Codec-typed field scan can't see
    // through those wrappers, so without a tag they'd fall back to raw JSON. On Fabric the
    // construction mixins tag these first (tiers 0a/0d) and none of this runs; on NeoForge the
    // mixins can't apply (DFU isn't on the transforming classloader), so this reflective recovery —
    // built on the same private-field reflection the structural tiers already use — is what keeps
    // those leaves editable. Conservative and companion-overridable, exactly like tier 3.

    /** {@code RecordCodecBuilder.build} output on NeoForge — walk the captured decoder tree. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Schema<?> unwrapRecordCodec(MapCodec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        java.util.List<RecordFieldTags.Entry> extracted = RecordCodecUnwrapper.extractFields(codec);
        if (extracted == null || extracted.isEmpty()) return null;
        CodecUI.LOGGER.debug("[codec_ui] tier-3.5 RCB unwrap for {} ({} fields)",
                codec.getClass().getName(), extracted.size());
        return schemaFromRecordEntries(extracted, cache);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Schema<?> schemaFromRecordEntries(java.util.List<RecordFieldTags.Entry> entries,
                                              IdentityHashMap<Object, Schema<?>> cache) {
        java.util.List<Schema.Field<?, ?>> fields = new java.util.ArrayList<>(entries.size());
        for (var e : entries) {
            Schema<?> fieldSchema;
            boolean optional;
            Object defaultValue = null;
            if (e.mapCodec() != null) {
                Schema<?> mapSchema = resolveMapCodec((MapCodec) e.mapCodec(), cache);
                if (mapSchema instanceof Schema.Record<?> rec && rec.fields().size() == 1) {
                    Schema.Field<?, ?> inner = rec.fields().get(0);
                    fieldSchema = inner.schema();
                    optional = inner.optional();
                    defaultValue = inner.defaultValue();
                } else {
                    fieldSchema = mapSchema;
                    optional = false;
                }
            } else {
                fieldSchema = resolveCodec((Codec) e.elementCodec(), cache);
                optional = false;
            }
            fields.add(new Schema.Field(e.name(), fieldSchema, optional, defaultValue));
        }
        return new Schema.Record(Object.class, java.util.List.copyOf(fields));
    }

    /** Required {@code Codec.fieldOf(name)} → {@code MapCodec.of(FieldEncoder, FieldDecoder, …)}:
     *  recover the field name + element codec and rebuild the single-field record (mirrors tier 0a). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Schema<?> unwrapFieldOf(MapCodec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        Object fieldDec = findFieldValueByClassName(codec, "FieldDecoder");
        if (fieldDec == null) return null;
        Object nameObj = readField(fieldDec, "name");
        Object elem = readField(fieldDec, "elementCodec");
        if (!(nameObj instanceof String name)) return null;
        Codec<?> inner = RecordCodecUnwrapper.decoderAsCodec(elem);
        if (inner == null) return null;
        Schema<?> innerSchema = resolveCodec(inner, cache);
        Schema.Field field = new Schema.Field(name, innerSchema, false, null);
        return new Schema.Record(Object.class, java.util.List.of(field));
    }

    /** Follow {@code Encoder}/{@code Decoder}/{@code MapEncoder}/{@code MapDecoder}-typed fields into
     *  the anonymous classes DFU builds for xmap/map/comap/validate/… to recover the single inner
     *  codec, then inherit its schema (shape-preserving-wrapper assumption, same as the mixin's
     *  inheritInner). Fires only when exactly one distinct inner codec is reachable. */
    private @Nullable Schema<?> unwrapCapturedInner(Object codec, IdentityHashMap<Object, Schema<?>> cache) {
        java.util.Set<Object> inners = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        collectCapturedInners(codec, codec, inners, visited, 0);
        if (inners.size() != 1) return null;
        return resolveAny(inners.iterator().next(), cache);
    }

    private void collectCapturedInners(Object root, Object current, java.util.Set<Object> inners,
                                       java.util.Set<Object> visited, int depth) {
        if (current == null || depth > 6 || !visited.add(current)) return;
        for (Class<?> cls = current.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                boolean wrapperTyped = com.mojang.serialization.Encoder.class.isAssignableFrom(t)
                        || com.mojang.serialization.Decoder.class.isAssignableFrom(t)
                        || com.mojang.serialization.MapEncoder.class.isAssignableFrom(t)
                        || com.mojang.serialization.MapDecoder.class.isAssignableFrom(t);
                if (!wrapperTyped) continue;
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(current);
                } catch (Throwable inaccessible) {
                    continue;
                }
                if (v == null || v == root || v == current) continue;
                if (v instanceof Codec<?> || v instanceof MapCodec<?>) {
                    inners.add(v);                                          // an actual inner codec
                } else {
                    collectCapturedInners(root, v, inners, visited, depth + 1); // a wrapper — descend
                }
            }
        }
    }

    private static @Nullable Object findFieldValueByClassName(Object obj, String simpleNameSuffix) {
        for (Class<?> cls = obj.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null && v.getClass().getSimpleName().endsWith(simpleNameSuffix)) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static @Nullable Object readField(Object obj, String name) {
        for (Class<?> cls = obj.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                // walk up
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /**
     * Walks a schema declared by a codecui {@code SchemaCodec} and replaces every
     * {@code Schema.Opaque} leaf that still carries a codec with the result of resolving that
     * codec through the full pipeline — but only when resolution yields something better than
     * raw JSON. This gives a third-party declaration's raw inner codecs (plain {@code Codec.FLOAT},
     * {@code ResourceLocation.CODEC}, registry/dispatch codecs, RCB records…) the same live
     * inference our own codecs get, instead of the Opaque fallback codecui bakes in without an
     * engine. Container schemas are rebuilt structurally; {@code Ref} nodes are left untouched
     * (recursion is already handled by the per-resolve cache).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Schema<?> enrichOpaques(Schema<?> schema, IdentityHashMap<Object, Schema<?>> cache) {
        if (schema instanceof Schema.Opaque<?> op) {
            Codec<?> inner = op.codec();
            if (inner == null) return schema;
            Schema<?> resolved = resolveCodec((Codec) inner, cache);
            return (resolved instanceof Schema.Opaque) ? schema : resolved;
        }
        if (schema instanceof Schema.Record<?> rec) {
            java.util.List<Schema.Field> out = new java.util.ArrayList<>();
            boolean changed = false;
            for (Schema.Field f : (java.util.List<Schema.Field>) (java.util.List) rec.fields()) {
                Schema<?> e = enrichOpaques(f.schema(), cache);
                changed |= e != f.schema();
                out.add(new Schema.Field(f.name(), e, f.optional(), f.defaultValue()));
            }
            return changed ? new Schema.Record(rec.type(), (java.util.List) out) : schema;
        }
        if (schema instanceof Schema.ListOf<?> l) {
            Schema<?> e = enrichOpaques(l.element(), cache);
            return e == l.element() ? schema : new Schema.ListOf(e, l.min(), l.max());
        }
        if (schema instanceof Schema.MapOf<?, ?> m) {
            Schema<?> k = enrichOpaques(m.key(), cache);
            Schema<?> v = enrichOpaques(m.value(), cache);
            return (k == m.key() && v == m.value()) ? schema : new Schema.MapOf(k, v);
        }
        if (schema instanceof Schema.PairOf<?, ?> p) {
            Schema<?> f = enrichOpaques(p.first(), cache);
            Schema<?> s = enrichOpaques(p.second(), cache);
            return (f == p.first() && s == p.second()) ? schema : new Schema.PairOf(f, s);
        }
        if (schema instanceof Schema.AnyOf<?> any) {
            java.util.List<Schema.AnyOf.Option> out = new java.util.ArrayList<>();
            boolean changed = false;
            for (Schema.AnyOf.Option o : any.options()) {
                Schema<?> e = enrichOpaques(o.schema(), cache);
                changed |= e != o.schema();
                out.add(new Schema.AnyOf.Option(o.label(), e));
            }
            return changed ? new Schema.AnyOf(java.util.List.copyOf(out)) : schema;
        }
        if (schema instanceof Schema.OneOf<?> one) {
            LinkedHashMap<String, Schema<?>> out = new LinkedHashMap<>();
            boolean changed = false;
            for (var en : ((java.util.Map<String, Schema<?>>) (java.util.Map) one.variants()).entrySet()) {
                Schema<?> e = enrichOpaques(en.getValue(), cache);
                changed |= e != en.getValue();
                out.put(en.getKey(), e);
            }
            return changed ? new Schema.OneOf(one.typeField(), (java.util.Map) out) : schema;
        }
        // Ref (cycle guard) + all leaf schemas (Bool, ranges, Str, ResourceId, Color, Enum,
        // Custom) are returned unchanged.
        return schema;
    }

    /**
     * Enumerates variants for a {@link KeyDispatchCodec}. Strategy:
     * <ol>
     *   <li>Bootstrap {@link VanillaDispatches} on first call (idempotent).</li>
     *   <li>Read the dispatch's private {@code decoder} field — a
     *       {@code Function<K, DataResult<MapDecoder<V>>>} (in practice {@code MapCodec<V>},
     *       since the public ctor of {@code KeyDispatchCodec} stores the user's codec function
     *       directly as the decoder).</li>
     *   <li>For every hook in {@link DispatchRegistry}, try applying the decoder to each known
     *       key. Any successful {@code DataResult} contributes a variant to the OneOf schema.</li>
     * </ol>
     *
     * <p>The reason for trying every hook (rather than picking one by K's class) is that the
     * dispatch doesn't expose K's runtime class — closures hide the type. We rely on
     * decoder.apply(K) failing fast for the wrong K type and returning a successful
     * {@code DataResult} only when K matches.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LinkedHashMap<String, Schema<?>> enumerateDispatchVariants(KeyDispatchCodec<?, ?> dispatch,
                                                                       IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();
        CodecUI.LOGGER.debug("[codec_ui] enumerateDispatchVariants called for {}", dispatch.getClass().getName());

        if (KEY_DISPATCH_DECODER == null) {
            CodecUI.LOGGER.warn("[codec_ui]   KEY_DISPATCH_DECODER VarHandle is null — field lookup failed at init");
            return variants;
        }
        VanillaDispatches.bootstrap();
        CodecUI.LOGGER.debug("[codec_ui]   DispatchRegistry has {} hooks", DispatchRegistry.all().size());

        Object decoderFn = KEY_DISPATCH_DECODER.get(dispatch);
        if (!(decoderFn instanceof Function<?, ?> fn)) {
            CodecUI.LOGGER.warn("[codec_ui]   decoder field is not a Function (got {})",
                    decoderFn == null ? "null" : decoderFn.getClass().getName());
            return variants;
        }

        // Path 0: ask the key codec itself. The stored keyCodec is the user codec wrapped in
        // fieldOf(typeKey); FieldOfTags gives us the inner codec back. If it implements
        // EnumerableCodec (custom registries like MapRegistry) or resolves to a Schema.Enum
        // (StringRepresentable codecs), we know the exact key set of THIS dispatch — feed
        // each key through the decoder and get real variant bodies.
        if (KEY_DISPATCH_KEYCODEC != null) {
            MapCodec<?> keyCodec = (MapCodec<?>) KEY_DISPATCH_KEYCODEC.get(dispatch);
            FieldOfTags.Entry foe = keyCodec == null ? null : FieldOfTags.get(keyCodec);
            Codec<?> innerKey = foe != null ? foe.innerCodec() : null;
            if (innerKey instanceof EnumerableCodec en) {
                for (var e : en.codecUiValues().entrySet()) {
                    MapCodec<?> variantCodec = applyDecoder((Function) fn, e.getValue());
                    if (variantCodec == null) continue;
                    variants.put(e.getKey(), resolveMapCodec(variantCodec, cache));
                }
            } else if (innerKey != null
                    && resolveCodec((Codec) innerKey, cache) instanceof Schema.Enum keyEnum) {
                for (Object k : keyEnum.options()) {
                    MapCodec<?> variantCodec = applyDecoder((Function) fn, k);
                    if (variantCodec == null) continue;
                    String name = ((Function<Object, String>) keyEnum.label()).apply(k);
                    variants.put(name, resolveMapCodec(variantCodec, cache));
                }
            }
            if (!variants.isEmpty()) {
                CodecUI.LOGGER.debug("[codec_ui]   key-codec enumeration produced {} variants", variants.size());
                return variants;
            }
        }

        for (DispatchRegistry.Hook<?> hook : DispatchRegistry.all()) {
            CodecUI.LOGGER.debug("[codec_ui]   trying hook {} with {} keys", hook.keyType().getName(), hook.keys().get().size());
            boolean any = false;
            LinkedHashMap<String, Schema<?>> local = new LinkedHashMap<>();
            for (Object k : hook.keys().get()) {
                MapCodec<?> variantCodec = applyDecoder((Function) fn, k);
                if (variantCodec == null) continue;
                any = true;
                Schema<?> variantSchema = resolveMapCodec(variantCodec, cache);
                String name = ((Function<Object, String>) hook.nameOf()).apply(k);
                local.put(name, variantSchema);
            }
            CodecUI.LOGGER.debug("[codec_ui]   hook {} produced {} variants", hook.keyType().getSimpleName(), local.size());
            if (any) {
                variants.putAll(local);
                break;
            }
        }

        // (Old name-only fallback removed: it picked the WRONG hook for unknown-K dispatches
        // because hook.codecOf.apply(k) succeeds for any registered K regardless of whether
        // the dispatch's actual K matches. Result: BlockState's dispatch got IntProvider variants.)

        CodecUI.LOGGER.debug("[codec_ui]   final variant count: {}", variants.size());
        return variants;
    }

    /**
     * Applies the dispatch's decoder function to a candidate key and unwraps the resulting
     * {@code DataResult<MapDecoder<? extends V>>} into a {@link MapCodec} (which is the concrete
     * type stored in practice by the public {@code KeyDispatchCodec} constructor).
     * Returns null on any failure: ClassCastException from a wrong-K hook, error DataResult, etc.
     */
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
    private LinkedHashMap<String, Schema<?>> enumerateFromRegistryTag(MapCodec<?> keyCodec, KeyDispatchCodec<?, ?> dispatch,
                                                                      IdentityHashMap<Object, Schema<?>> cache) {
        LinkedHashMap<String, Schema<?>> variants = new LinkedHashMap<>();

        // Try the lazy FieldOfTags first (typical case: BLOCK.byNameCodec().fieldOf("Name")).
        // Resolve the inner Codec fresh and check if its schema is a ResourceId.
        Schema.ResourceId rid = null;
        net.mehvahdjukaar.codecui.internal.FieldOfTags.Entry foe =
                net.mehvahdjukaar.codecui.internal.FieldOfTags.get(keyCodec);
        if (foe != null) {
            IdentityHashMap<Object, Schema<?>> tmpCache = new IdentityHashMap<>();
            Schema<?> innerSchema = resolveCodec((Codec) foe.innerCodec(), tmpCache);
            CodecUI.LOGGER.debug("[codec_ui]   registry-tag fallback: FieldOfTags inner={}, schema={}",
                    foe.innerCodec().getClass().getSimpleName(), innerSchema);
            if (innerSchema instanceof Schema.ResourceId r && r.registry() != null) rid = r;
        }
        // Fall back to eager SchemaTags entry (manual companion tagging on the keyCodec).
        if (rid == null) {
            Schema<?> keyCodecSchema = SchemaTags.lookupMap(keyCodec);
            CodecUI.LOGGER.debug("[codec_ui]   registry-tag fallback: SchemaTags entry={}", keyCodecSchema);
            if (keyCodecSchema instanceof Schema.Record<?> rec && rec.fields().size() == 1) {
                Schema<?> fs = rec.fields().get(0).schema();
                if (fs instanceof Schema.ResourceId r && r.registry() != null) rid = r;
            }
        }
        if (rid == null) return variants;

        try {
            var holderOpt = net.minecraft.core.registries.BuiltInRegistries.REGISTRY.get(rid.registry().identifier());
            if (holderOpt.isEmpty()) {
                CodecUI.LOGGER.warn("[codec_ui]   registry {} not found in BuiltInRegistries", rid.registry());
                return variants;
            }
            net.minecraft.core.Registry<?> registry = (net.minecraft.core.Registry<?>) holderOpt.get().value();
            // For small registries (rule tests, height providers, ...) the registry VALUES are
            // the dispatch keys themselves — feed each through the decoder for a real variant
            // body. Large registries (Block, Item, ...) stay name-only with opaque bodies.
            boolean resolveBodies = registry.size() <= 128 && KEY_DISPATCH_DECODER != null;
            Object decoderFn = resolveBodies ? KEY_DISPATCH_DECODER.get(dispatch) : null;
            java.util.List<net.minecraft.resources.Identifier> ids = new java.util.ArrayList<>(registry.keySet());
            ids.sort(java.util.Comparator.comparing(net.minecraft.resources.Identifier::toString));
            int bodies = 0;
            for (net.minecraft.resources.Identifier id : ids) {
                Schema<?> body = new Schema.Opaque<>(null, null);
                if (decoderFn instanceof Function<?, ?> fn) {
                    Object value = registry.getValue(id);
                    MapCodec<?> variantCodec = value == null ? null : applyDecoder((Function) fn, value);
                    if (variantCodec != null) {
                        body = resolveMapCodec(variantCodec, cache);
                        bodies++;
                    }
                }
                variants.put(id.toString(), body);
            }
            CodecUI.LOGGER.debug("[codec_ui]   registry-backed dispatch: populated {} variants ({} with real bodies) from {}",
                    variants.size(), bodies, rid.registry().identifier());
        } catch (Throwable t) {
            CodecUI.LOGGER.warn("[codec_ui]   Failed to enumerate registry {}: {}", rid.registry(), t.toString());
        }
        return variants;
    }

    private static String extractFirstKey(MapCodec<?> keyCodec) {
        try {
            return keyCodec.keys(com.mojang.serialization.JsonOps.INSTANCE)
                    .map(SchemaResolver::unwrapJsonKey)
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
}
