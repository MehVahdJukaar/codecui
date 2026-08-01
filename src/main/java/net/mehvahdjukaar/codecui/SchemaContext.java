package net.mehvahdjukaar.codecui;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

public class SchemaContext {
    private static RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    // Get the current RegistryAccess
    public static RegistryAccess getRegistries() {
        return SchemaContext.registries;
    }
    // Update whenever tags are read to store the current RegistryAccess
    public static void update(RegistryAccess registries) {
        SchemaContext.registries = registries;
    }
}
