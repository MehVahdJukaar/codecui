package net.mehvahdjukaar.codecui.internal;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

// Decodes a base object, then decodes zero or more EXTRA map-fields off the same input and
// folds them into the base via merge. The extras are decode-only: encoding delegates straight
// to the base codec, so they don't round-trip back out on their own.
public final class CodecWithExtra<A> implements Codec<A> {
    private final Codec<A> base;
    private final List<MapCodec<?>> extras;
    private final BiFunction<A, List<Object>, A> merge;

    public CodecWithExtra(Codec<A> base, List<MapCodec<?>> extras, BiFunction<A, List<Object>, A> merge) {
        this.base = Objects.requireNonNull(base);
        this.extras = List.copyOf(extras);
        this.merge = Objects.requireNonNull(merge);
    }

    // Exposed so the schema resolver can render the primary shape.
    public Codec<A> base() {
        return base;
    }

    @Override
    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
        return base.decode(ops, input).flatMap(basePair -> {
            DataResult<List<Object>> acc = DataResult.success(new ArrayList<>(extras.size()));
            for (MapCodec<?> extra : extras) {
                DataResult<? extends Pair<?, T>> r = extra.codec().decode(ops, input);
                acc = acc.flatMap(values -> r.map(pair -> {
                    values.add(pair.getFirst());
                    return values;
                }));
            }
            return acc.map(values -> Pair.of(merge.apply(basePair.getFirst(), values), basePair.getSecond()));
        });
    }

    @Override
    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
        return base.encode(input, ops, prefix);
    }

    @Override
    public String toString() {
        return "CodecWithExtra[" + base + ", extras=" + extras + "]";
    }
}
