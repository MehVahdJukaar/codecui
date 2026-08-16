package net.mehvahdjukaar.codecui;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.mehvahdjukaar.codecui.internal.SchemaResolver;

import java.util.function.Supplier;

/**
 * A Codec that also carries its Schema, so an existing static Codec field can become a SchemaCodec in place.
 * Declare the schema with of, SchemaRecord or SchemaCodecs, or let wrap infer it (falls back to Schema.Opaque).
 */
public sealed interface SchemaCodec<A> extends Codec<A> {

    Schema<A> schema();

    /**
     * Returns the codec itself if it already carries a schema, else wraps it with one the inference
     * engine derives fresh on each schema() call, so late-registered companions and handlers still apply.
     */
    @SuppressWarnings("unchecked")
    static <A> SchemaCodec<A> wrap(Codec<A> codec) {
        if (codec instanceof SchemaCodec<?> sc) return (SchemaCodec<A>) sc;
        return lazy(codec, () -> SchemaResolver.get().resolve(codec));
    }

    static <A> SchemaCodec<A> of(Codec<A> codec, Schema<A> schema) {
        return new SimpleSchemaCodec<>(codec, schema);
    }

    /**
     * Wraps a codec with a schema the supplier computes on each schema() call. Use it from static
     * initializers whose schema refers to other codecs' schemas, so nothing resolves at class load.
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
