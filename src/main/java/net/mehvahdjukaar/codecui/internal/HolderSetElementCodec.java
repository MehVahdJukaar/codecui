package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public interface HolderSetElementCodec {

    Codec<?> codecui$getElementCodec();

    ResourceKey<? extends Registry<?>> codecui$getRegistryKey();
}
