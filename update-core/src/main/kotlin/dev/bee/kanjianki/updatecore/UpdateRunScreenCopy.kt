package dev.bee.kanjianki.updatecore

object UpdateRunScreenCopy {
    @JvmStatic
    fun forRun(cachedPending: Boolean): Copy {
        if (cachedPending) {
            return Copy(
                "Starting installer",
                "Preparing verified APK",
            )
        }
        return Copy(
            "Checking release",
            "Checking GitHub Releases",
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
