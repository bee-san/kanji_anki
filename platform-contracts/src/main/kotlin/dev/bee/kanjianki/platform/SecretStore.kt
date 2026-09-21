package dev.bee.kanjianki.platform

import java.util.Arrays

class SecretReference private constructor(
    val value: String,
) {
    init {
        require(VALID_REFERENCE.matches(value)) {
            "secret reference must use a stable lowercase identifier"
        }
    }

    override fun toString(): String = "SecretReference([REDACTED])"

    override fun equals(other: Any?): Boolean =
        other is SecretReference && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        private val VALID_REFERENCE = Regex("[a-z0-9][a-z0-9._-]{0,127}")

        @JvmStatic
        fun create(value: String): SecretReference = SecretReference(value)
    }
}

class SecretValue private constructor(
    value: CharArray,
) : AutoCloseable {
    private var value: CharArray? = value.copyOf()

    fun <T> withValue(block: (CharArray) -> T): T {
        val current = checkNotNull(value) { "secret value is closed" }
        val copy = current.copyOf()
        return try {
            block(copy)
        } finally {
            Arrays.fill(copy, '\u0000')
        }
    }

    override fun close() {
        value?.let { Arrays.fill(it, '\u0000') }
        value = null
    }

    override fun toString(): String = "SecretValue([REDACTED])"

    companion object {
        @JvmStatic
        fun create(value: CharArray): SecretValue = SecretValue(value)

        @JvmStatic
        fun create(value: String): SecretValue = SecretValue(value.toCharArray())
    }
}

enum class SecretPersistence {
    SESSION_ONLY,
    OS_CREDENTIAL_STORE,
}

interface SecretStore {
    val persistence: SecretPersistence

    fun read(reference: SecretReference): SecretValue?

    fun write(reference: SecretReference, value: SecretValue): Boolean

    fun delete(reference: SecretReference): Boolean
}
