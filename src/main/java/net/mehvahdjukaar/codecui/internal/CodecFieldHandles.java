package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.CompoundListCodec;
import com.mojang.serialization.codecs.EitherMapCodec;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import com.mojang.serialization.codecs.PairCodec;
import com.mojang.serialization.codecs.PairMapCodec;
import com.mojang.serialization.codecs.SimpleMapCodec;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * VarHandles for the private fields of the concrete DFU/MC codec classes the resolver
 * introspects. Split out from {@link SchemaResolver} so the resolver reads as tier logic
 * rather than reflection plumbing; consumed via {@code import static} so call sites are
 * unchanged. Every handle is nullable — a failed lookup (DFU version drift, module access)
 * leaves it null and the corresponding tier branch is skipped.
 *
 * <p>net.minecraft.* registry/holder codecs are NOT here: they are read via their
 * (access-widened) fields directly, since string field names would not survive Fabric's
 * intermediary remap.</p>
 */
final class CodecFieldHandles {

    private CodecFieldHandles() {}


    static final @Nullable VarHandle PAIR_CODEC_FIRST;
    static final @Nullable VarHandle PAIR_CODEC_SECOND;

    static final @Nullable VarHandle OPTIONAL_FIELD_NAME;
    static final @Nullable VarHandle OPTIONAL_FIELD_ELEMENT;
    static final @Nullable VarHandle OPTIONAL_FIELD_LENIENT;

    static final @Nullable VarHandle PAIR_MAP_FIRST;
    static final @Nullable VarHandle PAIR_MAP_SECOND;

    static final @Nullable VarHandle KEY_DISPATCH_KEYCODEC;
    static final @Nullable VarHandle KEY_DISPATCH_TYPE;
    static final @Nullable VarHandle KEY_DISPATCH_DECODER;
    //? <1.21.11
    //static final @Nullable VarHandle KEY_DISPATCH_TYPEKEY;

    static final @Nullable VarHandle SIMPLE_MAP_KEYCODEC;
    static final @Nullable VarHandle SIMPLE_MAP_ELEMENT;
    static final @Nullable VarHandle SIMPLE_MAP_KEYS;

    static final @Nullable VarHandle RECURSIVE_WRAPPED;
    static final @Nullable Class<?> RECURSIVE_MAP_CLASS;
    static final @Nullable VarHandle RECURSIVE_MAP_WRAPPED;
    static final @Nullable VarHandle COMPOUND_LIST_KEY;
    static final @Nullable VarHandle COMPOUND_LIST_ELEMENT;
    static final @Nullable VarHandle EITHER_MAP_FIRST;
    static final @Nullable VarHandle EITHER_MAP_SECOND;

