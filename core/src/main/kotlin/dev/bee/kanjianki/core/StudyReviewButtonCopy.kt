package dev.bee.kanjianki.core

object StudyReviewButtonCopy {
    private const val LABEL_AGAIN = "Again"
    private const val LABEL_GOOD = "Good"
    private const val DESCRIPTION_AGAIN = "Again: show this card again sooner"
    private const val DESCRIPTION_GOOD = "Good: keep the next review on schedule"

    @JvmStatic
    fun againLabel(): String = LABEL_AGAIN

    @JvmStatic
    fun goodLabel(): String = LABEL_GOOD

    @JvmStatic
    fun againContentDescription(): String = DESCRIPTION_AGAIN

    @JvmStatic
    fun goodContentDescription(): String = DESCRIPTION_GOOD
}
