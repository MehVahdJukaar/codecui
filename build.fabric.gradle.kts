@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.kikugie.loom-back-compat")
    id("dev.kikugie.postprocess.jsonlang")
}

val isUnobfuscated = stonecutter.eval(stonecutter.current.version, ">=26")

tasks.named<ProcessResources>("processResources") {
    fun prop(name: String) = project.property(name) as String

    val props = HashMap<String, String>().apply {
        this["version"] = "${prop("deps.minecraft")}-${prop("mod.version")}"
        this["minecraft"] = prop("mod.mc_dep_fabric")
        this["fabric_api_version"] = prop("deps.fabric-api")
        this["fabric_version"] = if (isUnobfuscated) prop("deps.fabric-loader") else "0.18"
        this["java"] = prop("deps.java")
        this["mod_accesswidener"] = "${prop("mod.id")}.${if (isUnobfuscated) "classtweaker" else "accesswidener"}"
        this["mod_id"] = prop("mod.id")
        this["mod_name"] = prop("mod.name")
        this["mod_description"] = prop("mod.description")
        this["mod_license"] = prop("mod.license")
        this["mod_authors"] = prop("mod.author")
        this["mod_homepage"] = prop("mod.homepage")
        this["mod_credits"] = prop("mod.credits")
        this["mod_github"] = prop("mod.github")
    }

    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
        expand(props)
    }

    val mixin = HashMap<String, String>().apply {
        this["java"] = "JAVA_${prop("deps.java")}"
    }

    filesMatching(listOf("codecui.mixins.json")) {
        expand(mixin)
    }
}

loom {
    if (isUnobfuscated) accessWidenerPath = rootProject.file("src/main/resources/${property("mod.id")}.classtweaker")
    else accessWidenerPath = rootProject.file("src/main/resources/${property("mod.id")}.accesswidener")
}

version = "${property("deps.minecraft")}-${property("mod.version")}-fabric"
base.archivesName = property("mod.archives_base") as String

jsonlang {
    languageDirectories = listOf("assets/${property("mod.id")}/lang")
    prettyPrint = true
}

repositories {
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
        name = "Architectury"
        url = uri("https://maven.architectury.dev/")
        content {
            includeGroup("dev.architectury")
        }
    }
    maven {
        name = "Jitpack"
        url = uri("https://jitpack.io")
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

dependencies {
    minecraft("com.mojang:minecraft:${property("deps.minecraft")}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric-loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric-api")}")
}


configurations.all {
    resolutionStrategy {
        force("net.fabricmc:fabric-loader:${property("deps.fabric-loader")}")
        force("net.fabricmc:fabric-api:${property("deps.fabric-api")}")
    }
}


fabricApi {
    configureDataGeneration() {
        outputDirectory = file("$rootDir/src/main/generated")
        client = true
    }
}

tasks.named("processResources") {
    dependsOn(":${stonecutter.current.project}:stonecutterGenerate")
}

tasks {
    processResources {
        exclude("**/neoforge.mods.toml")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(loomx.modJar.map { it.archiveFile })
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

    replacements.string(current.parsed >= "26.1") {
        replace("PayloadTypeRegistry.playS2C()", "PayloadTypeRegistry.clientboundPlay()")
    }

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
