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

import java.util.List;

// Captures per-field information during RecordCodecBuilder construction so the MapCodec
// returned by build(...) can carry a real Schema.Record; without this the anonymous MapCodec
// is fully opaque. Coverage: only the of(...) entry points are tagged - dependent(...) and
// point/stable carry empty tag lists and fall through to Opaque. Optionality is best-effort:
// only the of(getter, MapCodec) form carries it; of(getter, name, codec) marks fields required.
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

    // Transfers the accumulated field tags to the OUTPUT MapCodec. Deliberately does not
    // populate SchemaTags: the resolver rebuilds the schema fresh at lookup time so
    // companions registered later still win.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyReturnValue(method = "build", at = @At("RETURN"))
    private static MapCodec<?> codecui$tagBuild(
            MapCodec<?> result,
            @Local(argsOnly = true) App<?, ?> builderBox) {
        try {
            RecordCodecBuilder<?, ?> builder = RecordCodecBuilder.unbox((App) builderBox);
            List<RecordFieldTags.Entry> entries = RecordFieldTags.get(builder);
            if (entries.isEmpty()) return result;
            RecordFieldTags.onBuilt(result, entries);
        } catch (Throwable ignored) {
        }
        return result;
    }
}
