// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core (rslib/src/rwkv/engine.rs and
// architecture.rs, the small-model engine), Copyright: Ankitects Pty Ltd and
// contributors, GNU AGPL version 3 or later.

package dev.bee.rwkv

import java.io.IOException
import kotlin.math.exp

/** The compiled dimensions of the small (SM92) architecture. */
object RwkvArchitecture {
    const val CARD_FEATURES: Int = 92
    const val D_MODEL: Int = 64
    const val HEADS: Int = 4
    const val HEAD_SIZE: Int = D_MODEL / HEADS
    const val MATRIX_SIZE: Int = HEADS * HEAD_SIZE * HEAD_SIZE

    /** `features_fc_dim` and `head_fc_dim`. */
    const val HEAD_DIM: Int = 128
    const val NUM_CURVES: Int = 3
    const val MAX_LAYERS: Int = 4

    /** Stream order of the modules (and of [RwkvStep.next]). */
    const val CARD: Int = 0
    const val DECK: Int = 1
    const val NOTE: Int = 2
    const val PRESET: Int = 3
    const val USER: Int = 4
    const val STREAMS: Int = 5

    internal val MODULE_LAYERS: IntArray = intArrayOf(2, 4, 1, 3, 3)
    internal val CHANNEL_MIXER_DIMS: IntArray = intArrayOf(64, 64, 64, 64, 64)

    /** `(module, layer)` pairs in execution order (the interleaved schedule). */
    internal val LAYER_STEPS: Array<IntArray> = arrayOf(
        intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(2, 0), intArrayOf(3, 0), intArrayOf(4, 0),
        intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(3, 1), intArrayOf(4, 1),
        intArrayOf(1, 2), intArrayOf(3, 2), intArrayOf(4, 2),
        intArrayOf(1, 3),
    )

    internal fun channelMixerPresent(module: Int, layer: Int): Boolean =
        (module == 0 && layer == 0) || (module == 1 && layer == 0) ||
            (module == 1 && layer == 3) || (module == 2 && layer == 0)

    /** Input 22 (`scaled_state`) was never set during the small model's training. */
    internal const val ZEROED_FEATURE: Int = 22

    internal const val V_LORA_RANK = 2
    internal const val A_LORA_RANK = 4
    internal const val D_LORA_RANK = 4
    internal const val G_LORA_RANK = 4
    internal const val LAYER_NORM_EPS = 1e-5f
    internal const val GROUP_NORM_EPS = 64e-5f
}

/** A power-mixture forgetting curve: `Σ w_i (1 + t / exp(s_i))^(-exp(d_i))`. */
class RwkvCurve internal constructor(
    internal val weights: FloatArray,
    internal val scales: FloatArray,
    internal val decays: FloatArray,
) {
    /** Recall probability [elapsedSeconds] after the review this curve follows. */
    fun predict(elapsedSeconds: Float): Float {
        val t = maxOf(elapsedSeconds, 1f)
        var raw = 0f
        for (index in weights.indices) {
            val decay = exp(decays[index].coerceIn(-25f, 25f))
            val scale = 1e-7f + exp(scales[index].coerceIn(-25f, 25f))
            raw += weights[index] * exp(-decay * kotlin.math.ln1p(t / scale))
        }
        raw = 1e-5f + (1f - 2e-5f) * raw
        return RwkvMath.sigmoid(kotlin.math.ln(raw / (1f - raw)) + 1e-5f)
    }
}

/** The outputs of one forward step. */
class RwkvStep internal constructor(
    /** The forgetting curve from this step on. */
    val curve: RwkvCurve,
    /** Softmax over again/hard/good/easy. */
    val buttonProbabilities: FloatArray,
    /** The five streams' next states in [RwkvArchitecture] stream order. */
    val next: Array<RwkvModuleState>,
) {
    /** `1 - P(again)`, the instant head's recall. */
    val retrievability: Float get() = 1f - buttonProbabilities[0]
}

