package net.mehvahdjukaar.codecui.internal;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.ArrayList;
import java.util.List;

public record AlternativeCodec<A>(Codec<? extends A>... codecs) implements Codec<A> {

    @SafeVarargs
    public AlternativeCodec {
        if (codecs.length == 0) {
            throw new IllegalArgumentException("AlternativeCodec requires at least one codec");
        }
        for (var codec : codecs) {
            if (codec == this) {
                throw new IllegalArgumentException("AlternativeCodec cannot contain itself as a codec");
            }
        }
    }

    @Override
    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
        List<String> errors = new ArrayList<>();
        DataResult<Pair<A, T>> lastPartial = null;

        for (int i = 0; i < codecs.length; i++) {
            Codec<? extends A> codec = codecs[i];
            var result = codec.decode(ops, input);

            if (result.isSuccess()) {
                return result.map(vo -> Pair.of(vo.getFirst(), vo.getSecond()));
            }

            if (result.hasResultOrPartial()) {
                lastPartial = result.map(vo -> Pair.of(vo.getFirst(), vo.getSecond()));
            }

            int finalI = i;
            result.error().ifPresent(e -> errors.add("[" + finalI + "]: " + e.message()));
        }

        String combined = String.join("; ", errors);
        if (lastPartial != null) {
            return lastPartial.mapError(msg ->
                    "No alternative matched (partial from one attempt: " + msg + "). All errors: " + combined);
        }

        return DataResult.error(() -> "Failed to parse any alternative codec. Errors: " + combined);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
        for (Codec<? extends A> codec : codecs) {
            try {
                DataResult<T> encoded = ((Codec<A>) codec).encode(input, ops, prefix);
                if (encoded.isSuccess()) {
                    return encoded;
                }
            } catch (ClassCastException ignored) {
            }
        }
        return DataResult.error(() -> "No alternative codec could encode value: " + input);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AlternativeCodec[");
        for (int i = 0; i < codecs.length; i++) {
            sb.append(codecs[i].toString());
            if (i < codecs.length - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }
}
