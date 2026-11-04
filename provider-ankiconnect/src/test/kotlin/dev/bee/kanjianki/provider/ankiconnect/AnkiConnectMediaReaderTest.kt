package dev.bee.kanjianki.provider.ankiconnect

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectMediaReaderTest {
    private val bytes = byteArrayOf(1, 2, 3, 4, 5)
    private val encoded: String = Base64.getEncoder().encodeToString(bytes)

    private fun serving(
        vararg files: Pair<String, String>,
    ): ScriptedAnkiConnectExchange {
        val byName = files.toMap()
        return ScriptedAnkiConnectExchange().onResult("retrieveMediaFile") { body ->
            val requested = requestedFilename(body)
            byName[requested]?.let { """"$it"""" } ?: "false"
        }
    }

    private fun reader(
        exchange: ScriptedAnkiConnectExchange,
        keyProvider: () -> String? = { null },
        maxCachedNames: Int = AnkiConnectMediaReader.MAX_CACHED_NAMES,
    ) = AnkiConnectMediaReader(exchange.transport(), keyProvider, maxCachedNames)

    @Test
    fun readsMediaTheCollectionHas() {
        val exchange = serving("hashi.mp3" to encoded)

        val read = reader(exchange).read("hashi.mp3", 1024)

        assertArrayEquals(bytes, read)
        assertEquals(1, exchange.bodiesFor("retrieveMediaFile").size)
    }

    /**
     * A traversal name is refused *before* the request. Anki resolves the name
     * against its own media directory and would refuse it too, but Kani must not be
     * the thing that asks, and the shared predicate is what keeps the answer the
     * same on every host.
     */
    @Test
    fun refusesUnsafeNamesWithoutSendingARequest() {
        val exchange = serving("hashi.mp3" to encoded)
        val unsafe = listOf(
            "../hashi.mp3",
            "..",
            ".",
            "sub/hashi.mp3",
            "sub\\hashi.mp3",
            "hashi\u0000.mp3",
            "",
            "   ",
        )

        for (name in unsafe) {
            assertNull(name, reader(exchange).read(name, 1024))
            assertNull(name, reader(exchange).metadata(name))
        }
        assertTrue(exchange.bodiesFor("retrieveMediaFile").isEmpty())
    }

    @Test
    fun aNonPositiveCapReadsNothing() {
        val exchange = serving("hashi.mp3" to encoded)

        assertNull(reader(exchange).read("hashi.mp3", 0))
        assertNull(reader(exchange).read("hashi.mp3", -1))
        assertTrue(exchange.bodiesFor("retrieveMediaFile").isEmpty())
    }

    /**
     * The cap is checked against the encoded length first, so an oversize payload
     * is rejected without allocating the decoded array — decoding first would
     * materialize the oversize array in order to discover it was oversize.
     */
    @Test
    fun rejectsAnOversizePayloadBeforeDecoding() {
        val big = Base64.getEncoder().encodeToString(ByteArray(4096))
        val exchange = serving("big.mp3" to big)

        assertNull(reader(exchange).read("big.mp3", 64))
        // The encoded-length pre-check alone is enough to refuse it.
        assertTrue(AnkiConnectMediaReader.minimumDecodedBytes(big.length) > 64)
    }

    /**
     * The second check is the decoded length. Base64 padding means the encoded
     * length is only a lower bound, so a payload that squeaks past the pre-check
     * must still be measured after decoding.
     */
    @Test
    fun rejectsAPayloadThatOnlyExceedsTheCapOnceDecoded() {
        val payload = ByteArray(9) { it.toByte() }
        val text = Base64.getEncoder().encodeToString(payload)
        val exchange = serving("nine.bin" to text)

        // Passes the encoded-length pre-check…
        assertTrue(AnkiConnectMediaReader.minimumDecodedBytes(text.length) <= 8)
        // …and is still refused, because it decodes to 9 bytes.
        assertNull(reader(exchange).read("nine.bin", 8))
        assertArrayEquals(payload, reader(exchange).read("nine.bin", 9))
    }

    /** The lower bound must never exceed what the payload can actually decode to. */
    @Test
    fun theEncodedLowerBoundNeverOverstatesTheDecodedSize() {
        for (size in 0..64) {
            val text = Base64.getEncoder().encodeToString(ByteArray(size))
            assertTrue(
                "size=$size encoded=${text.length}",
                AnkiConnectMediaReader.minimumDecodedBytes(text.length) <= size.toLong(),
            )
        }
    }

    /** AnkiConnect answers `false` for a file it does not have. */
    @Test
    fun aMissingFileReadsNull() {
        assertNull(reader(serving()).read("absent.mp3", 1024))
        assertNull(reader(serving()).metadata("absent.mp3"))
    }

    @Test
    fun malformedBase64ReadsNull() {
        val exchange = serving("broken.mp3" to "not!base64!")

        assertNull(reader(exchange).read("broken.mp3", 1024))
    }

    /**
     * A media miss must not fail a Study load, so transport, error, and protocol
     * failures all degrade to "no media" rather than throwing.
     */
    @Test
    fun everyFailureShapeDegradesToNoMediaRatherThanThrowing() {
        val unreachable = ScriptedAnkiConnectExchange().onRaw("retrieveMediaFile") {
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("refused")
        }
        assertNull(reader(unreachable).read("hashi.mp3", 1024))

        val errored = ScriptedAnkiConnectExchange()
            .onError("retrieveMediaFile", "media folder is not available")
        assertNull(reader(errored).read("hashi.mp3", 1024))

        val garbled = ScriptedAnkiConnectExchange().onRaw("retrieveMediaFile") {
            AnkiConnectTransport.HttpExchange.Result.Ok(200, "not json")
        }
        assertNull(reader(garbled).read("hashi.mp3", 1024))
    }

    /**
     * AnkiConnect exposes no modification time, and a fabricated one would poison
     * the caller's file-identity cache — which treats an unchanged fingerprint as
     * "safe to reuse the parsed index".
     */
    @Test
    fun metadataReportsTheRealSizeAndNoFabricatedTimestamp() {
        val metadata = reader(serving("hashi.mp3" to encoded)).metadata("hashi.mp3")

        assertNotNull(metadata)
        assertEquals("hashi.mp3", metadata!!.name)
        assertEquals(bytes.size.toLong(), metadata.sizeBytes)
        assertEquals(0L, metadata.modifiedAtMillis)
    }

    /**
     * The size cache exists for exactly one reason: a metadata-then-read pair
     * should not retrieve the file twice.
     */
    @Test
    fun aMetadataProbeIsNotRepeatedForTheSameName() {
        val exchange = serving("hashi.mp3" to encoded)
        val reader = reader(exchange)

        reader.read("hashi.mp3", 1024)
        val requestsAfterRead = exchange.bodiesFor("retrieveMediaFile").size
        assertEquals(bytes.size.toLong(), reader.metadata("hashi.mp3")!!.sizeBytes)

        assertEquals(requestsAfterRead, exchange.bodiesFor("retrieveMediaFile").size)
    }

    /**
     * The cache is bounded and evicts the oldest name. It is a probe-deduplicator,
     * not a media index — and it caches sizes only, never bytes, or the byte caps
     * above would be pointless.
     */
    @Test
    fun theNameCacheIsBoundedAndEvictsTheOldest() {
        val exchange = ScriptedAnkiConnectExchange()
            .onResult("retrieveMediaFile", """"$encoded"""")
        val reader = reader(exchange, maxCachedNames = 2)

        assertNotNull(reader.read("a.mp3", 1024))
        assertNotNull(reader.read("b.mp3", 1024))
        assertNotNull(reader.read("c.mp3", 1024))
        val requestsAfterReads = exchange.bodiesFor("retrieveMediaFile").size

        // b and c are still cached: metadata answers without a retrieval.
        reader.metadata("b.mp3")
        reader.metadata("c.mp3")
        assertEquals(requestsAfterReads, exchange.bodiesFor("retrieveMediaFile").size)
        // a was evicted, so its metadata costs a fresh retrieval.
        reader.metadata("a.mp3")
        assertEquals(requestsAfterReads + 1, exchange.bodiesFor("retrieveMediaFile").size)
    }

    /**
     * The identity is the endpoint, which is what distinguishes one machine's media
     * root from another's. It must not embed a key or any other secret, because
     * callers persist it alongside their parse caches.
     */
    @Test
    fun cacheIdentityNamesTheEndpointAndCarriesNoSecret() {
        val exchange = serving()
        val identity = reader(exchange, keyProvider = { "s3cret" }).cacheIdentity()

        assertTrue(identity, identity.startsWith("ankiconnect:"))
        assertTrue(identity, identity.contains("127.0.0.1"))
        assertFalse(identity, identity.contains("s3cret"))
    }

    @Test
    fun forwardsTheApiKey() {
        val exchange = serving("hashi.mp3" to encoded)

        reader(exchange, keyProvider = { "s3cret" }).read("hashi.mp3", 1024)

        assertTrue(exchange.bodiesFor("retrieveMediaFile").single().contains("s3cret"))
    }

    /** The filename a `retrieveMediaFile` request body asked for. */
    private fun requestedFilename(body: String): String? {
        val params = (AnkiConnectJson.decode(body) as? AnkiConnectJson.Json.Obj)
            ?.entries?.get("params") as? AnkiConnectJson.Json.Obj
            ?: return null
        return (params.entries["filename"] as? AnkiConnectJson.Json.Str)?.value
    }
}