/** The SM92 network: a feature MLP, five interleaved RWKV-7 streams and three heads. */
class RwkvModel private constructor(
    private val features0: Linear,
    private val featuresNorm: Norm,
    private val features3: Linear,
    private val modules: Array<Array<RwkvLayer>>,
    private val preheadNorm: Norm,
    private val headW0: Linear,
    private val headWNorm: Norm,
    private val headW4: Linear,
    private val gruW: Linear,
    private val gruS: Linear,
    private val gruD: Linear,
    private val headP0: Linear,
    private val pLinear: Linear,
) {
    /**
     * Runs one review row. [states] holds the five streams' previous states in
     * stream order (`null` for an id the model has not seen). The inputs are
     * not modified.
     */
    fun step(features: FloatArray, states: Array<RwkvModuleState?>): RwkvStep {
        require(features.size == RwkvArchitecture.CARD_FEATURES) { "expected ${RwkvArchitecture.CARD_FEATURES} features" }
        require(states.size == RwkvArchitecture.STREAMS) { "expected ${RwkvArchitecture.STREAMS} stream states" }
        val input = features.copyOf()
        input[RwkvArchitecture.ZEROED_FEATURE] = 0f
        var x = features0.apply(input)
        RwkvMath.siluInPlace(x)
        x = featuresNorm.apply(x)
        x = features3.apply(x)
        RwkvMath.siluInPlace(x)

        val v0 = arrayOfNulls<FloatArray>(RwkvArchitecture.STREAMS)
        val next = Array(RwkvArchitecture.STREAMS) { ArrayList<RwkvLayerState>(RwkvArchitecture.MODULE_LAYERS[it]) }
        for (step in RwkvArchitecture.LAYER_STEPS) {
            val module = step[0]
            val layer = step[1]
            val previous = states[module]?.layers?.getOrNull(layer)
            val result = modules[module][layer].run(x, v0[module], previous)
            x = result.output
            v0[module] = result.v0
            next[module] += result.state
        }

        x = preheadNorm.apply(x)
        var headW = headW0.apply(x)
        RwkvMath.reluInPlace(headW)
        headW = headWNorm.apply(headW)
        headW = headW4.apply(headW)
        val curve = RwkvCurve(RwkvMath.softmax(gruW.apply(headW)), gruS.apply(headW), gruD.apply(headW))

        val headP = headP0.apply(x)
        RwkvMath.reluInPlace(headP)
        val buttons = RwkvMath.softmax(pLinear.apply(headP))
        return RwkvStep(curve, buttons, Array(RwkvArchitecture.STREAMS) { RwkvModuleState(next[it]) })
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun load(weights: RwkvWeights): RwkvModel {
            if (!weights.hasPrefix("rwkv_modules.0.blocks.1.")) {
                throw IOException("RWKV weights are not the two-card-layer small architecture")
            }
            val d = RwkvArchitecture.D_MODEL
            val headDim = RwkvArchitecture.HEAD_DIM
            val modules = Array(RwkvArchitecture.STREAMS) { module ->
                Array(RwkvArchitecture.MODULE_LAYERS[module]) { layer -> RwkvLayer.load(weights, module, layer) }
            }
            return RwkvModel(
                features0 = weights.linear("features2card.0", RwkvArchitecture.CARD_FEATURES, headDim, true),
                featuresNorm = weights.layerNorm("features2card.2", headDim, RwkvArchitecture.LAYER_NORM_EPS),
                features3 = weights.linear("features2card.3", headDim, d, true),
                modules = modules,
                preheadNorm = weights.layerNorm("prehead_norm", d, RwkvArchitecture.LAYER_NORM_EPS),
                headW0 = weights.linear("head_w.0", d, d, true),
                headWNorm = weights.layerNorm("head_w.2", d, RwkvArchitecture.LAYER_NORM_EPS),
                headW4 = weights.linear("head_w.4", d, headDim, true),
                gruW = weights.namedLinear("gru_w_weight", "gru_w_bias", headDim, RwkvArchitecture.NUM_CURVES),
                gruS = weights.namedLinear("gru_s_weight", "gru_s_bias", headDim, RwkvArchitecture.NUM_CURVES),
                gruD = weights.namedLinear("gru_d_weight", "gru_d_bias", headDim, RwkvArchitecture.NUM_CURVES),
                headP0 = weights.linear("head_p.0", d, headDim, true),
                pLinear = weights.linear("p_linear", headDim, 4, true),
            )
        }
    }
}