    static {
        VarHandle pf = null, ps = null;
        VarHandle ofn = null, ofe = null, ofl = null;
        VarHandle pmf = null, pms = null;
        VarHandle kdk = null, kdt = null, kdd = null/*? <1.21.11 {*//*, kdtk = null*//*?}*/;
        VarHandle smk = null, sme = null, sms = null;
        VarHandle rw = null, rmw = null;
        Class<?> rmc = null;
        VarHandle clk = null, cle = null;
        VarHandle emf = null, ems = null;
        try {
            var lookup = MethodHandles.privateLookupIn(PairCodec.class, MethodHandles.lookup());
            pf = lookup.findVarHandle(PairCodec.class, "first", Codec.class);
            ps = lookup.findVarHandle(PairCodec.class, "second", Codec.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(OptionalFieldCodec.class, MethodHandles.lookup());
            ofn = lookup.findVarHandle(OptionalFieldCodec.class, "name", String.class);
            ofe = lookup.findVarHandle(OptionalFieldCodec.class, "elementCodec", Codec.class);
            ofl = lookup.findVarHandle(OptionalFieldCodec.class, "lenient", boolean.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(PairMapCodec.class, MethodHandles.lookup());
            pmf = lookup.findVarHandle(PairMapCodec.class, "first", MapCodec.class);
            pms = lookup.findVarHandle(PairMapCodec.class, "second", MapCodec.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(KeyDispatchCodec.class, MethodHandles.lookup());
            // keyCodec's declared type differs across DFU versions: 1.21.11's DFU 9 stores the
            // already-fieldOf-wrapped MapCodec, 1.21.1's DFU 8 stores the raw key Codec. Try both;
            // this lookup must NOT abort the sibling lookups below (a shared try that failed here
            // left `decoder` null on DFU 8, killing all dispatch enumeration).
            try {
                kdk = lookup.findVarHandle(KeyDispatchCodec.class, "keyCodec", MapCodec.class);
            } catch (Throwable e) {
                kdk = lookup.findVarHandle(KeyDispatchCodec.class, "keyCodec", Codec.class);
            }
            // KeyDispatchCodec field is named "type" (Function<? super V, ...>), used for the type field name.
            kdt = lookup.findVarHandle(KeyDispatchCodec.class, "type", Function.class);
            // "decoder" Function<? super K, DataResult<? extends MapDecoder<? extends V>>> — the
            // public constructor uses the same `codec` function as both decoder and source for the
            // (lazily-wrapped) encoder, so applying decoder to a candidate K yields the variant
            // MapCodec wrapped in DataResult. We use this for variant enumeration.
            kdd = lookup.findVarHandle(KeyDispatchCodec.class, "decoder", Function.class);
            // DFU 8 (1.21.1) keeps the JSON type field name in a `typeKey` String field; DFU 9
            // dropped it (the name lives inside the fieldOf-wrapped keyCodec instead).
            //? <1.21.11 {
            /*try {
                kdtk = lookup.findVarHandle(KeyDispatchCodec.class, "typeKey", String.class);
            } catch (Throwable ignored) {}
            *///?}
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(SimpleMapCodec.class, MethodHandles.lookup());
            smk = lookup.findVarHandle(SimpleMapCodec.class, "keyCodec", Codec.class);
            sme = lookup.findVarHandle(SimpleMapCodec.class, "elementCodec", Codec.class);
            sms = lookup.findVarHandle(SimpleMapCodec.class, "keys", com.mojang.serialization.Keyable.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(Codec.RecursiveCodec.class, MethodHandles.lookup());
            rw = lookup.findVarHandle(Codec.RecursiveCodec.class, "wrapped", Supplier.class);
        } catch (Throwable ignored) {}
        try {
            rmc = Class.forName("com.mojang.serialization.MapCodec$RecursiveMapCodec");
            var lookup = MethodHandles.privateLookupIn(rmc, MethodHandles.lookup());
            rmw = lookup.findVarHandle(rmc, "wrapped", Supplier.class);
        } catch (Throwable ignored) {
            rmc = null;
        }
        try {
            var lookup = MethodHandles.privateLookupIn(CompoundListCodec.class, MethodHandles.lookup());
            clk = lookup.findVarHandle(CompoundListCodec.class, "keyCodec", Codec.class);
            cle = lookup.findVarHandle(CompoundListCodec.class, "elementCodec", Codec.class);
        } catch (Throwable ignored) {}
        try {
            var lookup = MethodHandles.privateLookupIn(EitherMapCodec.class, MethodHandles.lookup());
            emf = lookup.findVarHandle(EitherMapCodec.class, "first", MapCodec.class);
            ems = lookup.findVarHandle(EitherMapCodec.class, "second", MapCodec.class);
        } catch (Throwable ignored) {}

        PAIR_CODEC_FIRST = pf;
        PAIR_CODEC_SECOND = ps;
        OPTIONAL_FIELD_NAME = ofn;
        OPTIONAL_FIELD_ELEMENT = ofe;
        OPTIONAL_FIELD_LENIENT = ofl;
        PAIR_MAP_FIRST = pmf;
        PAIR_MAP_SECOND = pms;
        KEY_DISPATCH_KEYCODEC = kdk;
        KEY_DISPATCH_TYPE = kdt;
        KEY_DISPATCH_DECODER = kdd;
        //? <1.21.11
        //KEY_DISPATCH_TYPEKEY = kdtk;
        SIMPLE_MAP_KEYCODEC = smk;
        SIMPLE_MAP_ELEMENT = sme;
        SIMPLE_MAP_KEYS = sms;
        RECURSIVE_WRAPPED = rw;
        RECURSIVE_MAP_CLASS = rmc;
        RECURSIVE_MAP_WRAPPED = rmw;
        COMPOUND_LIST_KEY = clk;
        COMPOUND_LIST_ELEMENT = cle;
        EITHER_MAP_FIRST = emf;
        EITHER_MAP_SECOND = ems;
    }
}