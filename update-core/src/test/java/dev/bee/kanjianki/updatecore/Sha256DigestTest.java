package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Sha256DigestTest {
    @Test
    public void findsFirstDigestInChecksumText() {
        assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Sha256Digest.findInText("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA  app.apk")
        );
    }

    @Test
    public void returnsEmptyWhenNoDigestExists() {
        assertEquals("", Sha256Digest.findInText(null));
        assertEquals("", Sha256Digest.findInText("not a digest"));
    }

    @Test
    public void validatesWholeDigestOnly() {
        assertTrue(Sha256Digest.isDigest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertTrue(Sha256Digest.isDigest(" AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA "));
        assertFalse(Sha256Digest.isDigest(null));
        assertFalse(Sha256Digest.isDigest(""));
        assertFalse(Sha256Digest.isDigest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(Sha256Digest.isDigest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  app.apk"));
    }
}
