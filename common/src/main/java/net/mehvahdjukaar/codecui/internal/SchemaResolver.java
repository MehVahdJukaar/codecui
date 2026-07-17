package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.BaseMapCodec;
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
import com.mojang.serialization.codecs.XorCodec;
import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.EnumerableCodec;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.SchemaHandler;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.mehvahdjukaar.codecui.internal.CodecFieldHandles.*;

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
    private static final List<SchemaHandler> HANDLERS = new CopyOnWriteArrayList<>();

    public static SchemaResolver get() {
        return INSTANCE;
    }

    public static void registerHandler(SchemaHandler handler) {
        HANDLERS.add(handler);
    }

    // Private-field VarHandles for DFU/MC codec classes live in CodecFieldHandles (static-imported).

    // Per-call cache; new IdentityHashMap each resolve() so it doesn't leak codecs.
    // Recursion is handled by inserting a Schema.Ref placeholder on entry; a recursive
    // lookup that hits the in-progress entry gets the Ref, which is bound to the real
    // schema on exit (lazy sub-editor in the UI).
    private static final ThreadLocal<IdentityHashMap<Object, Schema<?>>>  CACHE = ThreadLocal.withInitial(IdentityHashMap::new);

    private final DispatchEnumerator dispatchEnumerator = new DispatchEnumerator(this);

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
    <A> Schema<A> resolveCodec(Codec<A> codec, IdentityHashMap<Object, Schema<?>> cache) {
        Schema<?> cached = cache.get(codec);
        if (cached != null) return (Schema<A>) cached;

        // Tier 0: mixin-attached side-channel tag (manual companions, hand-tagged codecs).
        Schema<A> tagged = SchemaTags.lookup(codec);
        if (tagged != null) {
            cache.put(codec, tagged);
            return tagged;
        }

        // Tier 0d: lazy xmap/stable/etc. wrapper tag — delegate to inner FRESH.
        Codec<?> innerWrapped = XmapTags.getCodec(codec);
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
        // Range recovery runs first: intRange/floatRange/doubleRange are flatXmaps over a primitive,
        // so the generic unwrap below would collapse them to an unbounded number and drop the bounds.
        if (result == null) result = (Schema<A>) recoverPrimitiveRange(codec);
        if (result == null) result = (Schema<A>) unwrapCapturedInner(codec, cache);
        if (result == null) result = (Schema<A>) new Schema.Opaque<>(codec, null);

        ref.bind(result);
        cache.put(codec, result);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <A> Schema<A> resolveMapCodec(MapCodec<A> codec, IdentityHashMap<Object, Schema<?>> cache) {
        Schema<?> cached = cache.get(codec);
        if (cached != null) return (Schema<A>) cached;

        // Tier 0a: lazy fieldOf tag — resolve inner FRESH so companions registered after the
        // fieldOf mixin fired still take effect.
        FieldOfTags.Entry foe =
                FieldOfTags.get(codec);
        if (foe != null) {
            Schema<?> innerSchema = resolveCodec((Codec) foe.innerCodec(), cache);
            Schema.Field field = new Schema.Field(foe.name(), innerSchema, foe.optional(), foe.defaultValue());
            Schema rec = new Schema.Record(Object.class, List.of(field));
            cache.put(codec, rec);
            return rec;
        }

        // Tier 0b: lazy RecordCodecBuilder.build tag — rebuild the Schema.Record fresh from
        // the accumulated field entries. Cached only in the per-resolve cache (not SchemaTags),
        // so a companion registered after RCB.build() still affects the next resolve.
        List<RecordFieldTags.Entry> built = RecordFieldTags.getBuilt(codec);
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
        MapCodec<?> innerWrappedMap = XmapTags.getMap(codec);
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
                CodecUI.LOGGER.warn("SchemaHandler {} threw on {}: {}",
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
        if (codec instanceof MapCodec.MapCodecCodec<?>(MapCodec<?> codec1)) {
            return resolveMapCodec(codec1, cache);
        }
        if (codec instanceof ListCodec<?>(Codec<?> elementCodec, int minSize, int maxSize)) {
            Schema<?> elem = resolveCodec(elementCodec, cache);
            return new Schema.ListOf(elem, minSize, maxSize);
        }
        // Our lenient list (drops undecodable elements) renders as a plain unbounded list.
        if (codec instanceof LenientListCodec<?> ll) {
            Schema<?> elem = resolveCodec(ll.elementCodec(), cache);
            return new Schema.ListOf(elem, 0, Integer.MAX_VALUE);
        }
        if (codec instanceof EitherCodec<?, ?>(Codec<?> first1, Codec<?> second1)) {
            Schema<?> l = resolveCodec(first1, cache);
            Schema<?> r = resolveCodec(second1, cache);
            return Schema.anyOf(Schema.option(l), Schema.option(r));
        }
        // Unbounded map-style Codec<Map<K,V>>. DFU's UnboundedMapCodec, vanilla's
        // ExtraCodecs.StrictUnboundedMapCodec and our own LenientUnboundedMapCodec all implement
        // BaseMapCodec, which publicly exposes the key/element codecs — so one branch covers every
        // present and future variant without per-type reflection. (SimpleMapCodec also implements
        // BaseMapCodec but is a MapCodec, handled on the map path; it never reaches here.)
        if (codec instanceof BaseMapCodec<?, ?> bm) {
            Schema<?> k = resolveCodec(bm.keyCodec(), cache);
            Schema<?> v = resolveCodec(bm.elementCodec(), cache);
            return new Schema.MapOf(k, v);
        }
        if (codec instanceof PairCodec<?, ?> pair && PAIR_CODEC_FIRST != null && PAIR_CODEC_SECOND != null) {
            Codec<?> first = (Codec<?>) PAIR_CODEC_FIRST.get(pair);
            Codec<?> second = (Codec<?>) PAIR_CODEC_SECOND.get(pair);
            Schema<?> f = resolveCodec(first, cache);
            Schema<?> s = resolveCodec(second, cache);
            return new Schema.PairOf(f, s);
        }
        if (codec instanceof XorCodec<?, ?>(Codec<?> first, Codec<?> second)) {
            Schema<?> l = resolveCodec(first, cache);
            Schema<?> r = resolveCodec(second, cache);
            return Schema.anyOf(Schema.option(l), Schema.option(r));
        }
        // Codec.recursive / Codec.lazyInitialized. Forcing the memoized supplier is safe:
        // the placeholder already inserted for this codec short-circuits self-references,
        // which render as Opaque (raw JSON validated by the real codec).
        if (codec instanceof Codec.RecursiveCodec<?> rec && RECURSIVE_WRAPPED != null) {
            try {
                Supplier<?> sup = (Supplier<?>) RECURSIVE_WRAPPED.get(rec);
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
        if (codec instanceof RegistryFileCodec<?> rfc) {
            var key = (ResourceKey<? extends Registry<?>>) rfc.registryKey;
            Schema<?> id = new Schema.ResourceId(key);
            if (rfc.allowInline) {
                Schema<?> inline = resolveCodec(rfc.elementCodec, cache);
                return Schema.anyOf(Schema.option("reference", id), Schema.option("inline", inline));
            }
            return id;
        }
        // Holder<E> strictly by registry id.
        if (codec instanceof RegistryFixedCodec<?> rfx) {
            return new Schema.ResourceId(rfx.registryKey);
        }
        // Recursive HolderSet<E>: like the vanilla one but every list element is itself a
        // tag / single / list. cache.get(codec) is the Ref placeholder inserted on entry; using
        // it as the list element makes the schema self-recursive once this call binds the Ref.
        if (codec instanceof RecursiveHolderSetCodec<?> rhs) {
            Schema<?> element = resolveCodec(rhs.elementCodec(), cache);
            ResourceKey<? extends Registry<?>> registry =
                    element instanceof Schema.ResourceId r ? r.registry() : null;
            Schema<?> tag = registry != null
                    ? new Schema.TagId(registry)
                    : new Schema.Str(0, Integer.MAX_VALUE, null);
            Schema<?> self = cache.get(codec);
            Schema<?> node = self != null ? self : element;
            return Schema.anyOf(
                    Schema.option("tag", tag),
                    Schema.option("single", element),
                    Schema.option("list", new Schema.ListOf(node, 0, Integer.MAX_VALUE)));
        }
        // HolderSet<E> (vanilla or our lenient copy): a "#namespace:path" tag, a single entry,
        // or a list of entries. Both expose the same element codec, so share one schema shape.
        Codec<?> holderSetElement = codec instanceof HolderSetCodec<?> hs ? hs.elementCodec
                : codec instanceof LenientHolderSetCodec<?> lhs ? lhs.elementCodec() : null;
        if (holderSetElement != null) {
            Schema<?> element = resolveCodec(holderSetElement, cache);
            // The element schema is the registry's ResourceId — reuse its key so the tag side
            // gets a real tag picker instead of a raw text box. Falls back to text if unknown.
            ResourceKey<? extends Registry<?>> registry =
                    element instanceof Schema.ResourceId r ? r.registry() : null;
            Schema<?> tag = registry != null
                    ? new Schema.TagId(registry)
                    : new Schema.Str(0, Integer.MAX_VALUE, null);
            return Schema.anyOf(
                    Schema.option("tag", tag),
                    Schema.option("single", element),
                    Schema.option("list", new Schema.ListOf(element, 0, Integer.MAX_VALUE)));
        }
        // Custom registries etc. that expose their value set — a dropdown of registered names.
        if (codec instanceof EnumerableCodec en) {
            List<String> names = new ArrayList<>(en.codecUiValues().keySet());
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
            return new Schema.Record(Object.class, List.of(field));
        }
        if (codec instanceof PairMapCodec<?, ?> pair && PAIR_MAP_FIRST != null && PAIR_MAP_SECOND != null) {
            MapCodec<?> first = (MapCodec<?>) PAIR_MAP_FIRST.get(pair);
            MapCodec<?> second = (MapCodec<?>) PAIR_MAP_SECOND.get(pair);
            Schema<?> f = resolveMapCodec(first, cache);
            Schema<?> s = resolveMapCodec(second, cache);
            return new Schema.PairOf(f, s);
        }
        if (codec instanceof KeyDispatchCodec<?, ?> dispatch && KEY_DISPATCH_KEYCODEC != null) {
            return dispatchEnumerator.resolve(dispatch, codec, cache);
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
                Supplier<?> sup = (Supplier<?>) RECURSIVE_MAP_WRAPPED.get(codec);
                Object inner = sup.get();
                if (inner instanceof MapCodec<?> mc && mc != codec) {
                    return resolveMapCodec(mc, cache);
                }
            } catch (Throwable ignored) {}
        }
        // ExtraCodecs.dispatchOptionalValue (advancement criteria etc.): an anonymous MapCodec that
        // nests its dispatched body under a value key. Not a KeyDispatchCodec, so it must be matched
        // structurally before tier-3 mistakes it for a scalar wrapper around the key codec.
        CodecReflection.OptionalValueDispatch ovd = CodecReflection.detectOptionalValueDispatch(codec);
        if (ovd != null) {
            Schema<?> dispatched = dispatchEnumerator.resolveOptionalValueDispatch(ovd, cache);
            if (dispatched != null) return dispatched;
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
        List<CodecReflection.ScannedInner> scanned = CodecReflection.scanInnerCodecs(codec);
        if (scanned.isEmpty()) return null;

        List<Object> inners = new ArrayList<>(scanned.size());
        List<String> names = new ArrayList<>(scanned.size());
        for (CodecReflection.ScannedInner s : scanned) {
            inners.add(s.value());
            names.add(s.fieldName());
        }

        CodecUI.LOGGER.debug("tier-3 reflective guess for {} ({} inner codecs: {})",
                codec.getClass().getName(), inners.size(), names);
        if (inners.size() == 1) {
            return resolveAny(inners.getFirst(), cache);
        }
        if (inners.size() == 2) {
            String a = names.get(0), b = names.get(1);
            boolean aKey = a.contains("key"), bKey = b.contains("key");
            boolean aVal = a.contains("element") || a.contains("value");
            boolean bVal = b.contains("element") || b.contains("value");
            if (aKey && bVal) return new Schema.MapOf(resolveAny(inners.get(0), cache), resolveAny(inners.get(1), cache));
            if (bKey && aVal) return new Schema.MapOf(resolveAny(inners.get(1), cache), resolveAny(inners.get(0), cache));
        }
        List<Schema.AnyOf.Option> options = new ArrayList<>(inners.size());
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

    private @Nullable Schema<?> unwrapRecordCodec(MapCodec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        List<RecordFieldTags.Entry> extracted = CodecReflection.extractRecordFields(codec);
        if (extracted == null || extracted.isEmpty()) return null;
        CodecUI.LOGGER.debug("tier-3.5 RCB unwrap for {} ({} fields)",
                codec.getClass().getName(), extracted.size());
        return schemaFromRecordEntries(extracted, cache);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Schema<?> schemaFromRecordEntries(List<RecordFieldTags.Entry> entries,
                                              IdentityHashMap<Object, Schema<?>> cache) {
        List<Schema.Field<?, ?>> fields = new ArrayList<>(entries.size());
        for (var e : entries) {
            Schema<?> fieldSchema;
            boolean optional;
            Object defaultValue = null;
            if (e.mapCodec() != null) {
                Schema<?> mapSchema = resolveMapCodec((MapCodec) e.mapCodec(), cache);
                // A raw MapCodec included in a group — of(getter, MapCodec) — flattens its keys
                // straight into the parent record (DFU never nests it). Only fieldOf("x") wraps a
                // value into a single-key map. So a single-field Record is the fieldOf form
                // (unwrap to one named field), while a multi-field Record is a grouped sub-codec
                // like ClimateSettings / MobSpawnSettings whose fields must be SPREAD into the
                // parent — nesting them under the sub-codec's first key produced malformed JSON
                // (e.g. spawners/spawn_costs buried inside a phantom creature_spawn_probability).
                if (mapSchema instanceof Schema.Record<?> rec) {
                    if (rec.fields().size() == 1) {
                        Schema.Field<?, ?> inner = rec.fields().getFirst();
                        addField(fields, new Schema.Field(e.name(), inner.schema(), inner.optional(), inner.defaultValue()));
                    } else {
                        for (Schema.Field<?, ?> f : rec.fields()) addField(fields, f);
                    }
                    continue;
                }
                // A grouped MapCodec that spans several flat keys but can't be spread into static
                // named fields - a dispatch (OneOf), pair, map, or either-of-maps (AnyOf). It still
                // flattens into the parent, so mark it inline and let the backend merge its object
                // flat (see Schema.Field.inline). Anything else (an Opaque that swallowed the whole
                // object, a lone scalar) keeps the old nested-under-name behavior.
                boolean flattenable = mapSchema instanceof Schema.OneOf<?>
                        || mapSchema instanceof Schema.PairOf<?, ?>
                        || mapSchema instanceof Schema.MapOf<?, ?>
                        || mapSchema instanceof Schema.AnyOf<?>;
                addField(fields, new Schema.Field(e.name(), mapSchema, false, null, flattenable));
                continue;
            } else {
                fieldSchema = resolveCodec((Codec) e.elementCodec(), cache);
            }
            optional = false;
            addField(fields, new Schema.Field(e.name(), fieldSchema, optional, defaultValue));
        }
        return new Schema.Record(Object.class, List.copyOf(fields));
    }

    /** Appends a field unless one with the same name is already present (mirrors the NeoForge
     *  decoder-tree walk's name dedup). Guards against a spread sub-record colliding with a
     *  sibling field of the same key. */
    private static void addField(List<Schema.Field<?, ?>> fields, Schema.Field<?, ?> field) {
        for (Schema.Field<?, ?> existing : fields) {
            if (existing.name().equals(field.name())) return;
        }
        fields.add(field);
    }

    /** Required {@code Codec.fieldOf(name)} → {@code MapCodec.of(FieldEncoder, FieldDecoder, …)}:
     *  recover the field name + element codec and rebuild the single-field record (mirrors tier 0a). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Schema<?> unwrapFieldOf(MapCodec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        CodecReflection.FieldOfEntry field = CodecReflection.unwrapFieldOf(codec);
        if (field == null) return null;
        Schema<?> innerSchema = resolveCodec(field.elementCodec(), cache);
        Schema.Field f = new Schema.Field(field.name(), innerSchema, false, null);
        return new Schema.Record(Object.class, List.of(f));
    }

    /** Follow {@code Encoder}/{@code Decoder}/{@code MapEncoder}/{@code MapDecoder}-typed fields into
     *  the anonymous classes DFU builds for xmap/map/comap/validate/… to recover the single inner
     *  codec, then inherit its schema (shape-preserving-wrapper assumption, same as the mixin's
     *  inheritInner). Fires only when exactly one distinct inner codec is reachable. */
    private @Nullable Schema<?> unwrapCapturedInner(Object codec, IdentityHashMap<Object, Schema<?>> cache) {
        Object inner = CodecReflection.singleCapturedInner(codec);
        return inner == null ? null : resolveAny(inner, cache);
    }

    /** Reflective fallback for {@code Codec.intRange/floatRange/doubleRange} when the construction
     *  mixin didn't apply (Tier 0 {@code SchemaTags} absent). DFU builds these as
     *  {@code PRIMITIVE.flatXmap(checkRange(min,max), …)} — a {@code [flatXmapped]} wrapper over a
     *  primitive number codec — so we recover the bounds from the checker lambda's captures. Gated
     *  on both the flatXmap marker and the inner being exactly INT/FLOAT/DOUBLE, so an unrelated
     *  flatXmap can't be misread as a range; falls through to the unbounded primitive if unsure. */
    private @Nullable Schema<?> recoverPrimitiveRange(Object codec) {
        if (!(codec instanceof Codec<?>) || !codec.toString().endsWith("[flatXmapped]")) return null;
        Object inner = CodecReflection.singleCapturedInner(codec);
        if (inner != Codec.INT && inner != Codec.FLOAT && inner != Codec.DOUBLE) return null;

        Number[] bounds = CodecReflection.recoverRangeBounds(codec);
        if (bounds == null) return null;
        Number min = bounds[0], max = bounds[1];
        if (inner == Codec.INT) return new Schema.IntRange(min.intValue(), max.intValue());
        if (inner == Codec.FLOAT) return new Schema.FloatRange(min.floatValue(), max.floatValue());
        return new Schema.DoubleRange(min.doubleValue(), max.doubleValue());
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
            // Not deconstructed: Record<?>'s `fields` component is List<Field<CAP,?>>, which a record
            // pattern can't name as List<Field<?,?>> (lists are invariant). Bind + accessor instead.
            List<Schema.Field> out = new ArrayList<>();
            boolean changed = false;
            for (Schema.Field f : (List<Schema.Field>) (List) rec.fields()) {
                Schema<?> e = enrichOpaques(f.schema(), cache);
                changed |= e != f.schema();
                out.add(new Schema.Field(f.name(), e, f.optional(), f.defaultValue()));
            }
            return changed ? new Schema.Record(rec.type(), out) : schema;
        }
        if (schema instanceof Schema.ListOf<?>(Schema<?> element, int min, int max)) {
            Schema<?> e = enrichOpaques(element, cache);
            return e == element ? schema : new Schema.ListOf(e, min, max);
        }
        if (schema instanceof Schema.MapOf<?, ?>(Schema<?> key, Schema<?> value)) {
            Schema<?> k = enrichOpaques(key, cache);
            Schema<?> v = enrichOpaques(value, cache);
            return (k == key && v == value) ? schema : new Schema.MapOf(k, v);
        }
        if (schema instanceof Schema.PairOf<?, ?>(Schema<?> first, Schema<?> second)) {
            Schema<?> f = enrichOpaques(first, cache);
            Schema<?> s = enrichOpaques(second, cache);
            return (f == first && s == second) ? schema : new Schema.PairOf(f, s);
        }
        if (schema instanceof Schema.AnyOf<?>(List<Schema.AnyOf.Option> options)) {
            List<Schema.AnyOf.Option> out = new ArrayList<>();
            boolean changed = false;
            for (Schema.AnyOf.Option o : options) {
                Schema<?> e = enrichOpaques(o.schema(), cache);
                changed |= e != o.schema();
                out.add(new Schema.AnyOf.Option(o.label(), e));
            }
            return changed ? new Schema.AnyOf(List.copyOf(out)) : schema;
        }
        if (schema instanceof Schema.OneOf<?> one) {
            // Not deconstructed: OneOf<?>'s `variants` component is Map<String, Schema<? extends CAP>>,
            // which a record pattern can't name as Map<String, Schema<?>>. Bind + accessor instead.
            LinkedHashMap<String, Schema<?>> out = new LinkedHashMap<>();
            boolean changed = false;
            for (var en : one.variants().entrySet()) {
                Schema<?> e = enrichOpaques(en.getValue(), cache);
                changed |= e != en.getValue();
                out.put(en.getKey(), e);
            }
            return changed ? new Schema.OneOf(one.typeField(), out, one.valueField()) : schema;
        }
        // Ref (cycle guard) + all leaf schemas (Bool, ranges, Str, ResourceId, Color, Enum,
        // Custom) are returned unchanged.
        return schema;
    }

}
