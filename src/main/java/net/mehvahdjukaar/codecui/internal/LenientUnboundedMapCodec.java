package net.mehvahdjukaar.codecui.internal;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.BaseMapCodec;
import net.mehvahdjukaar.codecui.CodecUI;

import java.util.Map;
import java.util.Objects;

public record LenientUnboundedMapCodec<K, V>(Codec<K> keyCodec,
                                             Codec<V> elementCodec) implements BaseMapCodec<K, V>, Codec<Map<K, V>> {

    @Override
    public <T> DataResult<Map<K, V>> decode(DynamicOps<T> ops, MapLike<T> input) {
        ImmutableMap.Builder<K, V> decoded = ImmutableMap.builder();
        input.entries().forEach((pair) -> {
            DataResult<K> key = this.keyCodec().parse(ops, pair.getFirst());
            DataResult<V> value = this.elementCodec().parse(ops, pair.getSecond());
            DataResult<Pair<K, V>> entry = key.apply2stable(Pair::of, value);
            entry.error().ifPresent((e) -> {
                CodecUI.LOGGER.error("Failed to decode key {} for value {}: {}", key, value, e);
            });
            entry.result().ifPresent((e) -> {
                decoded.put(e.getFirst(), e.getSecond());
            });
        });
        return DataResult.success(decoded.build());
    }

    @Override
    public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
        return ops.getMap(input).setLifecycle(Lifecycle.stable()).flatMap((map) -> this.decode(ops, map)).map((r) -> Pair.of(r, input));
    }

    @Override
    public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T prefix) {
        return this.encode(input, ops, ops.mapBuilder()).build(prefix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            LenientUnboundedMapCodec<?, ?> that = (LenientUnboundedMapCodec) o;
            return Objects.equals(this.keyCodec, that.keyCodec) && Objects.equals(this.elementCodec, that.elementCodec);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.keyCodec, this.elementCodec);
    }

    @Override
    public String toString() {
        return "LenientUnboundedMapCodec[" + String.valueOf(this.keyCodec) + " -> " + this.elementCodec + "]";
    }
}
