// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core (rslib/src/rwkv/engine.rs),
// Copyright: Ankitects Pty Ltd and contributors, GNU AGPL version 3 or later.

package dev.bee.rwkv

import kotlin.math.E
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A dense layer stored column-major ("by input") exactly as the upstream
 * scalar path stores it, so the accumulation order (bias first, then one
 * scaled weight column per non-zero input) matches the reference arithmetic.
 */
internal class Linear(
    val input: Int,
    val output: Int,
    private val weightByInput: FloatArray,
    private val bias: FloatArray?,
) {
    init {
        require(weightByInput.size == input * output) { "linear weight size mismatch" }
        require(bias == null || bias.size == output) { "linear bias size mismatch" }
    }

    fun apply(values: FloatArray): FloatArray {
        val out = FloatArray(output)
        applyInto(values, out)
        return out
    }

    fun applyInto(values: FloatArray, out: FloatArray) {
        val n = output
        val weights = weightByInput
        val b = bias
        if (b != null) {
            System.arraycopy(b, 0, out, 0, n)
        } else {
            out.fill(0f, 0, n)
        }
        val unrolled = n - n % 4
        for (column in 0 until input) {
            val scale = values[column]
            if (scale == 0f) {
                continue
            }
            var at = column * n
            var row = 0
            // Same per-element arithmetic as the reference (one multiply-add
            // per weight, in input order); unrolled for the JIT.
            while (row < unrolled) {
                out[row] += weights[at] * scale
                out[row + 1] += weights[at + 1] * scale
                out[row + 2] += weights[at + 2] * scale
                out[row + 3] += weights[at + 3] * scale
                row += 4
                at += 4
            }
            while (row < n) {
                out[row] += weights[at] * scale
                row++
                at++
            }
        }
    }

    companion object {
        /** Builds a layer from a PyTorch `[output, input]` row-major weight. */
        fun fromRowMajor(input: Int, output: Int, weight: FloatArray, bias: FloatArray?): Linear {
            require(weight.size == input * output) { "linear weight size mismatch" }
            val byInput = FloatArray(weight.size)
            for (row in 0 until output) {
                for (column in 0 until input) {
                    byInput[column * output + row] = weight[row * input + column]
                }
            }
            return Linear(input, output, byInput, bias)
        }

        fun zero(input: Int, output: Int, bias: Boolean): Linear =
            Linear(input, output, FloatArray(input * output), if (bias) FloatArray(output) else null)
    }
}

/** LayerNorm (one group) or GroupNorm with an affine transform. */
internal class Norm(
    private val groups: Int,
    private val dim: Int,
    private val eps: Float,
    private val weight: FloatArray,
    private val bias: FloatArray,
) {
    fun apply(values: FloatArray): FloatArray {
        val out = FloatArray(dim)
        val groupSize = dim / groups
        for (group in 0 until groups) {
            val start = group * groupSize
            val end = start + groupSize
            var sum = 0f
            for (index in start until end) {
                sum += values[index]
            }
            val mean = sum / groupSize.toFloat()
            var variance = 0f
            for (index in start until end) {
                val diff = values[index] - mean
                variance += diff * diff
            }
            variance /= groupSize.toFloat()
            val scale = 1f / sqrt(variance + eps)
            for (index in start until end) {
                out[index] = (values[index] - mean) * scale * weight[index] + bias[index]
            }
        }
        return out
    }
}

internal object RwkvMath {
    const val PI_F: Float = Math.PI.toFloat()

    fun sigmoid(value: Float): Float =
        if (value >= 0f) {
            1f / (1f + exp(-value))
        } else {
            val e = exp(value)
            e / (1f + e)
        }

    fun sigmoidInPlace(values: FloatArray) {
        for (index in values.indices) {
            values[index] = sigmoid(values[index])
        }
    }

    fun siluInPlace(values: FloatArray) {
        for (index in values.indices) {
            values[index] *= sigmoid(values[index])
        }
    }

    fun reluInPlace(values: FloatArray) {
        for (index in values.indices) {
            values[index] = max(values[index], 0f)
        }
    }

    fun tanhInPlace(values: FloatArray) {
        for (index in values.indices) {
            values[index] = tanh(values[index])
        }
    }

    fun softplus(value: Float): Float =
        when {
            value > 20f -> value
            value < -20f -> exp(value)
            else -> ln1p(exp(value))
        }

    fun softmax(values: FloatArray): FloatArray {
        var maximum = Float.NEGATIVE_INFINITY
        for (value in values) {
            maximum = max(maximum, value)
        }
        val out = FloatArray(values.size)
        var sum = 0f
        for (index in values.indices) {
            out[index] = exp(values[index] - maximum)
            sum += out[index]
        }
        for (index in out.indices) {
            out[index] /= sum
        }
        return out
    }

    fun lerp(start: Float, end: Float, weight: Float): Float = start + weight * (end - start)

    /** L2-normalises each head of [values] in place (eps 1e-12, as F.normalize). */
    fun normalizeHeadsInPlace(values: FloatArray, heads: Int, headSize: Int) {
        for (head in 0 until heads) {
            val start = head * headSize
            var squares = 0f
            for (index in start until start + headSize) {
                squares += values[index] * values[index]
            }
            val norm = max(sqrt(squares), 1e-12f)
            for (index in start until start + headSize) {
                values[index] /= norm
            }
        }
    }

    fun dot(left: FloatArray, leftOffset: Int, right: FloatArray, rightOffset: Int, length: Int): Float {
        var sum = 0f
        for (index in 0 until length) {
            sum += left[leftOffset + index] * right[rightOffset + index]
        }
        return sum
    }

    /** `ln(ln(e + x))`, the day-offset gap scaling. */
    fun lnLnE(x: Float): Float = ln(ln(E.toFloat() + x))
}
