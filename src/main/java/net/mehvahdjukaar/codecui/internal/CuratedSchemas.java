package net.mehvahdjukaar.codecui.internal;

//? >=26.1
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.CodecUI;
import net.mehvahdjukaar.codecui.Schema;
import net.mehvahdjukaar.codecui.SchemaCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;
//? <26.1 {
/*import net.minecraft.util.valueproviders.FloatProviderType;
import net.minecraft.util.valueproviders.IntProviderType;
*///?}
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.featuresize.FeatureSizeType;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.rootplacers.RootPlacerType;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraft.world.level.levelgen.heightproviders.HeightProviderType;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosRuleTestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifierType;
//? <26.1 {
/*import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.providers.nbt.LootNbtProviderType;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.score.LootScoreProviderType;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
 *   <li>Schemas describe the ON-DISK JSON shape, not the runtime type - widgets edit JSON.</li>
 *   <li>Comment WHY inference fails for each entry.</li>
 * </ul>
 *
 * <p>Bootstrapped lazily by the resolver on first resolve; safe because companions are
 * looked up fresh each resolve (never baked into cached schemas at construction time).</p>
 */
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
        // Separate block: these reference classes that need the game bootstrap (Blocks,
        // ItemStack components). On a bare JVM they fail without taking down the safe
        // registrations above.
        try {
            registerBootstrapDependent();
        } catch (Throwable t) {
            CodecUI.LOGGER.info("game-dependent curated schemas unavailable (no game bootstrap): {}",
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
        SchemaCodecs.registerCompanion(BlockPos.CODEC, (Schema) new Schema.ListOf<>(intAll, 3, 3));

        // JOML vector codecs are FLOAT/INT.listOf().comapFlatMap with an arity check hidden
        // in the lambda; inference sees an unbounded list, curation restores the fixed size.
        // Note: ExtraCodecs.VECTOR2F / VECTOR3I do not exist on 1.21.1 (added in 1.21.11).
        //? >=1.21.11
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR2F, (Schema) new Schema.ListOf<>(floatAll, 2, 2));
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR3F, (Schema) new Schema.ListOf<>(floatAll, 3, 3));
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR4F, (Schema) new Schema.ListOf<>(floatAll, 4, 4));
        //? >=1.21.11
        SchemaCodecs.registerCompanion(ExtraCodecs.VECTOR3I, (Schema) new Schema.ListOf<>(intAll, 3, 3));

        // Color codecs: inference at best yields AnyOf(integer, text); a color picker is the
        // point of this whole exercise. INT-primary variants emit packed ints, STRING_*
        // variants emit "#RRGGBB"/"#AARRGGBB" strings (hexString flag). On 1.21.1 only ARGB_COLOR_CODEC exists (int-primary);
        // RGB_COLOR_CODEC and the STRING_* hex-string color codecs were added in 1.21.11.
        //? >=1.21.11
        SchemaCodecs.registerCompanion(ExtraCodecs.RGB_COLOR_CODEC, new Schema.Color(false, false));
        SchemaCodecs.registerCompanion(ExtraCodecs.ARGB_COLOR_CODEC, new Schema.Color(true, false));
        //? >=1.21.11 {
        SchemaCodecs.registerCompanion(ExtraCodecs.STRING_RGB_COLOR, new Schema.Color(false, true));
        SchemaCodecs.registerCompanion(ExtraCodecs.STRING_ARGB_COLOR, new Schema.Color(true, true));
        //?}
    }

    /**
     * Dispatch key sets for vanilla {@code Codec.dispatch(...)} families keyed on a "type" object
     * from a registry (e.g. {@link RuleTestType}). A {@code KeyDispatchCodec} hides its valid key
     * set inside a closure, so the resolver can't enumerate variants without this side channel; it
     * feeds each key back through the dispatch's own decoder to recover the variant body.
     *
     * <p>These are hand-listed rather than derived from "all registries" because only <i>type</i>
     * registries (whose elements carry a per-element {@code MapCodec}) are dispatch-backed - plain
     * value registries (Block, Item, …) enumerate through the {@link Schema.ResourceId} id-picker
     * path instead - and nothing reflectively distinguishes the two. Add more the same way.</p>
     *
     * <p><b>Only registries of concrete "type" objects belong here</b> - ones whose dispatch decoder
     * casts the key to a specific class (so a foreign key fails fast, letting the resolver's probe
     * tell hooks apart). Registries whose elements are bare {@code MapCodec<? extends X>}
     * (DENSITY_FUNCTION_TYPE, MATERIAL_RULE/CONDITION, ENTITY_SUB_PREDICATE_TYPE, the ENCHANTMENT_*
     * effect registries, …) are deliberately excluded: their dispatch decoder is identity, so every
     * such registry would accept every other's keys and cross-contaminate the variant lists.</p>
     */
    private static void registerVanillaDispatches() {
        // Value providers (used all over worldgen configs).
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*IntProviderType*//*?}*/.class, BuiltInRegistries.INT_PROVIDER_TYPE);
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*FloatProviderType*//*?}*/.class, BuiltInRegistries.FLOAT_PROVIDER_TYPE);
        registerRegistryDispatch(HeightProviderType.class, BuiltInRegistries.HEIGHT_PROVIDER_TYPE);

        // Worldgen features / placement / block-state providers.
        registerRegistryDispatch(Feature.class, BuiltInRegistries.FEATURE);
        registerRegistryDispatch(PlacementModifierType.class, BuiltInRegistries.PLACEMENT_MODIFIER_TYPE);
        registerRegistryDispatch(BlockStateProviderType.class, BuiltInRegistries.BLOCKSTATE_PROVIDER_TYPE);
        registerRegistryDispatch(BlockPredicateType.class, BuiltInRegistries.BLOCK_PREDICATE_TYPE);
        registerRegistryDispatch(WorldCarver.class, BuiltInRegistries.CARVER);
        registerRegistryDispatch(FeatureSizeType.class, BuiltInRegistries.FEATURE_SIZE_TYPE);

        // Tree building blocks (feature sub-configs).
        registerRegistryDispatch(FoliagePlacerType.class, BuiltInRegistries.FOLIAGE_PLACER_TYPE);
        registerRegistryDispatch(TrunkPlacerType.class, BuiltInRegistries.TRUNK_PLACER_TYPE);
        registerRegistryDispatch(RootPlacerType.class, BuiltInRegistries.ROOT_PLACER_TYPE);
        registerRegistryDispatch(TreeDecoratorType.class, BuiltInRegistries.TREE_DECORATOR_TYPE);

        // Structures, placement, jigsaw pool elements, and the structure-template rule system.
        registerRegistryDispatch(StructureType.class, BuiltInRegistries.STRUCTURE_TYPE);
        registerRegistryDispatch(StructurePlacementType.class, BuiltInRegistries.STRUCTURE_PLACEMENT);
        registerRegistryDispatch(StructurePoolElementType.class, BuiltInRegistries.STRUCTURE_POOL_ELEMENT);
        registerRegistryDispatch(StructureProcessorType.class, BuiltInRegistries.STRUCTURE_PROCESSOR);
        registerRegistryDispatch(RuleTestType.class, BuiltInRegistries.RULE_TEST);
        registerRegistryDispatch(PosRuleTestType.class, BuiltInRegistries.POS_RULE_TEST);
        registerRegistryDispatch(RuleBlockEntityModifierType.class, BuiltInRegistries.RULE_BLOCK_ENTITY_MODIFIER);

        // Loot tables: entries, functions, conditions, and the number/nbt/score providers.
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*LootPoolEntryType*//*?}*/.class, BuiltInRegistries.LOOT_POOL_ENTRY_TYPE);
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*LootItemFunctionType*//*?}*/.class, BuiltInRegistries.LOOT_FUNCTION_TYPE);
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*LootItemConditionType*//*?}*/.class, BuiltInRegistries.LOOT_CONDITION_TYPE);
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*LootNumberProviderType*//*?}*/.class, BuiltInRegistries.LOOT_NUMBER_PROVIDER_TYPE);
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*LootNbtProviderType*//*?}*/.class, BuiltInRegistries.LOOT_NBT_PROVIDER_TYPE);
        registerRegistryDispatch(/*? >=26.1 {*/MapCodec/*?} <26.1 {*//*LootScoreProviderType*//*?}*/.class, BuiltInRegistries.LOOT_SCORE_PROVIDER_TYPE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <K> void registerRegistryDispatch(Class<? super K> keyType, Registry<? extends K> registry) {
        // LAZY: re-snapshot on each call so we see whatever's in the registry when the editor opens,
        // not at static-init time (avoids any class-loading order race with registry population).
        Supplier<List<K>> keys = () -> {
            List<K> snapshot = new ArrayList<>();
            try {
                for (K v : registry) snapshot.add(v);
            } catch (Throwable t) {
                CodecUI.LOGGER.warn("[codecui] Failed to iterate registry for {}: {}",
                        keyType.getSimpleName(), t.toString());
            }
            return snapshot;
        };
        Function<K, String> nameOf = v -> {
            Identifier id = ((Registry) registry).getKey(v);
            return id != null ? id.toString() : String.valueOf(v);
        };
        SchemaCodecs.registerDispatchKeys((Class<K>) keyType, keys, nameOf);
    }

    private static void registerBootstrapDependent() {
        // BlockState.CODEC is built during *very early* MC bootstrap (Blocks init), before
        // our codec_ui mixins are applied to Codec.fieldOf - so the internal keyCodec never
        // gets the ResourceId tag and the registry-tag dropdown fallback finds nothing.
        // Manual Record matching the on-disk shape: {"Name": id, "Properties": {prop: value}}
        // (Properties is lenientOptionalFieldOf in vanilla, so omitting it is fine).
        Schema.Str anyStr = new Schema.Str(0, Integer.MAX_VALUE, null);
        SchemaCodecs.registerCompanion(net.minecraft.world.level.block.state.BlockState.CODEC,
                new Schema.Record<>(net.minecraft.world.level.block.state.BlockState.class,
                        List.<Schema.Field<net.minecraft.world.level.block.state.BlockState, ?>>of(
                        new Schema.Field<>("Name", new Schema.ResourceId(Registries.BLOCK), false, null),
                        new Schema.Field<>("Properties", new Schema.MapOf<>(anyStr, anyStr), true, null))));

        // ItemStack.CODEC routes through data components - opaque to inference. Minimal
        // round-tripping shape for plain stacks.
        SchemaCodecs.registerCompanion(net.minecraft.world.item.ItemStack.CODEC,
                new Schema.Record<>(net.minecraft.world.item.ItemStack.class,
                        List.of(
                        new Schema.Field<>("id", new Schema.ResourceId(Registries.ITEM), false, null),
                        new Schema.Field<>("count", new Schema.IntRange(1, 99), true, 1))));
        // In 26.1 and beyond, ItemStackTemplate is a class that acts as a template for an ItemStack, as such, we handle it the same.
        //? >=26.1 {
        SchemaCodecs.registerCompanion(net.minecraft.world.item.ItemStackTemplate.CODEC,
                new Schema.Record<>(net.minecraft.world.item.ItemStackTemplate.class,
                        List.of(
                                new Schema.Field<>("id", new Schema.ResourceId(Registries.ITEM), false, null),
                                new Schema.Field<>("count", new Schema.IntRange(1, 99), true, 1))));
        //?}

        // Ingredient.CODEC is either(list(Value), Value) with Value = xor(ItemValue, TagValue) -
        // and on NeoForge a custom-ingredient type dispatch too. Structurally that resolves to an
        // unlabeled AnyOf: both Value records surface as bare "object" and the custom dispatch as a
        // "raw" opaque. Curate the on-disk shape with real labels - {"item": id} / {"tag": id}, or a
        // list mixing the two. (The NeoForge {"type": ...} custom form falls outside this surface.)
        Schema<Ingredient> ingredientItem = new Schema.Record<>(Ingredient.class,
                List.<Schema.Field<Ingredient, ?>>of(
                        new Schema.Field<>("item", new Schema.ResourceId(Registries.ITEM), false, null)));
        Schema<Ingredient> ingredientTag = new Schema.Record<>(Ingredient.class,
                List.<Schema.Field<Ingredient, ?>>of(
                        new Schema.Field<>("tag", new Schema.TagId(Registries.ITEM), false, null)));
        Schema<Ingredient> ingredientValue = Schema.anyOf(
                Schema.option("item", ingredientItem), Schema.option("tag", ingredientTag));
        Schema<Ingredient> ingredient = Schema.anyOf(
                Schema.option("item", ingredientItem),
                Schema.option("tag", ingredientTag),
                Schema.option("list", new Schema.ListOf<>(ingredientValue, 1, Integer.MAX_VALUE)));
        SchemaCodecs.registerCompanion(Ingredient.CODEC, ingredient);
        // By 1.21.11, Ingredient.CODEC_NONEMPTY was removed as Ingredient.CODEC enforces being non-empty now
        //? <=1.21.11
        //SchemaCodecs.registerCompanion(Ingredient.CODEC_NONEMPTY, ingredient);

        // DimensionType.DIRECT_CODEC wraps fields via ExtraCodecs.catchDecoderException
        // (a raw Codec.of with anonymous decoder) - no mixin point. Companion describes the
        // standard vanilla on-disk shape.
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
