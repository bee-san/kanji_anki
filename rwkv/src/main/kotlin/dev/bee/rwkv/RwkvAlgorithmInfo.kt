// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core, Copyright: Ankitects Pty Ltd and
// contributors, GNU AGPL version 3 or later.

package dev.bee.rwkv

/**
 * Provenance of the RWKV memory model implemented by this package.
 *
 * The forward pass, feature builder, ID-encoding RNG, curve head and interval
 * search are a Kotlin port of the small (SM92) engine in
 * `rslib/src/rwkv/engine.rs` at [UPSTREAM_COMMIT]. The architecture is the
 * srs-benchmark RWKV model as retrained by JSchoreels (anki-rwkv). The weights
 * are the upstream `qt/aqt/rwkv_inference/sm92/weights.f32.bin`, verified
 * against [WEIGHTS_SHA256] on load.
 */
object RwkvAlgorithmInfo {
    const val UPSTREAM_REPOSITORY: String = "Pedrubik2000/kuma3-anki-core"
    const val UPSTREAM_BRANCH: String = "rwkv"
    const val UPSTREAM_COMMIT: String = "a8cf880ce2907229d2edcadcd6977d855b7954a8"
    const val MODEL_LABEL: String = "sm92-e26-dec1ep32k"
    const val MODEL_FAMILY: String = "RWKV-Curve small (SM92)"
    const val PARAMETER_COUNT: Int = 331_511
    const val WEIGHTS_BYTES: Int = 1_351_553
    const val WEIGHTS_SHA256: String = "4fbc5f963b6ef18d23dbc0f11358fed8a9c7c97c271d6eda95df3b8ed6fbeb6e"

    /** Bumped whenever the persisted state layout or the feature rules change. */
    const val STATE_VERSION: Int = 1

    @JvmStatic
    fun upstreamReference(): String = "$UPSTREAM_REPOSITORY@$UPSTREAM_COMMIT"

    /** A short identity for the persisted state header: model plus state layout. */
    @JvmStatic
    fun stateIdentity(): String = "$MODEL_LABEL/$WEIGHTS_SHA256/state-v$STATE_VERSION"
}
