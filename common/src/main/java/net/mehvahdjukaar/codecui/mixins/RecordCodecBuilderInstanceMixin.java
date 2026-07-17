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

// Propagates accumulated field tags through the applicative apN combinators.
// Each apN takes an applicative function plus N field-carrying RCBs and produces a
// new RCB whose tag list is the concatenation of the inputs' tag lists. The applicative
// function position (func) typically carries no field tags itself (it's built from
// point); concat just skips empty contributions.
@Mixin(RecordCodecBuilder.Instance.class)
public abstract class RecordCodecBuilderInstanceMixin {

    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "ap2", at = @At("RETURN"))
    private App<?, ?> codecui$tagAp2(App<?, ?> result,
                                       @Local(argsOnly = true, ordinal = 0) App func,
                                       @Local(argsOnly = true, ordinal = 1) App a,
                                       @Local(argsOnly = true, ordinal = 2) App b) {
        codecui$concat(result, func, a, b);
        return result;
    }

    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "ap3", at = @At("RETURN"))
    private App<?, ?> codecui$tagAp3(App<?, ?> result,
                                       @Local(argsOnly = true, ordinal = 0) App func,
                                       @Local(argsOnly = true, ordinal = 1) App t1,
                                       @Local(argsOnly = true, ordinal = 2) App t2,
                                       @Local(argsOnly = true, ordinal = 3) App t3) {
        codecui$concat(result, func, t1, t2, t3);
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
        codecui$concat(result, func, t1, t2, t3, t4);
        return result;
    }

    // The Applicative interface defaults for ap5..ap16 all begin with
    // this.map(curryN, func) before delegating to ap2/ap3/ap4. Instance overrides
    // map, producing a NEW builder - without this hook the tags accumulated on
    // func are dropped there, and any record with more than 4 fields loses all
    // fields captured before the map (e.g. a 9-field record only showed fields 5–9).
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

    // Single-field records go through Products.P1.apply -> Applicative.ap ->
    // lift1(func).apply(t1), never touching ap2/3/4. Wrap the function returned by
    // lift1 so the App it produces inherits the tags of both the function position
    // and the argument (in that order).
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
                // Best-effort.
            }
            return result;
        };
    }

    @Unique
    @SuppressWarnings("rawtypes")
    private static void codecui$concat(App<?, ?> result, App... inputs) {
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
