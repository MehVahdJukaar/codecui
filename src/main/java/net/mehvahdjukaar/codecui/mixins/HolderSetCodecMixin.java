package net.mehvahdjukaar.codecui.mixins;

import com.mojang.serialization.Codec;
import net.mehvahdjukaar.codecui.internal.HolderSetCodecExtension;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HolderSetCodec.class)
public abstract class HolderSetCodecMixin<E> implements HolderSetCodecExtension {

    @Unique private Codec<Holder<E>> codecui$elementCodec;
    @Unique private ResourceKey<? extends Registry<E>> codecui$registryKey;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void codecui$captureElementCodec(ResourceKey<? extends Registry<E>> registryKey,
                                             Codec<Holder<E>> elementCodec,
                                             boolean alwaysUseList,
                                             CallbackInfo ci) {
        this.codecui$elementCodec = elementCodec;
        this.codecui$registryKey = registryKey;
    }

    @Override public Codec<?> codecui$elementCodec() { return this.codecui$elementCodec; }
    @Override public ResourceKey<? extends Registry<?>> codecui$registryKey() { return this.codecui$registryKey; }
}
