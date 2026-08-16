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
import net.mehvahdjukaar.codecui.internal.McCompat;
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
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? >=26.1
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.Comparator;
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
 * Public facade: primitives and combinators that pair a Codec with its Schema, plus the
 * registration hooks (registerCompanion, registerHandler, registerDispatchKeys) that extend
 * inference for codecs you don't own. SchemaCodec.wrap infers a schema for any other codec.
 */
public final class SchemaCodecs {
    public enum Inference {
        MIXIN,
        REFLECTION
    }

    /**
     * MIXIN when the codec-construction mixins are active, REFLECTION for the reduced-fidelity
     * fallback (always on NeoForge, and on Fabric when a DFU type loaded too early).
     */
    public static Inference inferenceMode() {
        return MixinDetection.constructionInterceptionActive() ? Inference.MIXIN : Inference.REFLECTION;
    }

    /**
     * Hand-crafted schema for one codec instance; wins over inference wherever that codec shows up.
     * The codec is held weakly but the schema strongly, so a per-instance codec must not appear
     * inside its own schema (Opaque over it, Custom widget bound to it) or it is never collected.
     */
    public static <A> void registerCompanion(Codec<A> codec, Schema<A> schema) {
        SchemaTags.tag(codec, schema);
    }

    public static <A> void registerCompanion(MapCodec<A> codec, Schema<A> schema) {
        SchemaTags.tag(codec, schema);
    }

    /** Teach the resolver a whole class of codecs. See SchemaHandler for the contract. */
    public static void registerHandler(SchemaHandler handler) {
        SchemaResolver.registerHandler(handler);
    }

    /**
     * Key set of a Codec.dispatch family whose key type can't implement EnumerableCodec.
     * Each key becomes a variant picker entry with a fully resolved body.
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

    public static SchemaCodec<Integer> colorRgb(Codec<Integer> codec) {
        return SchemaCodec.of(codec, new Schema.Color(false));
    }

    public static SchemaCodec<Integer> colorArgb(Codec<Integer> codec) {
        return SchemaCodec.of(codec, new Schema.Color(true));
    }

    /** Schema for any codec: its own when it is a SchemaCodec, otherwise inferred (Opaque as last resort). */
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

    public static <K, V> SchemaCodec<Map<K, V>> map(SchemaCodec<K> keyCodec, SchemaCodec<V> valueCodec) {
        Codec<Map<K, V>> codec = Codec.unboundedMap(keyCodec, valueCodec);
        Schema<Map<K, V>> schema = new Schema.MapOf<>(keyCodec.schema(), valueCodec.schema());
        return SchemaCodec.of(codec, schema);
    }

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

    public record Alt<A>(String label, Codec<A> codec) {}

    public static <A> Alt<A> alt(String label, Codec<A> codec) {
        return new Alt<>(label, codec);
    }

    /**
     * Labeled Codec.withAlternative: codec and AnyOf schema from the same two declarations.
     * Pass SchemaCodecs as alternatives where you can; a raw codec is inferred (Opaque at worst).
     */
    public static <A> SchemaCodec<A> withAlternative(Alt<A> primary, Alt<? extends A> secondary) {
        return SchemaCodec.lazy(Codec.withAlternative(primary.codec(), secondary.codec()),
                () -> Schema.anyOf(
                        Schema.option(primary.label(), resolve(primary.codec())),
                        Schema.option(secondary.label(), resolve(secondary.codec()))));
    }

    /**
     * Labels an existing multi-format codec: the wire codec passes through untouched, the schema
     * is a flat AnyOf over the given labeled parts.
     */
    public static <A> SchemaCodec<A> labeled(Codec<A> codec, Alt<?>... alternatives) {
        // an empty AnyOf is a dead picker, so with no parts just infer the codec itself
        if (alternatives.length == 0) {
            return SchemaCodec.lazy(codec, () -> resolve(codec));
        }
        return SchemaCodec.lazy(codec, () -> {
            List<Schema.AnyOf.Option> options = new ArrayList<>(alternatives.length);
            for (Alt<?> alt : alternatives) {
                options.add(Schema.option(alt.label(), resolve(alt.codec())));
            }
            return Schema.anyOf(options);
        });
    }

