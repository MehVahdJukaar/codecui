package net.mehvahdjukaar.codecui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mehvahdjukaar.codecui.internal.MixinDetection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CodecUI {
    // Used for encoding JsonObjects, JsonNulls, and JsonArrays into Strings inside LateBoundIdMapperMixin
    public static final Gson GSON = new GsonBuilder().create();
    public static final String MOD_ID = "codecui";
    public static final Logger LOGGER = LogManager.getLogger("CodecUI");

    public static void init(boolean isDev, boolean neoForge) {
        MixinDetection.run(neoForge);
    }
}
