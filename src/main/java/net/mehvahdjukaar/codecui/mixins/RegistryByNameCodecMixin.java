package net.mehvahdjukaar.codecui.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.internal.McCompat;
import net.mehvahdjukaar.codecui.internal.SchemaTags;
import net.mehvahdjukaar.codecui.internal.WrappedEnumerableCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Tags Registry#byNameCodec() output with Schema.ResourceId(registry key). Default method, so it
// covers every registry implementation.
@Mixin(Registry.class)
public interface RegistryByNameCodecMixin<T> {

    @Shadow
    Set<Map.Entry<ResourceKey<T>, T>> entrySet();

    @ModifyReturnValue(method = "byNameCodec", at = @At("RETURN"))
    private Codec<T> codecui$tagByNameCodec(Codec<T> wrapped) {
        codecui$tagResourceId(wrapped);
        return wrapped;
    }

    // Holder<T>-typed twin (MobEffect.CODEC, etc.) - same id-string on-disk form.
    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "holderByNameCodec", at = @At("RETURN"))
    private Codec codecui$tagHolderByNameCodec(Codec wrapped) {
        codecui$tagResourceId(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "referenceHolderWithLifecycle", at = @At("RETURN"))
    private Codec<T> codecui$tagReferenceHolderWithLifecycle(Codec<T> wrapped) {
        codecui$tagResourceId(wrapped);
        return new WrappedEnumerableCodec<>(wrapped, () -> {
            Map<String, T> valuesById = new HashMap<>();
            this.entrySet().forEach(entry -> valuesById.put(McCompat.keyId(entry.getKey()).toString(), entry.getValue()));
            return valuesById;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private void codecui$tagResourceId(Codec<?> wrapped) {
        try {
            ResourceKey<? extends Registry<T>> key = ((Registry<T>) this).key();
            Schema.ResourceId schema = new Schema.ResourceId(key);
            SchemaTags.tag((Codec) wrapped, (Schema) schema);
        } catch (Throwable ignored) {
        }
    }
}
