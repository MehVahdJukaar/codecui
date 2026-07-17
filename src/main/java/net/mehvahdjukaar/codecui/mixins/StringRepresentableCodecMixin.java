package net.mehvahdjukaar.codecui.mixins;

import com.mojang.serialization.Codec;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import net.minecraft.util.StringRepresentable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

// Tags every StringRepresentable.StringRepresentableCodec (which includes
// StringRepresentable.EnumCodec, i.e. the output of fromEnum /
// fromEnumWithMapping / fromValues) with a Schema.Enum carrying its
// value list. The values array is only reachable here - the codec itself stores it inside
// composed lambdas - so this constructor hook is what turns every vanilla and modded
// string-enum codec into a dropdown instead of a free-text string field.
@Mixin(StringRepresentable.StringRepresentableCodec.class)
public abstract class StringRepresentableCodecMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("TAIL"))
    private void codecui$tagEnum(StringRepresentable[] values, Function<String, ?> nameLookup,
                                  ToIntFunction<?> indexLookup, CallbackInfo ci) {
        try {
            Codec self = (Codec) this;
            SchemaTags.tag(self, new Schema.Enum<>(List.of((Object[]) values),
                    v -> ((StringRepresentable) v).getSerializedName()));
        } catch (Throwable ignored) {
        }
    }
}
