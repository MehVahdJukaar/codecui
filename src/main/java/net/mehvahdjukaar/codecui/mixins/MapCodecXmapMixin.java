package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.internal.XmapTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// CodecXmapMixin for MapCodec: vanilla often does something.fieldOf("x").xmap(ctor, getter).
@Mixin(MapCodec.class)
public abstract class MapCodecXmapMixin {

    @ModifyReturnValue(method = "xmap", at = @At("RETURN"))
    private MapCodec<?> codecui$tagXmap(MapCodec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "flatXmap", at = @At("RETURN"))
    private MapCodec<?> codecui$tagFlatXmap(MapCodec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "validate", at = @At("RETURN"))
    private MapCodec<?> codecui$tagValidate(MapCodec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void codecui$inheritInner(MapCodec<?> wrapped) {
        if (wrapped == null || wrapped == (Object) this) return;
        try {
            XmapTags.putMap(
                    wrapped, (MapCodec<?>) (Object) this);
        } catch (Throwable ignored) {
        }
    }
}
