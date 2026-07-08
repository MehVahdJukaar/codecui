package net.mehvahdjukaar.codecui;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.internal.DispatchRegistry;
import net.mehvahdjukaar.codecui.internal.SchemaResolver;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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

    private SchemaCodecs() {}

    // ---- inference-engine extension points (feed the resolver behind SchemaCodec.wrap) ----

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
                                                Function<K, MapCodec<?>> codecOf, Function<K, String> nameOf) {
        DispatchRegistry.register(keyType, keys, codecOf, nameOf);
    }

    // ---- primitive SchemaCodecs: declare a field without wrapping by hand ----

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

    // ---- labeled alternatives: state each alternative ONCE (label + codec together) ----

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
        return SchemaCodec.lazy(codec, () -> {
            List<Schema.AnyOf.Option> options = new java.util.ArrayList<>(alternatives.length);
            for (Alt<?> alt : alternatives) {
                options.add(Schema.option(alt.label(), resolve(alt.codec())));
            }
            return Schema.anyOf(options);
        });
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
}
