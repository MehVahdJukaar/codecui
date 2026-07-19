package net.mehvahdjukaar.codecui.mixins;

//? >=1.21.11 {
import com.google.common.collect.BiMap;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.gen.Accessor;
//?}
//? <1.21.11
//import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import org.spongepowered.asm.mixin.Mixin;

// idToValue is LateBoundIdMapper's only handle on the id<->codec map, and it's needed only on
// >=1.21.11. LateBoundIdMapper doesn't exist on older versions, so there the mixin degrades to an
// empty accessor on a client class that always does (it stays listed in the client mixin config).
@Mixin(
        //? >=1.21.11 {
        ExtraCodecs.LateBoundIdMapper.class
        //?}
        //? <1.21.11
        /*SpriteSources.class*/
)
public interface LateBoundIdMapperAccessor {

    //? >=1.21.11 {
    @Accessor("idToValue")
    BiMap codecui$getIdToValue();
    //?}
}
