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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Schema-aware mirror of {@link RecordCodecBuilder}: declare fields, then call
 * {@code buildN} with a constructor reference to obtain a {@link SchemaCodec}.
 */
public final class SchemaRecordBuilder<A> {

    private final Class<A> type;
    private final List<Field<A, ?>> fields = new ArrayList<>();

    private SchemaRecordBuilder(Class<A> type) {
        this.type = type;
    }

    public static <A> SchemaRecordBuilder<A> of(Class<A> type) {
        return new SchemaRecordBuilder<>(type);
    }

    public <F> Field<A, F> field(String name, SchemaCodec<F> codec, Function<A, F> getter) {
        Field<A, F> f = new Field<>(name, codec, false, null, getter);
        fields.add(f);
        return f;
    }

    public <F> Field<A, F> optional(String name, SchemaCodec<F> codec, F defaultValue, Function<A, F> getter) {
        Field<A, F> f = new Field<>(name, codec, true, defaultValue, getter);
        fields.add(f);
        return f;
    }

    private <F> MapCodec<F> mapCodecFor(Field<A, F> field) {
        if (field.optional && field.defaultValue != null) {
            return field.codec.optionalFieldOf(field.name, field.defaultValue);
        }
        // required (or optional with null default - shouldn't really happen here)
        return field.codec.fieldOf(field.name);
    }

    @SafeVarargs
    private void validate(Field<A, ?>... fs) {
        if (fs.length != fields.size()) {
            throw new IllegalStateException("SchemaRecordBuilder<" + type.getSimpleName()
                    + ">: build expected " + fields.size() + " fields, got " + fs.length);
        }
        for (Field<A, ?> f : fs) {
            boolean found = false;
            for (Field<A, ?> reg : fields) {
                if (reg == f) { found = true; break; }
            }
            if (!found) {
                throw new IllegalStateException("Field '" + f.name + "' was not registered on this builder");
            }
        }
    }

    private Schema<A> buildSchema() {
        List<Schema.Field<A, ?>> schemaFields = new ArrayList<>(fields.size());
        for (Field<A, ?> f : fields) {
            schemaFields.add(toSchemaField(f));
        }
        return new Schema.Record<>(type, schemaFields);
    }

    private static <A, F> Schema.Field<A, F> toSchemaField(Field<A, F> f) {
        return new Schema.Field<>(f.name, f.codec.schema(), f.optional, f.defaultValue);
    }

    public <F1> SchemaCodec<A> build1(Function<F1, A> ctor, Field<A, F1> f1) {
        validate(f1);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        // Arity 1: MapCodec.xmap is the cleanest path.
        var codec = mc1.xmap(ctor, f1.getter).codec();
        return SchemaCodec.of(codec, buildSchema());
    }

    public <F1, F2> SchemaCodec<A> build2(BiFunction<F1, F2, A> ctor, Field<A, F1> f1, Field<A, F2> f2) {
        validate(f1, f2);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        var codec = RecordCodecBuilder.<A>create(i -> i.apply2(
                ctor,
                RecordCodecBuilder.of(f1.getter, mc1),
                RecordCodecBuilder.of(f2.getter, mc2)
        ));
        return SchemaCodec.of(codec, buildSchema());
    }

    public <F1, F2, F3> SchemaCodec<A> build3(Function3<F1, F2, F3, A> ctor,
                                              Field<A, F1> f1, Field<A, F2> f2, Field<A, F3> f3) {
        validate(f1, f2, f3);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<F3> mc3 = mapCodecFor(f3);
        var codec = RecordCodecBuilder.<A>create(i -> i.apply3(
                ctor,
                RecordCodecBuilder.of(f1.getter, mc1),
                RecordCodecBuilder.of(f2.getter, mc2),
                RecordCodecBuilder.of(f3.getter, mc3)
        ));
        return SchemaCodec.of(codec, buildSchema());
    }

    public <F1, F2, F3, F4> SchemaCodec<A> build4(Function4<F1, F2, F3, F4, A> ctor,
                                                  Field<A, F1> f1, Field<A, F2> f2,
                                                  Field<A, F3> f3, Field<A, F4> f4) {
        validate(f1, f2, f3, f4);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<F3> mc3 = mapCodecFor(f3);
        MapCodec<F4> mc4 = mapCodecFor(f4);
        var codec = RecordCodecBuilder.<A>create(i -> i.apply4(
                ctor,
                RecordCodecBuilder.of(f1.getter, mc1),
                RecordCodecBuilder.of(f2.getter, mc2),
                RecordCodecBuilder.of(f3.getter, mc3),
                RecordCodecBuilder.of(f4.getter, mc4)
        ));
        return SchemaCodec.of(codec, buildSchema());
    }

    public <F1, F2, F3, F4, F5> SchemaCodec<A> build5(Function5<F1, F2, F3, F4, F5, A> ctor,
                                                      Field<A, F1> f1, Field<A, F2> f2, Field<A, F3> f3,
                                                      Field<A, F4> f4, Field<A, F5> f5) {
        validate(f1, f2, f3, f4, f5);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<F3> mc3 = mapCodecFor(f3);
        MapCodec<F4> mc4 = mapCodecFor(f4);
        MapCodec<F5> mc5 = mapCodecFor(f5);
        var codec = RecordCodecBuilder.<A>create(i -> i.apply5(
                ctor,
                RecordCodecBuilder.of(f1.getter, mc1),
                RecordCodecBuilder.of(f2.getter, mc2),
                RecordCodecBuilder.of(f3.getter, mc3),
                RecordCodecBuilder.of(f4.getter, mc4),
                RecordCodecBuilder.of(f5.getter, mc5)
        ));
        return SchemaCodec.of(codec, buildSchema());
    }

