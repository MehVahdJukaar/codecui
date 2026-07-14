package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tags the {@link Codec} returned by {@link TagKey#codec} (and {@code hashedCodec}) with a
 * {@link Schema.TagId} carrying the target registry key — the tag twin of
 * {@link RegistryByNameCodecMixin}. The resolver then routes any {@code TagKey.codec(reg)} field
 * to a tag picker instead of the inherited {@code Identifier}-string schema.
 */
@Mixin(TagKey.class)
public class TagKeyCodecMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "codec", at = @At("RETURN"))
    private static Codec codecui$tagCodec(Codec original, ResourceKey<? extends Registry<?>> registry) {
        SchemaTags.tag(original, (Schema) new Schema.TagId(registry));
        return original;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "hashedCodec", at = @At("RETURN"))
    private static Codec codecui$tagHashedCodec(Codec original, ResourceKey<? extends Registry<?>> registry) {
        SchemaTags.tag(original, (Schema) new Schema.TagId(registry));
        return original;
    }
}
