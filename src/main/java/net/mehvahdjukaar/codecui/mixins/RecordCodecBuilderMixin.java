package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.internal.SchemaResolver;
import net.mehvahdjukaar.codecui.internal.RecordFieldTags;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Tags RCB fields at construction so build(...) output can become a Schema.Record.
// Only the of(...) entry points are tagged; dependent/point/stable fall through to Opaque.
@Mixin(RecordCodecBuilder.class)
public abstract class RecordCodecBuilderMixin {

    @ModifyReturnValue(
            method = "of(Ljava/util/function/Function;Ljava/lang/String;Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;",
            at = @At("RETURN"))
    private static RecordCodecBuilder<?, ?> codecui$tagOfNamed(
            RecordCodecBuilder<?, ?> result,
            @Local(argsOnly = true) String name,
            @Local(argsOnly = true) Codec<?> fieldCodec) {
        RecordFieldTags.single(result, name, fieldCodec);
        return result;
    }

    @ModifyReturnValue(
            method = "of(Ljava/util/function/Function;Lcom/mojang/serialization/MapCodec;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;",
            at = @At("RETURN"))
    private static RecordCodecBuilder<?, ?> codecui$tagOfMap(
            RecordCodecBuilder<?, ?> result,
            @Local(argsOnly = true) MapCodec<?> mapCodec) {
        RecordFieldTags.singleMap(result, mapCodec);
        return result;
    }

    // Only field tags, no SchemaTags: the resolver builds the schema at lookup time so later companions win.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "build", at = @At("RETURN"))
    private static MapCodec<?> codecui$tagBuild(
            MapCodec<?> result,
            @Local(argsOnly = true) App<?, ?> builderBox) {
        try {
            RecordFieldTags.transferBuilt(RecordCodecBuilder.unbox((App) builderBox), result);
        } catch (Throwable ignored) {
        }
        return result;
    }
}
