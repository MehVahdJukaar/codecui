package net.mehvahdjukaar.codecui;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

public class SchemaContext {
    private static RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    public static RegistryAccess getRegistries() {
        return SchemaContext.registries;
    }

    // called from the loader entrypoints whenever tags are (re)loaded
    public static void update(RegistryAccess registries) {
        SchemaContext.registries = registries;
    }
}
