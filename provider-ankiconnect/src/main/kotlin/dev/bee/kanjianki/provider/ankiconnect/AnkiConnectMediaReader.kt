package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.platform.ReadingMediaMetadata
import dev.bee.kanjianki.platform.ReadingMediaSource
import dev.bee.kanjianki.platform.isSafeMediaName
import java.util.Base64

/**
 * Reads media out of Anki's collection.media over AnkiConnect, as a
 * [ReadingMediaSource] so the Study/reading-exposure path does not know or care
 * which provider served the bytes.
 *
 * This is the narrowest read surface in the adapter, and every bound below exists
 * because `retrieveMediaFile` is the one action where the *response* size is
 * controlled by the collection rather than by Kani's own batching:
 *
 * - **Filename validation happens before the request, not after.** A name with a
 *   path separator, a `..` segment, or a NUL is refused locally by the shared
 *   [isSafeMediaName] predicate. Anki resolves the name against its media
 *   directory, so a traversal attempt is Anki's problem to refuse — but Kani must
 *   not be the thing that asks.
 * - **Never writes to disk.** Bytes are returned to the caller and never
 *   persisted, so there is no path for a collection-controlled filename to become
 *   a filesystem write. This is why the class implements only the read side of the
 *   contract and holds no directory.
 * - **Byte cap enforced twice.** `maximumBytes` is checked against the decoded
 *   length, and the base64 payload is rejected before decoding when its encoded
 *   length alone already exceeds the cap — decoding first would materialize an
 *   oversize array in order to discover it was oversize. The transport's own
 *   response cap is the outer bound.
 * - **A bounded name cache, and no byte cache.** AnkiConnect exposes no
 *   modification time, so [metadata] costs a full retrieval; the size is cached
 *   per name (bounded by [MAX_CACHED_NAMES], oldest evicted) so a
 *   metadata-then-read pair does not fetch twice, while the bytes themselves are
 *   never held. Caching the bytes here would defeat the point of the caps.
 */
class AnkiConnectMediaReader(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
    private val maxCachedNames: Int = MAX_CACHED_NAMES,
) : ReadingMediaSource {
    /**
     * Sizes observed per media name, insertion-ordered so the oldest entry is the
     * one evicted. Guarded by [lock]: a Study load may probe several names at once.
     */
    private val sizeByName = LinkedHashMap<String, Long>()
    private val lock = Any()

    /**
     * The endpoint identifies the media root: two profiles on one machine answer
     * on the same endpoint, but they are also never open at the same time, so the
     * endpoint is a sufficient cache identity for invalidating parse caches when
     * a host switches media roots.
     */
    override fun cacheIdentity(): String = "ankiconnect:${transport.endpointUrl()}"

    /**
     * The file's size, or null when Anki has no such media.
     *
     * AnkiConnect has no stat action, so this is a full retrieval with the size
     * kept and the bytes dropped. [ReadingMediaMetadata.modifiedAtMillis] is
     * therefore reported as 0: a fabricated timestamp would silently poison the
     * caller's file-identity cache, which treats an unchanged fingerprint as
     * "safe to reuse the parsed index".
     */
    override fun metadata(name: String): ReadingMediaMetadata? {
        if (!isSafeMediaName(name)) return null
        val cached = synchronized(lock) { sizeByName[name] }
        val size = cached ?: read(name, MAX_METADATA_PROBE_BYTES)?.size?.toLong() ?: return null
        return ReadingMediaMetadata(name = name, sizeBytes = size, modifiedAtMillis = 0L)
    }

    override fun read(name: String, maximumBytes: Int): ByteArray? {
        if (maximumBytes <= 0 || !isSafeMediaName(name)) return null
        val request = AnkiConnectRequests.retrieveMediaFile(name, keyProvider())
        val body = when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> exchange.text
            // A media miss must not fail a Study load, so transport and protocol
            // failures alike degrade to "no media" rather than throwing.
            is AnkiConnectTransport.Exchange.Failure -> return null
        }
        val encoded = when (val response = AnkiConnectEnvelope.parse(body)) {
            is AnkiConnectEnvelope.Response.Ok -> when (val result = response.result) {
                // AnkiConnect answers `false` when the file does not exist.
                is AnkiConnectJson.Json.Str -> result.value
                else -> return null
            }
            is AnkiConnectEnvelope.Response.Failed -> return null
            AnkiConnectEnvelope.Response.ProtocolError -> return null
        }
        // Reject before decoding: base64 is 4/3 of the payload, so an encoded
        // length past the cap cannot decode to anything within it.
        if (minimumDecodedBytes(encoded.length) > maximumBytes) return null
        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (decoded.size > maximumBytes) return null
        rememberSize(name, decoded.size.toLong())
        return decoded
    }

    private fun rememberSize(name: String, size: Long) {
        synchronized(lock) {
            sizeByName.remove(name)
            sizeByName[name] = size
            while (sizeByName.size > maxCachedNames) {
                val oldest = sizeByName.keys.first()
                sizeByName.remove(oldest)
            }
        }
    }

    companion object {
        /**
         * Names whose observed size is remembered. Small on purpose: this exists
         * to stop the metadata-then-read pair from costing two retrievals, not to
         * be a media index.
         */
        const val MAX_CACHED_NAMES = 64

        /**
         * The cap [metadata] probes with when the size is not already known. It
         * matches the reading-exposure stats budget, which is the largest media
         * Kani's Study flow reads; a bigger file is not media this reader is for.
         */
        const val MAX_METADATA_PROBE_BYTES = 32 * 1024 * 1024

        /**
         * The smallest number of bytes a base64 payload of [encodedLength] can
         * decode to (i.e. assuming maximum padding). Used to reject an oversize
         * response before allocating the decoded array.
         */
        fun minimumDecodedBytes(encodedLength: Int): Long =
            (encodedLength.toLong() / 4L) * 3L - 2L
    }
}