internal class RwkvLayerResult(val output: FloatArray, val v0: FloatArray, val state: RwkvLayerState)

internal class RwkvLayer(private val timeMixer: TimeMixer, private val channelMixer: ChannelMixer?) {
    fun run(input: FloatArray, v0: FloatArray?, state: RwkvLayerState?): RwkvLayerResult {
        val time = timeMixer.run(input, v0, state)
        if (channelMixer == null) {
            return RwkvLayerResult(time.output, time.v0, RwkvLayerState(time.xShift, time.matrix, null))
        }
        val channel = channelMixer.run(time.output, state?.channelShift)
        return RwkvLayerResult(channel.first, time.v0, RwkvLayerState(time.xShift, time.matrix, channel.second))
    }

    companion object {
        fun load(weights: RwkvWeights, module: Int, layer: Int): RwkvLayer =
            RwkvLayer(
                TimeMixer.load(weights, module, layer),
                if (RwkvArchitecture.channelMixerPresent(module, layer)) ChannelMixer.load(weights, module, layer) else null,
            )
    }
}

internal class LoraSimple(private val a: Linear, private val b: Linear) {
    fun applySigmoid(input: FloatArray): FloatArray {
        val out = b.apply(a.apply(input))
        RwkvMath.sigmoidInPlace(out)
        return out
    }

    fun applyTanh(input: FloatArray): FloatArray {
        val hidden = a.apply(input)
        RwkvMath.tanhInPlace(hidden)
        return b.apply(hidden)
    }

    companion object {
        fun load(weights: RwkvWeights, prefix: String, rank: Int): LoraSimple =
            LoraSimple(
                weights.linear("$prefix.A", RwkvArchitecture.D_MODEL, rank, false),
                weights.linear("$prefix.B_and_lamb", rank, RwkvArchitecture.D_MODEL, true),
            )

        fun zero(rank: Int): LoraSimple =
            LoraSimple(
                Linear.zero(RwkvArchitecture.D_MODEL, rank, false),
                Linear.zero(rank, RwkvArchitecture.D_MODEL, true),
            )
    }
}

internal class TimeMixerResult(val output: FloatArray, val v0: FloatArray, val xShift: FloatArray, val matrix: FloatArray)

