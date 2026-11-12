# CodecUI

The problem this small project aim to solve is the following: 
given an arbitrary Codec object how do I construct a user interface to edit its property? 
The mod aims to solve this with the introduction of lots of mixin hacks and the use of a Schema & SchemaCodec concept


This project is open source and you are welcome to include in your mods.

Please dont clone this code in your mods as doing so will not make your codecs available to other mods using codecui.

Codec UI purpose is turning any DataFixerUpper **`Codec`** into an editable **`Schema`**: declare the edit surface
explicitly, or let the bundled inference engine derive one from an existing codec - then render
your own editor UI from it. Ships the schema vocabulary **and** the resolver; the UI is yours.


## How to add

Published to the [somethingcatchy Nexus](https://registry.somethingcatchy.net/#browse/browse:maven-public).
You can JIJ it as such:

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

**Inferred schema mode**:
Given any codec just use SchemaCodec.wrap() and chances are it will work out of the box already

```java
SchemaCodec<Biome> wrapped = SchemaCodec.wrap(Biome.DIRECT_CODEC);
Schema<Biome> schema = wrapped.schema();   // feed this to your own editor UI
```

**Explicit schema mode**:
Declare your codec as a SchemaCodec. SchemaCodec subclasses Codec and as such you can use it as you would normally.
SchemaCodecs class contains all your utilities that CodecUtil and Codec class normally contain. Check them out.

```java
static final SchemaCodec<MyThing> CODEC = SchemaRecord.create(MyThing.class, i -> i.group(
        i.field("name", SchemaCodecs.STRING, MyThing::name),
        i.optional("count", SchemaCodecs.intRange(0, 64), 1, MyThing::count)
).apply(i, MyThing::new));
```

## Teaching the engine

For codecs which arent introspected automatically ( such as opaque `Codec.of(enc, dec)` wrappers, custom combinators),
you can register the missing knowledge once at init:

```java
SchemaCodecs.registerCompanion(SomeCodec.INSTANCE, new Schema.Color(false));  // hand-made schema for one codec
SchemaCodecs.registerHandler(handler);                                        // structural handler for a whole class of codecs
SchemaCodecs.registerDispatchKeys(MyType.class, keys, MyType::codec, MyType::name);
```

## Building a GUI

SchemaCodec provides a Schema. A `Schema` is merely a sealed tree. All an editor has to do is switch over it:

```java
switch (MyThing.CODEC.schema()) {
    case Schema.Bool b -> addCheckbox();
    case Schema.IntRange i -> addSlider(i.min(), i.max());
    case Schema.Record<?> r -> r.fields().forEach(f -> addLabel(f.name()));
    default -> addJsonBox();
}
```
## A simple example

We'll build a simple codec for a spell mod and provide a config screen for it.

```java
public record Spell(String name, int manaCost) {
    public static final SchemaCodec<Spell> CODEC = SchemaRecord.create(Spell.class, i -> i.group(
            i.field("name", SchemaCodecs.STRING, Spell::name),
            i.optional("mana_cost", SchemaCodecs.intRange(0, 100), 10, Spell::manaCost)
    ).apply(i, Spell::new));
}
```
CODEC is still a normal codec so you use it as you normally would.

To build a UI we simply switch over its schema object as such (only using non exhaustive switch as we know the resulting schema only has these primitive but ideally you should cover them all.)

```java
Schema.Record<Spell> schema = (Schema.Record<Spell>) Spell.CODEC.schema();
for (Schema.Field<Spell, ?> f : schema.fields()) {
    switch (f.schema()) {
        case Schema.Str s -> addTextBox(f.name());                  // name
        case Schema.IntRange r -> addSlider(f.name(), r.min(), r.max()); // mana_cost, 0-100
        default -> addJsonBox(f.name());
    }
}
```
as the otput of your UI simply produce your new Spell object which you can put back ina  Json using the Spell.CODEC itself.



## Companion Schemas
For rare cases when you cant construct a SchemaCodec and have to rely on an existing Codec you don't own that isnt inferred correctly, attack a companion schema to it.

```java
SchemaCodecs.registerCompanion(ThirdPartyMod.HEX_COLOR_CODEC, new Schema.Color(false, true));
```
