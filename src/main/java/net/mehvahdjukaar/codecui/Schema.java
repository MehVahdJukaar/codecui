package net.mehvahdjukaar.codecui;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

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

    /** hexString: the on-disk form is a "#RRGGBB" string instead of a packed integer. */
    record Color(boolean hasAlpha, boolean hexString) implements Schema<Integer> {
        public Color(boolean hasAlpha) {
            this(hasAlpha, false);
        }
    }

    record LongRange(long min, long max) implements Schema<Long> {}

    record FloatRange(float min, float max) implements Schema<Float> {}

    record DoubleRange(double min, double max) implements Schema<Double> {}

    record Str(int minLen, int maxLen, @Nullable Pattern pattern) implements Schema<String> {}

    record ResourceId(@Nullable ResourceKey<? extends Registry<?>> registry) implements Schema<Identifier> {}

    /**
     * One tag of registry, picked from SchemaCodecs.availableTagIds (plain text when null or no
     * tags loaded). hashed selects the on-disk form: "#ns:path" (TagKey.hashedCodec) or bare
     * "ns:path" (TagKey.codec); the game codec rejects the wrong one.
     */
    record TagId(@Nullable ResourceKey<? extends Registry<?>> registry, boolean hashed) implements Schema<Identifier> {
        public TagId(@Nullable ResourceKey<? extends Registry<?>> registry) {
            this(registry, true);
        }
    }

    record Enum<A>(List<A> options, Function<A, String> label) implements Schema<A> {}

    record Record<A>(Class<A> type, List<Field<A, ?>> fields) implements Schema<A> {}

    /**
     * inline marks a field whose MapCodec spans the parent's own keys instead of nesting under
     * name (a dispatch, pair or map via RecordCodecBuilder.of(getter, MapCodec)). Backends must
     * merge it flat into the parent and hand it the whole parent object on load.
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
     * Codec.dispatchedMap: valueForKey maps the JSON key to that entry's value schema, lazily
     * (the key space can be every block) and never null (Opaque when unresolvable). Backends
     * re-resolve a row when its key changes.
     */
    record DispatchedMapOf<K, V>(Schema<K> key,
                                 Function<String, Schema<?>> valueForKey) implements Schema<Map<K, V>> {}

    /**
     * Flat N-way choice between alternative shapes (either/withAlternative chains). Build via
     * anyOf, which splices nested AnyOfs flat and auto-labels unlabeled options.
     */
    record AnyOf<A>(List<Option> options) implements Schema<A> {
        public record Option(@Nullable String label, Schema<?> schema) {}
    }

    record PairOf<F, S>(Schema<F> first, Schema<S> second) implements Schema<Pair<F, S>> {}

    /**
     * typeField selects one of variants. The variant body sits flat next to the type field when
     * valueField is null (plain KeyDispatchCodec), else nested under valueField and omitted when
     * empty (ExtraCodecs.dispatchOptionalValue, e.g. advancement criteria).
     */
    record OneOf<A>(String typeField, Map<String, Schema<? extends A>> variants,
                    @Nullable String valueField) implements Schema<A> {
        public OneOf(String typeField, Map<String, Schema<? extends A>> variants) {
            this(typeField, variants, null);
        }
    }

    /**
     * Back-reference to a schema still being resolved (self-recursive codecs). Bound by the
     * resolver before the editor sees it. Backends must materialize the target lazily or a
     * cyclic schema recurses forever.
     */
    final class Ref<A> implements Schema<A> {
        private volatile @Nullable Schema<?> target;

        public @Nullable Schema<?> target() {
            return target;
        }

        /** First bind wins; self-binding is ignored. Resolver use only. */
        public void bind(Schema<?> resolved) {
            if (this.target == null && resolved != this) {
                this.target = resolved;
            }
        }
    }

    record Opaque<A>(Codec<A> codec, @Nullable A example) implements Schema<A> {}

    /** A backend-specific widget; widgetDef is opaque here and backends dispatch on its runtime type. */
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

    /** Unlabeled option; anyOf gives it a kind-name label. */
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
     * Splices nested AnyOf options flat, collapses a single option to its own schema, and labels
     * unlabeled options by kind ("object", "id"), numbered only when a kind repeats.
     */
    @SuppressWarnings("unchecked")
    static <A> Schema<A> anyOf(List<AnyOf.Option> options) {
        ArrayList<AnyOf.Option> flat = new ArrayList<>(options.size());
        for (AnyOf.Option o : options) {
            if (o.schema() instanceof AnyOf<?>(List<AnyOf.Option> nestedOptions)) flat.addAll(nestedOptions);
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
            int ordinal = kindSeen.merge(kind, 1, Integer::sum);
            boolean kindIsUnique = kindTotals.get(kind) == 1;
            flat.set(i, new AnyOf.Option(kindIsUnique ? kind : kind + " #" + ordinal, o.schema()));
        }
        return new AnyOf<>(List.copyOf(flat));
    }

    /** Short human word for a schema's kind, used for anyOf auto-labels. */
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
            case DispatchedMapOf<?, ?> ignored -> "map";
            case PairOf<?, ?> ignored -> "pair";
            case OneOf<?> ignored -> "typed object";
            case AnyOf<?> ignored -> "alternatives";
            case Ref<?> ref -> {
                Schema<?> target = ref.target();
                yield (target == null || target instanceof Ref<?>) ? "recursive" : kindName(target);
            }
            case Opaque<?> ignored -> "raw";
            case Custom<?> ignored -> "custom";
        };
    }
}
