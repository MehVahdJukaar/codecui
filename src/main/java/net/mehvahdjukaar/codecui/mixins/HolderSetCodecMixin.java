package net.mehvahdjukaar.codecui.mixins;

import com.mojang.serialization.Codec;
import net.mehvahdjukaar.codecui.internal.HolderSetElementCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HolderSetCodec.class)
public class HolderSetCodecMixin implements HolderSetElementCodec {

    @Unique
    private Codec<?> codecui$elementCodec;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void codecui$captureElementCodec(ResourceKey<? extends Registry<?>> registryKey, Codec<?> elementCodec, boolean disallowInline, CallbackInfo ci) {
        this.codecui$elementCodec = elementCodec;
    }

    @Override
    public Codec<?> codecui$getElementCodec() {
        return codecui$elementCodec;
    }
}
