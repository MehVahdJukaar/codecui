package net.mehvahdjukaar.codecui.internal;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class McCompat {

    // ResourceKey.location() -> ResourceKey.identifier() (renamed in 1.21.11)
    public static Identifier keyId(ResourceKey<?> key) {
        return key./*? >=1.21.11 {*/identifier/*?} <1.21.11 {*//*location*//*?}*/();
    }

    // Registry.get(Identifier) -> Registry.getValue(Identifier) (renamed after 1.21.1);
    // both return the value directly, or null when absent.
    public static <T> @Nullable T getValue(Registry<T> registry, Identifier id) {
        return registry./*? >1.21.1 {*/getValue/*?} <=1.21.1 {*//*get*//*?}*/(id);
    }

    // ItemStack.getItemHolder() -> ItemStack.typeHolder() (renamed in 26.1)
    public static Holder<Item> itemHolder(ItemStack stack) {
        return stack./*? >=26.1 {*/typeHolder/*?} <26.1 {*//*getItemHolder*//*?}*/();
    }
}
