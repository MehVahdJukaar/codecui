# CodecUI

Turn any DataFixerUpper **`Codec`** into an editable **`Schema`** — declare the edit surface
explicitly, or let the bundled inference engine derive one from an existing codec — then render
your own editor UI from it. Ships the schema vocabulary **and** the resolver; the UI is yours.

Multi-loader (Fabric + NeoForge), Minecraft **1.21.11** (also a `1.21.1` line). Depends on
nothing beyond Minecraft + DataFixerUpper.

## Adding it

Published to [muon.rip](https://maven.muon.rip/releases). Jar-in-jar it so the library ships
inside your mod:

```gradle
repositories {
    maven { url "https://maven.muon.rip/releases" }
}

dependencies {
    // Fabric (Loom)
    modImplementation "net.mehvahdjukaar:codecui-fabric:1.21.11-0.2.0"
    include           "net.mehvahdjukaar:codecui-fabric:1.21.11-0.2.0"

    // NeoForge (ModDevGradle)
    implementation "net.mehvahdjukaar:codecui-neoforge:1.21.11-0.2.0"
    jarJar         "net.mehvahdjukaar:codecui-neoforge:1.21.11-0.2.0"
}
```

## Quick start

**Infer a schema from any codec** — the engine walks the codec graph (records, lists, dispatch,
registries, enums, …); anything it can't introspect degrades to a raw-JSON `Schema.Opaque`:

```java
SchemaCodec<Biome> wrapped = SchemaCodec.wrap(Biome.DIRECT_CODEC);
Schema<Biome> schema = wrapped.schema();   // feed this to your own editor UI
```

**Or declare the edit surface explicitly** — `CODEC` stays a real `Codec<MyThing>` (wire format
unchanged) and also carries its schema:

```java
static final SchemaCodec<MyThing> CODEC = SchemaRecord.create(MyThing.class, i -> i.group(
        i.field("name", SchemaCodecs.STRING, MyThing::name),
        i.optional("count", SchemaCodecs.intRange(0, 64), 1, MyThing::count)
).apply(i, MyThing::new));
```

`SchemaCodecs` also has the primitives (`INT`, `STRING`, `BOOL`, …) and combinators
(`list`, `either`, `dispatch`, `withAlternative`, `registryEntry`, `colorRgb`, `map`, `pair`, …).

## Teaching the engine

For codecs it can't introspect (opaque `Codec.of(enc, dec)` wrappers, custom combinators),
register the missing knowledge once at init:

```java
SchemaCodecs.registerCompanion(SomeCodec.INSTANCE, new Schema.Color(false));  // hand-made schema for one codec
SchemaCodecs.registerHandler(handler);                                        // structural handler for a whole class of codecs
SchemaCodecs.registerDispatchKeys(MyType.class, keys, MyType::codec, MyType::name);
```

## What a consuming mod uses

- **`Schema<A>`** — the sealed ADT describing an edit surface (Bool, IntRange, Str,
  ResourceId, Enum, Record, ListOf, MapOf, AnyOf, OneOf, PairOf, plus `Opaque`/`Custom`
  escape hatches).
- **`SchemaCodec<A> extends Codec<A>`** — a codec paired with its `Schema`. Drop-in for an
  existing `Codec` field; the editor reads `.schema()` off it. `SchemaCodec.wrap(anyCodec)`
  runs the inference engine.
- **`SchemaMapCodec<A>`** — the `MapCodec` counterpart (dispatch sub-codecs).
- **`SchemaRecord` / `SchemaRecordBuilder`** — `RecordCodecBuilder`-shaped DSLs that build a
  codec and its record schema together.
- **`SchemaCodecs`** — primitives, combinators, and the engine extension points
  (`registerCompanion` / `registerHandler` / `registerDispatchKeys`).
