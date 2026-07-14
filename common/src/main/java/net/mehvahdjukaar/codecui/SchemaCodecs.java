package net.mehvahdjukaar.codecui;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.internal.AlternativeCodec;
import net.mehvahdjukaar.codecui.internal.AlternativeMapCodec;
import net.mehvahdjukaar.codecui.internal.BestAlternativeCodec;
import net.mehvahdjukaar.codecui.internal.DispatchRegistry;
import net.mehvahdjukaar.codecui.internal.EitherLeftCodec;
import net.mehvahdjukaar.codecui.internal.LenientCodecWithLog;
import net.mehvahdjukaar.codecui.internal.LenientHolderSetCodec;
import net.mehvahdjukaar.codecui.internal.LenientListCodec;
import net.mehvahdjukaar.codecui.internal.LenientUnboundedMapCodec;
import net.mehvahdjukaar.codecui.internal.CodecWithExtra;
import net.mehvahdjukaar.codecui.internal.MixinDetection;
import net.mehvahdjukaar.codecui.internal.RecursiveHolderSetCodec;
import net.mehvahdjukaar.codecui.internal.ReferenceOrDirectCodec;
import net.mehvahdjukaar.codecui.internal.SchemaResolver;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Facade of the CodecUI declarative API: primitives and combinators that pair a {@link Codec}
 * with its {@link Schema}, so a mod can declare an editable surface for its own codecs and
 * have a CodecUI-aware editor render it.
 *
 * <p>Schemas may be stated explicitly — here, via {@link SchemaCodec#of}, or via
 * {@link SchemaRecord}/{@link SchemaRecordBuilder} — or inferred: {@link SchemaCodec#wrap}
 * runs the bundled inference engine (structural walk + construction-mixin tags + registered
 * companions/handlers). Anything the engine can't resolve degrades to {@link Schema.Opaque}
 * (a raw-JSON editor). Extend the engine via {@link #registerCompanion}, {@link #registerHandler}
 * and {@link #registerDispatchKeys}. The declaration contract is simply: produce a
 * {@link SchemaCodec} (or {@link SchemaMapCodec}); the editor reads {@link SchemaCodec#schema()}.</p>
 */
public final class SchemaCodecs {

    /**
     * How CodecUI recovers schema metadata from a codec's construction.
     *
     * @see #inferenceMode()
     */
    public enum Inference {
        /**
         * Codec construction is intercepted by mixins: numeric bounds ({@code intRange}…),
         * optional-field defaults and transform links are captured losslessly at build time.
         * The normal mode on Fabric.
         */
        MIXIN,
        /**
         * The construction mixins did not weave, so schemas are recovered by best-effort
         * reflection. Structure, field names and numeric ranges survive; optional-field default
         * values do not. This is <b>always</b> the mode on NeoForge (DFU isn't on the transforming
         * classloader) and <b>occasionally</b> on Fabric (a DFU type was classloaded before Mixin
         * could weave it).
         */
        REFLECTION
    }

    /**
     * Whether codec-construction interception is active. {@link Inference#REFLECTION} means schema
     * inference is running in reduced-fidelity fallback (see the enum) - always on NeoForge, and on
     * Fabric when a DFU type loaded too early. Valid after CodecUI has initialized.
     */
    public static Inference inferenceMode() {
        return MixinDetection.constructionInterceptionActive() ? Inference.MIXIN : Inference.REFLECTION;
    }

    /**
     * Manually register a schema for a codec that can't be auto-introspected (opaque
     * {@code Codec.of(enc, dec)} wrappers, etc.). After registration, any resolution of this
     * codec — including nested inside another — finds the hand-crafted schema first.
     */
    public static <A> void registerCompanion(Codec<A> codec, Schema<A> schema) {
        SchemaTags.tag(codec, schema);
    }

    /** Same as {@link #registerCompanion(Codec, Schema)} for a {@link MapCodec}. */
    public static <A> void registerCompanion(MapCodec<A> codec, Schema<A> schema) {
        SchemaTags.tag(codec, schema);
    }

    /**
     * Register a {@link SchemaHandler} that teaches the resolver how to introspect a whole
     * class of codecs. See {@link SchemaHandler} for contract, ordering and an example.
     */
    public static void registerHandler(SchemaHandler handler) {
        SchemaResolver.registerHandler(handler);
    }

    /**
     * Register the key set of a {@code Codec.dispatch(...)} family whose key type can't
     * implement {@link EnumerableCodec} (vanilla or third-party K). Keys the dispatch accepts
     * become entries in the variant picker, with fully resolved bodies.
     */
    public static <K> void registerDispatchKeys(Class<K> keyType, Supplier<List<K>> keys,
                                                Function<K, String> nameOf) {
        DispatchRegistry.register(keyType, keys, nameOf);
    }

    public static final SchemaCodec<Boolean> BOOL =
            SchemaCodec.of(Codec.BOOL, new Schema.Bool());
    public static final SchemaCodec<Integer> INT =
            SchemaCodec.of(Codec.INT, new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE));
    public static final SchemaCodec<Long> LONG =
            SchemaCodec.of(Codec.LONG, new Schema.LongRange(Long.MIN_VALUE, Long.MAX_VALUE));
    public static final SchemaCodec<Float> FLOAT =
            SchemaCodec.of(Codec.FLOAT, new Schema.FloatRange(-Float.MAX_VALUE, Float.MAX_VALUE));
    public static final SchemaCodec<Double> DOUBLE =
            SchemaCodec.of(Codec.DOUBLE, new Schema.DoubleRange(-Double.MAX_VALUE, Double.MAX_VALUE));
    public static final SchemaCodec<String> STRING =
            SchemaCodec.of(Codec.STRING, Schema.str());

    public static SchemaCodec<Integer> intRange(int min, int max) {
        return SchemaCodec.of(Codec.intRange(min, max), new Schema.IntRange(min, max));
    }

    public static SchemaCodec<Float> floatRange(float min, float max) {
        return SchemaCodec.of(Codec.floatRange(min, max), new Schema.FloatRange(min, max));
    }

    public static SchemaCodec<Double> doubleRange(double min, double max) {
        return SchemaCodec.of(Codec.doubleRange(min, max), new Schema.DoubleRange(min, max));
    }

    /** A closed, named choice rendered as a dropdown. */
    public static <E> SchemaCodec<E> enumeration(Codec<E> codec, List<E> options, Function<E, String> label) {
        return SchemaCodec.of(codec, new Schema.Enum<>(options, label));
    }

    /** Convenience: a {@link SchemaCodec} that pairs an integer codec with a {@link Schema.Color} (RGB). */
    public static SchemaCodec<Integer> colorRgb(Codec<Integer> codec) {
        return SchemaCodec.of(codec, new Schema.Color(false));
    }

    /** Convenience: same as {@link #colorRgb} but for ARGB (with alpha channel). */
    public static SchemaCodec<Integer> colorArgb(Codec<Integer> codec) {
        return SchemaCodec.of(codec, new Schema.Color(true));
    }

    /**
     * Best-effort schema for any codec: its own schema when it already is a {@link SchemaCodec},
     * otherwise a raw {@link Schema.Opaque}. No structural inference is performed — this only
     * unwraps schema-carrying codecs and provides a raw fallback for the rest.
     */
    public static <A> Schema<A> resolve(Codec<A> codec) {
        return SchemaCodec.wrap(codec).schema();
    }

    @SuppressWarnings("unchecked")
    private static <A, B> Schema<B> castSchema(Schema<A> schema) {
        return (Schema<B>) schema;
    }

    public static <A, B> SchemaCodec<B> xmap(SchemaCodec<A> inner, Function<A, B> to, Function<B, A> from) {
        Codec<B> codec = inner.xmap(to, from);
        return SchemaCodec.of(codec, castSchema(inner.schema()));
    }

    public static <A, B> SchemaCodec<B> xmapWithSchema(SchemaCodec<A> inner, Function<A, B> to, Function<B, A> from, Schema<B> schema) {
        Codec<B> codec = inner.xmap(to, from);
        return SchemaCodec.of(codec, schema);
    }

    public static <E> SchemaCodec<List<E>> list(SchemaCodec<E> elementCodec) {
        return list(elementCodec, 0, Integer.MAX_VALUE);
    }

    public static <E> SchemaCodec<List<E>> list(SchemaCodec<E> elementCodec, int minSize, int maxSize) {
        Codec<List<E>> codec;
        if (minSize == 0 && maxSize == Integer.MAX_VALUE) {
            codec = elementCodec.listOf();
        } else {
            codec = elementCodec.listOf(minSize, maxSize);
        }
        Schema<List<E>> schema = new Schema.ListOf<>(elementCodec.schema(), minSize, maxSize);
        return SchemaCodec.of(codec, schema);
    }

    /** An unbounded {@code Map<K, V>} codec paired with a {@link Schema.MapOf} (key + value editors). */
    public static <K, V> SchemaCodec<Map<K, V>> map(SchemaCodec<K> keyCodec, SchemaCodec<V> valueCodec) {
        Codec<Map<K, V>> codec = Codec.unboundedMap(keyCodec, valueCodec);
        Schema<Map<K, V>> schema = new Schema.MapOf<>(keyCodec.schema(), valueCodec.schema());
        return SchemaCodec.of(codec, schema);
    }

    /** A {@link Pair} codec paired with a {@link Schema.PairOf} (first + second editors). */
    public static <F, S> SchemaCodec<Pair<F, S>> pair(SchemaCodec<F> first, SchemaCodec<S> second) {
        Codec<Pair<F, S>> codec = Codec.pair(first, second);
        Schema<Pair<F, S>> schema = new Schema.PairOf<>(first.schema(), second.schema());
        return SchemaCodec.of(codec, schema);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <A> SchemaMapCodec<A> fieldOf(String name, SchemaCodec<A> codec) {
        MapCodec<A> mapCodec = codec.fieldOf(name);
        Schema.Field<Object, A> field = new Schema.Field<>(name, codec.schema(), false, null);
        List<Schema.Field<Object, ?>> fields = List.of(field);
        Schema<A> schema = (Schema<A>) (Schema) new Schema.Record<>(Object.class, fields);
        return SchemaMapCodec.of(mapCodec, schema);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <A> SchemaMapCodec<Optional<A>> optionalFieldOf(String name, SchemaCodec<A> codec) {
        MapCodec<Optional<A>> mapCodec = codec.optionalFieldOf(name);
        Schema.Field<Object, A> field = new Schema.Field<>(name, codec.schema(), true, null);
        List<Schema.Field<Object, ?>> fields = List.of(field);
        Schema<Optional<A>> schema = (Schema<Optional<A>>) (Schema) new Schema.Record<>(Object.class, fields);
        return SchemaMapCodec.of(mapCodec, schema);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <A> SchemaMapCodec<A> optionalFieldOf(String name, SchemaCodec<A> codec, A defaultValue) {
        MapCodec<A> mapCodec = codec.optionalFieldOf(name, defaultValue);
        Schema.Field<Object, A> field = new Schema.Field<>(name, codec.schema(), true, defaultValue);
        List<Schema.Field<Object, ?>> fields = List.of(field);
        Schema<A> schema = (Schema<A>) (Schema) new Schema.Record<>(Object.class, fields);
        return SchemaMapCodec.of(mapCodec, schema);
    }

    /** A labeled alternative for {@link #withAlternative} / {@link #labeled}. */
    public record Alt<A>(String label, Codec<A> codec) {}

    public static <A> Alt<A> alt(String label, Codec<A> codec) {
        return new Alt<>(label, codec);
    }

    /**
     * Labeled {@link Codec#withAlternative}: builds the codec AND a lazy {@link Schema.AnyOf}
     * from the same two declarations — each alternative stated once. Prefer passing
     * {@link SchemaCodec}s as the alternatives so their schemas render structurally; a raw
     * codec alternative falls back to {@link Schema.Opaque}.
     */
    public static <A> SchemaCodec<A> withAlternative(Alt<A> primary, Alt<? extends A> secondary) {
        return SchemaCodec.lazy(Codec.withAlternative(primary.codec(), secondary.codec()),
                () -> Schema.anyOf(
                        Schema.option(primary.label(), resolve(primary.codec())),
                        Schema.option(secondary.label(), resolve(secondary.codec()))));
    }

    /**
     * Labeled view over an EXISTING multi-format codec whose alternative structure can't be
     * rebuilt here: the wire codec is passed through untouched, and the editor surface is a
     * lazy flat {@link Schema.AnyOf} over the given labeled parts. Alternatives that are
     * themselves AnyOf splice flat, keeping their own (more specific) labels.
     */
    public static <A> SchemaCodec<A> labeled(Codec<A> codec, Alt<?>... alternatives) {
        // No explicit alternatives: this is just a SchemaCodec adapter over the raw codec, so
        // resolve it structurally. Wrapping it in an AnyOf would produce a picker with zero
        // options (an empty AnyOf), which is degenerate and blows up the widget layer.
        if (alternatives.length == 0) {
            return SchemaCodec.lazy(codec, () -> resolve(codec));
        }
        return SchemaCodec.lazy(codec, () -> {
            List<Schema.AnyOf.Option> options = new java.util.ArrayList<>(alternatives.length);
            for (Alt<?> alt : alternatives) {
                options.add(Schema.option(alt.label(), resolve(alt.codec())));
            }
            return Schema.anyOf(options);
        });
    }

    /**
     * N-ary labeled alternatives: builds a "try each" wire codec (see {@link AlternativeCodec})
     * AND a lazy flat {@link Schema.AnyOf} from the same declarations — each alternative stated
     * once. Unlike {@link #withAlternative} (a 2-way {@link Codec#withAlternative} fold that always
     * encodes with the primary), every alternative is tried for both decode and encode. Prefer
     * passing {@link SchemaCodec}s so their schemas render structurally; a raw codec alternative
     * falls back to {@link Schema.Opaque}. Alternatives that are themselves AnyOf splice flat.
     */
    @SafeVarargs
    public static <A> SchemaCodec<A> alternatives(Alt<? extends A>... alternatives) {
        if (alternatives.length == 0) {
            throw new IllegalArgumentException("alternatives() requires at least one alternative");
        }
        @SuppressWarnings("unchecked")
        Codec<? extends A>[] codecs = new Codec[alternatives.length];
        for (int i = 0; i < alternatives.length; i++) {
            codecs[i] = alternatives[i].codec();
        }
        Codec<A> codec = new AlternativeCodec<>(codecs);
        return SchemaCodec.lazy(codec, () -> {
            List<Schema.AnyOf.Option> options = new java.util.ArrayList<>(alternatives.length);
            for (Alt<? extends A> alt : alternatives) {
                options.add(Schema.option(alt.label(), resolve(alt.codec())));
            }
            return Schema.anyOf(options);
        });
    }

    /**
     * Unlabeled n-ary "try each" alternatives over raw codecs — a flat {@link Schema.AnyOf} with
     * auto-derived option names. Use the {@code (label, codec)} overloads or {@link #alt} when you
     * want to name the picker options.
     */
    @SafeVarargs
    public static <A> SchemaCodec<A> alternatives(Codec<? extends A>... codecs) {
        if (codecs.length == 0) {
            throw new IllegalArgumentException("alternatives() requires at least one alternative");
        }
        Codec<A> codec = new AlternativeCodec<>(codecs);
        return SchemaCodec.lazy(codec, () -> {
            List<Schema.AnyOf.Option> options = new java.util.ArrayList<>(codecs.length);
            for (Codec<? extends A> c : codecs) {
                options.add(Schema.option(resolve(c)));
            }
            return Schema.anyOf(options);
        });
    }

    /** {@link #alternatives(Alt[])} stated as interleaved {@code (label, codec)} pairs. */
    public static <A> SchemaCodec<A> alternatives(String l1, Codec<? extends A> c1,
                                                  String l2, Codec<? extends A> c2) {
        return alternatives(alt(l1, c1), alt(l2, c2));
    }

    /** {@link #alternatives(Alt[])} stated as interleaved {@code (label, codec)} pairs. */
    public static <A> SchemaCodec<A> alternatives(String l1, Codec<? extends A> c1,
                                                  String l2, Codec<? extends A> c2,
                                                  String l3, Codec<? extends A> c3) {
        return alternatives(alt(l1, c1), alt(l2, c2), alt(l3, c3));
    }

    /** {@link #alternatives(Alt[])} stated as interleaved {@code (label, codec)} pairs. */
    public static <A> SchemaCodec<A> alternatives(String l1, Codec<? extends A> c1,
                                                  String l2, Codec<? extends A> c2,
                                                  String l3, Codec<? extends A> c3,
                                                  String l4, Codec<? extends A> c4) {
        return alternatives(alt(l1, c1), alt(l2, c2), alt(l3, c3), alt(l4, c4));
    }

    /** {@link #alternatives(Alt[])} stated as interleaved {@code (label, codec)} pairs. */
    public static <A> SchemaCodec<A> alternatives(String l1, Codec<? extends A> c1,
                                                  String l2, Codec<? extends A> c2,
                                                  String l3, Codec<? extends A> c3,
                                                  String l4, Codec<? extends A> c4,
                                                  String l5, Codec<? extends A> c5) {
        return alternatives(alt(l1, c1), alt(l2, c2), alt(l3, c3), alt(l4, c4), alt(l5, c5));
    }

    public static <L, R> SchemaCodec<Either<L, R>> either(SchemaCodec<L> left, SchemaCodec<R> right) {
        Codec<Either<L, R>> codec = Codec.either(left, right);
        Schema<Either<L, R>> schema = Schema.anyOf(
                Schema.option(left.schema()), Schema.option(right.schema()));
        return SchemaCodec.of(codec, schema);
    }

    public static <T> SchemaCodec<T> registryEntry(ResourceKey<? extends Registry<T>> registryKey, Codec<T> nameCodec) {
        Schema<T> schema = castSchema(new Schema.ResourceId(registryKey));
        return SchemaCodec.of(nameCodec, schema);
    }

    /**
     * The tag twin of {@link #registryEntry}: a field that picks one {@link TagKey} of
     * {@code registryKey}. Uses {@link TagKey#codec} for the wire form and a {@link Schema.TagId}
     * so the editor shows a tag picker. Note {@code TagKey.codec} is already tagged with the same
     * schema on construction (see {@code TagKeyCodecMixin}), so {@code SchemaCodec.wrap} of a bare
     * {@code TagKey.codec(...)} yields the same result — this is just the explicit spelling.
     */
    public static <T> SchemaCodec<TagKey<T>> tag(ResourceKey<? extends Registry<T>> registryKey) {
        Schema<TagKey<T>> schema = castSchema(new Schema.TagId(registryKey));
        return SchemaCodec.of(TagKey.codec(registryKey), schema);
    }

    /**
     * A {@link HolderSet} codec like vanilla's (a tag, a single entry, or a list of entries) except
     * the list is <b>recursive</b>: every element may itself be a tag, an entry or a nested list, all
     * flattened into one set. Lets tags and entries be mixed inside a single list. The resolver
     * derives a self-recursive tag / single / list schema, so {@code SchemaCodec.wrap} suffices.
     */
    public static <E> SchemaCodec<HolderSet<E>> recursiveHolderSet(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec) {
        return SchemaCodec.wrap(RecursiveHolderSetCodec.create(registryKey, elementCodec));
    }

    /**
     * Enumerates the currently loaded tag ids of {@code registryKey}, sorted — the candidate list
     * a UI backend feeds into a {@link Schema.TagId} picker (the tag analogue of iterating a
     * registry's {@code keySet()} for {@link Schema.ResourceId}). Tags are data-driven, so this is
     * only meaningful once tags have been loaded/synced (i.e. with a world open); it returns an
     * empty list — never throws — for an unknown registry or before tags bind, letting the backend
     * fall back to a free-text field.
     */
    public static List<ResourceLocation> availableTagIds(ResourceKey<? extends Registry<?>> registryKey) {
        if (registryKey == null) return List.of();
        Registry<?> registry = BuiltInRegistries.REGISTRY.get(registryKey.location());
        if (registry == null) return List.of();
        return registry.getTags()
                .map(pair -> pair.getFirst().location())
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /**
     * Sum-type dispatch: serialize {@code A} as a tagged map where {@code typeField} selects a variant.
     * Each variant is a {@link SchemaMapCodec} keyed by the same string as produced by {@code typeFn}.
     */
    public static <A> SchemaCodec<A> dispatch(
            String typeField,
            Function<A, String> typeFn,
            Map<String, SchemaMapCodec<? extends A>> variants
    ) {
        Codec<A> codec = Codec.STRING.dispatch(
                typeField,
                typeFn,
                key -> variants.get(key).mapCodec()
        );
        Map<String, Schema<? extends A>> variantSchemas = new LinkedHashMap<>();
        variants.forEach((k, v) -> variantSchemas.put(k, v.schema()));
        Schema<A> schema = new Schema.OneOf<>(typeField, variantSchemas);
        return SchemaCodec.of(codec, schema);
    }

    /**
     * A "reference OR inline" codec (like {@code RegistryFileCodec} for simple map registries):
     * a string decodes through {@code reference}, anything else through {@code direct}. Renders
     * as a labeled reference/inline picker.
     */
    public static <E> SchemaCodec<E> referenceOrDirect(Codec<? extends E> reference, Codec<? extends E> direct) {
        return referenceOrDirect(reference, direct, false);
    }

    public static <E> SchemaCodec<E> referenceOrDirect(Codec<? extends E> reference, Codec<? extends E> direct, boolean bothStrings) {
        Codec<E> raw = new ReferenceOrDirectCodec<>(reference, direct, bothStrings);
        return SchemaCodec.lazy(raw, () -> Schema.anyOf(
                Schema.option("reference", resolve(reference)),
                Schema.option("inline", resolve(direct))));
    }

    /** Try both alternatives and keep the "best" one per {@code chooseFirst} when both parse. */
    public static <A, B extends A, C extends A> SchemaCodec<A> bestAlternative(
            Codec<B> first, Codec<C> second, BiPredicate<B, C> chooseFirst) {
        Codec<A> raw = new BestAlternativeCodec<>(first, second, chooseFirst);
        return SchemaCodec.lazy(raw, () -> Schema.anyOf(
                Schema.option(resolve(first)),
                Schema.option(resolve(second))));
    }

    /** Decodes {@code A} and wraps it as {@link Either#left}; its schema IS the left branch's. */
    public static <A, B> SchemaCodec<Either<A, B>> eitherLeft(Codec<A> leftCodec) {
        Codec<Either<A, B>> raw = new EitherLeftCodec<>(leftCodec);
        return SchemaCodec.lazy(raw, () -> castSchema(resolve(leftCodec)));
    }

    /** A single element OR a list of them — rendered as a "single / list" picker. */
    public static <A> SchemaCodec<List<A>> singleOrList(Codec<A> elementCodec) {
        Codec<List<A>> raw = Codec.withAlternative(elementCodec.listOf(), elementCodec, List::of);
        return SchemaCodec.lazy(raw, () -> singleOrListSchema(elementCodec));
    }

    /** As {@link #singleOrList(Codec)} but collapsing to a single {@code A} via {@code listToSingle}. */
    public static <A> SchemaCodec<A> singleOrList(Codec<A> elementCodec, Function<List<A>, A> listToSingle) {
        Codec<A> raw = Codec.withAlternative(elementCodec, elementCodec.listOf(), listToSingle);
        return SchemaCodec.lazy(raw, () -> castSchema(singleOrListSchema(elementCodec)));
    }

    private static <A> Schema<List<A>> singleOrListSchema(Codec<A> elementCodec) {
        Schema<A> element = resolve(elementCodec);
        return Schema.anyOf(
                Schema.option("single", element),
                Schema.option("list", new Schema.ListOf<>(element, 0, Integer.MAX_VALUE)));
    }

    /** A list that skips (rather than fails on) elements which can't decode. */
    public static <A> SchemaCodec<List<A>> lenientList(Codec<A> elementCodec) {
        Codec<List<A>> raw = LenientListCodec.of(elementCodec);
        return SchemaCodec.lazy(raw, () -> new Schema.ListOf<>(resolve(elementCodec), 0, Integer.MAX_VALUE));
    }

    /**
     * A {@link HolderSet} codec like vanilla's (a tag, a single entry, or a list of entries) whose
     * list is lenient: entries that fail to decode are skipped instead of failing the whole set.
     * For the recursive (mixed tags/entries in one list) variant see {@link #recursiveHolderSet}.
     */
    public static <E> SchemaCodec<HolderSet<E>> lenientHolderSet(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec) {
        return SchemaCodec.wrap(LenientHolderSetCodec.create(registryKey, elementCodec, false));
    }

    /** An unbounded {@code Map} that logs and skips entries which fail to decode, instead of failing the whole map. */
    public static <K, V> SchemaCodec<Map<K, V>> lenientUnboundedMap(Codec<K> keyCodec, Codec<V> valueCodec) {
        Codec<Map<K, V>> raw = new LenientUnboundedMapCodec<>(keyCodec, valueCodec);
        return SchemaCodec.lazy(raw, () -> new Schema.MapOf<>(resolve(keyCodec), resolve(valueCodec)));
    }

    /** An optional field that logs and skips (rather than failing) when its value can't decode. */
    public static <A> MapCodec<A> lenientWithLog(Codec<A> elementCodec, String name, A defaultValue) {
        return LenientCodecWithLog.of(elementCodec, name, defaultValue);
    }

    public static <A> MapCodec<Optional<A>> lenientWithLog(Codec<A> elementCodec, String name) {
        return LenientCodecWithLog.of(elementCodec, name);
    }

    /** A required field readable under either {@code primaryName} or {@code alias}. */
    public static <B> MapCodec<B> alias(Codec<B> codec, String primaryName, String alias) {
        return AlternativeMapCodec.alias(codec, primaryName, alias);
    }

    /** An optional field readable under either {@code primaryName} or {@code alias}. */
    public static <B> MapCodec<Optional<B>> optionalAlias(Codec<B> codec, String primaryName, String alias) {
        return AlternativeMapCodec.optionalAlias(codec, primaryName, alias);
    }

    /** A membership {@link Predicate} over a single-or-list of elements. */
    public static <A> Codec<Predicate<A>> predicate(Codec<A> elementCodec) {
        return singleOrList(elementCodec).xmap(
                list -> a -> {
                    for (var e : list) {
                        if (e.equals(a)) return true;
                    }
                    return false;
                },
                predicate -> List.of());
    }

    /**
     * Decode a base object plus extra merged map-fields read off the same input, folding them into
     * the base via {@code merge};
     */
    public static <A> Codec<A> withExtra(Codec<A> base, List<MapCodec<?>> extras,
                                         BiFunction<A, List<Object>, A> merge) {
        Codec<A> raw = new CodecWithExtra<>(base, extras, merge);
        return SchemaCodec.lazy(raw, () -> resolve(base));
    }

    @SuppressWarnings("unchecked")
    public static <A, B> Codec<A> withExtra(Codec<A> base, MapCodec<B> c1,
                                            BiFunction<A, B, A> f) {
        return withExtra(base, List.of(c1), (A a, List<Object> v) -> f.apply(a, (B) v.get(0)));
    }

    /** Typed two-extra {@link #withExtra(Codec, List, BiFunction)}. */
    @SuppressWarnings("unchecked")
    public static <A, B, C> Codec<A> withExtra(Codec<A> base, MapCodec<B> c1, MapCodec<C> c2,
                                               Function3<A, B, C, A> f) {
        return withExtra(base, List.of(c1, c2),
                (A a, List<Object> v) -> f.apply(a, (B) v.get(0), (C) v.get(1)));
    }

    @SuppressWarnings("unchecked")
    public static <A, B, C, D> Codec<A> withExtra(Codec<A> base, MapCodec<B> c1, MapCodec<C> c2,
                                                  MapCodec<D> c3, Function4<A, B, C, D, A> f) {
        return withExtra(base, List.of(c1, c2, c3),
                (A a, List<Object> v) -> f.apply(a, (B) v.getFirst(), (C) v.get(1), (D) v.get(2)));
    }

    @SuppressWarnings("unchecked")
    public static <A, B, C, D, E> Codec<A> withExtra(Codec<A> base,
                                                     MapCodec<B> c1, MapCodec<C> c2,
                                                     MapCodec<D> c3, MapCodec<E> c4,
                                                     Function5<A, B, C, D, E, A> f) {
        return withExtra(base, List.of(c1, c2, c3, c4),
                (A a, List<Object> v) -> f.apply(a, (B) v.getFirst(), (C) v.get(1), (D) v.get(2), (E) v.get(3)));
    }

    /** An {@link ItemStack} written either as a full stack object or as a bare item id. */
    public static final Codec<ItemStack> ITEM_OR_STACK = Codec.lazyInitialized(() ->
            Codec.withAlternative(ItemStack.SINGLE_ITEM_CODEC, BuiltInRegistries.ITEM.byNameCodec(),
                    Item::getDefaultInstance));

    private static final Codec<List<ItemStack>> ITEMSTACK_OR_ITEMSTACK_LIST = singleOrList(ITEM_OR_STACK);

    private static final Codec<Supplier<List<ItemStack>>> ITEMSTACK_HOLDER_SET = RegistryCodecs.homogeneousList(Registries.ITEM)
            .xmap(l -> () -> l.stream().map(Holder::value).map(ItemStack::new).toList(),
                    s -> HolderSet.direct(s.get().stream().map(ItemStack::getItemHolder).toList()));

    /** A single item/stack, a list of them, or an item tag/holder-set — all as a {@code Supplier<List<ItemStack>>}. */
    public static final Codec<Supplier<List<ItemStack>>> ITEMSTACK_OR_LIST_OR_HOLDER_SET =
            Codec.withAlternative(
                    ITEMSTACK_OR_ITEMSTACK_LIST.xmap(l -> () -> l, Supplier::get),
                    ITEMSTACK_HOLDER_SET);
}
