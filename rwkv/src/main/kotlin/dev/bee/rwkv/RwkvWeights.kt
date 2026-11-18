// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core (rslib/src/rwkv/engine.rs, WeightMap),
// Copyright: Ankitects Pty Ltd and contributors, GNU AGPL version 3 or later.

package dev.bee.rwkv

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** A tensor from an `ARWKVWEIGHTS1` float32 export. */
internal class RwkvTensor(val shape: IntArray, val values: FloatArray)

/**
 * The named tensors of an anki-rwkv float32 weight export: magic
 * `ARWKVWEIGHTS1`, a u32 tensor count, then per tensor a u16 name length, the
 * UTF-8 name, a u8 rank, rank u32 dimensions and the little-endian f32 values.
 */
class RwkvWeights internal constructor(internal val tensors: Map<String, RwkvTensor>) {
    internal fun tensor(name: String, vararg shape: Int): RwkvTensor {
        val tensor = tensors[name] ?: throw IOException("missing weight: $name")
        if (!tensor.shape.contentEquals(shape)) {
            throw IOException("weight $name has shape ${tensor.shape.contentToString()}, expected ${shape.contentToString()}")
        }
        return tensor
    }

    internal fun values(name: String): FloatArray =
        (tensors[name] ?: throw IOException("missing weight: $name")).values

    internal fun hasPrefix(prefix: String): Boolean = tensors.keys.any { it.startsWith(prefix) }

    internal fun linear(name: String, input: Int, output: Int, bias: Boolean): Linear =
        Linear.fromRowMajor(
            input,
            output,
            tensor("$name.weight", output, input).values,
            if (bias) tensor("$name.bias", output).values else null,
        )

    internal fun namedLinear(weight: String, bias: String, input: Int, output: Int): Linear =
        Linear.fromRowMajor(input, output, tensor(weight, output, input).values, tensor(bias, output).values)

    internal fun layerNorm(name: String, dim: Int, eps: Float): Norm =
        Norm(1, dim, eps, tensor("$name.weight", dim).values, tensor("$name.bias", dim).values)

    internal fun groupNorm(name: String, groups: Int, dim: Int, eps: Float): Norm =
        Norm(groups, dim, eps, tensor("$name.weight", dim).values, tensor("$name.bias", dim).values)

    companion object {
        private val MAGIC = "ARWKVWEIGHTS1".toByteArray(Charsets.US_ASCII)

        /**
         * Parses [bytes]. When [expectedSha256] is non-null the bytes must hash
         * to it, so a truncated or substituted asset fails loudly instead of
         * scheduling from different weights.
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun parse(bytes: ByteArray, expectedSha256: String? = RwkvAlgorithmInfo.WEIGHTS_SHA256): RwkvWeights {
            if (expectedSha256 != null) {
                val actual = sha256Hex(bytes)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("RWKV weights hash $actual does not match $expectedSha256")
                }
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes.size < MAGIC.size || !MAGIC.indices.all { bytes[it] == MAGIC[it] }) {
                throw IOException("unexpected RWKV weights magic")
            }
            buffer.position(MAGIC.size)
            val count = readU32(buffer)
            val tensors = LinkedHashMap<String, RwkvTensor>(count * 2)
            repeat(count) {
                val nameLength = readU16(buffer)
                val nameBytes = readBytes(buffer, nameLength)
                val name = String(nameBytes, Charsets.UTF_8)
                val rank = readU8(buffer)
                val shape = IntArray(rank)
                var length = 1L
                for (axis in 0 until rank) {
                    shape[axis] = readU32(buffer)
                    length *= shape[axis]
                    if (length > Int.MAX_VALUE) {
                        throw IOException("tensor $name is too large")
                    }
                }
                if (buffer.remaining() < length * 4) {
                    throw IOException("RWKV weights ended early in $name")
                }
                val values = FloatArray(length.toInt())
                buffer.asFloatBuffer().get(values)
                buffer.position(buffer.position() + values.size * 4)
                tensors[name] = RwkvTensor(shape, values)
            }
            if (buffer.hasRemaining()) {
                throw IOException("trailing bytes in RWKV weights")
            }
            return RwkvWeights(tensors)
        }

        @JvmStatic
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun readU8(buffer: ByteBuffer): Int {
            ensure(buffer, 1)
            return buffer.get().toInt() and 0xff
        }

        private fun readU16(buffer: ByteBuffer): Int {
            ensure(buffer, 2)
            return buffer.short.toInt() and 0xffff
        }

        private fun readU32(buffer: ByteBuffer): Int {
            ensure(buffer, 4)
            val value = buffer.int.toLong() and 0xffffffffL
            if (value > Int.MAX_VALUE) {
                throw IOException("RWKV weights count out of range")
            }
            return value.toInt()
        }

        private fun readBytes(buffer: ByteBuffer, length: Int): ByteArray {
            ensure(buffer, length)
            val out = ByteArray(length)
            buffer.get(out)
            return out
        }

        private fun ensure(buffer: ByteBuffer, length: Int) {
            if (buffer.remaining() < length) {
                throw IOException("RWKV weights ended early")
            }
        }
    }
}
