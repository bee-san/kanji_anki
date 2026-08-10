package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncapi.CollectionFailure

/**
 * Opens the user's own Anki card browser on an exact Kani search.
 *
 * This is the desktop counterpart of the Android "copy the Anki search" affordance,
 * and it is deliberately a handoff rather than a feature: Kani shows a kanji's
 * browser search, and this puts the user in front of *that* search in Anki, with
 * their own note types, cards, and scheduling in view. Nothing is read back —
 * `guiBrowse` returns matched card ids and Kani discards them, because inspecting
 * the result would make this a read path with a read path's obligations
 * (capability gating, bounds, source binding) for no benefit.
 *
 * The query is passed through verbatim. Rewriting or "fixing" it here would break
 * the one property that makes the handoff trustworthy: what the user sees in Anki
 * is exactly what Kani said it would show them. Kani's queries come from
 * `TextUtil.browserSearchForKanji` and `ProviderNotePolicy`, which already quote
 * and escape.
 */
class AnkiConnectBrowseHandoff(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
) {
    /**
     * Asks Anki to show [query] in its browser and bring the window forward.
     *
     * @return true when Anki accepted the handoff. A blank query is refused
     *   locally, because AnkiConnect would answer it by selecting the entire
     *   collection.
     * @throws CollectionFailure when Anki is unreachable, refused the request, or
     *   does not support `guiBrowse` (older AnkiConnect builds); the caller falls
     *   back to showing the query for the user to copy.
     */
    @Throws(CollectionFailure::class)
    fun browse(query: String): Boolean {
        if (query.isBlank()) return false
        val request = AnkiConnectRequests.guiBrowse(query, keyProvider())
        val body = when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> exchange.text
            is AnkiConnectTransport.Exchange.Failure ->
                throw AnkiConnectStatusMapping.transportFailure(exchange)
        }
        return when (val response = AnkiConnectEnvelope.parse(body)) {
            // The result is the matched card id array, intentionally unused.
            is AnkiConnectEnvelope.Response.Ok -> true
            is AnkiConnectEnvelope.Response.Failed ->
                throw AnkiConnectStatusMapping.failureFor(response.message)
            AnkiConnectEnvelope.Response.ProtocolError ->
                throw AnkiConnectStatusMapping.protocolFailure(request.action)
        }
    }
}
