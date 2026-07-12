package net.mehvahdjukaar.codecui.internal;

import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.SchemaCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * THE hand-maintained list of schema registrations for codecs that auto-inspection can't
 * (or shouldn't) handle. Deliberately separate from the inference machinery in
 * {@link SchemaResolver}: everything here goes through the exact same public API a
 * third-party mod would use ({@code SchemaCodecs.registerCompanion / registerHandler /
 * registerDispatchKeys}), so this class doubles as the reference example for external
 * curation of "weird" codecs.
 *
 * <p>Ground rules for adding entries:</p>
 * <ul>
 *   <li>First try to make inference handle the CLASS of codec (new tier-2 handler,
 *       {@code EnumerableCodec}, mixin tag). Curate here only when that's impossible
 *       (opaque lambdas, shape-changing xmaps we want a nicer surface for) or not worth it.</li>
 *   <li>Schemas describe the ON-DISK JSON shape, not the runtime type — widgets edit JSON.</li>
 *   <li>Comment WHY inference fails for each entry.</li>
 * </ul>
 *
 * <p>Bootstrapped lazily by the resolver on first resolve; safe because companions are
 * looked up fresh each resolve (never baked into cached schemas at construction time).</p>
 */
public final class CuratedSchemas {

    private static final Logger LOGGER = LogManager.getLogger("Schema Logger");

    private static volatile boolean bootstrapped = false;

    private CuratedSchemas() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        synchronized (CuratedSchemas.class) {
            if (bootstrapped) return;
            bootstrapped = true;
        }
        VanillaDispatches.bootstrap();
        try {
            register();
        } catch (Throwable t) {
            LOGGER.warn("[codec_ui] curated schema registration failed", t);
        }
        // Separate block: these reference classes that need the game bootstrap (Blocks,
        // ItemStack components). On a bare JVM they fail without taking down the safe
        // registrations above.
        try {
            registerBootstrapDependent();
        } catch (Throwable t) {
            LOGGER.info("[codec_ui] game-dependent curated schemas unavailable (no game bootstrap): {}",
                    String.valueOf(t));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register() {
        // ResourceLocation.CODEC is STRING.comapFlatMap -> inference yields plain text; an id
        // widget (with the registry-less picker) is the nicer surface.
        SchemaCodecs.registerCompanion(ResourceLocation.CODEC, new Schema.ResourceId(null));

        // UUIDUtil.CODEC is INT_STREAM.comapFlatMap -> opaque (INT_STREAM has no widget).
        // On disk it is a fixed quadruple of ints.
        SchemaCodecs.registerCompanion(UUIDUtil.CODEC, (Schema) new Schema.ListOf<>(
                new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE), 4, 4));

        // UUIDUtil.STRING_CODEC / LENIENT_CODEC decode via opaque lambdas; on disk they are
        // the canonical hyphenated string form.
        Schema uuidString = new Schema.Str(36, 36, java.util.regex.Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        SchemaCodecs.registerCompanion(UUIDUtil.STRING_CODEC, uuidString);

        // BlockPos.CODEC is INT_STREAM.comapFlatMap — INT_STREAM is opaque. On disk: [x, y, z].
        Schema intAll = new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Schema floatAll = new Schema.FloatRange(-Float.MAX_VALUE, Float.MAX_VALUE);
        SchemaCodecs.registerCompanion(BlockPos.CODEC, (Schema) new Schema.ListOf<>(intAll, 3, 3));

        // JOML vector codecs are FLOAT/INT.listOf().comapFlatMap with an arity check hidden
        // in the lambda; inference sees an unbounded list, curation restores the fixed size.
        // Note: ExtraCodecs.VECTOR2F / VECTOR3I do not exist on 1.21.1 (added in 1.21.11).
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR3F, (Schema) new Schema.ListOf<>(floatAll, 3, 3));
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR4F, (Schema) new Schema.ListOf<>(floatAll, 4, 4));

        // Color codecs: inference at best yields AnyOf(integer, text); a color picker is the
        // point of this whole exercise. On 1.21.1 only ARGB_COLOR_CODEC exists (int-primary);
        // RGB_COLOR_CODEC and the STRING_* hex-string color codecs were added in 1.21.11.
        SchemaCodecs.registerCompanion(ExtraCodecs.ARGB_COLOR_CODEC, new Schema.Color(true, false));
    }

    private static void registerBootstrapDependent() {
        // BlockState.CODEC is built during *very early* MC bootstrap (Blocks init), before
        // our codec_ui mixins are applied to Codec.fieldOf — so the internal keyCodec never
        // gets the ResourceId tag and the registry-tag dropdown fallback finds nothing.
        // Manual Record matching the on-disk shape: {"Name": id, "Properties": {prop: value}}
        // (Properties is lenientOptionalFieldOf in vanilla, so omitting it is fine).
        Schema.Str anyStr = new Schema.Str(0, Integer.MAX_VALUE, null);
        SchemaCodecs.registerCompanion(net.minecraft.world.level.block.state.BlockState.CODEC,
                new Schema.Record<>(net.minecraft.world.level.block.state.BlockState.class,
                        List.<Schema.Field<net.minecraft.world.level.block.state.BlockState, ?>>of(
                        new Schema.Field<>("Name", new Schema.ResourceId(Registries.BLOCK), false, null),
                        new Schema.Field<>("Properties", new Schema.MapOf<>(anyStr, anyStr), true, null))));

        // ItemStack.CODEC routes through data components — opaque to inference. Minimal
        // round-tripping shape for plain stacks.
        SchemaCodecs.registerCompanion(net.minecraft.world.item.ItemStack.CODEC,
                new Schema.Record<>(net.minecraft.world.item.ItemStack.class,
                        List.<Schema.Field<net.minecraft.world.item.ItemStack, ?>>of(
                        new Schema.Field<>("id", new Schema.ResourceId(Registries.ITEM), false, null),
                        new Schema.Field<>("count", new Schema.IntRange(1, 99), true, 1))));

        // DimensionType.DIRECT_CODEC wraps fields via ExtraCodecs.catchDecoderException
        // (a raw Codec.of with anonymous decoder) — no mixin point. Companion describes the
        // standard vanilla on-disk shape.
        SchemaCodecs.registerCompanion(net.minecraft.world.level.dimension.DimensionType.DIRECT_CODEC,
                new Schema.Record<>(net.minecraft.world.level.dimension.DimensionType.class,
                        List.of(
                        new Schema.Field<>("ultrawarm", new Schema.Bool(), false, null),
                        new Schema.Field<>("natural", new Schema.Bool(), false, null),
                        new Schema.Field<>("coordinate_scale", new Schema.DoubleRange(1e-5, 30_000_000.0), false, null),
                        new Schema.Field<>("has_skylight", new Schema.Bool(), false, null),
                        new Schema.Field<>("has_ceiling", new Schema.Bool(), false, null),
                        new Schema.Field<>("ambient_light", new Schema.FloatRange(0f, 1f), false, null),
                        new Schema.Field<>("fixed_time", new Schema.LongRange(0L, 24000L), true, null),
                        new Schema.Field<>("monster_spawn_block_light_limit", new Schema.IntRange(0, 15), false, null),
                        new Schema.Field<>("piglin_safe", new Schema.Bool(), false, null),
                        new Schema.Field<>("bed_works", new Schema.Bool(), false, null),
                        new Schema.Field<>("respawn_anchor_works", new Schema.Bool(), false, null),
                        new Schema.Field<>("has_raids", new Schema.Bool(), false, null),
                        new Schema.Field<>("logical_height", new Schema.IntRange(0, 4064), false, null),
                        new Schema.Field<>("min_y", new Schema.IntRange(-2032, 2031), false, null),
                        new Schema.Field<>("height", new Schema.IntRange(16, 4064), false, null),
                        new Schema.Field<>("infiniburn", anyStr, false, null),
                        new Schema.Field<>("effects", anyStr, true, "minecraft:overworld"))));
    }
}
