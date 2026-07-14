package net.mehvahdjukaar.codecui;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function13;
import com.mojang.datafixers.util.Function14;
import com.mojang.datafixers.util.Function15;
import com.mojang.datafixers.util.Function16;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.codecui.internal.BiggerCodecs;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class SchemaRecord {

    public static <A> SchemaCodec<A> create(Class<A> type, Function<Instance<A>, Group<A>> fn) {
        Instance<A> instance = new Instance<>(type);
        Group<A> group = fn.apply(instance);
        return group.build();
    }

    public static <A, F> FieldRef<A, F> field(String name, SchemaCodec<F> codec, Function<A, F> getter) {
        return new FieldRef<>(name, codec, false, null, getter);
    }

    public static <A, F> FieldRef<A, F> field(String name, Codec<F> codec, Function<A, F> getter) {
        return field(name, SchemaCodec.wrap(codec), getter);
    }

    /**
     * Field backed by an already-built {@link MapCodec} — e.g. a lenient wrapper that embeds
     * its own key and default and recovers from malformed input. The map codec is used
     * verbatim, so decode/encode semantics (including lenient fallback) are preserved exactly;
     * {@code elementSchema} only supplies the schema the editor renders for the value. Treated
     * as optional in the UI, since such wrappers always carry a fallback.
     */
    public static <A, F> FieldRef<A, F> field(String name, MapCodec<F> mapCodec, SchemaCodec<F> elementSchema,
                                              Function<A, F> getter) {
        return new FieldRef<>(name, null, true, null, getter, mapCodec, elementSchema);
    }

    public static <A, F> FieldRef<A, F> field(String name, MapCodec<F> mapCodec, Codec<F> elementSchema,
                                              Function<A, F> getter) {
        return field(name, mapCodec, SchemaCodec.wrap(elementSchema), getter);
    }

    public static <A, F> FieldRef<A, F> optional(String name, SchemaCodec<F> codec, F defaultValue, Function<A, F> getter) {
        return new FieldRef<>(name, codec, true, defaultValue, getter);
    }

    public static <A, F> FieldRef<A, F> optional(String name, Codec<F> codec, F defaultValue, Function<A, F> getter) {
        return optional(name, SchemaCodec.wrap(codec), defaultValue, getter);
    }

    /** Optional field with NO default — round-trips as {@code Optional<F>}, mirroring
     *  {@code codec.optionalFieldOf(name).forGetter(...)}. */
    public static <A, F> FieldRef<A, java.util.Optional<F>> optional(String name, SchemaCodec<F> codec,
                                                                     Function<A, java.util.Optional<F>> getter) {
        return new FieldRef<>(name, null, true, null, getter, codec.optionalFieldOf(name), codec);
    }

    public static <A, F> FieldRef<A, java.util.Optional<F>> optional(String name, Codec<F> codec,
                                                                     Function<A, java.util.Optional<F>> getter) {
        return optional(name, SchemaCodec.wrap(codec), getter);
    }

    /** Per-record builder context; exposes field/optional and group(...) factories. */
    public static final class Instance<A> {
        private final Class<A> type;

        private Instance(Class<A> type) {
            this.type = type;
        }

        public <F> FieldRef<A, F> field(String name, SchemaCodec<F> codec, Function<A, F> getter) {
            return SchemaRecord.field(name, codec, getter);
        }

        public <F> FieldRef<A, F> field(String name, Codec<F> codec, Function<A, F> getter) {
            return SchemaRecord.field(name, codec, getter);
        }

        /**
         * Field backed by an already-built {@link MapCodec} — e.g. a lenient wrapper that embeds
         * its own key and default and recovers from malformed input. The map codec is used
         * verbatim, so decode/encode semantics (including lenient fallback) are preserved exactly;
         * {@code elementSchema} only supplies the schema the editor renders for the value. Treated
         * as optional in the UI, since such wrappers always carry a fallback.
         */
        public <F> FieldRef<A, F> field(String name, MapCodec<F> mapCodec, SchemaCodec<F> elementSchema,
                                        Function<A, F> getter) {
            return SchemaRecord.field(name, mapCodec, elementSchema, getter);
        }

        public <F> FieldRef<A, F> field(String name, MapCodec<F> mapCodec, Codec<F> elementSchema,
                                        Function<A, F> getter) {
            return SchemaRecord.field(name, mapCodec, elementSchema, getter);
        }

        public <F> FieldRef<A, F> optional(String name, SchemaCodec<F> codec, F defaultValue, Function<A, F> getter) {
            return SchemaRecord.optional(name, codec, defaultValue, getter);
        }

        public <F> FieldRef<A, F> optional(String name, Codec<F> codec, F defaultValue, Function<A, F> getter) {
            return SchemaRecord.optional(name, codec, defaultValue, getter);
        }

        /** Optional field with NO default — round-trips as {@code Optional<F>}, mirroring
         *  {@code codec.optionalFieldOf(name).forGetter(...)}. */
        public <F> FieldRef<A, java.util.Optional<F>> optional(String name, SchemaCodec<F> codec,
                                                               Function<A, java.util.Optional<F>> getter) {
            return SchemaRecord.optional(name, codec, getter);
        }

        public <F> FieldRef<A, java.util.Optional<F>> optional(String name, Codec<F> codec,
                                                               Function<A, java.util.Optional<F>> getter) {
            return SchemaRecord.optional(name, codec, getter);
        }

        public <F1> Group1<A, F1> group(FieldRef<A, F1> f1) {
            return new Group1<>(this, f1);
        }

        public <F1, F2> Group2<A, F1, F2> group(FieldRef<A, F1> f1, FieldRef<A, F2> f2) {
            return new Group2<>(this, f1, f2);
        }

        public <F1, F2, F3> Group3<A, F1, F2, F3> group(FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3) {
            return new Group3<>(this, f1, f2, f3);
        }

        public <F1, F2, F3, F4> Group4<A, F1, F2, F3, F4> group(FieldRef<A, F1> f1, FieldRef<A, F2> f2,
                                                                FieldRef<A, F3> f3, FieldRef<A, F4> f4) {
            return new Group4<>(this, f1, f2, f3, f4);
        }

        public <F1, F2, F3, F4, F5> Group5<A, F1, F2, F3, F4, F5> group(FieldRef<A, F1> f1, FieldRef<A, F2> f2,
                                                                        FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                                                                        FieldRef<A, F5> f5) {
            return new Group5<>(this, f1, f2, f3, f4, f5);
        }

        public <F1, F2, F3, F4, F5, F6> Group6<A, F1, F2, F3, F4, F5, F6> group(FieldRef<A, F1> f1, FieldRef<A, F2> f2,
                                                                                FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                                                                                FieldRef<A, F5> f5, FieldRef<A, F6> f6) {
            return new Group6<>(this, f1, f2, f3, f4, f5, f6);
        }

        public <F1, F2, F3, F4, F5, F6, F7> Group7<A, F1, F2, F3, F4, F5, F6, F7> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7) {
            return new Group7<>(this, f1, f2, f3, f4, f5, f6, f7);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8> Group8<A, F1, F2, F3, F4, F5, F6, F7, F8> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8) {
            return new Group8<>(this, f1, f2, f3, f4, f5, f6, f7, f8);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9> Group9<A, F1, F2, F3, F4, F5, F6, F7, F8, F9> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9) {
            return new Group9<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10> Group10<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10) {
            return new Group10<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11> Group11<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11) {
            return new Group11<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12> Group12<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12) {
            return new Group12<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13> Group13<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13) {
            return new Group13<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14> Group14<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14) {
            return new Group14<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15> Group15<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15) {
            return new Group15<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16> Group16<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15, FieldRef<A, F16> f16) {
            return new Group16<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16);
        }

        // Arities 17..21 exceed DFU's built-in apply16; they route through BiggerCodecs.

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17> Group17<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15, FieldRef<A, F16> f16,
                FieldRef<A, F17> f17) {
            return new Group17<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18> Group18<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15, FieldRef<A, F16> f16,
                FieldRef<A, F17> f17, FieldRef<A, F18> f18) {
            return new Group18<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19> Group19<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15, FieldRef<A, F16> f16,
                FieldRef<A, F17> f17, FieldRef<A, F18> f18, FieldRef<A, F19> f19) {
            return new Group19<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20> Group20<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15, FieldRef<A, F16> f16,
                FieldRef<A, F17> f17, FieldRef<A, F18> f18, FieldRef<A, F19> f19, FieldRef<A, F20> f20) {
            return new Group20<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20);
        }

        public <F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, F21> Group21<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, F21> group(
                FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3, FieldRef<A, F4> f4,
                FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
                FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11, FieldRef<A, F12> f12,
                FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15, FieldRef<A, F16> f16,
                FieldRef<A, F17> f17, FieldRef<A, F18> f18, FieldRef<A, F19> f19, FieldRef<A, F20> f20,
                FieldRef<A, F21> f21) {
            return new Group21<>(this, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21);
        }
    }

    private static <A, F> MapCodec<F> mapCodecFor(FieldRef<A, F> field) {
        if (field.mapCodecOverride != null) {
            return field.mapCodecOverride;
        }
        if (field.optional && field.defaultValue != null) {
            return field.codec.optionalFieldOf(field.name, field.defaultValue);
        }
        return field.codec.fieldOf(field.name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <A, F> Schema.Field<A, F> toSchemaField(FieldRef<A, F> f) {
        // Optional<F> fields display their INNER codec's schema (the editor models optionality
        // via the field flag, not via an Optional wrapper type).
        Schema<?> schema = f.innerCodec != null ? f.innerCodec.schema() : f.codec.schema();
        return new Schema.Field<>(f.name, (Schema) schema, f.optional, f.defaultValue);
    }

    @SafeVarargs
    private static <A> Schema<A> buildSchema(Class<A> type, FieldRef<A, ?>... fs) {
        List<Schema.Field<A, ?>> schemaFields = new ArrayList<>(fs.length);
        for (FieldRef<A, ?> f : fs) {
            schemaFields.add(toSchemaField(f));
        }
        return new Schema.Record<>(type, schemaFields);
    }

    private static void checkInstance(Instance<?> expected, Instance<?> actual) {
        if (expected != actual) {
            throw new IllegalArgumentException("apply called with foreign Instance");
        }
    }

    public sealed interface Group<A>
            permits Group1, Group2, Group3, Group4, Group5, Group6, Group7, Group8, Group9,
                    Group10, Group11, Group12, Group13, Group14, Group15, Group16,
                    Group17, Group18, Group19, Group20, Group21 {
        SchemaCodec<A> build();
    }

    public static final class Group1<A, F1> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private Function<F1, A> ctor;

        Group1(Instance<A> instance, FieldRef<A, F1> f1) {
            this.instance = instance;
            this.f1 = f1;
        }

        public Group1<A, F1> apply(Instance<A> i, Function<F1, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            Codec<A> codec = mc1.xmap(ctor, f1.getter).codec();
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1));
        }
    }

    public static final class Group2<A, F1, F2> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private BiFunction<F1, F2, A> ctor;

        Group2(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
        }

        public Group2<A, F1, F2> apply(Instance<A> i, BiFunction<F1, F2, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F3> Group3<A, F1, F2, F3> and(FieldRef<A, F3> f) {
            return new Group3<>(instance, f1, f2, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply2(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2));
        }
    }

    public static final class Group3<A, F1, F2, F3> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private final FieldRef<A, F3> f3;
        private Function3<F1, F2, F3, A> ctor;

        Group3(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
        }

        public Group3<A, F1, F2, F3> apply(Instance<A> i, Function3<F1, F2, F3, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F4> Group4<A, F1, F2, F3, F4> and(FieldRef<A, F4> f) {
            return new Group4<>(instance, f1, f2, f3, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            MapCodec<F3> mc3 = mapCodecFor(f3);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply3(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2),
                    RecordCodecBuilder.of(f3.getter, mc3)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3));
        }
    }

    public static final class Group4<A, F1, F2, F3, F4> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4;
        private Function4<F1, F2, F3, F4, A> ctor;

        Group4(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2,
               FieldRef<A, F3> f3, FieldRef<A, F4> f4) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
            this.f4 = f4;
        }

        public Group4<A, F1, F2, F3, F4> apply(Instance<A> i, Function4<F1, F2, F3, F4, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F5> Group5<A, F1, F2, F3, F4, F5> and(FieldRef<A, F5> f) {
            return new Group5<>(instance, f1, f2, f3, f4, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            MapCodec<F3> mc3 = mapCodecFor(f3);
            MapCodec<F4> mc4 = mapCodecFor(f4);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply4(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2),
                    RecordCodecBuilder.of(f3.getter, mc3),
                    RecordCodecBuilder.of(f4.getter, mc4)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4));
        }
    }

    public static final class Group5<A, F1, F2, F3, F4, F5> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4;
        private final FieldRef<A, F5> f5;
        private Function5<F1, F2, F3, F4, F5, A> ctor;

        Group5(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2,
               FieldRef<A, F3> f3, FieldRef<A, F4> f4, FieldRef<A, F5> f5) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
            this.f4 = f4;
            this.f5 = f5;
        }

        public Group5<A, F1, F2, F3, F4, F5> apply(Instance<A> i, Function5<F1, F2, F3, F4, F5, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F6> Group6<A, F1, F2, F3, F4, F5, F6> and(FieldRef<A, F6> f) {
            return new Group6<>(instance, f1, f2, f3, f4, f5, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            MapCodec<F3> mc3 = mapCodecFor(f3);
            MapCodec<F4> mc4 = mapCodecFor(f4);
            MapCodec<F5> mc5 = mapCodecFor(f5);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply5(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2),
                    RecordCodecBuilder.of(f3.getter, mc3),
                    RecordCodecBuilder.of(f4.getter, mc4),
                    RecordCodecBuilder.of(f5.getter, mc5)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5));
        }
    }

    public static final class Group6<A, F1, F2, F3, F4, F5, F6> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4;
        private final FieldRef<A, F5> f5;
        private final FieldRef<A, F6> f6;
        private Function6<F1, F2, F3, F4, F5, F6, A> ctor;

        Group6(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2,
               FieldRef<A, F3> f3, FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
            this.f4 = f4;
            this.f5 = f5;
            this.f6 = f6;
        }

        public Group6<A, F1, F2, F3, F4, F5, F6> apply(Instance<A> i, Function6<F1, F2, F3, F4, F5, F6, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F7> Group7<A, F1, F2, F3, F4, F5, F6, F7> and(FieldRef<A, F7> f) {
            return new Group7<>(instance, f1, f2, f3, f4, f5, f6, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            MapCodec<F3> mc3 = mapCodecFor(f3);
            MapCodec<F4> mc4 = mapCodecFor(f4);
            MapCodec<F5> mc5 = mapCodecFor(f5);
            MapCodec<F6> mc6 = mapCodecFor(f6);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply6(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2),
                    RecordCodecBuilder.of(f3.getter, mc3),
                    RecordCodecBuilder.of(f4.getter, mc4),
                    RecordCodecBuilder.of(f5.getter, mc5),
                    RecordCodecBuilder.of(f6.getter, mc6)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6));
        }
    }

    public static final class Group7<A, F1, F2, F3, F4, F5, F6, F7> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4;
        private final FieldRef<A, F5> f5;
        private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7;
        private Function7<F1, F2, F3, F4, F5, F6, F7, A> ctor;

        Group7(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2,
               FieldRef<A, F3> f3, FieldRef<A, F4> f4, FieldRef<A, F5> f5,
               FieldRef<A, F6> f6, FieldRef<A, F7> f7) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
            this.f4 = f4;
            this.f5 = f5;
            this.f6 = f6;
            this.f7 = f7;
        }

        public Group7<A, F1, F2, F3, F4, F5, F6, F7> apply(Instance<A> i, Function7<F1, F2, F3, F4, F5, F6, F7, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F8> Group8<A, F1, F2, F3, F4, F5, F6, F7, F8> and(FieldRef<A, F8> f) {
            return new Group8<>(instance, f1, f2, f3, f4, f5, f6, f7, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            MapCodec<F3> mc3 = mapCodecFor(f3);
            MapCodec<F4> mc4 = mapCodecFor(f4);
            MapCodec<F5> mc5 = mapCodecFor(f5);
            MapCodec<F6> mc6 = mapCodecFor(f6);
            MapCodec<F7> mc7 = mapCodecFor(f7);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply7(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2),
                    RecordCodecBuilder.of(f3.getter, mc3),
                    RecordCodecBuilder.of(f4.getter, mc4),
                    RecordCodecBuilder.of(f5.getter, mc5),
                    RecordCodecBuilder.of(f6.getter, mc6),
                    RecordCodecBuilder.of(f7.getter, mc7)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7));
        }
    }

    public static final class Group8<A, F1, F2, F3, F4, F5, F6, F7, F8> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4;
        private final FieldRef<A, F5> f5;
        private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7;
        private final FieldRef<A, F8> f8;
        private Function8<F1, F2, F3, F4, F5, F6, F7, F8, A> ctor;

        Group8(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2,
               FieldRef<A, F3> f3, FieldRef<A, F4> f4, FieldRef<A, F5> f5,
               FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
            this.f4 = f4;
            this.f5 = f5;
            this.f6 = f6;
            this.f7 = f7;
            this.f8 = f8;
        }

        public Group8<A, F1, F2, F3, F4, F5, F6, F7, F8> apply(Instance<A> i,
                                                               Function8<F1, F2, F3, F4, F5, F6, F7, F8, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F9> Group9<A, F1, F2, F3, F4, F5, F6, F7, F8, F9> and(FieldRef<A, F9> f) {
            return new Group9<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            MapCodec<F3> mc3 = mapCodecFor(f3);
            MapCodec<F4> mc4 = mapCodecFor(f4);
            MapCodec<F5> mc5 = mapCodecFor(f5);
            MapCodec<F6> mc6 = mapCodecFor(f6);
            MapCodec<F7> mc7 = mapCodecFor(f7);
            MapCodec<F8> mc8 = mapCodecFor(f8);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply8(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2),
                    RecordCodecBuilder.of(f3.getter, mc3),
                    RecordCodecBuilder.of(f4.getter, mc4),
                    RecordCodecBuilder.of(f5.getter, mc5),
                    RecordCodecBuilder.of(f6.getter, mc6),
                    RecordCodecBuilder.of(f7.getter, mc7),
                    RecordCodecBuilder.of(f8.getter, mc8)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8));
        }
    }

    public static final class Group9<A, F1, F2, F3, F4, F5, F6, F7, F8, F9> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1;
        private final FieldRef<A, F2> f2;
        private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4;
        private final FieldRef<A, F5> f5;
        private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7;
        private final FieldRef<A, F8> f8;
        private final FieldRef<A, F9> f9;
        private Function9<F1, F2, F3, F4, F5, F6, F7, F8, F9, A> ctor;

        Group9(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2,
               FieldRef<A, F3> f3, FieldRef<A, F4> f4, FieldRef<A, F5> f5,
               FieldRef<A, F6> f6, FieldRef<A, F7> f7, FieldRef<A, F8> f8,
               FieldRef<A, F9> f9) {
            this.instance = instance;
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
            this.f4 = f4;
            this.f5 = f5;
            this.f6 = f6;
            this.f7 = f7;
            this.f8 = f8;
            this.f9 = f9;
        }

        public Group9<A, F1, F2, F3, F4, F5, F6, F7, F8, F9> apply(Instance<A> i,
                Function9<F1, F2, F3, F4, F5, F6, F7, F8, F9, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F10> Group10<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10> and(FieldRef<A, F10> f) {
            return new Group10<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f);
        }

        @Override
        public SchemaCodec<A> build() {
            MapCodec<F1> mc1 = mapCodecFor(f1);
            MapCodec<F2> mc2 = mapCodecFor(f2);
            MapCodec<F3> mc3 = mapCodecFor(f3);
            MapCodec<F4> mc4 = mapCodecFor(f4);
            MapCodec<F5> mc5 = mapCodecFor(f5);
            MapCodec<F6> mc6 = mapCodecFor(f6);
            MapCodec<F7> mc7 = mapCodecFor(f7);
            MapCodec<F8> mc8 = mapCodecFor(f8);
            MapCodec<F9> mc9 = mapCodecFor(f9);
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.apply9(
                    ctor,
                    RecordCodecBuilder.of(f1.getter, mc1),
                    RecordCodecBuilder.of(f2.getter, mc2),
                    RecordCodecBuilder.of(f3.getter, mc3),
                    RecordCodecBuilder.of(f4.getter, mc4),
                    RecordCodecBuilder.of(f5.getter, mc5),
                    RecordCodecBuilder.of(f6.getter, mc6),
                    RecordCodecBuilder.of(f7.getter, mc7),
                    RecordCodecBuilder.of(f8.getter, mc8),
                    RecordCodecBuilder.of(f9.getter, mc9)
            ));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9));
        }
    }

    public static final class Group10<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10;
        private Function10<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, A> ctor;

        Group10(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5;
            this.f6 = f6; this.f7 = f7; this.f8 = f8; this.f9 = f9; this.f10 = f10;
        }

        public Group10<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10> apply(Instance<A> i,
                Function10<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F11> Group11<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11> and(FieldRef<A, F11> f) {
            return new Group11<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.group(
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10));
        }
    }

    public static final class Group11<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11;
        private Function11<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, A> ctor;

        Group11(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6;
            this.f7 = f7; this.f8 = f8; this.f9 = f9; this.f10 = f10; this.f11 = f11;
        }

        public Group11<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11> apply(Instance<A> i,
                Function11<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F12> Group12<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12> and(FieldRef<A, F12> f) {
            return new Group12<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.group(
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11));
        }
    }

    public static final class Group12<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private Function12<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, A> ctor;

        Group12(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6;
            this.f7 = f7; this.f8 = f8; this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12;
        }

        public Group12<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12> apply(Instance<A> i,
                Function12<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F13> Group13<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13> and(FieldRef<A, F13> f) {
            return new Group13<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.group(
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12));
        }
    }

    public static final class Group13<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13;
        private Function13<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, A> ctor;

        Group13(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7;
            this.f8 = f8; this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13;
        }

        public Group13<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13> apply(Instance<A> i,
                Function13<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F14> Group14<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14> and(FieldRef<A, F14> f) {
            return new Group14<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.group(
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13));
        }
    }

    public static final class Group14<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14;
        private Function14<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, A> ctor;

        Group14(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7;
            this.f8 = f8; this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14;
        }

        public Group14<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14> apply(Instance<A> i,
                Function14<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F15> Group15<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15> and(FieldRef<A, F15> f) {
            return new Group15<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.group(
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14));
        }
    }

    public static final class Group15<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14; private final FieldRef<A, F15> f15;
        private Function15<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, A> ctor;

        Group15(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7; this.f8 = f8;
            this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14; this.f15 = f15;
        }

        public Group15<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15> apply(Instance<A> i,
                Function15<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        /** Append one more field, widening to the next arity. */
        public <F16> Group16<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16> and(FieldRef<A, F16> f) {
            return new Group16<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.group(
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14)),
                    RecordCodecBuilder.of(f15.getter, mapCodecFor(f15))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15));
        }
    }

    public static final class Group16<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14; private final FieldRef<A, F15> f15;
        private final FieldRef<A, F16> f16;
        private Function16<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, A> ctor;

        Group16(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15,
                FieldRef<A, F16> f16) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7; this.f8 = f8;
            this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14; this.f15 = f15; this.f16 = f16;
        }

        public Group16<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16> apply(Instance<A> i,
                Function16<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        /** Append one more field, widening to the next arity. */
        public <F17> Group17<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17> and(FieldRef<A, F17> f) {
            return new Group17<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> i.group(
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14)),
                    RecordCodecBuilder.of(f15.getter, mapCodecFor(f15)),
                    RecordCodecBuilder.of(f16.getter, mapCodecFor(f16))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16));
        }
    }

    public static final class Group17<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14; private final FieldRef<A, F15> f15;
        private final FieldRef<A, F16> f16; private final FieldRef<A, F17> f17;
        private BiggerCodecs.Function17<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, A> ctor;

        Group17(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15,
                FieldRef<A, F16> f16, FieldRef<A, F17> f17) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7; this.f8 = f8;
            this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14; this.f15 = f15; this.f16 = f16;
            this.f17 = f17;
        }

        public Group17<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17> apply(Instance<A> i,
                BiggerCodecs.Function17<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F18> Group18<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18> and(FieldRef<A, F18> f) {
            return new Group18<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> BiggerCodecs.group(i,
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14)),
                    RecordCodecBuilder.of(f15.getter, mapCodecFor(f15)),
                    RecordCodecBuilder.of(f16.getter, mapCodecFor(f16)),
                    RecordCodecBuilder.of(f17.getter, mapCodecFor(f17))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17));
        }
    }

    public static final class Group18<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14; private final FieldRef<A, F15> f15;
        private final FieldRef<A, F16> f16; private final FieldRef<A, F17> f17; private final FieldRef<A, F18> f18;
        private BiggerCodecs.Function18<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, A> ctor;

        Group18(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15,
                FieldRef<A, F16> f16, FieldRef<A, F17> f17, FieldRef<A, F18> f18) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7; this.f8 = f8;
            this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14; this.f15 = f15; this.f16 = f16;
            this.f17 = f17; this.f18 = f18;
        }

        public Group18<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18> apply(Instance<A> i,
                BiggerCodecs.Function18<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F19> Group19<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19> and(FieldRef<A, F19> f) {
            return new Group19<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> BiggerCodecs.group(i,
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14)),
                    RecordCodecBuilder.of(f15.getter, mapCodecFor(f15)),
                    RecordCodecBuilder.of(f16.getter, mapCodecFor(f16)),
                    RecordCodecBuilder.of(f17.getter, mapCodecFor(f17)),
                    RecordCodecBuilder.of(f18.getter, mapCodecFor(f18))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18));
        }
    }

    public static final class Group19<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14; private final FieldRef<A, F15> f15;
        private final FieldRef<A, F16> f16; private final FieldRef<A, F17> f17; private final FieldRef<A, F18> f18;
        private final FieldRef<A, F19> f19;
        private BiggerCodecs.Function19<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, A> ctor;

        Group19(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15,
                FieldRef<A, F16> f16, FieldRef<A, F17> f17, FieldRef<A, F18> f18, FieldRef<A, F19> f19) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7; this.f8 = f8;
            this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14; this.f15 = f15; this.f16 = f16;
            this.f17 = f17; this.f18 = f18; this.f19 = f19;
        }

        public Group19<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19> apply(Instance<A> i,
                BiggerCodecs.Function19<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F20> Group20<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20> and(FieldRef<A, F20> f) {
            return new Group20<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> BiggerCodecs.group(i,
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14)),
                    RecordCodecBuilder.of(f15.getter, mapCodecFor(f15)),
                    RecordCodecBuilder.of(f16.getter, mapCodecFor(f16)),
                    RecordCodecBuilder.of(f17.getter, mapCodecFor(f17)),
                    RecordCodecBuilder.of(f18.getter, mapCodecFor(f18)),
                    RecordCodecBuilder.of(f19.getter, mapCodecFor(f19))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19));
        }
    }

    public static final class Group20<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14; private final FieldRef<A, F15> f15;
        private final FieldRef<A, F16> f16; private final FieldRef<A, F17> f17; private final FieldRef<A, F18> f18;
        private final FieldRef<A, F19> f19; private final FieldRef<A, F20> f20;
        private BiggerCodecs.Function20<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, A> ctor;

        Group20(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15,
                FieldRef<A, F16> f16, FieldRef<A, F17> f17, FieldRef<A, F18> f18, FieldRef<A, F19> f19,
                FieldRef<A, F20> f20) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7; this.f8 = f8;
            this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14; this.f15 = f15; this.f16 = f16;
            this.f17 = f17; this.f18 = f18; this.f19 = f19; this.f20 = f20;
        }

        public Group20<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20> apply(Instance<A> i,
                BiggerCodecs.Function20<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        public <F21> Group21<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, F21> and(FieldRef<A, F21> f) {
            return new Group21<>(instance, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f);
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.create(i -> BiggerCodecs.group(i,
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14)),
                    RecordCodecBuilder.of(f15.getter, mapCodecFor(f15)),
                    RecordCodecBuilder.of(f16.getter, mapCodecFor(f16)),
                    RecordCodecBuilder.of(f17.getter, mapCodecFor(f17)),
                    RecordCodecBuilder.of(f18.getter, mapCodecFor(f18)),
                    RecordCodecBuilder.of(f19.getter, mapCodecFor(f19)),
                    RecordCodecBuilder.of(f20.getter, mapCodecFor(f20))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20));
        }
    }

    public static final class Group21<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, F21> implements Group<A> {
        private final Instance<A> instance;
        private final FieldRef<A, F1> f1; private final FieldRef<A, F2> f2; private final FieldRef<A, F3> f3;
        private final FieldRef<A, F4> f4; private final FieldRef<A, F5> f5; private final FieldRef<A, F6> f6;
        private final FieldRef<A, F7> f7; private final FieldRef<A, F8> f8; private final FieldRef<A, F9> f9;
        private final FieldRef<A, F10> f10; private final FieldRef<A, F11> f11; private final FieldRef<A, F12> f12;
        private final FieldRef<A, F13> f13; private final FieldRef<A, F14> f14; private final FieldRef<A, F15> f15;
        private final FieldRef<A, F16> f16; private final FieldRef<A, F17> f17; private final FieldRef<A, F18> f18;
        private final FieldRef<A, F19> f19; private final FieldRef<A, F20> f20; private final FieldRef<A, F21> f21;
        private BiggerCodecs.Function21<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, F21, A> ctor;

        Group21(Instance<A> instance, FieldRef<A, F1> f1, FieldRef<A, F2> f2, FieldRef<A, F3> f3,
                FieldRef<A, F4> f4, FieldRef<A, F5> f5, FieldRef<A, F6> f6, FieldRef<A, F7> f7,
                FieldRef<A, F8> f8, FieldRef<A, F9> f9, FieldRef<A, F10> f10, FieldRef<A, F11> f11,
                FieldRef<A, F12> f12, FieldRef<A, F13> f13, FieldRef<A, F14> f14, FieldRef<A, F15> f15,
                FieldRef<A, F16> f16, FieldRef<A, F17> f17, FieldRef<A, F18> f18, FieldRef<A, F19> f19,
                FieldRef<A, F20> f20, FieldRef<A, F21> f21) {
            this.instance = instance;
            this.f1 = f1; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5; this.f6 = f6; this.f7 = f7; this.f8 = f8;
            this.f9 = f9; this.f10 = f10; this.f11 = f11; this.f12 = f12; this.f13 = f13; this.f14 = f14; this.f15 = f15; this.f16 = f16;
            this.f17 = f17; this.f18 = f18; this.f19 = f19; this.f20 = f20; this.f21 = f21;
        }

        public Group21<A, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, F21> apply(Instance<A> i,
                BiggerCodecs.Function21<F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18, F19, F20, F21, A> ctor) {
            checkInstance(this.instance, i);
            this.ctor = ctor;
            return this;
        }

        @Override
        public SchemaCodec<A> build() {
            Codec<A> codec = RecordCodecBuilder.<A>create(i -> BiggerCodecs.group(i,
                    RecordCodecBuilder.of(f1.getter, mapCodecFor(f1)),
                    RecordCodecBuilder.of(f2.getter, mapCodecFor(f2)),
                    RecordCodecBuilder.of(f3.getter, mapCodecFor(f3)),
                    RecordCodecBuilder.of(f4.getter, mapCodecFor(f4)),
                    RecordCodecBuilder.of(f5.getter, mapCodecFor(f5)),
                    RecordCodecBuilder.of(f6.getter, mapCodecFor(f6)),
                    RecordCodecBuilder.of(f7.getter, mapCodecFor(f7)),
                    RecordCodecBuilder.of(f8.getter, mapCodecFor(f8)),
                    RecordCodecBuilder.of(f9.getter, mapCodecFor(f9)),
                    RecordCodecBuilder.of(f10.getter, mapCodecFor(f10)),
                    RecordCodecBuilder.of(f11.getter, mapCodecFor(f11)),
                    RecordCodecBuilder.of(f12.getter, mapCodecFor(f12)),
                    RecordCodecBuilder.of(f13.getter, mapCodecFor(f13)),
                    RecordCodecBuilder.of(f14.getter, mapCodecFor(f14)),
                    RecordCodecBuilder.of(f15.getter, mapCodecFor(f15)),
                    RecordCodecBuilder.of(f16.getter, mapCodecFor(f16)),
                    RecordCodecBuilder.of(f17.getter, mapCodecFor(f17)),
                    RecordCodecBuilder.of(f18.getter, mapCodecFor(f18)),
                    RecordCodecBuilder.of(f19.getter, mapCodecFor(f19)),
                    RecordCodecBuilder.of(f20.getter, mapCodecFor(f20)),
                    RecordCodecBuilder.of(f21.getter, mapCodecFor(f21))
            ).apply(i, ctor));
            return SchemaCodec.lazy(codec, () -> buildSchema(instance.type, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21));
        }
    }

    public record FieldRef<A, F>(String name, @Nullable SchemaCodec<F> codec, boolean optional,
                                 @Nullable F defaultValue, Function<A, F> getter,
                                 @Nullable MapCodec<F> mapCodecOverride,
                                 @Nullable SchemaCodec<?> innerCodec) {
        public FieldRef(String name, SchemaCodec<F> codec, boolean optional,
                        @Nullable F defaultValue, Function<A, F> getter) {
            this(name, codec, optional, defaultValue, getter, null, null);
        }
    }
}
