package net.mehvahdjukaar.codecui.internal;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Vanilla HolderSetCodec with a LenientListCodec for the list form: bad elements are skipped.
public final class LenientHolderSetCodec<E> implements Codec<HolderSet<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<Holder<E>> elementCodec;
    private final Codec<List<Holder<E>>> homogenousListCodec;
    private final Codec<Either<TagKey<E>, List<Holder<E>>>> registryAwareCodec;

    private static <E> Codec<List<Holder<E>>> homogenousList(Codec<Holder<E>> holderCodec, boolean disallowInline) {
        Codec<List<Holder<E>>> codec = LenientListCodec.of(holderCodec).validate(ExtraCodecs.ensureHomogenous(Holder::kind));
        return disallowInline
                ? codec
                : Codec.either(codec, holderCodec)
                .xmap(either -> either.map(list -> list, List::of), list -> list.size() == 1 ? Either.right(list.get(0)) : Either.left(list));
    }

    public static <E> Codec<HolderSet<E>> create(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> holderCodec, boolean disallowInline) {
        return new LenientHolderSetCodec<>(registryKey, holderCodec, disallowInline);
    }

    private LenientHolderSetCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec, boolean disallowInline) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.homogenousListCodec = homogenousList(elementCodec, disallowInline);
        this.registryAwareCodec = Codec.either(TagKey.hashedCodec(registryKey), this.homogenousListCodec);
    }

    Codec<Holder<E>> elementCodec() {
        return elementCodec;
    }

    ResourceKey<? extends Registry<E>> registryKey() {
        return registryKey;
    }

    @Override
    public <T> DataResult<Pair<HolderSet<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<T> registryOps) {
            Optional<HolderGetter<E>> getter = registryOps.getter(this.registryKey);
            if (getter.isPresent()) {
                HolderGetter<E> holderGetter = getter.get();
                return this.registryAwareCodec
                        .decode(ops, input)
                        .flatMap(pair -> {
                            DataResult<HolderSet<E>> dataResult = pair.getFirst()
                                    .map(tagKey -> lookupTag(holderGetter, tagKey), list -> DataResult.success(HolderSet.direct(list)));
                            return dataResult.map(holderSet -> Pair.of(holderSet, pair.getSecond()));
                        });
            }
        }
        return this.decodeWithoutRegistry(ops, input);
    }

    @SuppressWarnings("unchecked")
    private static <E> DataResult<HolderSet<E>> lookupTag(HolderGetter<E> getter, TagKey<E> tagKey) {
        return (DataResult<HolderSet<E>>) (Object) getter.get(tagKey)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Missing tag: '" + tagKey.location() + "' in '" + McCompat.keyId(tagKey.registry()) + "'"));
    }

    @Override
    public <T> DataResult<T> encode(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<T> registryOps) {
            //? <26.3 {
            Optional<HolderOwner<E>> owner = registryOps.owner(this.registryKey);
             //?} else {
            /*Optional<? extends HolderOwner<E>> owner = registryOps.getter(this.registryKey);
            *///?}
            if (owner.isPresent()) {
                if (!input.canSerializeIn(owner.get())) {
                    return DataResult.error(() -> "HolderSet " + input + " is not valid in current registry set");
                }
                return this.registryAwareCodec.encode(input.unwrap().mapRight(List::copyOf), ops, prefix);
            }
        }
        return this.encodeWithoutRegistry(input, ops, prefix);
    }

    private <T> DataResult<Pair<HolderSet<E>, T>> decodeWithoutRegistry(DynamicOps<T> ops, T input) {
        return this.elementCodec.listOf().decode(ops, input).flatMap(pair -> {
            List<Holder.Direct<E>> list = new ArrayList<>();
            for (Holder<E> holder : pair.getFirst()) {
                if (!(holder instanceof Holder.Direct<E> direct)) {
                    return DataResult.error(() -> "Can't decode element " + holder + " without registry");
                }
                list.add(direct);
            }
            return DataResult.success(new Pair<>(HolderSet.direct(list), pair.getSecond()));
        });
    }

    private <T> DataResult<T> encodeWithoutRegistry(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        return this.homogenousListCodec.encode(input.stream().toList(), ops, prefix);
    }

    @Override
    public String toString() {
        return "LenientHolderSetCodec[" + McCompat.keyId(this.registryKey) + "]";
    }
}
