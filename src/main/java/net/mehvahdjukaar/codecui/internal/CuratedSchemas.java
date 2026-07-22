package net.mehvahdjukaar.codecui.internal;

import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.SchemaCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
//? >=26.1
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

// THE hand-maintained list of schema registrations for codecs that auto-inspection can't
// (or shouldn't) handle. Everything here goes through the same public API a third-party mod
// would use, so this class doubles as the reference example for curating "weird" codecs.
//
// Ground rules: first try to make inference handle the CLASS of codec (tier-2 handler,
// EnumerableCodec, mixin tag); curate here only when that's impossible or not worth it.
// Schemas describe the ON-DISK JSON shape, not the runtime type. Comment WHY inference
// fails for each entry.
public final class CuratedSchemas {

    private static volatile boolean bootstrapped = false;

    public static void bootstrap() {
        if (bootstrapped) return;
        synchronized (CuratedSchemas.class) {
            if (bootstrapped) return;
            bootstrapped = true;
        }
        try {
            register();
        } catch (Throwable t) {
            CodecUI.LOGGER.warn("curated schema registration failed", t);
        }
        // These need the game bootstrap (Blocks, ItemStack components); on a bare JVM they
        // fail without taking down the registrations above.
        try {
            registerBootstrapDependent();
        } catch (Throwable t) {
            CodecUI.LOGGER.info("game-dependent curated schemas unavailable (no game bootstrap): {}",
                    String.valueOf(t));
        }
        // Client-only formats reference net.minecraft.client, absent on a dedicated server - guard
        // so the NoClassDefFoundError there just skips them instead of failing the whole bootstrap.
        try {
            ClientCuratedSchemas.register();
        } catch (Throwable t) {
            CodecUI.LOGGER.info("client curated schemas unavailable (dedicated server?): {}",
                    String.valueOf(t));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register() {
        registerVanillaDispatches();

        // Identifier.CODEC is STRING.comapFlatMap -> inference yields plain text; an id
        // widget (with the registry-less picker) is the nicer surface.
        SchemaCodecs.registerCompanion(Identifier.CODEC, new Schema.ResourceId(null));

        // UUIDUtil.CODEC is INT_STREAM.comapFlatMap -> opaque (INT_STREAM has no widget).
        // On disk it is a fixed quadruple of ints.
        SchemaCodecs.registerCompanion(UUIDUtil.CODEC, (Schema) new Schema.ListOf<>(
                new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE), 4, 4));

        // UUIDUtil.STRING_CODEC / LENIENT_CODEC decode via opaque lambdas; on disk they are
        // the canonical hyphenated string form.
        Schema uuidString = new Schema.Str(36, 36, Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        SchemaCodecs.registerCompanion(UUIDUtil.STRING_CODEC, uuidString);

        // BlockPos.CODEC is INT_STREAM.comapFlatMap - INT_STREAM is opaque. On disk: [x, y, z].
        Schema intAll = new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Schema floatAll = new Schema.FloatRange(-Float.MAX_VALUE, Float.MAX_VALUE);
        SchemaCodecs.registerCompanion(BlockPos.CODEC, new Schema.ListOf<>(intAll, 3, 3));

        // JOML vector codecs are FLOAT/INT.listOf().comapFlatMap with an arity check hidden
        // in the lambda; inference sees an unbounded list, curation restores the fixed size.
        // Note: ExtraCodecs.VECTOR2F / VECTOR3I do not exist on 1.21.1 (added in 1.21.6).
        //? >=1.21.6 {
        try {
            SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR2F, new Schema.ListOf<>(floatAll, 2, 2));
        } catch (Throwable e) {
            CodecUI.LOGGER.info("Vector2F codec does not exist, are you on 1.21.5? Error: ", e);
        }
        //?}
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR3F, new Schema.ListOf<>(floatAll, 3, 3));
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR4F, new Schema.ListOf<>(floatAll, 4, 4));
        //? >=1.21.6 {
        try {
            SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR3I, new Schema.ListOf<>(intAll, 3, 3));
        } catch (Throwable e) {
            CodecUI.LOGGER.info("Vector2F codec does not exist, are you on 1.21.5? Error: ", e);
        }
        //?}

        // Color codecs: inference at best yields AnyOf(integer, text); a color picker is the
        // point of this whole exercise. INT-primary variants emit packed ints, STRING_*
        // variants emit "#RRGGBB"/"#AARRGGBB" strings (hexString flag). On 1.21.1 only ARGB_COLOR_CODEC exists (int-primary);
        // RGB_COLOR_CODEC was added in 1.21.2.
        // STRING_* hex-string color codecs were added in 1.21.11.
        //? >=1.21.2
        SchemaCodecs.registerCompanion(ExtraCodecs.RGB_COLOR_CODEC, new Schema.Color(false, false));
        SchemaCodecs.registerCompanion(ExtraCodecs.ARGB_COLOR_CODEC, new Schema.Color(true, false));
        //? >=1.21.11 {
        SchemaCodecs.registerCompanion(ExtraCodecs.STRING_RGB_COLOR, new Schema.Color(false, true));
        SchemaCodecs.registerCompanion(ExtraCodecs.STRING_ARGB_COLOR, new Schema.Color(true, true));
        //?}
    }

    // Dispatch key sets for vanilla Codec.dispatch(...) families keyed on a "type" object from
    // a registry. A KeyDispatchCodec hides its valid key set inside a closure, so the resolver
    // can't enumerate variants without this side channel.
    //
    // Only registries of concrete "type" objects belong here - ones whose dispatch decoder casts
    // the key to a specific class, so a foreign key fails fast and the resolver's probe can tell
    // hooks apart. Registries whose elements are bare MapCodec<? extends X> (DENSITY_FUNCTION_TYPE,
    // MATERIAL_RULE, ...) are deliberately excluded: their dispatch decoder is identity, so every
    // such registry would accept every other's keys and cross-contaminate the variant lists.
    //
    // For most use cases, the RegistryByNameCodecMixin should handle it as it wraps the codecs
    // for the registry to provide the keys by default. However, this should be used for any registries
    // which have dispatch codecs not covered by RegistryByNameCodecMixin
    private static void registerVanillaDispatches() {

    }

    @SuppressWarnings({"unused", "unchecked", "rawtypes"})
    private static <K> void registerRegistryDispatch(Class<K> type, Registry<? extends K> registry) {
        // LAZY: re-snapshot on each call so we see whatever's in the registry when the editor opens,
        // not at static-init time (avoids any class-loading order race with registry population).
        Supplier<List<K>> keys = () -> {
            List<K> snapshot = new ArrayList<>();
            try {
                for (K v : registry) snapshot.add(v);
            } catch (Throwable t) {
                CodecUI.LOGGER.warn("[codecui] Failed to iterate registry for {}: {}",
                        type.getSimpleName(), t.toString());
            }
            return snapshot;
        };
        Function<K, String> nameOf = v -> {
            Identifier id = ((Registry) registry).getKey(v);
            return id != null ? id.toString() : String.valueOf(v);
        };
        SchemaCodecs.registerDispatchKeys(type, keys, nameOf);
    }

    private static void registerBootstrapDependent() {
        // BlockState.CODEC is built during very early MC bootstrap, before our mixins apply, so
        // the internal keyCodec never gets its ResourceId tag. On-disk shape:
        // {"Name": id, "Properties": {prop: value}} (Properties optional in vanilla).
        Schema.Str anyStr = new Schema.Str(0, Integer.MAX_VALUE, null);
        SchemaCodecs.registerCompanion(BlockState.CODEC,
                new Schema.Record<>(BlockState.class,
                        List.<Schema.Field<BlockState, ?>>of(
                        new Schema.Field<>("Name", new Schema.ResourceId(Registries.BLOCK), false, null),
                        new Schema.Field<>("Properties", new Schema.MapOf<>(anyStr, anyStr), true, null))));

        // ItemStack.CODEC routes through data components - opaque to inference. Minimal
        // round-tripping shape for plain stacks.
        SchemaCodecs.registerCompanion(ItemStack.CODEC,
                new Schema.Record<>(ItemStack.class,
                        List.of(
                        new Schema.Field<>("id", new Schema.ResourceId(Registries.ITEM), false, null),
                        new Schema.Field<>("count", new Schema.IntRange(1, 99), true, 1))));
        // In 26.1 and beyond, ItemStackTemplate is a class that acts as a template for an ItemStack, as such, we handle it the same.
        //? >=26.1 {
        SchemaCodecs.registerCompanion(ItemStackTemplate.CODEC,
                new Schema.Record<>(ItemStackTemplate.class,
                        List.of(
                                new Schema.Field<>("id", new Schema.ResourceId(Registries.ITEM), false, null),
                                new Schema.Field<>("count", new Schema.IntRange(1, 99), true, 1))));
        //?}

        // Ingredient.CODEC is either(list(Value), Value) with Value = xor(ItemValue, TagValue);
        // structurally that resolves to an unlabeled AnyOf. Curate the on-disk shape with real
        // labels: {"item": id} / {"tag": id}, or a list mixing the two. (The NeoForge
        // {"type": ...} custom form falls outside this surface.)
        Schema<Ingredient> ingredientItem = new Schema.Record<>(Ingredient.class,
                List.<Schema.Field<Ingredient, ?>>of(
                        new Schema.Field<>("item", new Schema.ResourceId(Registries.ITEM), false, null)));
        Schema<Ingredient> ingredientTag = new Schema.Record<>(Ingredient.class,
                List.<Schema.Field<Ingredient, ?>>of(
                        new Schema.Field<>("tag", new Schema.TagId(Registries.ITEM, false), false, null)));
        Schema<Ingredient> ingredientValue = Schema.anyOf(
                Schema.option("item", ingredientItem), Schema.option("tag", ingredientTag));
        Schema<Ingredient> ingredient = Schema.anyOf(
                Schema.option("item", ingredientItem),
                Schema.option("tag", ingredientTag),
                Schema.option("list", new Schema.ListOf<>(ingredientValue, 1, Integer.MAX_VALUE)));
        SchemaCodecs.registerCompanion(Ingredient.CODEC, ingredient);
        // By 1.21.2, Ingredient.CODEC_NONEMPTY was removed as Ingredient.CODEC enforces being non-empty now
        //? <1.21.2
        //SchemaCodecs.registerCompanion(Ingredient.CODEC_NONEMPTY, ingredient);

        // DimensionType.DIRECT_CODEC wraps fields via ExtraCodecs.catchDecoderException (a raw
        // Codec.of with anonymous decoder) - no mixin point.
        SchemaCodecs.registerCompanion(DimensionType.DIRECT_CODEC,
                new Schema.Record<>(DimensionType.class,
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
