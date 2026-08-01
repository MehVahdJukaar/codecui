package net.mehvahdjukaar.codecui.fabric;

//? fabric {
/*import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.SchemaContext;
import net.minecraft.resources.Identifier;

public class CodecUIFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CodecUI.init(FabricLoader.getInstance().isDevelopmentEnvironment(), false);
        CommonLifecycleEvents.TAGS_LOADED.register(Identifier.fromNamespaceAndPath(CodecUI.MOD_ID, "extract_context"), (registryAccess, client) -> SchemaContext.update(registryAccess));
    }
}
*///?}