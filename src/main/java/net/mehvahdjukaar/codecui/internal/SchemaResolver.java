package net.mehvahdjukaar.codecui.internal;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
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
import org.jspecify.annotations.Nullable;

//~ if >26.2 'resources.*' -> 'core.registries.codec.*'
import net.minecraft.resources.*;

//? >26.2
//import net.minecraft.resources.ResourceKey;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.mehvahdjukaar.codecui.internal.CodecFieldHandles.*;

// Walks a Codec graph and produces a Schema; anything it can't introspect falls back to
// Schema.Opaque. Entry point is SchemaCodec.wrap; tier order is in ARCHITECTURE.md.
public final class SchemaResolver implements SchemaHandler.Resolver {

    private static final SchemaResolver INSTANCE = new SchemaResolver();

    private static final List<SchemaHandler> HANDLERS = new CopyOnWriteArrayList<>();

    public static SchemaResolver get() {
        return INSTANCE;
    }

    public static void registerHandler(SchemaHandler handler) {
        HANDLERS.add(handler);
    }

    // Per-resolve cache. A Schema.Ref placeholder goes in on entry so cycles short-circuit;
    // it is bound to the real schema on exit.
    private static final ThreadLocal<IdentityHashMap<Object, Schema<?>>> CACHE = ThreadLocal.withInitial(IdentityHashMap::new);

    private final DispatchEnumerator dispatchEnumerator = new DispatchEnumerator(this);

    private SchemaResolver() {}

    public <A> Schema<A> resolve(Codec<A> codec) {
        CuratedSchemas.bootstrap();
        IdentityHashMap<Object, Schema<?>> cache = CACHE.get();
        boolean owner = cache.isEmpty();
        try {
            return resolveCodec(codec, cache);
        } finally {
            // remove(), not clear(): an IdentityHashMap keeps its grown table after clear(), so a
            // one-off resolve over a big codec graph would park that table on the thread forever.
            if (owner) CACHE.remove();
        }
    }

    public <A> Schema<A> resolveMap(MapCodec<A> codec) {
        CuratedSchemas.bootstrap();
        IdentityHashMap<Object, Schema<?>> cache = CACHE.get();
        boolean owner = cache.isEmpty();
        try {
            return resolveMapCodec(codec, cache);
        } finally {
            if (owner) CACHE.remove();
        }
    }

