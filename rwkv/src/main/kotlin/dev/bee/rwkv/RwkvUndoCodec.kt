// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.bee.rwkv

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * Binary form of a [RwkvStateUndo] for the undo journal: the pre-answer value
 * of every key an answer wrote, or "absent" for keys the answer created.
 * Stream states are stored at the precision they are persisted at, which is
 * lossless for already-quantized states.
 */
object RwkvUndoCodec {
    private const val MAGIC: Int = 0x4b525531 // "KRU1"

    @JvmStatic
    fun encode(undo: RwkvStateUndo, precisionFor: (Int) -> RwkvStatePrecision): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            writeBlob(out, undo.globals.encode())
            out.writeInt(undo.states.size)
            for ((key, state) in undo.states) {
                out.writeByte(key.stream)
                out.writeLong(key.id)
                writeOptional(out, state?.let { RwkvStateCodec.encode(it, precisionFor(key.stream)) })
            }
            out.writeInt(undo.cards.size)
            for ((id, features) in undo.cards) {
                out.writeLong(id)
                writeOptional(out, features?.encode())
            }
            out.writeInt(undo.encodings.size)
            for ((key, values) in undo.encodings) {
                out.writeByte(key.kind.code)
                out.writeLong(key.id)
                writeOptional(out, values?.let(RwkvEncodingCodec::encode))
            }
            out.writeInt(undo.histories.size)
            for ((id, history) in undo.histories) {
                out.writeLong(id)
                writeOptional(out, history?.encode())
            }
        }
        return bytes.toByteArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(bytes: ByteArray): RwkvStateUndo {
        try {
            DataInputStream(bytes.inputStream()).use { input ->
                if (input.readInt() != MAGIC) {
                    throw IOException("invalid RWKV undo magic")
                }
                val globals = RwkvGlobalFeatures.decode(readBlob(input))
                val states = LinkedHashMap<RwkvStateKey, RwkvModuleState?>()
                repeat(count(input)) {
                    val stream = input.readUnsignedByte()
                    if (stream !in 0 until RwkvArchitecture.STREAMS) {
                        throw IOException("invalid RWKV undo stream $stream")
                    }
                    val key = RwkvStateKey(stream, input.readLong())
                    states[key] = readOptional(input)?.let(RwkvStateCodec::decode)
                }
                val cards = LinkedHashMap<Long, RwkvCardFeatures?>()
                repeat(count(input)) {
                    val id = input.readLong()
                    cards[id] = readOptional(input)?.let(RwkvCardFeatures::decode)
                }
                val encodings = LinkedHashMap<RwkvIdKey, FloatArray?>()
                repeat(count(input)) {
                    val kind = RwkvIdKind.fromCode(input.readUnsignedByte())
                    val key = RwkvIdKey(kind, input.readLong())
                    encodings[key] = readOptional(input)?.let { RwkvEncodingCodec.decode(kind, it) }
                }
                val histories = LinkedHashMap<Long, RwkvCardHistory?>()
                repeat(count(input)) {
                    val id = input.readLong()
                    histories[id] = readOptional(input)?.let(RwkvCardHistory::decode)
                }
                if (input.read() != -1) {
                    throw IOException("trailing bytes in RWKV undo")
                }
                return RwkvStateUndo(globals, states, cards, encodings, histories)
            }
        } catch (error: EOFException) {
            throw IOException("RWKV undo ended early", error)
        }
    }

    private fun count(input: DataInputStream): Int {
        val count = input.readInt()
        if (count !in 0..MAX_ENTRIES) {
            throw IOException("invalid RWKV undo count $count")
        }
        return count
    }

    private fun writeBlob(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readBlob(input: DataInputStream): ByteArray {
        val size = input.readInt()
        if (size !in 0..MAX_BLOB_BYTES) {
            throw IOException("invalid RWKV undo blob size $size")
        }
        return ByteArray(size).also(input::readFully)
    }

    private fun writeOptional(out: DataOutputStream, bytes: ByteArray?) {
        out.writeBoolean(bytes != null)
        if (bytes != null) {
            writeBlob(out, bytes)
        }
    }

    private fun readOptional(input: DataInputStream): ByteArray? = if (input.readBoolean()) readBlob(input) else null

    private const val MAX_ENTRIES = 1_024
    private const val MAX_BLOB_BYTES = 1 shl 20
}
