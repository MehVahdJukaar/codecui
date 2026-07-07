# CodecUI

A small, dependency-free API that lets a mod **declare an edit surface for its own
DataFixerUpper `Codec`s**, so a CodecUI-aware editor can render them. No vanilla-codec
inference, no UI — just the declaration vocabulary.

Multi-loader (Fabric + NeoForge), Minecraft 1.21.11. Depends on nothing beyond Minecraft +
DataFixerUpper.

## What a consuming mod uses

- **`Schema<A>`** — the sealed ADT describing an edit surface (Bool, IntRange, Str,
  ResourceId, Enum, Record, ListOf, MapOf, AnyOf, OneOf, PairOf, plus `Opaque`/`Custom`
  escape hatches).
- **`SchemaCodec<A> extends Codec<A>`** — a codec paired with its `Schema`. Drop-in for an
  existing `Codec` field; the editor reads `.schema()` off it.
- **`SchemaMapCodec<A>`** — the `MapCodec` counterpart (dispatch sub-codecs).
- **`SchemaRecord` / `SchemaRecordBuilder`** — `RecordCodecBuilder`-shaped DSLs that build a
  codec and its record schema together.
- **`SchemaCodecs`** — primitives (`INT`, `STRING`, `BOOL`, …) and combinators (`list`,
  `either`, `dispatch`, `withAlternative`, `registryEntry`, `colorRgb`, …).

### Declaring a codec

```java
static final SchemaCodec<MyThing> CODEC = SchemaRecord.create(MyThing.class, i -> i.group(
        i.field("name", SchemaCodecs.STRING, MyThing::name),
        i.optional("count", SchemaCodecs.intRange(0, 64), 1, MyThing::count)
).apply(i, MyThing::new));
```

`CODEC` is a real `Codec<MyThing>` — the wire format is unchanged — and a CodecUI editor can
generate a form for it from `CODEC.schema()`. A raw codec with no declared schema degrades to
a raw-JSON (`Schema.Opaque`) editor rather than being guessed at.
