plugins {
    id("com.possible-triangle.core")
    id("com.possible-triangle.common") apply false
    id("com.possible-triangle.fabric") apply false
    id("com.possible-triangle.neoforge") apply false
    id("net.mehvahdjukaar.candlelight") version "1.1.3" apply false
    id("dev.mixinmcp.decompile") version "0.9.0" apply false
}

mod {
    val mod_description: String by extra
    val mod_credits: String by extra
    val mod_license: String by extra
    val mod_homepage: String by extra
    val mod_github: String by extra
    val mod_authors: String by extra
    additional.add("mod_description", provider { mod_description })
    additional.add("mod_credits", provider { mod_credits })
    additional.add("mod_license", provider { mod_license })
    additional.add("mod_homepage", provider { mod_homepage })
    additional.add("mod_authors", provider { mod_authors })
    additional.add("mod_github", provider { mod_github })
}


subprojects {

    apply(plugin = "com.possible-triangle.core")
    apply(plugin = "net.mehvahdjukaar.candlelight")
    apply(plugin = "dev.mixinmcp.decompile")

    dependencies {
        compileOnly("net.mehvahdjukaar:candlelight:1.1.1")
    }

    repositories {
        nexus()
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "4000"))
    }

    repositories {
        // Standard repositories
        mavenLocal()
        mavenCentral()

        flatDir {
            dirs("mods")
        }

        maven { url = uri("https://jitpack.io") }

        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://maven.architectury.dev") }
        maven { url = uri("https://maven.parchmentmc.org") }
    }
}
