package net.mehvahdjukaar.codecui.mixins;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryFileCodec.class)
public interface RegistryFileCodecAccessor {

    @Accessor("registryKey")
    ResourceKey<? extends Registry<?>> codecui$getRegistryKey();

    @Accessor("elementCodec")
    Codec<?> codecui$getElementCodec();

    @Accessor("allowInline")
    boolean codecui$allowsInline();
}
