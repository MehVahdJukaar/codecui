package net.mehvahdjukaar.codecui.internal;

import com.mojang.datafixers.util.Function5;
import com.mojang.serialization.*;
import com.mojang.datafixers.util.Pair;

import java.util.Objects;

/**
 * Type-safe post-processing codecs for decoding a base object plus extra fields.
 * Supports chaining for a small number of extras while maintaining full type safety.
 */
public class PostProcessCodecs {
    /** Convenience static constructor */
    public static <A, B, C, D, E> C5<A, B, C, D, E> of(Codec<A> base,
                                                     MapCodec<B> e1,
                                                     MapCodec<C> e2,
                                                     MapCodec<D> e3,
                                                     MapCodec<E> e4,
                                                     Function5<A, B, C, D, E, A> f) {
        return new C5<>(base, e1, e2, e3, e4, f);
    }

    public static final class C5<A, B, C, D, E> implements Codec<A> {
        private final Codec<A> base;
        private final MapCodec<B> extra1;
        private final MapCodec<C> extra2;
        private final MapCodec<D> extra3;
        private final MapCodec<E> extra4;
        private final Function5<A, B, C, D, E, A> applyFunc;

        public C5(Codec<A> base,
                  MapCodec<B> extra1,
                  MapCodec<C> extra2,
                  MapCodec<D> extra3,
                  MapCodec<E> extra4,
                  Function5<A, B, C, D, E, A> applyFunc) {
            this.base = Objects.requireNonNull(base);
            this.extra1 = Objects.requireNonNull(extra1);
            this.extra2 = Objects.requireNonNull(extra2);
            this.extra3 = Objects.requireNonNull(extra3);
            this.extra4 = Objects.requireNonNull(extra4);
            this.applyFunc = Objects.requireNonNull(applyFunc);
        }

        /** The base codec — exposed so the schema resolver can render the primary shape. */
        public Codec<A> base() {
            return base;
        }

        @Override
        public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<Pair<A, T>> baseResult = base.decode(ops, input);
            DataResult<Pair<B, T>> r1 = extra1.codec().decode(ops, input);
            DataResult<Pair<C, T>> r2 = extra2.codec().decode(ops, input);
            DataResult<Pair<D, T>> r3 = extra3.codec().decode(ops, input);
            DataResult<Pair<E, T>> r4 = extra4.codec().decode(ops, input);

            return baseResult.flatMap(basePair ->
                    r1.flatMap(p1 ->
                            r2.flatMap(p2 ->
                                    r3.flatMap(p3 ->
                                            r4.map(p4 ->
                                                    Pair.of(applyFunc.apply(basePair.getFirst(),
                                                                    p1.getFirst(), p2.getFirst(), p3.getFirst(), p4.getFirst()),
                                                            basePair.getSecond())
                                            )
                                    )
                            )
                    )
            );
        }

        @Override
        public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
            return base.encode(input, ops, prefix);
        }

        @Override
        public String toString() {
            return "PostProcessCodec5[" + base + ", " + extra1 + ", " + extra2 + ", " + extra3 + ", " + extra4 + "]";
        }
    }
}
