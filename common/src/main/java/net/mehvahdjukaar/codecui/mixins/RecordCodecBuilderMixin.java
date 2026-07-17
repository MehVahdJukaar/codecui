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

// Captures per-field information on RecordCodecBuilder during construction so the
// MapCodec returned by RecordCodecBuilder.build(...) can carry a real
// Schema.Record. Without this mixin, the anonymous MapCodec produced by
// build is fully opaque to the SchemaResolver.
//
// Coverage limitations:
//
//   - Only the of(getter, name, codec) and of(getter, MapCodec) entry
//       points are tagged. RCBs created via dependent(...) or point/stable/deprecated
//       carry empty tag lists; their fields will fall through to Opaque.
//   - The Instance.lift1 / Instance.map fast-paths are not tagged. In practice
//       all real vanilla and mod record codecs go through ap2/ap3/ap4 which we cover.
//   - Field optionality is best-effort: only the of(getter, MapCodec) form carries
//       it (resolver introspects the MapCodec via the existing tier-2 OptionalFieldCodec path).
//       The 3-arg of(getter, name, codec) marks fields as required.
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

    // On build, transfer the accumulated field tags to the OUTPUT MapCodec via
    // RecordFieldTags#onBuilt. Resolver rebuilds the schema FRESH at lookup time so
    // companions registered later still win. We do NOT eagerly populate SchemaTags.
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