    /**
     * N-ary labeled "try each" codec (AlternativeCodec) plus a flat AnyOf schema. Unlike
     * withAlternative, every alternative is tried for both decode and encode.
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
            List<Schema.AnyOf.Option> options = new ArrayList<>(alternatives.length);
            for (Alt<? extends A> alt : alternatives) {
                options.add(Schema.option(alt.label(), resolve(alt.codec())));
            }
            return Schema.anyOf(options);
        });
    }

    /** Unlabeled form of alternatives(Alt...); picker options get auto-derived kind names. */
    @SafeVarargs
    public static <A> SchemaCodec<A> alternatives(Codec<? extends A>... codecs) {
        if (codecs.length == 0) {
            throw new IllegalArgumentException("alternatives() requires at least one alternative");
        }
        Codec<A> codec = new AlternativeCodec<>(codecs);
        return SchemaCodec.lazy(codec, () -> {
            List<Schema.AnyOf.Option> options = new ArrayList<>(codecs.length);
            for (Codec<? extends A> c : codecs) {
                options.add(Schema.option(resolve(c)));
            }
            return Schema.anyOf(options);
        });
    }

    public static <A> SchemaCodec<A> alternatives(String l1, Codec<? extends A> c1,
                                                  String l2, Codec<? extends A> c2) {
        return alternatives(alt(l1, c1), alt(l2, c2));
    }

    public static <A> SchemaCodec<A> alternatives(String l1, Codec<? extends A> c1,
                                                  String l2, Codec<? extends A> c2,
                                                  String l3, Codec<? extends A> c3) {
        return alternatives(alt(l1, c1), alt(l2, c2), alt(l3, c3));
    }

    public static <A> SchemaCodec<A> alternatives(String l1, Codec<? extends A> c1,
                                                  String l2, Codec<? extends A> c2,
                                                  String l3, Codec<? extends A> c3,
                                                  String l4, Codec<? extends A> c4) {
        return alternatives(alt(l1, c1), alt(l2, c2), alt(l3, c3), alt(l4, c4));
    }

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

    /** One TagKey of registryKey, wire form TagKey.codec (bare "ns:path", no '#'), rendered as a tag picker. */
    public static <T> SchemaCodec<TagKey<T>> tag(ResourceKey<? extends Registry<T>> registryKey) {
        Schema<TagKey<T>> schema = castSchema(new Schema.TagId(registryKey, false));
        return SchemaCodec.of(TagKey.codec(registryKey), schema);
    }

    /**
     * Datapack tag file over vanilla TagFile.CODEC, with members rendered as registryKey pickers.
     * Per registry because the target registry is in the file path, not the JSON.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static SchemaCodec<TagFile> tagFile(ResourceKey<? extends Registry<?>> registryKey) {
        Schema<Object> idOf = (Schema) new Schema.ResourceId(registryKey);
        Schema<Object> tagOf = (Schema) new Schema.TagId(registryKey);

        // a member is an entry id, a #tag reference, or the {id, required} object form
        Schema<Object> idOrTag = (Schema) Schema.anyOf(Schema.option("id", idOf), Schema.option("tag", tagOf));
        List<Schema.Field<Object, ?>> objectFields = List.of(
                new Schema.Field<>("id", idOrTag, false, null),
                new Schema.Field<>("required", new Schema.Bool(), true, Boolean.TRUE));
        Schema<Object> objectMember = new Schema.Record<>(Object.class, objectFields);
        Schema<Object> member = (Schema) Schema.anyOf(
                Schema.option("id", idOf),
                Schema.option("tag", tagOf),
                Schema.option("object", objectMember));

        Schema<List<Object>> values = new Schema.ListOf<>(member, 0, Integer.MAX_VALUE);
        List<Schema.Field<TagFile, ?>> fields = List.of(
                new Schema.Field<>("replace", new Schema.Bool(), true, Boolean.FALSE),
                new Schema.Field<>("values", values, false, null));
        Schema<TagFile> schema = (Schema) new Schema.Record<>(TagFile.class, fields);

        return SchemaCodec.of(TagFile.CODEC, schema);
    }

    /**
     * Vanilla-style HolderSet codec (tag, single entry, or list) whose list is recursive: elements
     * may themselves be tags, entries or nested lists, all flattened into one set.
     */
    public static <E> SchemaCodec<HolderSet<E>> recursiveHolderSet(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec) {
        return SchemaCodec.wrap(RecursiveHolderSetCodec.create(registryKey, elementCodec));
    }

