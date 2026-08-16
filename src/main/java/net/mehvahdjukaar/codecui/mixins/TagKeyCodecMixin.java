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

// Tags TagKey#codec / hashedCodec output with Schema.TagId(registry key).
@Mixin(TagKey.class)
public class TagKeyCodecMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "codec", at = @At("RETURN"))
    private static Codec codecui$tagCodec(Codec original, ResourceKey<? extends Registry<?>> registry) {
        // codec writes "namespace:path", hashedCodec writes "#namespace:path".
        SchemaTags.tag(original, (Schema) new Schema.TagId(registry, false));
        return original;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "hashedCodec", at = @At("RETURN"))
    private static Codec codecui$tagHashedCodec(Codec original, ResourceKey<? extends Registry<?>> registry) {
        SchemaTags.tag(original, (Schema) new Schema.TagId(registry));
        return original;
    }
}
