package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.codecui.CodecUI;

import java.util.function.Function;

// Runtime detection of whether the codec-construction mixins actually wove. They fail on
// NeoForge always (DFU isn't on the transforming classloader) and on Fabric occasionally
// (a DFU type classloaded before Mixin prepares its configs is silently skipped,
// required:false). Detected by exercising an intercepted call and checking whether the
// side-channel tag appeared; exposed through SchemaCodecs.inferenceMode().
public final class MixinDetection {

    private static volatile boolean detected = false;
    private static volatile boolean codecActive = false;
    private static volatile boolean mapCodecActive = false;
    private static volatile boolean recordBuilderActive = false;

    // Optimistic-false before #run completes; callers use it after init.
    public static boolean constructionInterceptionActive() {
        return codecActive && mapCodecActive && recordBuilderActive;
    }

    public static boolean codecMixinActive() { return codecActive; }
    public static boolean mapCodecMixinActive() { return mapCodecActive; }
    public static boolean recordBuilderMixinActive() { return recordBuilderActive; }

    public static synchronized void run(boolean neoForge) {
        if (detected) return;
        detected = true;

        codecActive = probeCodecXmap();
        mapCodecActive = probeMapCodecXmap();
        recordBuilderActive = probeRecordBuilder();

        if (constructionInterceptionActive()) {
            CodecUI.LOGGER.debug("Codec construction mixins active - schema metadata captured losslessly.");
            return;
        }

        String missing = describeMissing();
        if (neoForge) {
            // Expected on NeoForge: DFU is off the transforming classloader by design.
            CodecUI.LOGGER.info(
                    "Codec construction mixins inactive ({}) - expected on NeoForge; using reflective "
                            + "schema inference (numeric ranges recovered, optional-field defaults not).",
                    missing);
        } else {
            // Anomaly on Fabric: something classloaded a DFU type before Mixin could weave.
            CodecUI.LOGGER.error("""
                    ============================================================
                     CodecUI: codec construction mixins DID NOT APPLY ({})
                    ------------------------------------------------------------
                     A DFU type was classloaded before Mixin could weave it
                     ("target loaded too early"). Codec construction is no
                     longer intercepted, so schemas fall back to reflective
                     resolution: nested structure, field names and numeric
                     bounds are still recovered, but optional-field default
                     values are LOST.
                     Likely cause: another mod (or a preLaunch entrypoint)
                     touches DFU during early bootstrap.
                     Query the state via SchemaCodecs.inferenceMode().
                    ============================================================""",
                    missing);
        }
    }

    private static String describeMissing() {
        StringBuilder sb = new StringBuilder();
        if (!codecActive) sb.append("Codec");
        if (!mapCodecActive) sb.append(sb.isEmpty() ? "" : ", ").append("MapCodec");
        if (!recordBuilderActive) sb.append(sb.isEmpty() ? "" : ", ").append("RecordCodecBuilder");
        return sb.toString();
    }

    private static boolean probeCodecXmap() {
        try {
            Codec<Integer> probe = Codec.INT.xmap(Function.identity(), Function.identity());
            return XmapTags.getCodec(probe) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean probeMapCodecXmap() {
        try {
            MapCodec<Integer> probe =
                    Codec.INT.fieldOf("codecui_probe").xmap(Function.identity(), Function.identity());
            return XmapTags.getMap(probe) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean probeRecordBuilder() {
        try {
            MapCodec<Integer> probe = RecordCodecBuilder.mapCodec(inst -> inst.group(
                    Codec.INT.fieldOf("codecui_probe").forGetter((Integer v) -> v)
            ).apply(inst, v -> v));
            return RecordFieldTags.getBuilt(probe) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
