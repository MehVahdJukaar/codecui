package net.mehvahdjukaar.codecui;
import net.mehvahdjukaar.codecui.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point for teaching the schema resolver about codec classes it can't introspect
 * on its own (hand-rolled {@code Codec.of(...)} implementations, exotic third-party
 * combinators, ...). Register via {@link SchemaResolvers#registerHandler}.
 *
 * <p>Handlers run for every codec being resolved, <em>after</em> the per-instance tag tiers
 * (companions registered with {@link SchemaResolvers#registerCompanion} and mixin-attached tags
 * always win) but <em>before</em> the built-in structural tiers and the reflective fallback —
 * so a handler can override a built-in guess for a whole class of codecs. Return {@code null}
 * to pass ("not mine"); the first handler returning non-null wins. Handlers should be fast:
 * typically an {@code instanceof} check followed by schema construction.</p>
 *
 * <p>Use the supplied {@link Resolver} to resolve inner codecs — never recurse into
 * schema resolution any other way, or per-resolve cycle detection is lost.</p>
 *
 * <p>Example — a handler for a custom "list or single element" codec:</p>
 * <pre>{@code
 * SchemaCodecs.registerHandler((codec, resolver) -> {
 *     if (!(codec instanceof MySingleOrListCodec<?> sol)) return null;
 *     Schema<?> element = resolver.resolve(sol.elementCodec());
 *     return Schema.anyOf(
 *             Schema.option("single", element),
 *             Schema.option("list", new Schema.ListOf<>(element, 0, Integer.MAX_VALUE)));
 * });
 * }</pre>
 */
@FunctionalInterface
public interface SchemaHandler {

    /** Attempt to produce a schema for the given codec; {@code null} to pass. */
    @Nullable Schema<?> tryResolve(Codec<?> codec, Resolver resolver);

    /** MapCodec counterpart; default passes. */
    default @Nullable Schema<?> tryResolveMap(MapCodec<?> codec, Resolver resolver) {
        return null;
    }

    /** Re-entrant view of the resolver for resolving inner codecs from within a handler. */
    interface Resolver {
        <A> Schema<A> resolve(Codec<A> codec);

        <A> Schema<A> resolveMap(MapCodec<A> codec);
    }
}
