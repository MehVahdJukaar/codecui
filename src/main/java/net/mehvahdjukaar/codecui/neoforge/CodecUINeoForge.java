package net.mehvahdjukaar.codecui.neoforge;

//? neoforge {
import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.SchemaContext;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
//? <26.1 && >=1.21.2
//import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
//? >=26.1 || <1.21.2
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@Mod("codecui")
public class CodecUINeoForge {

    public CodecUINeoForge(IEventBus modBus) {
        CodecUI.init(!FMLEnvironment./*? >=1.21.9 {*/isProduction()/*?} <1.21.9 {*//*production*//*?}*/, true);
        NeoForge.EVENT_BUS.register(this);
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