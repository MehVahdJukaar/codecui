package net.mehvahdjukaar.codecui.mixins;

import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryFixedCodec.class)
public interface RegistryFixedCodecAccessor {

    @Accessor("registryKey")
    ResourceKey<? extends Registry<?>> codecui$getRegistryKey();
}
