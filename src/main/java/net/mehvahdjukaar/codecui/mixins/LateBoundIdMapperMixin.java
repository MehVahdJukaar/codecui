package net.mehvahdjukaar.codecui.mixins;

import net.mehvahdjukaar.codecui.CodecUI;
//? >=1.21.4 {
import com.google.common.collect.BiMap;
import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.mehvahdjukaar.codecui.SchemaContext;
import net.mehvahdjukaar.codecui.internal.WrappedEnumerableCodec;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
//?}
import org.spongepowered.asm.mixin.Mixin;
//? >=1.21.4 {
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.Map;
//?}

@Mixin(/*? >=1.21.4 {*/ExtraCodecs.LateBoundIdMapper.class/*?} <1.21.4 {*//*CodecUI.class*//*?}*/)
public class LateBoundIdMapperMixin<I, V> {
    //? >=1.21.4 {
    @Shadow
    @Final
    private BiMap<I, V> idToValue;

    // We need some way to extract the possible ids for any given LateBoundIdMapper on 1.21.4+, as it may be used by mods
    @WrapMethod(method = "codec")
    public Codec<V> extractDispatchKeys(Codec<I> codec, Operation<Codec<V>> original) {
        return new WrappedEnumerableCodec<>(original.call(codec), () -> {
            DynamicOps<JsonElement> ops = SchemaContext.getRegistries().createSerializationContext(JsonOps.INSTANCE);
            Map<String, V> vMap = new HashMap<>();
            this.idToValue.forEach((id, value) -> {
                String key;
                try {
                    key = codec.encodeStart(ops, id).flatMap(element -> {
                        if (!element.isJsonPrimitive())
                            return DataResult.success(CodecUI.GSON.toJson(element));
                        return DataResult.success(element.getAsString());
                    }).getOrThrow();
                } catch (Exception e) {
                    key = id.toString();
                    CodecUI.LOGGER.error("Error encoding {} as string, falling back to {}. Error: {}", id, key, e);
                }
                vMap.put(key, value);
            });
            return vMap;
        });
    }
    //?}
}
