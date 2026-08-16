package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.codecui.internal.RecordFieldTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

// Carries field tags through apN: the result RCB gets the inputs' tags concatenated.
@Mixin(RecordCodecBuilder.Instance.class)
public abstract class RecordCodecBuilderInstanceMixin {

    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "ap2", at = @At("RETURN"))
    private App<?, ?> codecui$tagAp2(App<?, ?> result,
                                       @Local(argsOnly = true, ordinal = 0) App func,
                                       @Local(argsOnly = true, ordinal = 1) App a,
                                       @Local(argsOnly = true, ordinal = 2) App b) {
        codecui$concatFieldTags(result, func, a, b);
        return result;
    }

    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "ap3", at = @At("RETURN"))
    private App<?, ?> codecui$tagAp3(App<?, ?> result,
                                       @Local(argsOnly = true, ordinal = 0) App func,
                                       @Local(argsOnly = true, ordinal = 1) App t1,
                                       @Local(argsOnly = true, ordinal = 2) App t2,
                                       @Local(argsOnly = true, ordinal = 3) App t3) {
        codecui$concatFieldTags(result, func, t1, t2, t3);
        return result;
    }

    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "ap4", at = @At("RETURN"))
    private App<?, ?> codecui$tagAp4(App<?, ?> result,
                                       @Local(argsOnly = true, ordinal = 0) App func,
                                       @Local(argsOnly = true, ordinal = 1) App t1,
                                       @Local(argsOnly = true, ordinal = 2) App t2,
                                       @Local(argsOnly = true, ordinal = 3) App t3,
                                       @Local(argsOnly = true, ordinal = 4) App t4) {
        codecui$concatFieldTags(result, func, t1, t2, t3, t4);
        return result;
    }

    // ap5..ap16 defaults start with this.map(curryN, func); without this, records past 4 fields
    // lose the fields captured before the map (see ARCHITECTURE.md, arity trap).
    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "map", at = @At("RETURN"))
    private App<?, ?> codecui$tagMap(App<?, ?> result, @Local(argsOnly = true) App ts) {
        try {
            if (result instanceof RecordCodecBuilder<?, ?> out && ts instanceof RecordCodecBuilder<?, ?> in) {
                RecordFieldTags.copy(in, out);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    // 1-field records go P1.apply -> Applicative.ap -> lift1(func).apply(t1), skipping ap2/3/4.
    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyReturnValue(method = "lift1", at = @At("RETURN"))
    private Function codecui$tagLift1(Function original, @Local(argsOnly = true) App func) {
        return arg -> {
            Object result = original.apply(arg);
            try {
                if (result instanceof RecordCodecBuilder<?, ?> out) {
                    RecordFieldTags.concat(out,
                            func instanceof RecordCodecBuilder<?, ?> f ? f : null,
                            arg instanceof RecordCodecBuilder<?, ?> a ? a : null);
                }
            } catch (Throwable ignored) {
            }
            return result;
        };
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private static void codecui$concatFieldTags(App<?, ?> result, App... inputs) {
        try {
            if (!(result instanceof RecordCodecBuilder<?, ?> resultBuilder)) return;
            RecordCodecBuilder<?, ?>[] in = new RecordCodecBuilder[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] instanceof RecordCodecBuilder<?, ?> rcb) {
                    in[i] = rcb;
                }
            }
            RecordFieldTags.concat(resultBuilder, in);
        } catch (Throwable ignored) {
        }
    }
}
