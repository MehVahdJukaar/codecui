package net.mehvahdjukaar.codecui.neoforge;

//? neoforge {
import net.mehvahdjukaar.codecui.CodecUI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("codecui")
public class CodecUINeoForge {

    public CodecUINeoForge(IEventBus modBus) {
        CodecUI.init(!FMLEnvironment./*? >=1.21.9 {*/isProduction()/*?} <1.21.9 {*//*production*//*?}*/, true);
    }
}
//?}