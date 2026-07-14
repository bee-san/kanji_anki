package dev.bee.kanjianki.backup

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WalSafeSnapshotOperationsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun apiTwentySixThroughTwentyNineFailBeforeTouchingDestination() {
        for (apiLevel in 26..29) {
            val destination = File(temp.root, "unsupported-$apiLevel.db")
            var invoked = false

            expectIOException {
                WalSafeSnapshotOperations.create(destination, apiLevel) {
                    invoked = true
                    it.writeText("must not be written")
                }
            }

            assertFalse("operation invoked at API $apiLevel", invoked)
            assertFalse("destination exists at API $apiLevel", destination.exists())
        }
    }

    @Test
    fun apiThirtyPublishesOnlyARegularNonEmptySnapshot() {
        val destination = File(temp.root, "supported.db")
        val expected = "SQLite format 3\u0000payload".toByteArray()

        WalSafeSnapshotOperations.create(destination, 30) { it.writeBytes(expected) }

        assertTrue(destination.isFile)
        assertArrayEquals(expected, destination.readBytes())
    }

    @Test
    fun partialVacuumFailureDeletesNewOutputAndNeverUsesAFileCopyFallback() {
        val destination = File(temp.root, "partial.db")

        expectIOException {
            WalSafeSnapshotOperations.create(destination, 35) {
                it.writeText("incomplete")
                throw IOException("vacuum failed")
            }
        }

        assertFalse(destination.exists())
    }

    @Test
    fun missingVacuumOutputFailsClosed() {
        val destination = File(temp.root, "missing.db")

        expectIOException {
            WalSafeSnapshotOperations.create(destination, 35) { }
        }

        assertFalse(destination.exists())
    }

    @Test
    fun preExistingDestinationIsPreservedWithoutInvokingVacuum() {
        val destination = temp.newFile("existing.db")
        val existing = "completed snapshot".toByteArray()
        destination.writeBytes(existing)
        var invoked = false

        expectIOException {
            WalSafeSnapshotOperations.create(destination, 35) {
                invoked = true
                it.writeText("replacement")
            }
        }

        assertFalse(invoked)
        assertArrayEquals(existing, destination.readBytes())
    }

    private fun expectIOException(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (_: IOException) {
            // Expected.
        }
    }
}
