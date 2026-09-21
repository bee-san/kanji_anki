package dev.bee.kanjianki.syncapi

import java.security.SecureRandom
import java.util.Base64
import java.util.Collections

enum class SourceBindingValidationState {
    VALIDATED,
    REVALIDATION_REQUIRED,
}

class PersistedSourceBinding(
    @JvmField val version: Int,
    @JvmField val providerKindDigest: String,
    @JvmField val sourceKeyDigest: String,
    @JvmField val bindingSalt: String,
    noteIdDigests: List<String>,
    cardIdDigests: List<String>,
    @JvmField val validationState: SourceBindingValidationState,
    @JvmField val lastValidatedAtMillis: Long,
) {
    @JvmField
    val noteIdDigests: List<String> =
        Collections.unmodifiableList(ArrayList(noteIdDigests))

    @JvmField
    val cardIdDigests: List<String> =
        Collections.unmodifiableList(ArrayList(cardIdDigests))

    init {
        require(version > 0) { "binding version must be positive" }
        require(DIGEST.matches(providerKindDigest)) { "invalid provider-kind digest" }
        require(DIGEST.matches(sourceKeyDigest)) { "invalid source-key digest" }
        require(bindingSalt.isNotBlank()) { "binding salt must not be blank" }
        require(noteIdDigests.size <= CollectionSourceIdentity.MAX_IDS_PER_KIND)
        require(cardIdDigests.size <= CollectionSourceIdentity.MAX_IDS_PER_KIND)
        require(noteIdDigests.all(DIGEST::matches)) { "invalid note-ID digest" }
        require(cardIdDigests.all(DIGEST::matches)) { "invalid card-ID digest" }
        require(noteIdDigests.size == noteIdDigests.toSet().size) {
            "duplicate note-ID digest"
        }
        require(cardIdDigests.size == cardIdDigests.toSet().size) {
            "duplicate card-ID digest"
        }
        require(noteIdDigests.toSet().intersect(cardIdDigests.toSet()).isEmpty()) {
            "note and card ID digests must be domain-separated"
        }
        require(lastValidatedAtMillis >= 0L) { "validation time must not be negative" }
    }

    override fun toString(): String =
        "PersistedSourceBinding(version=$version, validationState=$validationState, " +
            "noteSample=${noteIdDigests.size}, cardSample=${cardIdDigests.size}, redacted=true)"

    override fun equals(other: Any?): Boolean =
        other is PersistedSourceBinding &&
            version == other.version &&
            providerKindDigest == other.providerKindDigest &&
            sourceKeyDigest == other.sourceKeyDigest &&
            bindingSalt == other.bindingSalt &&
            noteIdDigests == other.noteIdDigests &&
            cardIdDigests == other.cardIdDigests &&
            validationState == other.validationState &&
            lastValidatedAtMillis == other.lastValidatedAtMillis

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + providerKindDigest.hashCode()
        result = 31 * result + sourceKeyDigest.hashCode()
        result = 31 * result + bindingSalt.hashCode()
        result = 31 * result + noteIdDigests.hashCode()
        result = 31 * result + cardIdDigests.hashCode()
        result = 31 * result + validationState.hashCode()
        result = 31 * result + lastValidatedAtMillis.hashCode()
        return result
    }

    companion object {
        const val CURRENT_VERSION: Int = 1
        internal val DIGEST = Regex("[0-9a-f]{64}")
    }
}

interface SourceBindingStore {
    fun load(): PersistedSourceBinding?

    fun save(binding: PersistedSourceBinding)

    fun clear()
}

object SourceBindingSalts {
    private const val SALT_BYTES = 32

