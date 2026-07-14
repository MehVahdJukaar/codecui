plugins {
    id("net.mehvahdjukaar.candlelight") version "1.1.1" apply false
    id("dev.mixinmcp.decompile") version "0.9.0" apply false
}


subprojects {
    apply(plugin = "net.mehvahdjukaar.candlelight")
    apply(plugin = "dev.mixinmcp.decompile")
    apply(plugin = "maven-publish")

    dependencies {
        compileOnly("net.mehvahdjukaar:candlelight:1.1.1")
    }

    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = true
    }

    repositories {
        nexus()
    }

    upload {
        maven {
            nexus()
        }
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
    }
}
