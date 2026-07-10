package dev.bee.kanjianki.data

internal interface SettingsStorage {
    fun get(key: String?): String?

    /** True when the current thread owns a storage transaction with uncommitted writes. */
    fun isTransactionOwner(): Boolean = false

    /**
     * Returns one point-in-time copy of every setting when the storage can do so efficiently.
     * Test and alternate stores may return null to retain the single-key fallback.
     */
    fun getAll(): Map<String, String>? = null

    fun put(key: String?, value: String?)
}
