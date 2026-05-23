package dev.bee.kanjianki.updatecore

object UpdateRunScreenCopy {
    @JvmStatic
    fun forRun(cachedPending: Boolean): Copy {
        if (cachedPending) {
            return Copy(
                "Starting installer",
                "Using the verified APK already cached by Kani.",
                "Preparing verified APK",
            )
        }
        return Copy(
            "Checking release",
            "Downloading metadata and verifying assets.",
            "Checking GitHub Releases",
        )
    }

    class Copy(
        private val title: String,
        private val body: String,
        private val progressLabel: String,
    ) {
        fun title(): String = title
        fun body(): String = body
        fun progressLabel(): String = progressLabel
    }
}