    /**
     * Sorted ids of the currently loaded tags of registryKey, for a TagId picker. Empty (never
     * throws) for an unknown registry or before tags are bound, so backends can fall back to text.
     */
    public static List<Identifier> availableTagIds(ResourceKey<? extends Registry<?>> registryKey) {
        if (registryKey == null) return List.of();
        Registry<?> registry = McCompat.getValue(BuiltInRegistries.REGISTRY, McCompat.keyId(registryKey));
        if (registry == null) return List.of();
        return registry.getTags()
                //? >=1.21.2 {
                .flatMap(named -> named.unwrapKey().stream())
                .map(TagKey::location)
                //?} <1.21.2 {
                /*.map(pair -> pair.getFirst().location())
                *///?}
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    /** Type-dispatched map: typeField selects the variant, keyed by the string typeFn produces. */
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

    /** A string decodes through reference, anything else through direct; renders as a reference/inline picker. */
    public static <E> SchemaCodec<E> referenceOrDirect(Codec<? extends E> reference, Codec<? extends E> direct) {
        return referenceOrDirect(reference, direct, false);
    }

    public static <E> SchemaCodec<E> referenceOrDirect(Codec<? extends E> reference, Codec<? extends E> direct, boolean bothStrings) {
        Codec<E> raw = new ReferenceOrDirectCodec<>(reference, direct, bothStrings);
        return SchemaCodec.lazy(raw, () -> Schema.anyOf(
                Schema.option("reference", resolve(reference)),
                Schema.option("inline", resolve(direct))));
    }

    /** Tries both and keeps the one chooseFirst prefers when both parse. */
    public static <A, B extends A, C extends A> SchemaCodec<A> bestAlternative(
            Codec<B> first, Codec<C> second, BiPredicate<B, C> chooseFirst) {
        Codec<A> raw = new BestAlternativeCodec<>(first, second, chooseFirst);
        return SchemaCodec.lazy(raw, () -> Schema.anyOf(
                Schema.option(resolve(first)),
                Schema.option(resolve(second))));
    }

    /** Decodes A as Either.left; the schema is the left branch's. */
    public static <A, B> SchemaCodec<Either<A, B>> eitherLeft(Codec<A> leftCodec) {
        Codec<Either<A, B>> raw = new EitherLeftCodec<>(leftCodec);
        return SchemaCodec.lazy(raw, () -> castSchema(resolve(leftCodec)));
    }

    /** A single element OR a list of them - rendered as a "single / list" picker. */
    public static <A> SchemaCodec<List<A>> singleOrList(Codec<A> elementCodec) {
        Codec<List<A>> raw = Codec.withAlternative(elementCodec.listOf(), elementCodec, List::of);
        return SchemaCodec.lazy(raw, () -> singleOrListSchema(elementCodec));
    }

    /** As singleOrList(Codec) but collapses the list to one A via listToSingle. */
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

    /** Vanilla-style HolderSet codec whose list skips entries that fail to decode. */
    public static <E> SchemaCodec<HolderSet<E>> lenientHolderSet(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec) {
        return SchemaCodec.wrap(LenientHolderSetCodec.create(registryKey, elementCodec, false));
    }

    /** An unbounded map that logs and skips entries which fail to decode. */
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

    /** A required field readable under either primaryName or alias. */
    public static <B> MapCodec<B> alias(Codec<B> codec, String primaryName, String alias) {
        return AlternativeMapCodec.alias(codec, primaryName, alias);
    }

    /** An optional field readable under either primaryName or alias. */
    public static <B> MapCodec<Optional<B>> optionalAlias(Codec<B> codec, String primaryName, String alias) {
        return AlternativeMapCodec.optionalAlias(codec, primaryName, alias);
    }

    /** Membership predicate over a single-or-list of elements. */
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

    /** Decodes base plus extra map fields read off the same input, folded into the base via merge. */
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

    /** An ItemStack written as a full stack object or a bare item id. */
    public static final Codec<ItemStack> ITEM_OR_STACK = Codec.lazyInitialized(() ->
            Codec.withAlternative(/*? >=26.1 {*/ItemStack.CODEC.xmap(itemStack -> itemStack.copyWithCount(1), Function.identity())/*?} <26.1 {*//*ItemStack.SINGLE_ITEM_CODEC*//*?}*/, BuiltInRegistries.ITEM.byNameCodec(),
                    Item::getDefaultInstance));

    private static final Codec<List<ItemStack>> ITEMSTACK_OR_ITEMSTACK_LIST = singleOrList(ITEM_OR_STACK);

    private static final Codec<Supplier<List<ItemStack>>> ITEMSTACK_HOLDER_SET = RegistryCodecs.homogeneousList(Registries.ITEM)
            .xmap(l -> () -> l.stream().map(Holder::value).map(ItemStack::new).toList(),
                    s -> HolderSet.direct(s.get().stream().map(McCompat::itemHolder).toList()));

    /** A single item/stack, a list of them, or an item tag/holder-set. */
    public static final Codec<Supplier<List<ItemStack>>> ITEMSTACK_OR_LIST_OR_HOLDER_SET =
            Codec.withAlternative(
                    ITEMSTACK_OR_ITEMSTACK_LIST.xmap(l -> () -> l, Supplier::get),
                    ITEMSTACK_HOLDER_SET);
    //? >=26.1 {
    /** An ItemStackTemplate written as a full stack object or a bare item id. */
    public static final Codec<ItemStackTemplate> ITEM_OR_STACK_TEMPLATE = Codec.lazyInitialized(() ->
            Codec.withAlternative(ItemStackTemplate.CODEC.xmap(itemStack -> itemStack.withCount(1), Function.identity()), BuiltInRegistries.ITEM.byNameCodec(),
                    ItemStackTemplate::new));

    private static final Codec<List<ItemStackTemplate>> ITEMSTACK_TEMPLATE_OR_ITEMSTACK_TEMPLATE_LIST = singleOrList(ITEM_OR_STACK_TEMPLATE);

    private static final Codec<Supplier<List<ItemStackTemplate>>> ITEMSTACK_TEMPLATE_HOLDER_SET = RegistryCodecs.homogeneousList(Registries.ITEM)
            .xmap(l -> () -> l.stream().map(Holder::value).map(ItemStackTemplate::new).toList(),
                    s -> HolderSet.direct(s.get().stream().map(ItemStackTemplate::/*? >=26.1 {*/typeHolder/*?} <26.1 {*//*getItemHolder*//*?}*/).toList()));

    /** A single item/stack template, a list of them, or an item tag/holder-set. */
    public static final Codec<Supplier<List<ItemStackTemplate>>> ITEMSTACK_TEMPLATE_OR_LIST_OR_HOLDER_SET =
            Codec.withAlternative(
                    ITEMSTACK_TEMPLATE_OR_ITEMSTACK_TEMPLATE_LIST.xmap(l -> () -> l, Supplier::get),
                    ITEMSTACK_TEMPLATE_HOLDER_SET);
    //?}
}
