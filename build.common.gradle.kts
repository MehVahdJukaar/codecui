import net.neoforged.nfrtgradle.CreateMinecraftArtifacts

plugins {
    id("net.neoforged.moddev")
    id("dev.kikugie.postprocess.jsonlang")
    id("com.possible-triangle.access") version "1.4.215"
}

val isUnobfuscated = stonecutter.eval(stonecutter.current.version, ">=26")

tasks.named<ProcessResources>("processResources") {
    fun prop(name: String) = project.property(name) as String

    val mixin = HashMap<String, String>().apply {
        this["java"] = "JAVA_${prop("deps.java")}"
    }

    filesMatching(listOf("codecui.mixins.json", "codecui-codec.mixins.json")) {
        expand(mixin)
    }
}

version = "${property("deps.minecraft")}-${property("mod.version")}-common"
base.archivesName = property("mod.archives_base") as String

jsonlang {
    languageDirectories = listOf("assets/${property("mod.id")}/lang")
    prettyPrint = true
}

repositories {
    maven {
        name = "Jitpack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "Sinytra"
        url = uri("https://maven.su5ed.dev/releases")
    }
    maven {
        name = "shedaniel (Cloth Config)"
        url = uri("https://maven.shedaniel.me/")
        content {
            includeGroupAndSubgroups("me.shedaniel")
        }
    }
    maven {
        name = "Terraformers (Mod Menu)"
        url = uri("https://maven.terraformersmc.com/releases/")
        content {
            includeGroupAndSubgroups("com.terraformersmc")
            includeGroupAndSubgroups("dev.emi")
        }
    }
    maven {
        name = "Wisp Forest Maven"
        url = uri("https://maven.wispforest.io/releases/")
        content {
            includeGroupAndSubgroups("io.wispforest")
        }
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroupAndSubgroups("maven.modrinth")
        }
    }
    maven {
        name = "WTHIT"
        url = uri("https://maven2.bai.lol")
        content {
            includeGroupAndSubgroups("mcp.mobius.waila")
            includeGroupAndSubgroups("lol.bai")
        }
    }
    maven {
        name = "Sisby Maven"
        url = uri("https://repo.sleeping.town/")
        content {
            includeGroupAndSubgroups("folk.sisby")
        }
    }
    maven {
        name = "Parchment Mappings"
        url = uri("https://maven.parchmentmc.org")
        content {
            includeGroupAndSubgroups("org.parchmentmc")
        }
    }
    maven {
        name = "Xander Maven"
        url = uri("https://maven.isxander.dev/releases")
        content {
            includeGroupAndSubgroups("dev.isxander")
            includeGroupAndSubgroups("org.quiltmc.parsers")
        }
    }
    maven {
        name = "Nucleoid Maven (Polymer/Trinkets)"
        url = uri("https://maven.nucleoid.xyz")
        content {
            includeGroupAndSubgroups("eu.pb4")
            includeGroupAndSubgroups("xyz.nucleoid")
        }
    }
    maven {
        name = "Fuzs Mod Resources"
        url = uri("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
        content {
            includeGroupAndSubgroups("fuzs")
        }
    }
    maven {
        name = "Kotlin For Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content {
            includeGroupAndSubgroups("thedarkcolour")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
              name = "Cassian's Maven"
              url = uri("https://maven.cassian.cc")
            }
        }
        filter {
            includeGroupAndSubgroups("cc.cassian")
        }
    }
    mavenCentral()
}

access {
    from = rootProject.file("src/main/resources/${property("mod.id")}.${if (isUnobfuscated) "classtweaker" else "accesswidener"}")
}

val output = layout.buildDirectory.file("accesstransformer.cfg").map { it.asFile }

project.tasks.withType<CreateMinecraftArtifacts> {
    dependsOn("transformAccessWidener")
}
project.tasks.named("copyAccessTransformersPublications") {
    dependsOn("transformAccessWidener")
}

neoForge {
    enable {
        neoFormVersion = "1.21-20240613.152323"
        // Disable recompilation for performance reasons.
        isDisableRecompilation = true
    }
    validateAccessTransformers = true
    accessTransformers {
        from(output)
        publish(output)
    }

    if (hasProperty("deps.parchment")) parchment {
        val (mc, ver) = (property("deps.parchment") as String).split(':')
        mappingsVersion = ver
        minecraftVersion = mc
    }

    runs {
        register("client") {
            gameDirectory = file("run/")
            client()
        }
        register("server") {
            gameDirectory = file("run/")
            server()
        }
    }

    mods {
        register("${property("mod.id")}") {
            sourceSet(sourceSets["main"])
        }
    }
    sourceSets["main"].resources.srcDir("src/main/generated")
}

dependencies {

}


tasks {
    processResources {
        exclude("**/fabric.mod.json", "**/*.accesswidener", "**/*.classtweaker", "**/neoforge.mods.toml", "**/mods.toml")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(jar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

java {
    withSourcesJar()
    val javaCompat = if (stonecutter.eval(stonecutter.current.version, ">=26")) {
        JavaVersion.VERSION_25
    } else if (stonecutter.eval(stonecutter.current.version, ">=1.20.5")) {
        JavaVersion.VERSION_21
    } else {
        JavaVersion.VERSION_17
    }
    sourceCompatibility = javaCompat
    targetCompatibility = javaCompat
}

stonecutter {
    val (version, loader) = current.project.split('-', limit = 2)
    properties.tags(version, loader)

    replacements.string(current.parsed >= "1.21.11") {
        replace("ResourceLocation", "Identifier")
        replace("net.minecraft.Util", "net.minecraft.util.Util")
        replace("net.minecraft.FileUtil", "net.minecraft.util.FileUtil")
        replace("org.jetbrains.annotations.Nullable", "org.jspecify.annotations.Nullable")
        replace("org.jetbrains.annotations.NotNull", "org.jspecify.annotations.NonNull")
        replace("@NotNull", "@NonNull")
    }

    replacements.string(current.parsed >= "1.21.4") {
        replace("net.minecraft.world.item.Tier", "net.minecraft.world.item.ToolMaterial")
        replace("Tiers.", "ToolMaterial.")
    }
}

val additionalVersionsStr = findProperty("publish.additionalVersions") as String?
val additionalVersions: List<String> = additionalVersionsStr
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()