    @JvmStatic
    fun random(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

object SourceBindingRecordCodec {
    const val KEY_VERSION = "collection_source_binding.version"
    const val KEY_PROVIDER_KIND_DIGEST = "collection_source_binding.provider_kind_digest"
    const val KEY_SOURCE_KEY_DIGEST = "collection_source_binding.source_key_digest"
    const val KEY_SALT = "collection_source_binding.salt"
    const val KEY_NOTE_ID_DIGESTS = "collection_source_binding.note_id_digests"
    const val KEY_CARD_ID_DIGESTS = "collection_source_binding.card_id_digests"
    const val KEY_VALIDATION_STATE = "collection_source_binding.validation_state"
    const val KEY_LAST_VALIDATED_AT = "collection_source_binding.last_validated_at"

    @JvmField
    val keys: Set<String> = Collections.unmodifiableSet(
        linkedSetOf(
            KEY_VERSION,
            KEY_PROVIDER_KIND_DIGEST,
            KEY_SOURCE_KEY_DIGEST,
            KEY_SALT,
            KEY_NOTE_ID_DIGESTS,
            KEY_CARD_ID_DIGESTS,
            KEY_VALIDATION_STATE,
            KEY_LAST_VALIDATED_AT,
        ),
    )

    @JvmStatic
    fun encode(binding: PersistedSourceBinding): Map<String, String> =
        linkedMapOf(
            KEY_VERSION to binding.version.toString(),
            KEY_PROVIDER_KIND_DIGEST to binding.providerKindDigest,
            KEY_SOURCE_KEY_DIGEST to binding.sourceKeyDigest,
            KEY_SALT to binding.bindingSalt,
            KEY_NOTE_ID_DIGESTS to binding.noteIdDigests.joinToString(","),
            KEY_CARD_ID_DIGESTS to binding.cardIdDigests.joinToString(","),
            KEY_VALIDATION_STATE to binding.validationState.name,
            KEY_LAST_VALIDATED_AT to binding.lastValidatedAtMillis.toString(),
        )

    @JvmStatic
    fun decode(values: Map<String, String>): PersistedSourceBinding? {
        val present = values.filterKeys { it in keys }
        if (present.isEmpty()) return null
        require(present.keys == keys) { "collection source binding record is incomplete" }
        return PersistedSourceBinding(
            version = present.getValue(KEY_VERSION).toInt(),
            providerKindDigest = present.getValue(KEY_PROVIDER_KIND_DIGEST),
            sourceKeyDigest = present.getValue(KEY_SOURCE_KEY_DIGEST),
            bindingSalt = present.getValue(KEY_SALT),
            noteIdDigests = decodeDigests(present.getValue(KEY_NOTE_ID_DIGESTS)),
            cardIdDigests = decodeDigests(present.getValue(KEY_CARD_ID_DIGESTS)),
            validationState = SourceBindingValidationState.valueOf(
                present.getValue(KEY_VALIDATION_STATE),
            ),
            lastValidatedAtMillis = present.getValue(KEY_LAST_VALIDATED_AT).toLong(),
        )
    }

    private fun decodeDigests(encoded: String): List<String> =
        if (encoded.isBlank()) emptyList() else encoded.split(',')
}

enum class SourceBindingAction {
    VALIDATE,
    FIRST_BIND,
    REBIND,
}

enum class SourceBindingDecisionKind {
    ALLOW,
    FIRST_BIND_REQUIRED,
    REBIND_REQUIRED,
    REJECT,
}

enum class SourceBindingResetScope {
    NONE,
    PROVIDER_PROJECTIONS_AND_WRITE_RECEIPTS,
}

enum class SourceBindingReason {
    VALIDATED,
    FIRST_BIND_REQUIRED,
    UNKNOWN_ORIGIN,
    PROVIDER_KIND_CHANGED,
    SOURCE_KEY_CHANGED,
    NO_STABLE_IDS,
    INSUFFICIENT_OVERLAP,
    EXPLICIT_BIND,
    EXPLICIT_REBIND,
    BACKUP_REQUIRED,
    FRESH_SALT_REQUIRED,
    UNSUPPORTED_VERSION,
}

data class SourceBindingRequest(
    val persisted: PersistedSourceBinding?,
    val candidate: CollectionSourceIdentity,
    val databaseIsEmpty: Boolean,
    val action: SourceBindingAction = SourceBindingAction.VALIDATE,
    val backupConfirmed: Boolean = false,
    val replacementSalt: String? = null,
    val nowMillis: Long,
) {
    init {
        require(nowMillis >= 0L) { "decision time must not be negative" }
    }
}

data class SourceBindingDecision(
    val kind: SourceBindingDecisionKind,
    val reason: SourceBindingReason,
    val bindingToPersist: PersistedSourceBinding? = null,
    val resetScope: SourceBindingResetScope = SourceBindingResetScope.NONE,
) {
    val allowsCollectionAccess: Boolean
        get() = kind == SourceBindingDecisionKind.ALLOW
}
