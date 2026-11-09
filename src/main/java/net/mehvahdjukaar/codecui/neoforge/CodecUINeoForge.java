package net.mehvahdjukaar.codecui.neoforge;

//? neoforge {
import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.SchemaContext;
import net.minecraft.core.RegistryAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
//? <26.1 && >=1.21.2
//import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
//? >=26.1 || <1.21.2
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@Mod("codecui")
public class CodecUINeoForge {

    public CodecUINeoForge(IEventBus modBus) {
        CodecUI.init(!FMLEnvironment./*? >=1.21.9 {*/isProduction()/*?} <1.21.9 {*//*production*//*?}*/, true);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SchemaContext.reset());
        if (FMLEnvironment./*? >=1.21.9 {*/getDist()/*?} <1.21.9 {*//*dist*//*?}*/ == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> SchemaContext.reset());
        }
    }

    //? >=26.1 || <1.21.2 {
    @SubscribeEvent
    public void onTagsLoaded(TagsUpdatedEvent event) {
        RegistryAccess access =
        //? >=26.1 {
                event.getRegistries();
        //?} <1.21.2 {
                /*event.getRegistryAccess();
        *///?}
        SchemaContext.update(access);
    }
    //?}

    //? <26.1 && >=1.21.2 {
    /*@SubscribeEvent
    public void onAddReloadListener(AddServerReloadListenersEvent event) {
        RegistryAccess access = event.getRegistryAccess();
        SchemaContext.update(access);
    }
    *///?}
}
//?}