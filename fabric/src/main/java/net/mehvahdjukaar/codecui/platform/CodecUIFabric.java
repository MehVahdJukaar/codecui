package net.mehvahdjukaar.codecui.platform;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.mehvahdjukaar.codecui.CodecUI;

public class CodecUIFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CodecUI.init(FabricLoader.getInstance().isDevelopmentEnvironment(), false);
    }
}
