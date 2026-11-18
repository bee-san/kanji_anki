// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.bee.rwkv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RwkvAlgorithmInfoTest {
    @Test
    fun bundledWeightsAreThePinnedUpstreamExport() {
        val bytes = RwkvFixtures.weightsBytes
        assertEquals(RwkvAlgorithmInfo.WEIGHTS_BYTES, bytes.size)
        assertEquals(RwkvAlgorithmInfo.WEIGHTS_SHA256, RwkvWeights.sha256Hex(bytes))
        val parameters = RwkvFixtures.weights.tensors.values.sumOf { it.values.size }
        assertEquals(RwkvAlgorithmInfo.PARAMETER_COUNT, parameters)
    }

    @Test
    fun identityNamesTheModelAndStateLayout() {
        assertEquals(
            "Pedrubik2000/kuma3-anki-core@a8cf880ce2907229d2edcadcd6977d855b7954a8",
            RwkvAlgorithmInfo.upstreamReference(),
        )
        assertTrue(RwkvAlgorithmInfo.stateIdentity().startsWith("sm92-e26-dec1ep32k/4fbc5f96"))
        assertTrue(RwkvAlgorithmInfo.stateIdentity().endsWith("/state-v1"))
    }
}