internal class TimeMixer(
    private val layerId: Int,
    private val layerNorm: Norm,
    private val rkvdagLerp: FloatArray,
    private val bonus: FloatArray,
    private val wR: Linear,
    private val wK: Linear,
    private val wV: Linear,
    private val wO: Linear,
    private val kScaleLinear: Linear,
    private val vScaleLinear: Linear,
    private val vLora: LoraSimple,
    private val aLora: LoraSimple,
    private val dLora: LoraSimple,
    private val loraAG: Linear,
    private val loraBG: Linear,
    private val outGroupNorm: Norm,
) {
    fun run(input: FloatArray, v0In: FloatArray?, state: RwkvLayerState?): TimeMixerResult {
        val d = RwkvArchitecture.D_MODEL
        val heads = RwkvArchitecture.HEADS
        val headSize = RwkvArchitecture.HEAD_SIZE
        val x = layerNorm.apply(input)
        val xShift = state?.xShift ?: x
        val stateMatrix = state?.matrix ?: ZERO_MATRIX

        val mixed = FloatArray(d)
        fun fillMixed(mix: Int) {
            val offset = mix * d
            for (channel in 0 until d) {
                mixed[channel] = RwkvMath.lerp(x[channel], xShift[channel], rkvdagLerp[offset + channel])
            }
        }

        fillMixed(0)
        val r = wR.apply(mixed)
        fillMixed(1)
        val k = wK.apply(mixed)
        fillMixed(6)
        val kScale = kScaleLinear.apply(mixed)
        RwkvMath.sigmoidInPlace(kScale)
        fillMixed(7)
        val vScale = vScaleLinear.apply(mixed)
        RwkvMath.sigmoidInPlace(vScale)

        fillMixed(2)
        val v: FloatArray
        val nextV0: FloatArray
        if (layerId == 0) {
            v = wV.apply(mixed)
            nextV0 = v.copyOf()
        } else {
            val previousV0 = v0In ?: throw IllegalStateException("RWKV layer $layerId ran before its module's layer 0")
            val vLerp = vLora.applySigmoid(mixed)
            val projected = wV.apply(mixed)
            for (channel in 0 until d) {
                vLerp[channel] = RwkvMath.lerp(projected[channel], previousV0[channel], vLerp[channel])
            }
            v = vLerp
            nextV0 = previousV0.copyOf()
        }

        fillMixed(4)
        val a = aLora.applySigmoid(mixed)
        fillMixed(5)
        var g = loraAG.apply(mixed)
        RwkvMath.sigmoidInPlace(g)
        g = loraBG.apply(g)

        fillMixed(3)
        val w = dLora.applyTanh(mixed)
        for (index in w.indices) {
            // exp(-exp(-0.5 - softplus(-x))) == exp(-exp(-0.5) * sigmoid(x)):
            // upstream's query-path form, two fewer transcendentals per channel.
            w[index] = exp(-DECAY_SCALE * RwkvMath.sigmoid(w[index]))
        }

        RwkvMath.normalizeHeadsInPlace(k, heads, headSize)
        for (head in 0 until heads) {
            for (index in 0 until headSize) {
                k[head * headSize + index] *= kScale[head]
            }
        }
        RwkvMath.normalizeHeadsInPlace(v, heads, headSize)
        for (head in 0 until heads) {
            for (index in 0 until headSize) {
                v[head * headSize + index] *= vScale[head]
            }
        }
        val kDeformed = k.copyOf()
        for (channel in 0 until d) {
            k[channel] *= a[channel]
        }

        val nextMatrix = FloatArray(RwkvArchitecture.MATRIX_SIZE)
        val recurrence = FloatArray(d)
        singleTimestep(r, k, v, w, a, kDeformed, stateMatrix, recurrence, nextMatrix)

        val out = outGroupNorm.apply(recurrence)
        val bonusOut = FloatArray(d)
        for (head in 0 until heads) {
            val base = head * headSize
            var bonusScale = 0f
            for (index in 0 until headSize) {
                bonusScale += r[base + index] * bonus[base + index] * k[base + index]
            }
            for (index in 0 until headSize) {
                bonusOut[base + index] = bonusScale * v[base + index]
            }
        }
        for (channel in 0 until d) {
            out[channel] = g[channel] * (out[channel] + bonusOut[channel])
        }
        val projected = wO.apply(out)
        for (channel in 0 until d) {
            projected[channel] += input[channel]
        }
        return TimeMixerResult(projected, nextV0, x, nextMatrix)
    }

    companion object {
        private val ZERO_MATRIX = FloatArray(RwkvArchitecture.MATRIX_SIZE)

        /** exp(-0.5) rounded to f32, upstream's `QUERY_DECAY_SCALE`. */
        private const val DECAY_SCALE = 0.60653067f

        fun load(weights: RwkvWeights, module: Int, layer: Int): TimeMixer {
            val prefix = "rwkv_modules.$module.blocks.$layer.time_mixer"
            val d = RwkvArchitecture.D_MODEL
            val lerp = weights.values("$prefix.rkvdag_lerp")
            if (lerp.size != 8 * d) {
                throw IOException("weight $prefix.rkvdag_lerp has ${lerp.size} values")
            }
            val bonus = weights.values("$prefix.bonus")
            if (bonus.size != d) {
                throw IOException("weight $prefix.bonus has ${bonus.size} values")
            }
            return TimeMixer(
                layerId = layer,
                layerNorm = weights.layerNorm("$prefix.layer_norm", d, RwkvArchitecture.LAYER_NORM_EPS),
                rkvdagLerp = lerp,
                bonus = bonus,
                wR = weights.linear("$prefix.W_r", d, d, false),
                wK = weights.linear("$prefix.W_k", d, d, false),
                wV = weights.linear("$prefix.W_v", d, d, false),
                wO = weights.linear("$prefix.W_o", d, d, false),
                kScaleLinear = weights.linear("$prefix.k_scale_linear", d, RwkvArchitecture.HEADS, true),
                vScaleLinear = weights.linear("$prefix.v_scale_linear", d, RwkvArchitecture.HEADS, true),
                vLora = if (layer == 0) {
                    LoraSimple.zero(RwkvArchitecture.V_LORA_RANK)
                } else {
                    LoraSimple.load(weights, "$prefix.v_lora_simple", RwkvArchitecture.V_LORA_RANK)
                },
                aLora = LoraSimple.load(weights, "$prefix.a_lora_simple", RwkvArchitecture.A_LORA_RANK),
                dLora = LoraSimple.load(weights, "$prefix.d_lora_mlp", RwkvArchitecture.D_LORA_RANK),
                loraAG = weights.linear("$prefix.lora_A_g", d, RwkvArchitecture.G_LORA_RANK, false),
                loraBG = weights.linear("$prefix.lora_B_g", RwkvArchitecture.G_LORA_RANK, d, false),
                outGroupNorm = weights.groupNorm(
                    "$prefix.out_group_norm",
                    RwkvArchitecture.HEADS,
                    d,
                    RwkvArchitecture.GROUP_NORM_EPS,
                ),
            )
        }

        /**
         * The RWKV-7 recurrence for one timestep, per head:
         * `S' = S ⊙ wᵀ − (S k̃)(a ⊙ k̃)ᵀ + v kᵀ`, `out = S' r`.
         */
        internal fun singleTimestep(
            r: FloatArray,
            k: FloatArray,
            v: FloatArray,
            w: FloatArray,
            a: FloatArray,
            kDeformed: FloatArray,
            state: FloatArray,
            out: FloatArray,
            nextState: FloatArray,
        ) {
            val headSize = RwkvArchitecture.HEAD_SIZE
            val aKDeformed = FloatArray(headSize)
            for (head in 0 until RwkvArchitecture.HEADS) {
                val headBase = head * headSize
                val matrixBase = head * headSize * headSize
                for (column in 0 until headSize) {
                    aKDeformed[column] = a[headBase + column] * kDeformed[headBase + column]
                }
                for (row in 0 until headSize) {
                    val rowBase = matrixBase + row * headSize
                    var stateDotK = 0f
                    for (column in 0 until headSize) {
                        stateDotK += state[rowBase + column] * kDeformed[headBase + column]
                    }
                    val value = v[headBase + row]
                    var output = 0f
                    for (column in 0 until headSize) {
                        val next = state[rowBase + column] * w[headBase + column] -
                            stateDotK * aKDeformed[column] +
                            value * k[headBase + column]
                        nextState[rowBase + column] = next
                        output += next * r[headBase + column]
                    }
                    out[headBase + row] = output
                }
            }
        }
    }
}

