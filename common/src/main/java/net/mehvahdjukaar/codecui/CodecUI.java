package net.mehvahdjukaar.codecui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.internal.XmapTags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;


public final class CodecUI {

    public static final String MOD_ID = "codecui";
    public static final Logger LOGGER = LogManager.getLogger("CodecUI");

    public static void init(boolean isDev, boolean neoForge) {
        verifyCodecMixins();
    }

    // CodecXmapMixin / MapCodecXmapMixin live in a required:false config because their targets
    // (the Codec / MapCodec interfaces) are foundational DFU types that can be classloaded before
    // Mixin prepares its configs. When that happens Mixin silently skips them ("target was loaded
    // too early") instead of crashing, and we fall back to reflective schema resolution - which
    // recovers structure but loses numeric/string bounds and optional/default field info.
    // Detect the skip by exercising an intercepted call and checking the side-channel table.
    private static void verifyCodecMixins() {
        boolean codecApplied = false;
        boolean mapCodecApplied = false;
        try {
            Codec<Integer> codecProbe = Codec.INT.xmap(Function.identity(), Function.identity());
            codecApplied = XmapTags.getCodec(codecProbe) != null;

            MapCodec<Integer> mapProbe =
                    Codec.INT.fieldOf("codecui_probe").xmap(Function.identity(), Function.identity());
            mapCodecApplied = XmapTags.getMap(mapProbe) != null;
        } catch (Throwable t) {
            LOGGER.error("CodecUI mixin self-test threw unexpectedly", t);
        }

        if (codecApplied && mapCodecApplied) return;

        String missing;
        if (!codecApplied && !mapCodecApplied) missing = "Codec and MapCodec";
        else if (!codecApplied) missing = "Codec";
        else missing = "MapCodec";

        LOGGER.error("""
                ============================================================
                 CodecUI: codec transform mixins DID NOT APPLY ({})
                ------------------------------------------------------------
                 The {} interface(s) were classloaded before Mixin could
                 weave codecui-common-codec.mixins.json ("target loaded too
                 early"). Codec construction is no longer being intercepted,
                 so schemas fall back to reflective resolution only.
                 Impact: nested structure is still recovered, but numeric /
                 string bounds (intRange/floatRange/string) and fieldOf
                 optional/default info are LOST for affected editors.
                 Likely cause: another mod (or a preLaunch entrypoint)
                 touches DFU Codec during early bootstrap.
                ============================================================""",
                missing, missing);
    }
}
