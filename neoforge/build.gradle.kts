plugins {
    id("com.possible-triangle.neoforge")
}

neoforge {
    dependOn(project(":common"))
    accessWidener(project(":common"))
}

// candlelight 1.2.x adds `candleEnsureAccessTransformerModuleMetadata`, a finalizer of
// GenerateModuleMetadata that reads the published module.json. The publishing convention
// disables Gradle module metadata (SKIPPED), so that file never exists and the task fails.
// Disable it — this mod publishes no .module file (as the 1.21.1/1.21.11 releases do).
tasks.matching { it.name == "candleEnsureAccessTransformerModuleMetadata" }.configureEach {
    enabled = false
}
