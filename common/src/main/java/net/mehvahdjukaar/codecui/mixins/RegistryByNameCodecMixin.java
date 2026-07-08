package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tags the {@link Codec} returned by {@link Registry#byNameCodec()} with a
 * {@link Schema.ResourceId} carrying this registry's key. The resolver then routes the codec
 * to a {@code ResourceIdWidget} (dropdown / picker) instead of the inherited String schema.
 *
 * <p>Because {@code byNameCodec()} is a default method on {@code Registry}, this mixin fires
 * for every concrete registry implementation — vanilla, mod registries, custom ones.</p>
 */
@Mixin(Registry.class)
public interface RegistryByNameCodecMixin<T> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "byNameCodec", at = @At("RETURN"))
    private Codec<T> polytone$tagByNameCodec(Codec<T> wrapped) {
        polytone$tagWithKey(wrapped);
        return wrapped;
    }

    // Holder<T>-typed twin (MobEffect.CODEC, etc.) — same id-string on-disk form.
    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "holderByNameCodec", at = @At("RETURN"))
    private Codec polytone$tagHolderByNameCodec(Codec wrapped) {
        polytone$tagWithKey(wrapped);
        return wrapped;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @org.spongepowered.asm.mixin.Unique
    private void polytone$tagWithKey(Codec<?> wrapped) {
        try {
            ResourceKey<? extends Registry<T>> key = ((Registry<T>) this).key();
            Schema.ResourceId schema = new Schema.ResourceId(key);
            SchemaTags.tag((Codec) wrapped, (Schema) schema);
        } catch (Throwable ignored) {
            // Best-effort.
        }
    }
}
