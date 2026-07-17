package net.mehvahdjukaar.codecui;

import com.mojang.serialization.MapCodec;

public sealed interface SchemaMapCodec<A> permits SchemaMapCodec.SimpleSchemaMapCodec {

    MapCodec<A> mapCodec();

    Schema<A> schema();

    default SchemaCodec<A> asCodec() {
        return SchemaCodec.of(mapCodec().codec(), schema());
    }

    static <A> SchemaMapCodec<A> of(MapCodec<A> mapCodec, Schema<A> schema) {
        return new SimpleSchemaMapCodec<>(mapCodec, schema);
    }

    record SimpleSchemaMapCodec<A>(MapCodec<A> mapCodec, Schema<A> schema) implements SchemaMapCodec<A> {}
}
