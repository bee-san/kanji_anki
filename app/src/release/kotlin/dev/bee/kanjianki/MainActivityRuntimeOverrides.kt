package dev.bee.kanjianki

import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.study.WritingRecognizer
import java.util.concurrent.ExecutorService

object MainActivityRuntimeOverrides {
    @JvmField
    val ankiDroidGateway: AnkiDroidGateway? = null

    @JvmField
    val collectionGateway: CollectionGateway? = null

    @JvmField
    val writingRecognizer: WritingRecognizer? = null

    @JvmField
    val writingRecognizerFactory: WritingRecognizerFactory? = null

    @JvmField
    val installPermission: Boolean? = null

    @JvmField
    val runtimeNotificationPermission: Boolean? = null

    @JvmField
    val notificationsAllowed: Boolean? = null

    fun interface WritingRecognizerFactory {
        fun create(executor: ExecutorService): WritingRecognizer?
    }
}
