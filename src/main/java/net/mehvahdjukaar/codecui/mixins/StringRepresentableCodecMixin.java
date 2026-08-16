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

// Tags every StringRepresentableCodec (fromEnum / fromValues output) with a Schema.Enum. The values
// array is only reachable in the constructor; the codec keeps it inside lambdas.
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
