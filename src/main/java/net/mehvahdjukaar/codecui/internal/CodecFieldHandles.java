package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Keyable;
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

// VarHandles for the private fields of the DFU codec classes the resolver introspects. A failed
// lookup leaves the handle null and the tier branch is skipped. net.minecraft.* codecs are read
// via the access widener instead: string field names would not survive Fabric's intermediary remap.
final class CodecFieldHandles {

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
            // keyCodec is a fieldOf-wrapped MapCodec on DFU 9, a raw Codec on DFU 8. A miss here
            // must not abort the sibling lookups (that once left decoder null on DFU 8).
            try {
                kdk = lookup.findVarHandle(KeyDispatchCodec.class, "keyCodec", MapCodec.class);
            } catch (Throwable e) {
                kdk = lookup.findVarHandle(KeyDispatchCodec.class, "keyCodec", Codec.class);
            }
            kdt = lookup.findVarHandle(KeyDispatchCodec.class, "type", Function.class);
            // decoder is the user's K -> MapCodec function; variant enumeration applies it per key.
            kdd = lookup.findVarHandle(KeyDispatchCodec.class, "decoder", Function.class);
            // DFU 8 keeps the JSON type field name in typeKey; DFU 9 folds it into keyCodec.
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
            sms = lookup.findVarHandle(SimpleMapCodec.class, "keys", Keyable.class);
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