    // Lazy and memoized: the key space can be every block (debug stick), and lookups happen after
    // the outer resolve finished, so each one starts a fresh resolve instead of reusing that cache.
    private static <K, V> Function<String, Schema<?>> dispatchedValues(DispatchedMapCodec<K, V> dispatchedMap) {
        Map<String, Schema<?>> memo = new ConcurrentHashMap<>();
        return jsonKey -> memo.computeIfAbsent(jsonKey, k -> {
            try {
                K key = dispatchedMap.keyCodec().parse(JsonOps.INSTANCE, new JsonPrimitive(k)).result().orElse(null);
                if (key == null) return new Schema.Opaque<>(null, null);
                Codec<? extends V> valueCodec = dispatchedMap.valueCodecFunction().apply(key);
                return valueCodec == null ? new Schema.Opaque<>(null, null) : get().resolve(valueCodec);
            } catch (Throwable t) {
                // unknown or half-registered key: raw JSON, never break the form
                return new Schema.Opaque<>(null, null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    <A> Schema<A> resolveCodec(Codec<A> codec, IdentityHashMap<Object, Schema<?>> cache) {
        Schema<?> cached = cache.get(codec);
        if (cached != null) return (Schema<A>) cached;

        // Tier 0: eager companion tag.
        Schema<A> tagged = SchemaTags.lookup(codec);
        if (tagged != null) {
            cache.put(codec, tagged);
            return tagged;
        }

        // Tier 0d: lazy xmap-style wrapper tag. Resolve the inner codec now, not a stored schema,
        // so companions registered after the mixin fired still win.
        Codec<?> innerWrapped = XmapTags.getCodec(codec);
        if (innerWrapped != null) {
            Schema<?> innerSchema = resolveCodec((Codec) innerWrapped, cache);
            cache.put(codec, innerSchema);
            return (Schema<A>) innerSchema;
        }

        // Ref placeholder so cycles short-circuit; bound to the finished schema below.
        Schema.Ref<A> ref = new Schema.Ref<>();
        cache.put(codec, ref);

        Schema<A> result;
        try {
            result = (Schema<A>) tierCustomHandlers(codec, false);
            if (result == null) result = (Schema<A>) tierOnePrimitive(codec);
            if (result == null) result = (Schema<A>) tierTwoStructural(codec, cache);
            if (result == null) result = (Schema<A>) tierThreeReflective(codec, cache);
            // Tier 3.5. Range recovery goes first: the generic unwrap would collapse
            // intRange/floatRange/doubleRange to an unbounded primitive.
            if (result == null) result = (Schema<A>) recoverPrimitiveRange(codec);
            if (result == null) result = (Schema<A>) unwrapCapturedInner(codec, cache);
        } catch (Throwable t) {
            result = failedInference(codec, t);
        }
        if (result == null) result = (Schema<A>) new Schema.Opaque<>(codec, null);

        ref.bind(result);
        cache.put(codec, result);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <A> Schema<A> resolveMapCodec(MapCodec<A> codec, IdentityHashMap<Object, Schema<?>> cache) {
        Schema<?> cached = cache.get(codec);
        if (cached != null) return (Schema<A>) cached;

        // Tier 0a: lazy fieldOf tag (inner resolved now, same reason as tier 0d).
        FieldOfTags.Entry fieldOfEntry = FieldOfTags.get(codec);
        if (fieldOfEntry != null) {
            Schema<?> innerSchema = resolveCodec((Codec) fieldOfEntry.innerCodec(), cache);
            Schema.Field field = new Schema.Field(fieldOfEntry.name(), innerSchema, fieldOfEntry.optional(), fieldOfEntry.defaultValue());
            Schema rec = new Schema.Record(Object.class, List.of(field));
            cache.put(codec, rec);
            return rec;
        }

        // Tier 0b: lazy RecordCodecBuilder.build tag.
        List<RecordFieldTags.Entry> built = RecordFieldTags.getBuilt(codec);
        if (built != null && !built.isEmpty()) {
            Schema<?> rec = schemaFromRecordEntries(built, cache);
            cache.put(codec, rec);
            return (Schema<A>) rec;
        }

        // Tier 0c: eager companion tag.
        Schema<A> tagged = SchemaTags.lookupMap(codec);
        if (tagged != null) {
            cache.put(codec, tagged);
            return tagged;
        }

        // Tier 0d: lazy MapCodec xmap wrapper tag.
        MapCodec<?> innerWrappedMap = XmapTags.getMap(codec);
        if (innerWrappedMap != null) {
            Schema<?> innerSchema = resolveMapCodec((MapCodec) innerWrappedMap, cache);
            cache.put(codec, innerSchema);
            return (Schema<A>) innerSchema;
        }

        // Ref placeholder for cycles (see resolveCodec).
        Schema.Ref<A> ref = new Schema.Ref<>();
        cache.put(codec, ref);

        Schema<A> result;
        try {
            result = (Schema<A>) tierCustomHandlers(codec, true);
            if (result == null) result = (Schema<A>) tierTwoMapStructural(codec, cache);
            // Before tier 3: its generic inner-unwrap would drop the fallback and leave a required field.
            if (result == null) result = (Schema<A>) unwrapOrElse(codec, cache);
            if (result == null) result = (Schema<A>) tierThreeReflective(codec, cache);
            // Tier 3.5: transform-free unwrap.
            if (result == null) result = (Schema<A>) unwrapFieldOf(codec, cache);
            if (result == null) result = (Schema<A>) unwrapRecordCodec(codec, cache);
            if (result == null) result = (Schema<A>) unwrapCapturedInner(codec, cache);
        } catch (Throwable t) {
            result = failedInference(codec, t);
        }
        if (result == null) result = (Schema<A>) new Schema.Opaque<>(codec.codec(), null);

        ref.bind(result);
        cache.put(codec, result);
        return result;
    }

    // Inference calls back into foreign codecs (toString, keys, getters); a broken one should cost
    // one raw-JSON leaf, not the whole form. Returns null so the caller falls through to Opaque.
    private static <A> @Nullable Schema<A> failedInference(Object codec, Throwable t) {
        CodecUI.LOGGER.warn("Schema inference failed for {}; falling back to raw JSON",
                codec.getClass().getName(), t);
        return null;
    }

    // Tier 0.5. The cache placeholder is already in place, so handlers can resolve inner codecs
    // through the Resolver view without breaking cycle detection.
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Schema<?> tierTwoStructural(Codec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        // A SchemaCodec built without our resolver freezes raw inner codecs as Opaque (still
        // carrying the codec); re-resolve those leaves so its plain fields render like ours.
        if (codec instanceof net.mehvahdjukaar.codecui.SchemaCodec<?> sc) {
            return enrichOpaques(sc.schema(), cache);
        }
        // Promote MapCodec.codec() wrappers to the MapCodec path (dispatch codecs, RCB outputs).
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
        // UnboundedMapCodec, StrictUnboundedMapCodec and our LenientUnboundedMapCodec via BaseMapCodec.
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
        // the Ref placeholder already in the cache short-circuits self-references.
        if (codec instanceof Codec.RecursiveCodec<?> rec && RECURSIVE_WRAPPED != null) {
            Codec<?> inner = null;
            try {
                Supplier<?> sup = (Supplier<?>) RECURSIVE_WRAPPED.get(rec);
                Object got = sup.get();
                if (got instanceof Codec<?> c && c != codec) inner = c;
            } catch (Throwable t) {
                CodecUI.LOGGER.warn("Failed to unwrap recursive codec {}", codec, t);
            }
            // Resolve outside the catch: swallowing a failure here would fall through to the
            // reflective tiers, which recover only simple fields and silently drop the rest.
            if (inner != null) return resolveCodec(inner, cache);
        }
        // Encodes as a JSON object of key -> value entries, so a map editor is the right surface.
        if (codec instanceof CompoundListCodec<?, ?> cl && COMPOUND_LIST_KEY != null && COMPOUND_LIST_ELEMENT != null) {
            Schema<?> k = resolveCodec((Codec<?>) COMPOUND_LIST_KEY.get(cl), cache);
            Schema<?> v = resolveCodec((Codec<?>) COMPOUND_LIST_ELEMENT.get(cl), cache);
            return new Schema.MapOf(k, v);
        }
        // Value codec depends on the key, so the value schema is resolved per key, lazily.
        if (codec instanceof DispatchedMapCodec<?, ?> dm) {
            Schema<?> k = resolveCodec(dm.keyCodec(), cache);
            return new Schema.DispatchedMapOf(k, dispatchedValues(dm));
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
        if (codec instanceof RegistryFixedCodec<?> rfx) {
            return new Schema.ResourceId(rfx.registryKey);
        }
        // Recursive HolderSet<E>. cache.get(codec) is the Ref placeholder inserted on entry;
        // using it as the list element makes the schema self-recursive once the Ref is bound.
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
        // or a list of entries.
        Codec<?> holderSetElement = null;
        ResourceKey<? extends Registry<?>> holderSetRegistry = null;
        if (codec instanceof HolderSetCodecExtension hs) {
            holderSetElement = hs.codecui$elementCodec();
            holderSetRegistry = hs.codecui$registryKey();
        } else if (codec instanceof LenientHolderSetCodec<?> lhs) {
            holderSetElement = lhs.elementCodec();
            holderSetRegistry = lhs.registryKey();
        }
        if (holderSetElement != null) {
            Schema<?> element = resolveCodec(holderSetElement, cache);
            ResourceKey<? extends Registry<?>> registry =
                    element instanceof Schema.ResourceId r ? r.registry() : holderSetRegistry;
            Schema<?> tag = registry != null
                    ? new Schema.TagId(registry)
                    : new Schema.Str(0, Integer.MAX_VALUE, null);
            return Schema.anyOf(
                    Schema.option("tag", tag),
                    Schema.option("single", element),
                    Schema.option("list", new Schema.ListOf(element, 0, Integer.MAX_VALUE)));
        }
        // Custom registries etc. that expose their value set - a dropdown of registered names.
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
            // Standalone fallback; a parent RecordCodecBuilder normally produces its own Record.
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
        // MapCodec.recursive - mirror of the RecursiveCodec handler above.
        if (RECURSIVE_MAP_CLASS != null && RECURSIVE_MAP_WRAPPED != null && RECURSIVE_MAP_CLASS.isInstance(codec)) {
            MapCodec<?> inner = null;
            try {
                Supplier<?> sup = (Supplier<?>) RECURSIVE_MAP_WRAPPED.get(codec);
                Object got = sup.get();
                if (got instanceof MapCodec<?> mc && mc != codec) inner = mc;
            } catch (Throwable t) {
                CodecUI.LOGGER.warn("Failed to unwrap recursive map codec {}", codec, t);
            }
            // Resolve outside the catch (see RecursiveCodec handler above).
            if (inner != null) return resolveMapCodec(inner, cache);
        }
        // ExtraCodecs.dispatchOptionalValue (advancement criteria etc.). Not a KeyDispatchCodec, so
        // match it structurally before tier 3 mistakes it for a scalar wrapper.
        CodecReflection.OptionalValueDispatch optionalValueDispatch = CodecReflection.detectOptionalValueDispatch(codec);
        if (optionalValueDispatch != null) {
            Schema<?> dispatched = dispatchEnumerator.resolveOptionalValueDispatch(optionalValueDispatch, cache);
            if (dispatched != null) return dispatched;
        }
        return null;
    }

    // Tier 3: last-resort guess from the inner Codec/MapCodec fields of an unknown codec class.
    // One inner -> wrapper, inherit; a (key, element/value) pair -> MapOf; several -> flat AnyOf.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Schema<?> tierThreeReflective(Object codec, IdentityHashMap<Object, Schema<?>> cache) {
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
            String first = names.get(0), second = names.get(1);
            boolean firstIsKey = first.contains("key"), secondIsKey = second.contains("key");
            boolean firstIsValue = first.contains("element") || first.contains("value");
            boolean secondIsValue = second.contains("element") || second.contains("value");
            if (firstIsKey && secondIsValue) return new Schema.MapOf(resolveAny(inners.get(0), cache), resolveAny(inners.get(1), cache));
            if (secondIsKey && firstIsValue) return new Schema.MapOf(resolveAny(inners.get(1), cache), resolveAny(inners.get(0), cache));
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

    // Tier 3.5: xmap/validate/orElse/fieldOf hide the inner codec inside anonymous
    // Encoder/Decoder classes that tier 3 can't see. Mostly a NeoForge path, where the
    // construction mixins can't apply.

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
            // A field that throws becomes raw JSON. Dropping it would lose its data on save.
            try {
                appendRecordField(fields, e, cache);
            } catch (Throwable t) {
                CodecUI.LOGGER.warn("Record field '{}' failed to resolve; falling back to raw JSON",
                        e.name(), t);
                addField(fields, opaqueField(e));
            }
        }
        return new Schema.Record(Object.class, List.copyOf(fields));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void appendRecordField(List<Schema.Field<?, ?>> fields, RecordFieldTags.Entry e,
                                   IdentityHashMap<Object, Schema<?>> cache) {
        if (e.mapCodec() != null) {
            Schema<?> mapSchema = resolveMapCodec((MapCodec) e.mapCodec(), cache);
            // A raw MapCodec in a group flattens its keys into the parent; only fieldOf("x") nests.
            // So a one-field Record is the fieldOf form, a multi-field Record gets spread.
            if (mapSchema instanceof Schema.Record<?> rec) {
                if (rec.fields().isEmpty()) {
                    // spreading zero fields would make the data vanish
                    addField(fields, opaqueField(e));
                } else if (rec.fields().size() == 1) {
                    Schema.Field<?, ?> inner = rec.fields().getFirst();
                    addField(fields, new Schema.Field(e.name(), inner.schema(), inner.optional(), inner.defaultValue()));
                } else {
                    for (Schema.Field<?, ?> f : rec.fields()) addField(fields, f);
                }
                return;
            }
            // Flat-keyed but not spreadable into static fields (dispatch, pair, map, either-of-maps):
            // mark inline so the backend merges its object flat (Schema.Field.inline).
            boolean flattenable = mapSchema instanceof Schema.OneOf<?>
                    || mapSchema instanceof Schema.PairOf<?, ?>
                    || mapSchema instanceof Schema.MapOf<?, ?>
                    || mapSchema instanceof Schema.DispatchedMapOf<?, ?>
                    || mapSchema instanceof Schema.AnyOf<?>;
            addField(fields, new Schema.Field(e.name(), mapSchema, false, null, flattenable));
            return;
        }
        Schema<?> fieldSchema = resolveCodec((Codec) e.elementCodec(), cache);
        addField(fields, new Schema.Field(e.name(), fieldSchema, false, null));
    }

    // Carries the field's own codec so the raw editor still validates. Optional so an unbuilt
    // field is not flagged as required-but-missing.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Schema.Field<?, ?> opaqueField(RecordFieldTags.Entry e) {
        Codec<?> codec = e.mapCodec() != null ? e.mapCodec().codec() : e.elementCodec();
        return new Schema.Field(e.name(), new Schema.Opaque(codec, null), true, null);
    }

    // Skips duplicate names: guards against a spread sub-record colliding with a sibling field.
    private static void addField(List<Schema.Field<?, ?>> fields, Schema.Field<?, ?> field) {
        for (Schema.Field<?, ?> existing : fields) {
            if (existing.name().equals(field.name())) return;
        }
        fields.add(field);
    }

    // fieldOf(name).orElse(value) is optionalFieldOf(name, value) to an editor. No mixin tags
    // MapCodec.orElse, so without this the field reads as required and an untouched form won't encode.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Schema<?> unwrapOrElse(MapCodec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        CodecReflection.OrElseEntry entry = CodecReflection.unwrapOrElse(codec);
        if (entry == null) return null;
        Schema<?> inner = resolveMapCodec((MapCodec) entry.inner(), cache);
        if (!(inner instanceof Schema.Record<?> rec) || rec.fields().size() != 1) return null;
        Schema.Field<?, ?> field = rec.fields().getFirst();
        if (field.optional()) return null;
        return new Schema.Record(Object.class,
                List.of(new Schema.Field(field.name(), field.schema(), true, entry.fallback())));
    }

    // Required Codec.fieldOf(name): recover name + element codec, rebuild the record (mirrors tier 0a).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Schema<?> unwrapFieldOf(MapCodec<?> codec, IdentityHashMap<Object, Schema<?>> cache) {
        CodecReflection.FieldOfEntry field = CodecReflection.unwrapFieldOf(codec);
        if (field == null) return null;
        Schema<?> innerSchema = resolveCodec(field.elementCodec(), cache);
        Schema.Field f = new Schema.Field(field.name(), innerSchema, false, null);
        return new Schema.Record(Object.class, List.of(f));
    }

    // Fires only when exactly one distinct inner codec is reachable through Encoder/Decoder fields.
    private @Nullable Schema<?> unwrapCapturedInner(Object codec, IdentityHashMap<Object, Schema<?>> cache) {
        Object inner = CodecReflection.singleCapturedInner(codec);
        return inner == null ? null : resolveAny(inner, cache);
    }

    // Codec.intRange/floatRange/doubleRange are PRIMITIVE.flatXmap(checkRange(min, max), ...);
    // read the bounds off the checker lambda. Gated on the marker and an exact INT/FLOAT/DOUBLE
    // inner so an unrelated flatXmap can't be misread as a range.
    private @Nullable Schema<?> recoverPrimitiveRange(Object codec) {
        if (!(codec instanceof Codec<?>) || !CodecReflection.safeToString(codec).endsWith("[flatXmapped]")) return null;
        Object inner = CodecReflection.singleCapturedInner(codec);
        if (inner != Codec.INT && inner != Codec.FLOAT && inner != Codec.DOUBLE) return null;

        Number[] bounds = CodecReflection.recoverRangeBounds(codec);
        if (bounds == null) return null;
        Number min = bounds[0], max = bounds[1];
        if (inner == Codec.INT) return new Schema.IntRange(min.intValue(), max.intValue());
        if (inner == Codec.FLOAT) return new Schema.FloatRange(min.floatValue(), max.floatValue());
        return new Schema.DoubleRange(min.doubleValue(), max.doubleValue());
    }

    // Re-resolves every Opaque leaf that still carries a codec, keeping the result when it is
    // better than raw JSON.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Schema<?> enrichOpaques(Schema<?> schema, IdentityHashMap<Object, Schema<?>> cache) {
        if (schema instanceof Schema.Opaque<?> op) {
            Codec<?> inner = op.codec();
            if (inner == null) return schema;
            Schema<?> resolved = resolveCodec((Codec) inner, cache);
            return (resolved instanceof Schema.Opaque) ? schema : resolved;
        }
        if (schema instanceof Schema.Record<?> rec) {
            // A record pattern can't name List<Field<CAP,?>> as List<Field<?,?>>; bind + accessor.
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
            // Same invariance problem as Record above; bind + accessor.
            LinkedHashMap<String, Schema<?>> out = new LinkedHashMap<>();
            boolean changed = false;
            for (var en : one.variants().entrySet()) {
                Schema<?> e = enrichOpaques(en.getValue(), cache);
                changed |= e != en.getValue();
                out.put(en.getKey(), e);
            }
            return changed ? new Schema.OneOf(one.typeField(), out, one.valueField()) : schema;
        }
        return schema;
    }

}
