package net.mehvahdjukaar.codecui.platform;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.mehvahdjukaar.codecui.CodecUI;

public class CodecUIFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CodecUI.init(FabricLoader.getInstance().isDevelopmentEnvironment(), false);
    }
}
