package dev.bee.kanjianki.time

fun interface AppClock {
    fun nowMillis(): Long

    companion object {
        @JvmField
        val SYSTEM: AppClock = AppClock { System.currentTimeMillis() }

        @JvmStatic
        fun systemClock(): AppClock = SYSTEM

        @JvmStatic
        fun orSystem(clock: AppClock?): AppClock {
            return clock ?: SYSTEM
        }
    }
}
