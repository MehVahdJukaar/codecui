package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.internal.FieldOfTags;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Codec.class)
public interface CodecXmapMixin {

    @ModifyReturnValue(method = "xmap", at = @At("RETURN"))
    private <S> Codec<S> codecui$tagXmap(Codec<S> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "flatXmap", at = @At("RETURN"))
    private <S> Codec<S> codecui$tagFlatXmap(Codec<S> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "comapFlatMap", at = @At("RETURN"))
    private <S> Codec<S> codecui$tagComapFlatMap(Codec<S> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "flatComapMap", at = @At("RETURN"))
    private <S> Codec<S> codecui$tagFlatComapMap(Codec<S> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }
    @ModifyReturnValue(method = "validate", at = @At("RETURN"))
    private Codec<?> codecui$tagValidate(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "mapResult", at = @At("RETURN"))
    private Codec<?> codecui$tagMapResult(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }
    @ModifyReturnValue(method = "orElse(Ljava/lang/Object;)Lcom/mojang/serialization/Codec;",
            at = @At("RETURN"))
    private Codec<?> codecui$tagOrElseValue(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "orElseGet(Ljava/util/function/Supplier;)Lcom/mojang/serialization/Codec;",
            at = @At("RETURN"))
    private Codec<?> codecui$tagOrElseGetSupplier(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "promotePartial(Ljava/util/function/Consumer;)Lcom/mojang/serialization/Codec;",
            at = @At("RETURN"))
    private Codec<?> codecui$tagPromotePartial(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "withLifecycle(Lcom/mojang/serialization/Lifecycle;)Lcom/mojang/serialization/Codec;",
            at = @At("RETURN"))
    private Codec<?> codecui$tagWithLifecycle(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "stable()Lcom/mojang/serialization/Codec;", at = @At("RETURN"))
    private Codec<?> codecui$tagStable(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "deprecated(I)Lcom/mojang/serialization/Codec;", at = @At("RETURN"))
    private Codec<?> codecui$tagDeprecated(Codec<?> wrapped) {
        codecui$inheritInner(wrapped);
        return wrapped;
    }
    @ModifyReturnValue(method = "fieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;",
            at = @At("RETURN"))
    private MapCodec<?> codecui$tagFieldOf(MapCodec<?> wrapped, @Local(argsOnly = true) String name) {
        codecui$tagSingleField(wrapped, name, false, null);
        return wrapped;
    }

    @ModifyReturnValue(
            method = "optionalFieldOf(Ljava/lang/String;Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;",
            at = @At("RETURN"))
    private MapCodec<?> codecui$tagOptionalFieldOfWithDefault(
            MapCodec<?> wrapped, @Local(argsOnly = true) String name, @Local(argsOnly = true) Object defaultValue) {
        codecui$tagSingleField(wrapped, name, true, defaultValue);
        return wrapped;
    }

    @ModifyReturnValue(method = "optionalFieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;",
            at = @At("RETURN"))
    private MapCodec<?> codecui$tagOptionalFieldOf(MapCodec<?> wrapped, @Local(argsOnly = true) String name) {
        codecui$tagSingleField(wrapped, name, true, null);
        return wrapped;
    }

    @ModifyReturnValue(method = "lenientOptionalFieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;",
            at = @At("RETURN"))
    private MapCodec<?> codecui$tagLenientOptionalFieldOf(MapCodec<?> wrapped, @Local(argsOnly = true) String name) {
        codecui$tagSingleField(wrapped, name, true, null);
        return wrapped;
    }

    @ModifyReturnValue(
            method = "lenientOptionalFieldOf(Ljava/lang/String;Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;",
            at = @At("RETURN"))
    private MapCodec<?> codecui$tagLenientOptionalFieldOfWithDefault(
            MapCodec<?> wrapped, @Local(argsOnly = true) String name, @Local(argsOnly = true) Object defaultValue) {
        codecui$tagSingleField(wrapped, name, true, defaultValue);
        return wrapped;
    }

    @ModifyReturnValue(method = "intRange", at = @At("RETURN"))
    private static Codec<Integer> codecui$tagIntRange(Codec<Integer> wrapped,
            @Local(argsOnly = true, ordinal = 0) int min, @Local(argsOnly = true, ordinal = 1) int max) {
        SchemaTags.tag(wrapped, new Schema.IntRange(min, max));
        return wrapped;
    }

    @ModifyReturnValue(method = "floatRange", at = @At("RETURN"))
    private static Codec<Float> codecui$tagFloatRange(Codec<Float> wrapped,
            @Local(argsOnly = true, ordinal = 0) float min, @Local(argsOnly = true, ordinal = 1) float max) {
        SchemaTags.tag(wrapped, new Schema.FloatRange(min, max));
        return wrapped;
    }

    @ModifyReturnValue(method = "doubleRange", at = @At("RETURN"))
    private static Codec<Double> codecui$tagDoubleRange(Codec<Double> wrapped,
            @Local(argsOnly = true, ordinal = 0) double min, @Local(argsOnly = true, ordinal = 1) double max) {
        SchemaTags.tag(wrapped, new Schema.DoubleRange(min, max));
        return wrapped;
    }

    @ModifyReturnValue(method = "string(II)Lcom/mojang/serialization/Codec;", at = @At("RETURN"))
    private static Codec<String> codecui$tagBoundedString(Codec<String> wrapped,
            @Local(argsOnly = true, ordinal = 0) int minSize, @Local(argsOnly = true, ordinal = 1) int maxSize) {
        SchemaTags.tag(wrapped, new Schema.Str(minSize, maxSize, null));
        return wrapped;
    }

    @Unique
    private void codecui$tagSingleField(MapCodec<?> wrapped, String name, boolean optional, Object defaultValue) {
        if (wrapped == null) return;
        try {
            // Store a LAZY tag: just (name, innerCodec). The resolver computes the inner
            // schema fresh at lookup time, so a companion registered AFTER this fieldOf
            // mixin fires still wins. Required because MC bootstrap calls fieldOf during
            // early init (e.g. BlockState.CODEC.fieldOf in RandomBlockStateMatchTest).
            Codec<?> inner = (Codec<?>) (Object) this;
            FieldOfTags.put(wrapped, name, inner, optional, defaultValue);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private void codecui$inheritInner(Codec<?> wrapped) {
        if (wrapped == null || wrapped == (Object) this) return;
        try {
            // LAZY: just record (wrapped -> inner); the resolver resolves the inner fresh at
            // lookup time (eager resolution here used to capture stale schemas during bootstrap).
            net.mehvahdjukaar.codecui.internal.XmapTags.putCodec(
                    wrapped, (Codec<?>) (Object) this);
        } catch (Throwable ignored) {
        }
    }
}
