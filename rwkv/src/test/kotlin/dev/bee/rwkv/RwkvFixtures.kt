// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.bee.rwkv

import java.io.File
import java.util.zip.GZIPInputStream

/** One row of the published SM92 parity vectors (`rslib/src/rwkv/testdata/sm92-vectors.json.gz`). */
internal class VectorRow(
    val features: FloatArray,
    /** `[card, deck, note, preset, user]`. */
    val ids: LongArray,
    val skip: Boolean,
    val elapsed: Float,
    val fp32: FloatArray,
    /** The row's own `(elapsed seconds, elapsed days)` interval inputs. */
    val interval: DoubleArray?,
)

internal class VectorUser(val name: String, val sweep: FloatArray, val rows: List<VectorRow>)

internal object RwkvFixtures {
    val weightsBytes: ByteArray by lazy { File(requireProperty("kani.rwkv.weights")).readBytes() }
    val weights: RwkvWeights by lazy { RwkvWeights.parse(weightsBytes) }
    val model: RwkvModel by lazy { RwkvModel.load(weights) }

    val vectors: List<VectorUser> by lazy {
        val text = GZIPInputStream(File(requireProperty("kani.rwkv.vectors")).inputStream()).bufferedReader().readText()
        TestJson.parse(text).asList().map { rawUser ->
            val user = rawUser.asObject()
            VectorUser(
                name = user["name"] as String,
                sweep = user["sweep"].asFloats(),
                rows = user["rows"].asList().map { rawRow ->
                    val row = rawRow.asObject()
                    VectorRow(
                        features = row["features"].asFloats(),
                        ids = row["ids"].asList().map { (it as Double).toLong() }.toLongArray(),
                        skip = row["skip"] as Boolean,
                        elapsed = (row["elapsed"] as Double).toFloat(),
                        fp32 = row["fp32"].asFloats(),
                        interval = (row["interval"] as List<*>?)?.map { it as Double }?.toDoubleArray(),
                    )
                },
            )
        }
    }

    private fun requireProperty(name: String): String =
        System.getProperty(name) ?: error("system property $name is not set (run through Gradle)")
}
