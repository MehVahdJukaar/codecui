package net.mehvahdjukaar.codecui;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

public class SchemaContext {
    private static final RegistryAccess BUILTIN = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static RegistryAccess registries = BUILTIN;

    public static RegistryAccess getRegistries() {
        return SchemaContext.registries;
    }

    public static void update(RegistryAccess registries) {
        SchemaContext.registries = registries;
    }

    public static void reset() {
        SchemaContext.registries = BUILTIN;
    }
}
