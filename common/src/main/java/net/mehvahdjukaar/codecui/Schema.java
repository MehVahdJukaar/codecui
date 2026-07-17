package net.mehvahdjukaar.codecui;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public sealed interface Schema<A> {

    record Bool() implements Schema<Boolean> {}

    record IntRange(int min, int max) implements Schema<Integer> {}

    /** {@code hexString}: the codec's on-disk form is a {@code "#RRGGBB"}-style string
     *  (e.g. {@code ExtraCodecs.STRING_RGB_COLOR}) instead of a packed integer. */
    record Color(boolean hasAlpha, boolean hexString) implements Schema<Integer> {
        public Color(boolean hasAlpha) {
            this(hasAlpha, false);
        }
    }

    record LongRange(long min, long max) implements Schema<Long> {}

    record FloatRange(float min, float max) implements Schema<Float> {}

    record DoubleRange(double min, double max) implements Schema<Double> {}

    record Str(int minLen, int maxLen, @Nullable Pattern pattern) implements Schema<String> {}

    record ResourceId(@Nullable ResourceKey<? extends Registry<?>> registry) implements Schema<ResourceLocation> {}

    /**
     * A tag id for {@code registry}. The twin of {@link ResourceId}: instead of one registry
     * entry it picks one {@link net.minecraft.tags.TagKey}. Backends render a tag picker whose
     * candidates come from the registry's loaded tags (see {@link SchemaCodecs#availableTagIds}),
     * degrading to a plain text field when {@code registry} is null or no tags are loaded.
     * {@code hashed} is the on-disk form: {@code true} writes the {@code "#namespace:path"} string
     * ({@code TagKey.hashedCodec}, the tag-reference form used inside HolderSets and tag files),
     * {@code false} writes a bare {@code "namespace:path"} ({@code TagKey.codec}, e.g. a recipe
     * ingredient's {@code "tag"} field). Writing the wrong one makes the game codec reject it.
     */
    record TagId(@Nullable ResourceKey<? extends Registry<?>> registry, boolean hashed) implements Schema<ResourceLocation> {
        public TagId(@Nullable ResourceKey<? extends Registry<?>> registry) {
            this(registry, true);
        }
    }

    record Enum<A>(List<A> options, Function<A, String> label) implements Schema<A> {}

    record Record<A>(Class<A> type, List<Field<A, ?>> fields) implements Schema<A> {}

    /**
     * One field of a {@link Record}. {@code inline} marks a field whose grouped MapCodec spans
     * the parent's own keys rather than nesting under {@code name} - a dispatch ({@link OneOf}),
     * pair, or map included via {@code RecordCodecBuilder.of(getter, MapCodec)}. DFU never nests
     * these, so backends must merge such a field's object flat into the parent (and feed it the
     * whole parent object back on load). {@code name} is still the primary key it reads.
     */
    record Field<A, F>(String name, Schema<F> schema, boolean optional, @Nullable F defaultValue,
                       boolean inline) {
        public Field(String name, Schema<F> schema, boolean optional, @Nullable F defaultValue) {
            this(name, schema, optional, defaultValue, false);
        }
    }

    record ListOf<E>(Schema<E> element, int min, int max) implements Schema<List<E>> {}

    record MapOf<K, V>(Schema<K> key, Schema<V> value) implements Schema<Map<K, V>> {}

    /**
     * A flat N-way "one of these alternative shapes" choice - for codecs that try several
     * formats in order ({@code Codec.either}/{@code withAlternative} chains, hand-rolled
     * alternative codecs, reference-or-inline). Always build via {@link #anyOf}: it splices
     * nested AnyOf options flat and auto-labels unlabeled ones ({@code "#N kind"}), so the
     * UI shows a single picker no matter how deep the underlying either-chain was.
     */
    record AnyOf<A>(List<Option> options) implements Schema<A> {
        public record Option(@Nullable String label, Schema<?> schema) {}
    }

    record PairOf<F, S>(Schema<F> first, Schema<S> second) implements Schema<Pair<F, S>> {}

    /**
     * A type-dispatched object: a {@code typeField} key selects one of {@code variants}.
     *
     * <p>{@code valueField} controls where the selected variant's body sits. When null (the common
     * case, a plain {@code KeyDispatchCodec}) the body is merged flat at the same level as the type
     * field. When set (e.g. vanilla {@code ExtraCodecs.dispatchOptionalValue}, where an advancement
     * criterion is {@code {"trigger": id, "conditions": {...}}}) the body is nested under that key
     * and omitted entirely when empty.</p>
     */
    record OneOf<A>(String typeField, Map<String, Schema<? extends A>> variants,
                    @Nullable String valueField) implements Schema<A> {
        public OneOf(String typeField, Map<String, Schema<? extends A>> variants) {
            this(typeField, variants, null);
        }
    }

    /**
     * Recursive back-reference: produced by the resolver when a codec's schema refers to a
     * codec that is still being resolved (self-recursive types like text components,
     * "not"/"and" predicates, {@code hidden_effect}). The resolver {@link #bind}s the target
     * once the outer resolve completes, so by the time an editor walks the schema the ref is
     * bound. UI backends MUST materialize the target lazily (on expand / on data load) -
     * eager materialization of a cyclic schema would recurse forever.
     */
    final class Ref<A> implements Schema<A> {
        private volatile @Nullable Schema<?> target;

        public @Nullable Schema<?> target() {
            return target;
        }

        /** First bind wins; self-binding ignored. Called by the resolver only. */
        public void bind(Schema<?> resolved) {
            if (this.target == null && resolved != this) {
                this.target = resolved;
            }
        }
    }

    record Opaque<A>(Codec<A> codec, @Nullable A example) implements Schema<A> {}

    /**
     * Binds an arbitrary domain-specific widget to a codec. The {@code widgetDef} is
     * opaque to this ADT (so the core schema layer stays UI-backend-agnostic); it is
     * populated by a backend-specific combinator - e.g. {@code SwingWidgetDef.bind(codec)}
     * for Swing. Backends pattern-match on the runtime type of {@code widgetDef} to dispatch.
     */
    record Custom<A>(Object widgetDef) implements Schema<A> {}

    static IntRange intRange(int min, int max) { return new IntRange(min, max); }

    static LongRange longRange(long min, long max) { return new LongRange(min, max); }

    static FloatRange floatRange(float min, float max) { return new FloatRange(min, max); }

    static DoubleRange doubleRange(double min, double max) { return new DoubleRange(min, max); }

    static Str str() { return new Str(0, Integer.MAX_VALUE, null); }

    static TagId tagId(ResourceKey<? extends Registry<?>> registry) { return new TagId(registry); }

    static Bool bool() { return new Bool(); }

    static Color colorRgb()  { return new Color(false); }

    static Color colorArgb() { return new Color(true); }

    /** Unlabeled alternative - receives an auto kind-name label in {@link #anyOf}. */
    static AnyOf.Option option(Schema<?> schema) {
        return new AnyOf.Option(null, schema);
    }

    static AnyOf.Option option(String label, Schema<?> schema) {
        return new AnyOf.Option(label, schema);
    }

    static <A> Schema<A> anyOf(AnyOf.Option... options) {
        return anyOf(Arrays.asList(options));
    }

    /**
     * Flattening factory for {@link AnyOf}: options that are themselves AnyOf are spliced
     * in-place (their more-specific labels survive), a single surviving option collapses to
     * its own schema, and unlabeled options get kind-name labels ({@code "object"}, {@code "id"},
     * ...), numbered ({@code "text #2"}) only when the same kind appears more than once.
     */
    @SuppressWarnings("unchecked")
    static <A> Schema<A> anyOf(List<AnyOf.Option> options) {
        ArrayList<AnyOf.Option> flat = new ArrayList<>(options.size());
        for (AnyOf.Option o : options) {
            if (o.schema() instanceof AnyOf<?>(List<AnyOf.Option> options1)) flat.addAll(options1);
            else flat.add(o);
        }
        if (flat.size() == 1) return (Schema<A>) flat.getFirst().schema();
        Map<String, Integer> kindTotals = new HashMap<>();
        for (AnyOf.Option o : flat) {
            if (o.label() == null) kindTotals.merge(kindName(o.schema()), 1, Integer::sum);
        }
        Map<String, Integer> kindSeen = new HashMap<>();
        for (int i = 0; i < flat.size(); i++) {
            AnyOf.Option o = flat.get(i);
            if (o.label() != null) continue;
            String kind = kindName(o.schema());
            int n = kindSeen.merge(kind, 1, Integer::sum);
            flat.set(i, new AnyOf.Option(kindTotals.get(kind) == 1 ? kind : kind + " #" + n, o.schema()));
        }
        return new AnyOf<>(List.copyOf(flat));
    }

    /** Short human word for a schema's kind; used for auto-labels in {@link #anyOf}. */
    static String kindName(Schema<?> schema) {
        return switch (schema) {
            case Bool ignored -> "boolean";
            case IntRange ignored -> "integer";
            case LongRange ignored -> "integer";
            case FloatRange ignored -> "number";
            case DoubleRange ignored -> "number";
            case Color ignored -> "color";
            case Str ignored -> "text";
            case ResourceId ignored -> "id";
            case TagId ignored -> "tag";
            case Enum<?> ignored -> "choice";
            case Record<?> ignored -> "object";
            case ListOf<?> ignored -> "list";
            case MapOf<?, ?> ignored -> "map";
            case PairOf<?, ?> ignored -> "pair";
            case OneOf<?> ignored -> "typed object";
            case AnyOf<?> ignored -> "alternatives";
            case Ref<?> ref -> {
                Schema<?> t = ref.target();
                yield (t == null || t instanceof Ref<?>) ? "recursive" : kindName(t);
            }
            case Opaque<?> ignored -> "raw";
            case Custom<?> ignored -> "custom";
        };
    }
}
