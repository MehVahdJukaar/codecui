package net.mehvahdjukaar.codecui.internal;

import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.SchemaCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ComponentSerialization;
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

// Hand-maintained schema registrations for codecs inference can't (or shouldn't) handle, all through
// the same public API a third-party mod would use. Prefer teaching inference the class of codec first;
// curate only when that's not worth it. Schemas describe the on-disk JSON shape. Say why inference fails.
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
        // Needs the game bootstrap (Blocks, ItemStack components); on a bare JVM this fails alone.
        try {
            registerBootstrapDependent();
        } catch (Throwable t) {
            CodecUI.LOGGER.info("game-dependent curated schemas unavailable (no game bootstrap): {}",
                    String.valueOf(t));
        }
        // net.minecraft.client is absent on a dedicated server; the NoClassDefFoundError just skips these.
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

        // Identifier.CODEC is STRING.comapFlatMap: inference yields plain text, an id widget is nicer.
        SchemaCodecs.registerCompanion(Identifier.CODEC, new Schema.ResourceId(null));

        // UUIDUtil.CODEC is INT_STREAM.comapFlatMap (opaque); on disk a fixed quadruple of ints.
        SchemaCodecs.registerCompanion(UUIDUtil.CODEC, (Schema) new Schema.ListOf<>(
                new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE), 4, 4));

        // UUIDUtil.STRING_CODEC decodes via opaque lambdas; on disk the canonical hyphenated string.
        Schema uuidString = new Schema.Str(36, 36, Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
        SchemaCodecs.registerCompanion(UUIDUtil.STRING_CODEC, uuidString);

        // BlockPos.CODEC is INT_STREAM.comapFlatMap (opaque); on disk [x, y, z].
        Schema intAll = new Schema.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Schema floatAll = new Schema.FloatRange(-Float.MAX_VALUE, Float.MAX_VALUE);
        SchemaCodecs.registerCompanion(BlockPos.CODEC, new Schema.ListOf<>(intAll, 3, 3));

        // JOML vectors are listOf().comapFlatMap with the arity check in the lambda; restore the fixed size.
        // VECTOR2F / VECTOR3I were added in 1.21.6.
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

        // Color codecs: inference at best yields AnyOf(integer, text). Int variants emit packed ints,
        // STRING_* variants (1.21.11+) emit "#RRGGBB"/"#AARRGGBB". RGB_COLOR_CODEC was added in 1.21.2.
        //? >=1.21.2
        SchemaCodecs.registerCompanion(ExtraCodecs.RGB_COLOR_CODEC, new Schema.Color(false, false));
        SchemaCodecs.registerCompanion(ExtraCodecs.ARGB_COLOR_CODEC, new Schema.Color(true, false));
        //? >=1.21.11 {
        SchemaCodecs.registerCompanion(ExtraCodecs.STRING_RGB_COLOR, new Schema.Color(false, true));
        SchemaCodecs.registerCompanion(ExtraCodecs.STRING_ARGB_COLOR, new Schema.Color(true, true));
        //?}

        // ComponentSerialization.CODEC is Codec.recursive over an Either tree; the inferred schema doesn't
        // round-trip and is too rich for a form anyway, so validated raw JSON. Opaque over its own key
        // codec pins the tag forever, fine only because the codec is a static singleton.
        SchemaCodecs.registerCompanion(ComponentSerialization.CODEC, new Schema.Opaque<>(ComponentSerialization.CODEC, null));
    }

    // Dispatch key sets for vanilla Codec.dispatch(...) families that RegistryByNameCodecMixin doesn't
    // cover. Only registries of concrete "type" objects: registries of bare MapCodec<? extends X>
    // (DENSITY_FUNCTION_TYPE, MATERIAL_RULE, ...) have an identity dispatch decoder and would accept
    // each other's keys, cross-contaminating the variant lists.
    private static void registerVanillaDispatches() {

    }

    @SuppressWarnings({"unused", "unchecked", "rawtypes"})
    private static <K> void registerRegistryDispatch(Class<K> type, Registry<? extends K> registry) {
        // Snapshot per call so we see the registry as it is when the editor opens, not at static init.
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
        // BlockState.CODEC is built before our mixins apply, so its keyCodec never gets the ResourceId tag.
        // On disk: {"Name": id, "Properties": {prop: value}}.
        Schema.Str anyStr = new Schema.Str(0, Integer.MAX_VALUE, null);
        SchemaCodecs.registerCompanion(BlockState.CODEC,
                new Schema.Record<>(BlockState.class,
                        List.of(
                        new Schema.Field<>("Name", new Schema.ResourceId(Registries.BLOCK), false, null),
                        new Schema.Field<>("Properties", new Schema.MapOf<>(anyStr, anyStr), true, null))));

        // ItemStack.CODEC routes through data components (opaque); minimal shape for plain stacks.
        SchemaCodecs.registerCompanion(ItemStack.CODEC,
                new Schema.Record<>(ItemStack.class,
                        List.of(
                        new Schema.Field<>("id", new Schema.ResourceId(Registries.ITEM), false, null),
                        new Schema.Field<>("count", new Schema.IntRange(1, 99), true, 1))));
        // ItemStackTemplate (26.1+) is an ItemStack template; same shape.
        //? >=26.1 {
        SchemaCodecs.registerCompanion(ItemStackTemplate.CODEC,
                new Schema.Record<>(ItemStackTemplate.class,
                        List.of(
                                new Schema.Field<>("id", new Schema.ResourceId(Registries.ITEM), false, null),
                                new Schema.Field<>("count", new Schema.IntRange(1, 99), true, 1))));
        //?}

        // Ingredient.CODEC is either(list(Value), Value), Value = xor(item, tag): an unlabeled AnyOf.
        // Curate with labels: {"item": id} / {"tag": id} or a list of those. NeoForge {"type": ...} not covered.
        Schema<Ingredient> ingredientItem = new Schema.Record<>(Ingredient.class,
                List.of(
                        new Schema.Field<>("item", new Schema.ResourceId(Registries.ITEM), false, null)));
        Schema<Ingredient> ingredientTag = new Schema.Record<>(Ingredient.class,
                List.of(
                        new Schema.Field<>("tag", new Schema.TagId(Registries.ITEM, false), false, null)));
        Schema<Ingredient> ingredientValue = Schema.anyOf(
                Schema.option("item", ingredientItem), Schema.option("tag", ingredientTag));
        Schema<Ingredient> ingredient = Schema.anyOf(
                Schema.option("item", ingredientItem),
                Schema.option("tag", ingredientTag),
                Schema.option("list", new Schema.ListOf<>(ingredientValue, 1, Integer.MAX_VALUE)));
        SchemaCodecs.registerCompanion(Ingredient.CODEC, ingredient);
        // 1.21.2 removed CODEC_NONEMPTY; CODEC is non-empty itself now.
        //? <1.21.2
        //SchemaCodecs.registerCompanion(Ingredient.CODEC_NONEMPTY, ingredient);

        // DimensionType.DIRECT_CODEC wraps fields in ExtraCodecs.catchDecoderException (raw Codec.of), no mixin point.
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
