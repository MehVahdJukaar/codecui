package net.mehvahdjukaar.codecui.internal;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.mehvahdjukaar.codecui.EnumerableCodec;
import net.mehvahdjukaar.codecui.SchemaContext;
import net.minecraft.core.RegistryAccess;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Wraps a codec as an EnumerableCodec; the values map is recomputed when the registries change.
public class WrappedEnumerableCodec<A> implements Codec<A>, EnumerableCodec {
    private final Codec<A> wrapped;
    private final Supplier<Map<String, ?>> valuesSupplier;
    private Map<String, ?> cachedValues = null;
    private RegistryAccess cachedForRegistries = null;

    public WrappedEnumerableCodec(Codec<A> wrapped, Supplier<Map<String, ?>> valuesSupplier) {
        this.wrapped = wrapped;
        this.valuesSupplier = valuesSupplier;
    }

    @Override
    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
        return this.wrapped.decode(ops, input);
    }

    @Override
    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
        return this.wrapped.encode(input, ops, prefix);
    }

    @Override
    public Map<String, ?> codecUiValues() {
        RegistryAccess registries = SchemaContext.getRegistries();
        if (this.cachedForRegistries == null || this.cachedForRegistries != registries) {
            this.cachedForRegistries = registries;
            this.cachedValues = null;
        }
        if (this.cachedValues == null)
            this.cachedValues = this.valuesSupplier.get();
        return this.cachedValues;
    }

    private <R> Codec<R> rewrap(Function<Codec<A>, Codec<R>> transform) {
        return new WrappedEnumerableCodec<>(transform.apply(this.wrapped), this.valuesSupplier);
    }

    @Override
    public Codec<A> withLifecycle(Lifecycle lifecycle) {
        return rewrap(wrapped -> wrapped.withLifecycle(lifecycle));
    }

    @Override
    public <S> Codec<S> xmap(Function<? super A, ? extends S> to, Function<? super S, ? extends A> from) {
        return rewrap(wrapped -> wrapped.xmap(to, from));
    }

    @Override
    public <S> Codec<S> comapFlatMap(Function<? super A, ? extends DataResult<? extends S>> to, Function<? super S, ? extends A> from) {
        return rewrap(wrapped -> wrapped.comapFlatMap(to, from));
    }

    @Override
    public <S> Codec<S> flatComapMap(Function<? super A, ? extends S> to, Function<? super S, ? extends DataResult<? extends A>> from) {
        return rewrap(wrapped -> wrapped.flatComapMap(to, from));
    }

    @Override
    public <S> Codec<S> flatXmap(Function<? super A, ? extends DataResult<? extends S>> to, Function<? super S, ? extends DataResult<? extends A>> from) {
        return rewrap(wrapped -> wrapped.flatXmap(to, from));
    }

    @Override
    public Codec<A> mapResult(ResultFunction<A> function) {
        return rewrap(wrapped -> wrapped.mapResult(function));
    }

    @Override
    public Codec<A> promotePartial(Consumer<String> onError) {
        return rewrap(wrapped -> wrapped.promotePartial(onError));
    }
}