    public <F1, F2, F3, F4, F5, F6> SchemaCodec<A> build6(Function6<F1, F2, F3, F4, F5, F6, A> ctor,
                                                          Field<A, F1> f1, Field<A, F2> f2, Field<A, F3> f3,
                                                          Field<A, F4> f4, Field<A, F5> f5, Field<A, F6> f6) {
        validate(f1, f2, f3, f4, f5, f6);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<F3> mc3 = mapCodecFor(f3);
        MapCodec<F4> mc4 = mapCodecFor(f4);
        MapCodec<F5> mc5 = mapCodecFor(f5);
        MapCodec<F6> mc6 = mapCodecFor(f6);
        var codec = RecordCodecBuilder.<A>create(i -> i.apply6(
                ctor,
                RecordCodecBuilder.of(f1.getter, mc1),
                RecordCodecBuilder.of(f2.getter, mc2),
                RecordCodecBuilder.of(f3.getter, mc3),
                RecordCodecBuilder.of(f4.getter, mc4),
                RecordCodecBuilder.of(f5.getter, mc5),
                RecordCodecBuilder.of(f6.getter, mc6)
        ));
        return SchemaCodec.of(codec, buildSchema());
    }

    public <F1, F2, F3, F4, F5, F6, F7> SchemaCodec<A> build7(Function7<F1, F2, F3, F4, F5, F6, F7, A> ctor,
                                                              Field<A, F1> f1, Field<A, F2> f2, Field<A, F3> f3,
                                                              Field<A, F4> f4, Field<A, F5> f5, Field<A, F6> f6,
                                                              Field<A, F7> f7) {
        validate(f1, f2, f3, f4, f5, f6, f7);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<F3> mc3 = mapCodecFor(f3);
        MapCodec<F4> mc4 = mapCodecFor(f4);
        MapCodec<F5> mc5 = mapCodecFor(f5);
        MapCodec<F6> mc6 = mapCodecFor(f6);
        MapCodec<F7> mc7 = mapCodecFor(f7);
        var codec = RecordCodecBuilder.<A>create(i -> i.apply7(
                ctor,
                RecordCodecBuilder.of(f1.getter, mc1),
                RecordCodecBuilder.of(f2.getter, mc2),
                RecordCodecBuilder.of(f3.getter, mc3),
                RecordCodecBuilder.of(f4.getter, mc4),
                RecordCodecBuilder.of(f5.getter, mc5),
                RecordCodecBuilder.of(f6.getter, mc6),
                RecordCodecBuilder.of(f7.getter, mc7)
        ));
        return SchemaCodec.of(codec, buildSchema());
    }

    public <F1, F2, F3, F4, F5, F6, F7, F8> SchemaCodec<A> build8(Function8<F1, F2, F3, F4, F5, F6, F7, F8, A> ctor,
                                                                  Field<A, F1> f1, Field<A, F2> f2, Field<A, F3> f3,
                                                                  Field<A, F4> f4, Field<A, F5> f5, Field<A, F6> f6,
                                                                  Field<A, F7> f7, Field<A, F8> f8) {
        validate(f1, f2, f3, f4, f5, f6, f7, f8);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<F3> mc3 = mapCodecFor(f3);
        MapCodec<F4> mc4 = mapCodecFor(f4);
        MapCodec<F5> mc5 = mapCodecFor(f5);
        MapCodec<F6> mc6 = mapCodecFor(f6);
        MapCodec<F7> mc7 = mapCodecFor(f7);
        MapCodec<F8> mc8 = mapCodecFor(f8);
        var codec = RecordCodecBuilder.<A>create(i -> i.apply8(
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
        return SchemaCodec.of(codec, buildSchema());
    }

    // ---- MapCodec-producing variants, for use as dispatch sub-codecs. ----

    public <F1> SchemaMapCodec<A> buildMapCodec1(Function<F1, A> ctor, Field<A, F1> f1) {
        validate(f1);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<A> mapCodec = mc1.xmap(ctor, f1.getter);
        return SchemaMapCodec.of(mapCodec, buildSchema());
    }

    public <F1, F2> SchemaMapCodec<A> buildMapCodec2(BiFunction<F1, F2, A> ctor,
                                                     Field<A, F1> f1, Field<A, F2> f2) {
        validate(f1, f2);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<A> mapCodec = RecordCodecBuilder.mapCodec(i -> i.group(
                mc1.forGetter(f1.getter),
                mc2.forGetter(f2.getter)
        ).apply(i, ctor::apply));
        return SchemaMapCodec.of(mapCodec, buildSchema());
    }

    public <F1, F2, F3> SchemaMapCodec<A> buildMapCodec3(Function3<F1, F2, F3, A> ctor,
                                                         Field<A, F1> f1, Field<A, F2> f2, Field<A, F3> f3) {
        validate(f1, f2, f3);
        MapCodec<F1> mc1 = mapCodecFor(f1);
        MapCodec<F2> mc2 = mapCodecFor(f2);
        MapCodec<F3> mc3 = mapCodecFor(f3);
        MapCodec<A> mapCodec = RecordCodecBuilder.mapCodec(i -> i.group(
                mc1.forGetter(f1.getter),
                mc2.forGetter(f2.getter),
                mc3.forGetter(f3.getter)
        ).apply(i, ctor));
        return SchemaMapCodec.of(mapCodec, buildSchema());
    }

    public record Field<A, F>(String name, SchemaCodec<F> codec, boolean optional,
                              @Nullable F defaultValue, Function<A, F> getter) {}
}
