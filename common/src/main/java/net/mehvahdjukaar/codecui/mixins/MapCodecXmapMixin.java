package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.MapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mirror of {@link CodecXmapMixin} for {@code MapCodec.xmap} / {@code flatXmap}.
 * Many vanilla CODECs are built like {@code something.fieldOf("x").xmap(ctor, getter)} — the
 * xmap is on the MapCodec, not the Codec. Without this mixin, those outputs are opaque to the
 * resolver and the entire record falls to a raw JSON editor.
 */
@Mixin(MapCodec.class)
public abstract class MapCodecXmapMixin {

    @ModifyReturnValue(method = "xmap", at = @At("RETURN"))
    private MapCodec<?> polytone$tagXmap(MapCodec<?> wrapped) {
        polytone$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "flatXmap", at = @At("RETURN"))
    private MapCodec<?> polytone$tagFlatXmap(MapCodec<?> wrapped) {
        polytone$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "validate", at = @At("RETURN"))
    private MapCodec<?> polytone$tagValidate(MapCodec<?> wrapped) {
        polytone$inheritInner(wrapped);
        return wrapped;
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void polytone$inheritInner(MapCodec<?> wrapped) {
        if (wrapped == null || wrapped == (Object) this) return;
        try {
            net.mehvahdjukaar.codecui.internal.XmapTags.putMap(
                    wrapped, (MapCodec<?>) (Object) this);
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }
}