internal class ChannelMixer(
    private val layerNorm: Norm,
    private val lerpK: FloatArray,
    private val wK: Linear,
    private val wV: Linear,
) {
    /** Returns the output and the next token shift. */
    fun run(input: FloatArray, state: FloatArray?): Pair<FloatArray, FloatArray> {
        val d = RwkvArchitecture.D_MODEL
        val x = layerNorm.apply(input)
        val xShift = state ?: x
        val mixed = FloatArray(d)
        for (channel in 0 until d) {
            mixed[channel] = RwkvMath.lerp(x[channel], xShift[channel], lerpK[channel])
        }
        val k = wK.apply(mixed)
        for (index in k.indices) {
            val relu = maxOf(k[index], 0f)
            k[index] = relu * relu
        }
        val out = wV.apply(k)
        for (channel in 0 until d) {
            out[channel] += input[channel]
        }
        return out to x
    }

    companion object {
        fun load(weights: RwkvWeights, module: Int, layer: Int): ChannelMixer {
            val prefix = "rwkv_modules.$module.blocks.$layer.channel_mixer"
            val d = RwkvArchitecture.D_MODEL
            val channelDim = RwkvArchitecture.CHANNEL_MIXER_DIMS[module]
            val lerpK = weights.values("$prefix.lerp_k")
            if (lerpK.size != d) {
                throw IOException("weight $prefix.lerp_k has ${lerpK.size} values")
            }
            return ChannelMixer(
                weights.layerNorm("$prefix.layer_norm", d, RwkvArchitecture.LAYER_NORM_EPS),
                lerpK,
                weights.linear("$prefix.W_k", d, channelDim, false),
                weights.linear("$prefix.W_v", channelDim, d, false),
            )
        }
    }
}
