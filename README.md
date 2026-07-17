# CodecUI

Turn any DataFixerUpper **`Codec`** into an editable **`Schema`** - declare the edit surface
explicitly, or let the bundled inference engine derive one from an existing codec - then render
your own editor UI from it. Ships the schema vocabulary **and** the resolver; the UI is yours.

Multi-loader (Fabric + NeoForge), Minecraft **1.21.11** (also a `1.21.1` line). Depends on
nothing beyond Minecraft + DataFixerUpper.

## Adding it

Published to the [somethingcatchy Nexus](https://registry.somethingcatchy.net/#browse/browse:maven-public).
Jar-in-jar it so the library ships inside your mod:

```gradle
repositories {
    maven { url "https://registry.somethingcatchy.net/repository/maven-public/" }
}

dependencies {
    // Fabric (Loom)
    modImplementation "net.mehvahdjukaar:codecui-fabric:1.21.11-0.4.0"
    include           "net.mehvahdjukaar:codecui-fabric:1.21.11-0.4.0"

    // NeoForge (ModDevGradle)
    implementation "net.mehvahdjukaar:codecui-neoforge:1.21.11-0.4.0"
    jarJar         "net.mehvahdjukaar:codecui-neoforge:1.21.11-0.4.0"
}
```

## Quick start

**Infer a schema from any codec** - the engine walks the codec graph (records, lists, dispatch,
registries, enums, …); anything it can't introspect degrades to a raw-JSON `Schema.Opaque`:

```java
SchemaCodec<Biome> wrapped = SchemaCodec.wrap(Biome.DIRECT_CODEC);
Schema<Biome> schema = wrapped.schema();   // feed this to your own editor UI
```

**Or declare the edit surface explicitly** - `CODEC` stays a real `Codec<MyThing>` (wire format
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

- **`Schema<A>`** - the sealed ADT describing an edit surface (Bool, IntRange, Str,
  ResourceId, Enum, Record, ListOf, MapOf, AnyOf, OneOf, PairOf, plus `Opaque`/`Custom`
  escape hatches).
- **`SchemaCodec<A> extends Codec<A>`** - a codec paired with its `Schema`. Drop-in for an
  existing `Codec` field; the editor reads `.schema()` off it. `SchemaCodec.wrap(anyCodec)`
  runs the inference engine.
- **`SchemaMapCodec<A>`** - the `MapCodec` counterpart (dispatch sub-codecs).
- **`SchemaRecord` / `SchemaRecordBuilder`** - `RecordCodecBuilder`-shaped DSLs that build a
  codec and its record schema together.
- **`SchemaCodecs`** - primitives, combinators, and the engine extension points
  (`registerCompanion` / `registerHandler` / `registerDispatchKeys`).

## A complete example

A datapack-driven **spell** config that pulls in most of the library at once: a record with
optional/defaulted fields, a bounded number slider, a color picker, a registry dropdown, and a
**list of a sum type** (`Effect`) whose variants are picked from a dropdown - each variant a
schema-carrying `MapCodec` declared exactly once. The result is a plain `Codec<Spell>` (unchanged
wire format) that *also* hands your editor a fully structural `Schema<Spell>`.

```java
// ---- the mod's own domain types ----
public enum Rarity implements StringRepresentable {
    COMMON("common"), RARE("rare"), LEGENDARY("legendary");
    private final String id;
    Rarity(String id) { this.id = id; }
    @Override public String getSerializedName() { return id; }
    static final Codec<Rarity> CODEC = StringRepresentable.fromEnum(Rarity::values);
}

// A sum type: every effect is exactly one of these variants, tagged by type().
public sealed interface Effect { String type(); }
public record Damage(float amount, boolean fire)             implements Effect { public String type() { return "damage"; } }
public record Heal(float amount)                             implements Effect { public String type() { return "heal";   } }
public record Summon(EntityType<?> entity, int count)        implements Effect { public String type() { return "summon"; } }

public record Spell(String name, Rarity rarity, int manaCost, int glowColor,
                    SoundEvent castSound, List<Effect> effects) {}
```

```java
public final class SpellCodecs {

    // Each dispatch variant is a schema-carrying MapCodec - declared once, fields + schema together.
    private static SchemaMapCodec<Damage> damage() {
        var b = SchemaRecordBuilder.of(Damage.class);
        return b.buildMapCodec2(Damage::new,
                b.field   ("amount", SchemaCodecs.floatRange(0, 1000), Damage::amount),
                b.optional("fire",   SchemaCodecs.BOOL, false,         Damage::fire));
    }
    private static SchemaMapCodec<Heal> heal() {
        var b = SchemaRecordBuilder.of(Heal.class);
        return b.buildMapCodec1(Heal::new,
                b.field("amount", SchemaCodecs.floatRange(0, 1000), Heal::amount));
    }
    private static SchemaMapCodec<Summon> summon() {
        var b = SchemaRecordBuilder.of(Summon.class);
        return b.buildMapCodec2(Summon::new,
                b.field   ("entity", SchemaCodecs.registryEntry(Registries.ENTITY_TYPE,
                                        BuiltInRegistries.ENTITY_TYPE.byNameCodec()), Summon::entity),
                b.optional("count",  SchemaCodecs.intRange(1, 16), 1, Summon::count));
    }

    // Tagged-union codec + a Schema.OneOf variant picker, from the same three declarations.
    static final SchemaCodec<Effect> EFFECT = SchemaCodecs.dispatch("type", Effect::type,
            new LinkedHashMap<>() {{                 // insertion order = picker order
                put("damage", damage());
                put("heal",   heal());
                put("summon", summon());
            }});

    // The top-level record: one call builds the Codec<Spell> and its Schema<Spell> together.
    public static final SchemaCodec<Spell> CODEC = SchemaRecord.create(Spell.class, i -> i.group(
            i.field   ("name",       SchemaCodecs.STRING,                                     Spell::name),
            i.optional("rarity",     SchemaCodecs.enumeration(Rarity.CODEC,
                                        List.of(Rarity.values()), Rarity::getSerializedName),
                                     Rarity.COMMON,                                           Spell::rarity),
            i.optional("mana_cost",  SchemaCodecs.intRange(0, 100), 10,                       Spell::manaCost),
            i.optional("glow_color", SchemaCodecs.colorRgb(Codec.INT), 0xFFFFFF,             Spell::glowColor),
            i.field   ("cast_sound", SchemaCodecs.registryEntry(Registries.SOUND_EVENT,
                                        BuiltInRegistries.SOUND_EVENT.byNameCodec()),         Spell::castSound),
            i.field   ("effects",    SchemaCodecs.list(EFFECT),                               Spell::effects)
    ).apply(i, Spell::new));

    private SpellCodecs() {}
}
```

`SpellCodecs.CODEC` is a real `Codec<Spell>` - serialize with it as usual:

```java
Spell fireball = new Spell("Fireball", Rarity.RARE, 20, 0xFF5500,
        SoundEvents.FIRECHARGE_USE, List.of(new Damage(6f, true), new Summon(EntityType.ARMOR_STAND, 2)));

JsonElement json = SpellCodecs.CODEC.encodeStart(JsonOps.INSTANCE, fireball).getOrThrow();
```

…and it simultaneously carries the edit surface. Hand `.schema()` to a CodecUI-aware editor and,
with no extra work, you get: a text box for `name`; a `common / rare / legendary` dropdown; a `0–100`
slider for `mana_cost` (pre-filled to its default when absent); a color swatch for `glow_color`; a
searchable sound-event registry dropdown; and an add/remove list of `effects` where each row starts
with a **`damage / heal / summon`** variant picker that swaps in that variant's own fields.

```java
Schema<Spell> editable = SpellCodecs.CODEC.schema();   // feed this to your editor UI
```

**Didn't declare it?** `SchemaCodec.wrap(SpellCodecs.CODEC)` would infer the same structure from a
plain codec. When inference hits a codec it can't introspect - a third-party `Codec.of(enc, dec)`,
say - teach the engine once at init and every schema that nests it (declared *or* inferred) resolves
structurally instead of degrading to raw JSON:

```java
SchemaCodecs.registerCompanion(ThirdPartyCodecs.HEX_COLOR, new Schema.Color(false));
```
