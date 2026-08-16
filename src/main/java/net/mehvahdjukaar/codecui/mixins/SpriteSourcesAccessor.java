package net.mehvahdjukaar.codecui.mixins;

import net.minecraft.client.renderer.texture.atlas.SpriteSources;
//? <1.21.5
//import com.google.common.collect.BiMap;
import org.spongepowered.asm.mixin.Mixin;
//? <1.21.5
//import org.spongepowered.asm.mixin.gen.Accessor;

// Accessor mixin, not AW: the field only exists before 1.21.5 (LateBoundIdMapper after) and loom's
// AW validation fails on entries whose target is missing.
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {

    //? <1.21.5 {
    /*@Accessor("TYPES")
    static BiMap codecui$getTypes() {
        throw new AssertionError();
    }
    *///?}
}
