package net.mehvahdjukaar.codecui.internal;

//? <1.21.5 {
/*import com.google.common.collect.BiMap;
import net.mehvahdjukaar.codecui.SchemaCodecs;
import net.mehvahdjukaar.codecui.mixins.SpriteSourcesAccessor;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;

import java.util.ArrayList;
*///?}

// Curated schemas for client-only codecs (resource-pack formats). Kept out of CuratedSchemas because
// net.minecraft.client won't classload on a dedicated server; bootstrap() calls this behind a guard.
final class ClientCuratedSchemas {

    // Atlas files dispatch on sprite-source type, whose keys live only in a private static map (not a
    // registry), so the codec can't enumerate them. Reading the map also picks up modded sources.
    //? <1.21.5
    //@SuppressWarnings({"unchecked", "rawtypes"})
    static void register() {
        // 1.21.5+ dropped SpriteSourceType; the keys are the MapCodecs held in a private LateBoundIdMapper.
        //? <1.21.5 {
        /*BiMap types = SpriteSourcesAccessor.codecui$getTypes();
        SchemaCodecs.registerDispatchKeys(SpriteSourceType.class,
                () -> new ArrayList<>(types.values()),
                type -> types.inverse().get(type).toString());
        *///?}
    }
}
