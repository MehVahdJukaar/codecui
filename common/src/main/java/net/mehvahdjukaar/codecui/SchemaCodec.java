package net.mehvahdjukaar.codecui;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.function.Supplier;

/**
 * Pairing of a {@link Codec} with its {@link Schema}. Since this IS a {@code Codec}, an
 * existing {@code static final Codec<X> CODEC = ...} declaration can be upgraded in place
 * to {@code static final SchemaCodec<X> CODEC = SchemaRecord.create(...)} (or {@code of}/
 * {@code lazy}) with no change for any code that uses it.
 *
 * <p>The edit surface can be declared explicitly via {@link #of}, {@link SchemaRecord},
 * {@link SchemaRecordBuilder} or the {@link SchemaCodecs} combinators/primitives. A raw codec
 * passed to {@link #wrap} is introspected by the bundled inference engine (structural walk +
 * construction-mixin tags + registered companions/handlers); anything it can't resolve degrades
 * to a {@link Schema.Opaque} (raw-JSON) surface.</p>
 */
public sealed interface SchemaCodec<A> extends Codec<A> {

    Schema<A> schema();

    /**
     * Return the codec unchanged when it already carries a schema; otherwise wrap it with a
     * schema derived LAZILY by the inference engine on each {@link #schema()} call, so
     * companions/handlers/registry content registered later are reflected. Anything the engine
     * can't introspect degrades to a {@link Schema.Opaque} raw-JSON surface.
     */
    @SuppressWarnings("unchecked")
    static <A> SchemaCodec<A> wrap(Codec<A> codec) {
        if (codec instanceof SchemaCodec<?> sc) return (SchemaCodec<A>) sc;
        return lazy(codec, () -> net.mehvahdjukaar.codecui.internal.SchemaResolver.get().resolve(codec));
    }

    static <A> SchemaCodec<A> of(Codec<A> codec, Schema<A> schema) {
        return new SimpleSchemaCodec<>(codec, schema);
    }

    /**
     * Wrap a raw codec with a lazily computed schema - the supplier runs fresh on each
     * {@link #schema()} call. Use when the schema references other codecs' schemas (e.g.
     * a labeled {@code SchemaCodecs.anyOf} over alternatives) from a static initializer:
     * resolution is deferred instead of being frozen at class load.
     */
    static <A> SchemaCodec<A> lazy(Codec<A> codec, Supplier<Schema<A>> schema) {
        return new LazySchemaCodec<>(codec, schema);
    }

    record SimpleSchemaCodec<A>(Codec<A> codec, Schema<A> schema) implements SchemaCodec<A> {
        @Override
        public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
            return codec.decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
            return codec.encode(input, ops, prefix);
        }
    }

    record LazySchemaCodec<A>(Codec<A> codec, Supplier<Schema<A>> schemaSupplier) implements SchemaCodec<A> {
        @Override
        public Schema<A> schema() {
            return schemaSupplier.get();
        }

        @Override
        public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
            return codec.decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
            return codec.encode(input, ops, prefix);
        }
    }
}
