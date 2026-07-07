package net.mehvahdjukaar.codecui.platform;

import net.mehvahdjukaar.codecui.CodecUI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("codecui")
public class CodecUINeoForge {

    public CodecUINeoForge(IEventBus modBus) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            CodecUI.init(!FMLEnvironment.isProduction(), true);
        } else {
            CodecUI.LOGGER.warn("CodecUI is a client-side library and does nothing on a dedicated server.");
        }
    }
}
