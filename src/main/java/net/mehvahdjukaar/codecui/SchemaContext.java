package net.mehvahdjukaar.codecui;

import net.minecraft.core.RegistryAccess;

public class SchemaContext {
    private static RegistryAccess registries = RegistryAccess.EMPTY;

    // Get the current RegistryAccess
    public static RegistryAccess getRegistries() {
        return SchemaContext.registries;
    }
    // Update whenever tags are read to store the current RegistryAccess
    public static void update(RegistryAccess registries) {
        SchemaContext.registries = registries;
    }
}
