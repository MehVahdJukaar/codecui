package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

public interface HolderSetCodecExtension {
    @Nullable Codec<?> codecui$elementCodec();
    @Nullable ResourceKey<? extends Registry<?>> codecui$registryKey();
}
