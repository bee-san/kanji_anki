package dev.bee.kanjianki.core

object WorkloadSettingsPolicy {
    const val MAXIMUM_SAVED_MESSAGE: String = "Maximum saved."
    const val MANUAL_ENABLED_MESSAGE: String = "Manual workload ready."
    const val AUTOMATIC_ENABLED_MESSAGE: String = "Kani will choose today's workload."
    const val WORKLOAD_SAVED_MESSAGE: String = "Today's workload saved."

    @JvmStatic
    fun saveMaximum(maxItems: Int): SaveRequest {
        return SaveRequest.create(
            null,
            null,
            AdaptiveLoadPlanner.normalizeMaxItems(maxItems),
            MAXIMUM_SAVED_MESSAGE,
        )
    }

    @JvmStatic
    fun enableManualMode(): SaveRequest {
        return SaveRequest.create(
            AdaptiveLoadPlanner.MODE_MANUAL,
            null,
            null,
            MANUAL_ENABLED_MESSAGE,
        )
    }

    @JvmStatic
    fun enableAutomaticMode(): SaveRequest {
        return SaveRequest.create(
            AdaptiveLoadPlanner.MODE_AUTO,
            null,
            null,
            AUTOMATIC_ENABLED_MESSAGE,
        )
    }

    @JvmStatic
    fun saveManualWorkload(workloadPercent: Int, maxItems: Int): SaveRequest {
        return SaveRequest.create(
            AdaptiveLoadPlanner.MODE_MANUAL,
            AdaptiveLoadPlanner.snapWorkloadPercent(workloadPercent),
            AdaptiveLoadPlanner.normalizeMaxItems(maxItems),
            WORKLOAD_SAVED_MESSAGE,
        )
    }

    class SaveRequest private constructor(
        @JvmField val mode: String?,
        @JvmField val workloadPercent: Int?,
        @JvmField val maxItems: Int?,
        @JvmField val message: String,
    ) {
        companion object {
            @JvmSynthetic
            fun create(
                mode: String?,
                workloadPercent: Int?,
                maxItems: Int?,
                message: String,
            ): SaveRequest {
                return SaveRequest(mode, workloadPercent, maxItems, message)
            }
        }
    }
}
