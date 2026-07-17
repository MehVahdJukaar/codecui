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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Like vanilla HolderSetCodec a value is a tag ("#c:ingots"), a single entry
// ("minecraft:iron_ingot") or a list of entries. Unlike vanilla the list is recursive:
// every element may itself be any of those forms - a tag, an entry or a nested list. All of them are
// flattened into a single HolderSet, so tags can be mixed with entries inside one list:
// ["#c:ingots", "minecraft:stick", ["#minecraft:planks", "minecraft:iron_ingot"]]
// Build one with #create; the recursion is closed over Codec#recursive.
public final class RecursiveHolderSetCodec<E> implements Codec<HolderSet<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<Holder<E>> elementCodec;
    // tag | single entry | list of (recursive) holder sets
    private final Codec<Either<TagKey<E>, Either<Holder<E>, List<HolderSet<E>>>>> registryAwareCodec;

    private RecursiveHolderSetCodec(ResourceKey<? extends Registry<E>> registryKey,
                                    Codec<Holder<E>> elementCodec,
                                    Codec<HolderSet<E>> self) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.registryAwareCodec = Codec.either(
                TagKey.hashedCodec(registryKey),
                Codec.either(elementCodec, self.listOf()));
    }

    public static <E> Codec<HolderSet<E>> create(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec) {
        return Codec.recursive(
                "RecursiveHolderSet[" + registryKey.location() + "]",
                self -> new RecursiveHolderSetCodec<>(registryKey, elementCodec, self));
    }

    // Used by SchemaResolver to build the tag picker / element editor.
    Codec<Holder<E>> elementCodec() {
        return elementCodec;
    }

    @Override
    public <T> DataResult<Pair<HolderSet<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<T> registryOps) {
            Optional<HolderGetter<E>> optional = registryOps.getter(this.registryKey);
            if (optional.isPresent()) {
                HolderGetter<E> holderGetter = optional.get();
                return this.registryAwareCodec.decode(ops, input).flatMap(pair -> {
                    DataResult<HolderSet<E>> result = pair.getFirst().map(
                            tagKey -> lookupTag(holderGetter, tagKey),
                            entryOrList -> entryOrList.map(
                                    holder -> DataResult.success(HolderSet.direct(holder)),
                                    sets -> DataResult.success(flatten(sets))));
                    return result.map(holderSet -> Pair.of(holderSet, pair.getSecond()));
                });
            }
        }
        return this.decodeWithoutRegistry(ops, input);
    }

    @Override
    public <T> DataResult<T> encode(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<T> registryOps) {
            Optional<HolderOwner<E>> optional = registryOps.owner(this.registryKey);
            if (optional.isPresent() && !input.canSerializeIn(optional.get())) {
                return DataResult.error(() -> "HolderSet " + input + " is not valid in current registry set");
            }
        }
        return this.registryAwareCodec.encode(toEither(input), ops, prefix);
    }

    // Reuses the recursive codec so nested lists still decode; only tags can't resolve here.
    private <T> DataResult<Pair<HolderSet<E>, T>> decodeWithoutRegistry(DynamicOps<T> ops, T input) {
        return this.registryAwareCodec.decode(ops, input).flatMap(pair -> {
            Either<TagKey<E>, Either<Holder<E>, List<HolderSet<E>>>> either = pair.getFirst();
            if (either.left().isPresent()) {
                TagKey<E> tagKey = either.left().get();
                return DataResult.error(() -> "Can't decode tag " + tagKey.location() + " without registry");
            }
            DataResult<HolderSet<E>> result = either.right().orElseThrow().map(
                    holder -> holder instanceof Holder.Direct
                            ? DataResult.success(HolderSet.direct(holder))
                            : DataResult.error(() -> "Can't decode element " + holder + " without registry"),
                    sets -> DataResult.success(flatten(sets)));
            return result.map(holderSet -> Pair.of(holderSet, pair.getSecond()));
        });
    }

    // A HolderSet is either a tag or a flat list of holders; encode a single-entry set as one entry.
    private static <E> Either<TagKey<E>, Either<Holder<E>, List<HolderSet<E>>>> toEither(HolderSet<E> input) {
        return input.unwrap().map(
                Either::left,
                list -> list.size() == 1
                        ? Either.right(Either.left(list.getFirst()))
                        : Either.right(Either.right(list.stream().map(h -> (HolderSet<E>) HolderSet.direct(h)).toList())));
    }

    private static <E> HolderSet<E> flatten(List<HolderSet<E>> sets) {
        List<Holder<E>> holders = new ArrayList<>();
        for (HolderSet<E> set : sets) {
            for (Holder<E> holder : set) {
                holders.add(holder);
            }
        }
        return HolderSet.direct(holders);
    }

    @SuppressWarnings("unchecked")
    private static <E> DataResult<HolderSet<E>> lookupTag(HolderGetter<E> holderGetter, TagKey<E> tagKey) {
        return (DataResult<HolderSet<E>>) (Object) holderGetter.get(tagKey)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(
                        () -> "Missing tag: '" + tagKey.location() + "' in '" + tagKey.registry().location() + "'"));
    }

    @Override
    public String toString() {
        return "RecursiveHolderSetCodec[" + this.registryKey.location() + "]";
    }
}
