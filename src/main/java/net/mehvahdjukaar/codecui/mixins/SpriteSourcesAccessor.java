package net.mehvahdjukaar.codecui.mixins;

import net.minecraft.client.renderer.texture.atlas.SpriteSources;
//? >=1.21.11
import net.minecraft.util.ExtraCodecs;
//? <1.21.11
//import com.google.common.collect.BiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The atlas dispatch's known keys live only in a private static field of SpriteSources (not a
// registry). Widen it here instead of via the access widener: loom's strict AW validation rejects
// the entry on the versions where the field's shape differs, and a single AW source can't be
// Stonecutter-toggled. The field changed shape in 1.21.11 (SpriteSourceType TYPES -> the MapCodec
// id mapper), so each version exposes only its own accessor.
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {

    //? >=1.21.11 {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper codecui$getIdMapper() {
        throw new AssertionError();
    }
    //?}

    //? <1.21.11 {
    /*@Accessor("TYPES")
    static BiMap codecui$getTypes() {
        throw new AssertionError();
    }
    *///?}
}
