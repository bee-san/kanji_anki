// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core (rslib/src/rwkv/engine.rs and
// precision.rs), Copyright: Ankitects Pty Ltd and contributors,
// GNU AGPL version 3 or later.

package dev.bee.rwkv

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One RWKV layer's recurrent state: the time mixer's token shift and WKV
 * matrix (`heads × head_size × head_size`), and the channel mixer's token
 * shift when the layer has a channel mixer.
 */
class RwkvLayerState internal constructor(
    internal val xShift: FloatArray,
    internal val matrix: FloatArray,
    internal val channelShift: FloatArray?,
) {
    internal fun copy(): RwkvLayerState =
        RwkvLayerState(xShift.copyOf(), matrix.copyOf(), channelShift?.copyOf())

    internal fun contentEquals(other: RwkvLayerState): Boolean =
        xShift.contentEquals(other.xShift) &&
            matrix.contentEquals(other.matrix) &&
            (channelShift?.contentEquals(other.channelShift) ?: (other.channelShift == null))
}

/** The recurrent state of one stream (card, deck, note, preset or user) for one id. */
class RwkvModuleState internal constructor(internal val layers: List<RwkvLayerState>) {
    fun layerCount(): Int = layers.size

    fun contentEquals(other: RwkvModuleState): Boolean =
        layers.size == other.layers.size && layers.indices.all { layers[it].contentEquals(other.layers[it]) }

    internal fun copy(): RwkvModuleState = RwkvModuleState(layers.map { it.copy() })
}

/**
 * Storage precision of a stream's state. [INT8] mirrors upstream's compact
 * state: each 16-value WKV matrix row is stored as a positive f32 scale and
 * signed int8 codes (round half to even, clamped to ±127), and token shifts as
 * IEEE half floats. Upstream compacts card and note states this way after
 * every review, so the next review always reads the rounded state.
 */
enum class RwkvStatePrecision(internal val code: Int) {
    F32(0),
    INT8(8),
    ;

    companion object {
        internal fun fromCode(code: Int): RwkvStatePrecision =
            entries.firstOrNull { it.code == code } ?: throw IOException("invalid RWKV state precision $code")
    }
}

/** Encodes and decodes stream states for persistence. */
object RwkvStateCodec {
    private const val MAGIC: Int = 0x4b525331 // "KRS1"
    private const val INT8_LIMIT = 127f

    /**
     * Rounds [state] exactly as storing it at [precision] would, so a replay
     * that never writes to disk computes the same numbers as live reviews that
     * read every state back from storage.
     */
    @JvmStatic
    fun quantize(state: RwkvModuleState, precision: RwkvStatePrecision): RwkvModuleState {
        if (precision == RwkvStatePrecision.F32) {
            return state
        }
        return RwkvModuleState(
            state.layers.map { layer ->
                val matrix = layer.matrix.copyOf()
                roundMatrixInt8(matrix)
                RwkvLayerState(
                    RwkvHalf.roundTrip(layer.xShift),
                    matrix,
                    layer.channelShift?.let(RwkvHalf::roundTrip),
                )
            },
        )
    }

