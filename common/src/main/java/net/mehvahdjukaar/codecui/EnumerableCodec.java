package net.mehvahdjukaar.codecui;

import java.util.Map;

/**
 * Opt-in marker for codecs that decode from a closed, enumerable set of named values —
 * custom registries, string→object dispatch maps, and similar. A CodecUI-aware editor can
 * use this to render the codec as a dropdown of the registered names instead of a raw string
 * field, and — when the codec is used as the key of a {@code Codec.dispatch(...)} — to
 * enumerate every variant with a real per-variant editor.
 *
 * <p>Implement it directly on your {@code Codec}. Called fresh on every editor open, so late
 * registrations are picked up. Keys are the display/serialized names; values are the objects
 * the codec decodes to (i.e. the dispatch keys).</p>
 */
public interface EnumerableCodec {

    Map<String, ?> codecUiValues();
}
