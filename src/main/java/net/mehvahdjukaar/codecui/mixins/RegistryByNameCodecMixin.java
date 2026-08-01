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

// Tags the Codec returned by Registry#byNameCodec() with a
// Schema.ResourceId carrying this registry's key. The resolver then routes the codec
// to a ResourceIdWidget (dropdown / picker) instead of the inherited String schema.
//
// Because byNameCodec() is a default method on Registry, this mixin fires
// for every concrete registry implementation - vanilla, mod registries, custom ones.
@Mixin(Registry.class)
public interface RegistryByNameCodecMixin<T> {

    @Shadow
    Set<Map.Entry<ResourceKey<T>, T>> entrySet();

    @ModifyReturnValue(method = "byNameCodec", at = @At("RETURN"))
    private Codec<T> codecui$tagByNameCodec(Codec<T> wrapped) {
        codecui$tagWithKey(wrapped);
        return wrapped;
    }

    // Holder<T>-typed twin (MobEffect.CODEC, etc.) - same id-string on-disk form.
    @SuppressWarnings("rawtypes")
    @ModifyReturnValue(method = "holderByNameCodec", at = @At("RETURN"))
    private Codec codecui$tagHolderByNameCodec(Codec wrapped) {
        codecui$tagWithKey(wrapped);
        return wrapped;
    }

    @ModifyReturnValue(method = "referenceHolderWithLifecycle", at = @At("RETURN"))
    private Codec<T> codecui$tagReferenceHolderWithLifecycle(Codec<T> wrapped) {
        codecui$tagWithKey(wrapped);
        return new WrappedEnumerableCodec<>(wrapped, () -> {
            Map<String, T> idToValues = new HashMap<>();
            this.entrySet().forEach(entry -> idToValues.put(McCompat.keyId(entry.getKey()).toString(), entry.getValue()));
            return idToValues;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private void codecui$tagWithKey(Codec<?> wrapped) {
        try {
            ResourceKey<? extends Registry<T>> key = ((Registry<T>) this).key();
            Schema.ResourceId schema = new Schema.ResourceId(key);
            SchemaTags.tag((Codec) wrapped, (Schema) schema);
        } catch (Throwable ignored) {
        }
    }
}
