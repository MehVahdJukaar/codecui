package net.mehvahdjukaar.codecui.internal;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

public final class EitherLeftCodec<A, B> implements Codec<Either<A, B>> {

    private final Codec<A> leftCodec;

    public EitherLeftCodec(Codec<A> leftCodec) {
        this.leftCodec = leftCodec;
    }

    @Override
    public <T> DataResult<Pair<Either<A, B>, T>> decode(DynamicOps<T> ops, T input) {
        return leftCodec.decode(ops, input).map(pair ->
                pair.mapFirst(Either::left)
        );
    }

    @Override
    public <T> DataResult<T> encode(Either<A, B> either, DynamicOps<T> ops, T prefix) {
        return either.left()
                .map(a -> leftCodec.encode(a, ops, prefix))
                .orElseGet(() -> DataResult.error(() -> "Expected left value"));
    }

    @Override
    public String toString() {
        return "EitherLeftCodec[" + leftCodec + "]";
    }
}
