/**
 * Internal machinery of codec_ui — <b>not API</b>. No compatibility guarantees; do not
 * reference from outside {@code codec_ui} (the construction mixins in
 * {@code net.mehvahdjukaar.codecui.mixins} are the one sanctioned exception:
 * they ARE this layer, just forced into the mixin package by the mixin config).
 *
 * <p>Contents: {@link net.mehvahdjukaar.codecui.internal.SchemaResolver}
 * (the tiered codec→schema walker) and the weak-identity side-channel tag stores written
 * by the mixins ({@code SchemaTags}, {@code XmapTags}, {@code FieldOfTags},
 * {@code RecordFieldTags}), plus dispatch key enumeration
 * ({@link net.mehvahdjukaar.codecui.internal.DispatchRegistry}) and
 * {@link net.mehvahdjukaar.codecui.internal.CuratedSchemas} — the hand-maintained list of
 * registrations for codecs inference can't handle (companions, handlers, and the vanilla
 * dispatch key sets). Kept strictly separate from the inference code: every curated entry
 * goes through only the public {@code SchemaCodecs} API.</p>
 *
 * <p>Invariant that must hold everywhere in this package: tags recorded at codec
 * construction time must be LAZY (store the inner codec, resolve at lookup), never an
 * eagerly resolved schema — MC bootstrap constructs codecs before companions register.</p>
 */
package net.mehvahdjukaar.codecui.internal;
