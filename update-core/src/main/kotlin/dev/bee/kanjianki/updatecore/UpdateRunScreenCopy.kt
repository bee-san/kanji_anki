package dev.bee.kanjianki.updatecore

object UpdateRunScreenCopy {
    @JvmStatic
    fun forRun(cachedPending: Boolean): Copy {
        if (cachedPending) {
            return Copy(
                "Preparing installer",
                "Verifying APK",
            )
        }
        return Copy(
            "Checking for updates",
            "Checking releases",
        )
    }

    class Copy(
        private val title: String,
        private val progressLabel: String,
    ) {
        fun title(): String = title
        fun progressLabel(): String = progressLabel
    }
}
