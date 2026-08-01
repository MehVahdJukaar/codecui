package net.mehvahdjukaar.codecui.mixins;

import net.minecraft.client.renderer.texture.atlas.SpriteSources;
//? <1.21.5
//import com.google.common.collect.BiMap;
import org.spongepowered.asm.mixin.Mixin;
//? <1.21.5
//import org.spongepowered.asm.mixin.gen.Accessor;

// The atlas dispatch's known keys live only in a private static field of SpriteSources (not a
// registry). Widen it here instead of via the access widener: loom's strict AW validation rejects
// the entry on the versions where the field's shape differs, and a single AW source can't be
// Stonecutter-toggled. The field SpriteSourceType TYPE was replaced with a LateBoundIdMapper in 1.21.5,
// so this accessor is only used before then.
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {

    //? <1.21.5 {
    /*@Accessor("TYPES")
    static BiMap codecui$getTypes() {
        throw new AssertionError();
    }
    *///?}
}
