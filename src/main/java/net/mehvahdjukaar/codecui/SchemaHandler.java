package net.mehvahdjukaar.codecui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

/**
 * Extension point for codec classes the resolver can't introspect on its own. Register with
 * SchemaCodecs.registerHandler. Handlers run after companions and mixin tags but before the built-in
 * structural tiers, so one can override a built-in guess for a whole class of codecs. Return null to
 * pass; the first non-null wins. Resolve inner codecs only through the given Resolver, or the
 * per-resolve cycle detection is lost.
 *
 * <pre>
 * SchemaCodecs.registerHandler((codec, resolver) -> {
 *     if (!(codec instanceof MySingleOrListCodec<?> sol)) return null;
 *     Schema<?> element = resolver.resolve(sol.elementCodec());
 *     return Schema.anyOf(
 *             Schema.option("single", element),
 *             Schema.option("list", new Schema.ListOf<>(element, 0, Integer.MAX_VALUE)));
 * });
 * </pre>
 */
@FunctionalInterface
public interface SchemaHandler {

    /** Returns a schema for the codec, or null to pass. */
    @Nullable Schema<?> tryResolve(Codec<?> codec, Resolver resolver);

    default @Nullable Schema<?> tryResolveMap(MapCodec<?> codec, Resolver resolver) {
        return null;
    }

    interface Resolver {
        <A> Schema<A> resolve(Codec<A> codec);

        <A> Schema<A> resolveMap(MapCodec<A> codec);
    }
}
