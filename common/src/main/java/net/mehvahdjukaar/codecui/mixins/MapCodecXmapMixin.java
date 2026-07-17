package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.MapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// Mirror of CodecXmapMixin for MapCodec.xmap / flatXmap.
// Many vanilla CODECs are built like something.fieldOf("x").xmap(ctor, getter) - the
// xmap is on the MapCodec, not the Codec. Without this mixin, those outputs are opaque to the
// resolver and the entire record falls to a raw JSON editor.
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
            net.mehvahdjukaar.codecui.internal.XmapTags.putMap(
                    wrapped, (MapCodec<?>) (Object) this);
        } catch (Throwable ignored) {
        }
    }
}