    @JvmStatic
    fun encode(state: RwkvModuleState, precision: RwkvStatePrecision): ByteArray {
        val size = 4 + 1 + 1 + state.layers.sumOf { layer ->
            1 + shiftBytes(layer.xShift.size, precision) + matrixBytes(layer.matrix.size, precision) +
                (layer.channelShift?.let { shiftBytes(it.size, precision) } ?: 0)
        }
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.put(precision.code.toByte())
        buffer.put(state.layers.size.toByte())
        for (layer in state.layers) {
            buffer.put(if (layer.channelShift != null) 1 else 0)
            writeShift(buffer, layer.xShift, precision)
            writeMatrix(buffer, layer.matrix, precision)
            layer.channelShift?.let { writeShift(buffer, it, precision) }
        }
        return buffer.array()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(bytes: ByteArray): RwkvModuleState {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != MAGIC) {
                throw IOException("invalid RWKV state magic")
            }
            val precision = RwkvStatePrecision.fromCode(buffer.get().toInt() and 0xff)
            val layerCount = buffer.get().toInt() and 0xff
            if (layerCount !in 1..RwkvArchitecture.MAX_LAYERS) {
                throw IOException("invalid RWKV state layer count $layerCount")
            }
            val layers = ArrayList<RwkvLayerState>(layerCount)
            repeat(layerCount) {
                val hasChannel = when (buffer.get().toInt()) {
                    0 -> false
                    1 -> true
                    else -> throw IOException("invalid RWKV layer flags")
                }
                val xShift = readShift(buffer, precision)
                val matrix = readMatrix(buffer, precision)
                val channel = if (hasChannel) readShift(buffer, precision) else null
                layers += RwkvLayerState(xShift, matrix, channel)
            }
            if (buffer.hasRemaining()) {
                throw IOException("trailing bytes in RWKV state")
            }
            return RwkvModuleState(layers)
        } catch (error: java.nio.BufferUnderflowException) {
            throw IOException("RWKV state ended early", error)
        }
    }

    private fun shiftBytes(count: Int, precision: RwkvStatePrecision): Int =
        if (precision == RwkvStatePrecision.F32) count * 4 else count * 2

    private fun matrixBytes(count: Int, precision: RwkvStatePrecision): Int =
        if (precision == RwkvStatePrecision.F32) {
            count * 4
        } else {
            count / RwkvArchitecture.HEAD_SIZE * (4 + RwkvArchitecture.HEAD_SIZE)
        }

    private fun writeShift(buffer: ByteBuffer, values: FloatArray, precision: RwkvStatePrecision) {
        check(values.size == RwkvArchitecture.D_MODEL) { "RWKV shift size ${values.size}" }
        for (value in values) {
            if (precision == RwkvStatePrecision.F32) {
                buffer.putFloat(value)
            } else {
                buffer.putShort(RwkvHalf.toHalfBits(value).toShort())
            }
        }
    }

    private fun readShift(buffer: ByteBuffer, precision: RwkvStatePrecision): FloatArray {
        val out = FloatArray(RwkvArchitecture.D_MODEL)
        for (index in out.indices) {
            out[index] = if (precision == RwkvStatePrecision.F32) {
                buffer.float
            } else {
                RwkvHalf.fromHalfBits(buffer.short.toInt() and 0xffff)
            }
            if (!out[index].isFinite()) {
                throw IOException("non-finite RWKV shift")
            }
        }
        return out
    }

    private fun writeMatrix(buffer: ByteBuffer, values: FloatArray, precision: RwkvStatePrecision) {
        check(values.size == RwkvArchitecture.MATRIX_SIZE) { "RWKV matrix size ${values.size}" }
        if (precision == RwkvStatePrecision.F32) {
            for (value in values) {
                buffer.putFloat(value)
            }
            return
        }
        val headSize = RwkvArchitecture.HEAD_SIZE
        for (rowStart in values.indices step headSize) {
            val scale = rowScale(values, rowStart)
            buffer.putFloat(scale)
            for (column in 0 until headSize) {
                buffer.put(int8Code(values[rowStart + column], scale).toByte())
            }
        }
    }

    private fun readMatrix(buffer: ByteBuffer, precision: RwkvStatePrecision): FloatArray {
        val out = FloatArray(RwkvArchitecture.MATRIX_SIZE)
        if (precision == RwkvStatePrecision.F32) {
            for (index in out.indices) {
                out[index] = buffer.float
                if (!out[index].isFinite()) {
                    throw IOException("non-finite RWKV matrix value")
                }
            }
            return out
        }
        val headSize = RwkvArchitecture.HEAD_SIZE
        for (rowStart in out.indices step headSize) {
            val scale = buffer.float
            if (!scale.isFinite() || scale <= 0f) {
                throw IOException("invalid RWKV matrix scale")
            }
            for (column in 0 until headSize) {
                val code = buffer.get().toInt()
                if (code == -128) {
                    throw IOException("out-of-range RWKV matrix code")
                }
                out[rowStart + column] = code.toFloat() * scale
            }
        }
        return out
    }

    private fun roundMatrixInt8(values: FloatArray) {
        val headSize = RwkvArchitecture.HEAD_SIZE
        for (rowStart in values.indices step headSize) {
            val scale = rowScale(values, rowStart)
            for (column in 0 until headSize) {
                values[rowStart + column] = int8Code(values[rowStart + column], scale).toFloat() * scale
            }
        }
    }

    private fun rowScale(values: FloatArray, rowStart: Int): Float {
        var maximum = 0f
        for (column in 0 until RwkvArchitecture.HEAD_SIZE) {
            maximum = maxOf(maximum, kotlin.math.abs(values[rowStart + column]))
        }
        return if (maximum == 0f) 1f else maxOf(maximum / INT8_LIMIT, Float.MIN_VALUE)
    }

    private fun int8Code(value: Float, scale: Float): Int =
        Math.rint((value / scale).toDouble()).toFloat().coerceIn(-INT8_LIMIT, INT8_LIMIT).toInt()
}

/** IEEE 754 binary16 conversion with round-half-to-even, as the `half` crate. */
internal object RwkvHalf {
    fun roundTrip(values: FloatArray): FloatArray = FloatArray(values.size) { fromHalfBits(toHalfBits(values[it])) }

    fun toHalfBits(value: Float): Int {
        val x = value.toRawBits()
        val sign = x and 0x8000_0000.toInt()
        val exp = x and 0x7F80_0000
        val man = x and 0x007F_FFFF
        val halfSign = sign ushr 16
        if (exp == 0x7F80_0000) {
            val nanBit = if (man == 0) 0 else 0x0200
            return halfSign or 0x7C00 or nanBit or (man ushr 13)
        }
        val halfExp = (exp ushr 23) - 127 + 15
        if (halfExp >= 0x1F) {
            return halfSign or 0x7C00
        }
        if (halfExp <= 0) {
            if (14 - halfExp > 24) {
                return halfSign
            }
            val fullMan = man or 0x0080_0000
            var halfMan = fullMan ushr (14 - halfExp)
            val roundBit = 1 shl (13 - halfExp)
            if ((fullMan and roundBit) != 0 && (fullMan and (3 * roundBit - 1)) != 0) {
                halfMan += 1
            }
            return halfSign or halfMan
        }
        val assembled = halfSign or (halfExp shl 10) or (man ushr 13)
        val roundBit = 0x0000_1000
        return if ((man and roundBit) != 0 && (man and (3 * roundBit - 1)) != 0) assembled + 1 else assembled
    }

    fun fromHalfBits(bits: Int): Float {
        val sign = if (bits and 0x8000 != 0) -1f else 1f
        val exp = (bits ushr 10) and 0x1F
        val man = bits and 0x03FF
        return when (exp) {
            0 -> sign * man.toFloat() * SUBNORMAL_UNIT
            0x1F -> if (man == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
            else -> Float.fromBits(((bits and 0x8000) shl 16) or ((exp - 15 + 127) shl 23) or (man shl 13))
        }
    }

    /** 2^-24, the binary16 subnormal step. */
    private const val SUBNORMAL_UNIT: Float = 5.9604645E-8f
}
