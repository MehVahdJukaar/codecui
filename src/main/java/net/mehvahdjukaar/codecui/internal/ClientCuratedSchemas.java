package net.mehvahdjukaar.codecui.internal;

import com.google.common.collect.BiMap;
import net.mehvahdjukaar.codecui.SchemaCodecs;
import net.mehvahdjukaar.codecui.mixins.SpriteSourcesAccessor;
//? >=1.21.11 {
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.mixins.LateBoundIdMapperAccessor;
//?}
//? <1.21.11
//import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;

import java.util.ArrayList;

// Curated schemas for client-only vanilla codecs (resource-pack formats). Split out from
// CuratedSchemas because these reference net.minecraft.client, which won't classload on a
// dedicated server - bootstrap() calls this behind its own guard so the server just skips it.
final class ClientCuratedSchemas {

    // The atlas file (SpriteSources.FILE_CODEC) dispatches on the sprite-source type, whose known
    // keys live only in a private static field (not a registry) behind a codec that can't enumerate
    // them - so hand codecui the known types. Reading the backing map (rather than the few public
    // fields) also picks up modded sprite sources.
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void register() {
        //? >=1.21.11 {
        // 1.21.11 dropped SpriteSourceType: the dispatch keys are now the MapCodecs themselves, held
        // in a LateBoundIdMapper whose private id<->codec BiMap is the only enumeration handle.
        var mapper = SpriteSourcesAccessor.codecui$getIdMapper();
        BiMap idMap = ((LateBoundIdMapperAccessor) (Object) mapper).codecui$getIdToValue();
        SchemaCodecs.registerDispatchKeys((Class) MapCodec.class,
                () -> new ArrayList(idMap.values()),
                mc -> idMap.inverse().get(mc).toString());
        //?} <1.21.11 {
        /*BiMap types = SpriteSourcesAccessor.codecui$getTypes();
        SchemaCodecs.registerDispatchKeys(SpriteSourceType.class,
                () -> new ArrayList<>(types.values()),
                type -> types.inverse().get(type).toString());
        *///?}
    }
}
