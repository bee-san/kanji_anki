package dev.bee.kanjianki.data

internal interface SettingsStorage {
    fun get(key: String?): String?
    fun put(key: String?, value: String?)
}
