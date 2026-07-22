package net.mehvahdjukaar.codecui.internal;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.mehvahdjukaar.codecui.EnumerableCodec;
import net.mehvahdjukaar.codecui.SchemaContext;
import net.minecraft.core.RegistryAccess;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// A simple class that allows for users to wrap an input codec as an EnumerableCodec, with a supplier for a values map
public class WrappedEnumerableCodec<A> implements Codec<A>, EnumerableCodec {
    private final Codec<A> wrapped;
    private final Supplier<Map<String, ?>> values;
    private Map<String, ?> cached = null;
    private RegistryAccess old = null;

    public WrappedEnumerableCodec(Codec<A> wrapped, Supplier<Map<String, ?>> values) {
        this.wrapped = wrapped;
        this.values = values;
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
        RegistryAccess newRegistries = SchemaContext.getRegistries();
        if (this.old == null || this.old != newRegistries) {
            this.old = newRegistries;
            this.cached = null;
        }
        if (this.cached == null)
            this.cached = this.values.get();
        return this.cached;
    }

    // Begin overrides for Codec transformations

    private <R> Codec<R> performOperationOnWrapped(Function<Codec<A>, Codec<R>> function) {
        return new WrappedEnumerableCodec<>(function.apply(this.wrapped), this.values);
    }

    @Override
    public Codec<A> withLifecycle(Lifecycle lifecycle) {
        return performOperationOnWrapped(wrapped -> wrapped.withLifecycle(lifecycle));
    }

    @Override
    public Codec<A> withAlternative(Codec<? extends A> alternative) {
        return performOperationOnWrapped(wrapped -> wrapped.withAlternative(alternative));
    }

    @Override
    public <U> Codec<A> withAlternative(Codec<U> alternative, Function<U, A> converter) {
        return performOperationOnWrapped(wrapped -> wrapped.withAlternative(alternative, converter));
    }

    @Override
    public Codec<List<A>> listOf() {
        return performOperationOnWrapped(Codec::listOf);
    }

    @Override
    public Codec<List<A>> listOf(int minSize, int maxSize) {
        return performOperationOnWrapped(wrapped -> wrapped.listOf(minSize, maxSize));
    }

    @Override
    public <S> Codec<S> xmap(Function<? super A, ? extends S> to, Function<? super S, ? extends A> from) {
        return performOperationOnWrapped(wrapped -> wrapped.xmap(to, from));
    }

    @Override
    public <S> Codec<S> comapFlatMap(Function<? super A, ? extends DataResult<? extends S>> to, Function<? super S, ? extends A> from) {
        return performOperationOnWrapped(wrapped -> wrapped.comapFlatMap(to, from));
    }

    @Override
    public <S> Codec<S> flatComapMap(Function<? super A, ? extends S> to, Function<? super S, ? extends DataResult<? extends A>> from) {
        return performOperationOnWrapped(wrapped -> wrapped.flatComapMap(to, from));
    }

    @Override
    public <S> Codec<S> flatXmap(Function<? super A, ? extends DataResult<? extends S>> to, Function<? super S, ? extends DataResult<? extends A>> from) {
        return performOperationOnWrapped(wrapped -> wrapped.flatXmap(to, from));
    }

    @Override
    public Codec<A> mapResult(ResultFunction<A> function) {
        return performOperationOnWrapped(wrapped -> wrapped.mapResult(function));
    }

    @Override
    public Codec<A> promotePartial(Consumer<String> onError) {
        return performOperationOnWrapped(wrapped -> wrapped.promotePartial(onError));
    }
}
