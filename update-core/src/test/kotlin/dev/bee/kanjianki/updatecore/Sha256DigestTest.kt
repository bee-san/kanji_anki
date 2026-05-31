package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha256DigestTest {
    @Test
    fun findsFirstDigestInChecksumText() {
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            Sha256Digest.findInText("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA  app.apk"),
        )
    }

    @Test
    fun returnsEmptyWhenNoDigestExists() {
        assertEquals("", Sha256Digest.findInText(null))
        assertEquals("", Sha256Digest.findInText("not a digest"))
    }

    @Test
    fun validatesWholeDigestOnly() {
        assertTrue(Sha256Digest.isDigest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertTrue(Sha256Digest.isDigest(" AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA "))
        assertFalse(Sha256Digest.isDigest(null))
        assertFalse(Sha256Digest.isDigest(""))
        assertFalse(Sha256Digest.isDigest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        assertFalse(Sha256Digest.isDigest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  app.apk"))
    }
}
