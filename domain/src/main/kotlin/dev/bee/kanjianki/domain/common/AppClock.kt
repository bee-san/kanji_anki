package dev.bee.kanjianki.domain.common

interface AppClock {
    fun nowMillis(): Long
}
