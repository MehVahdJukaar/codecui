package net.mehvahdjukaar.codecui;

import net.mehvahdjukaar.codecui.internal.MixinDetection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public final class CodecUI {

    public static final String MOD_ID = "codecui";
    public static final Logger LOGGER = LogManager.getLogger("CodecUI");

    public static void init(boolean isDev, boolean neoForge) {
        MixinDetection.run(neoForge);
    }
}
