package dev.bee.kanjianki

import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.study.WritingRecognizer
import java.util.concurrent.ExecutorService

object MainActivityRuntimeOverrides {
    @JvmField
    var ankiDroidGateway: AnkiDroidGateway? = null

    @JvmField
    var collectionGateway: CollectionGateway? = null

    @JvmField
    var writingRecognizer: WritingRecognizer? = null

    @JvmField
    var writingRecognizerFactory: WritingRecognizerFactory? = null

    @JvmField
    var installPermission: Boolean? = null

    @JvmField
    var runtimeNotificationPermission: Boolean? = null

    @JvmField
    var notificationsAllowed: Boolean? = null

    fun interface WritingRecognizerFactory {
        fun create(executor: ExecutorService): WritingRecognizer?
    }

    @JvmStatic
    fun setWritingRecognizer(recognizer: WritingRecognizer?) {
        writingRecognizer = recognizer
    }

    @JvmStatic
    fun setWritingRecognizerFactory(factory: WritingRecognizerFactory?) {
        writingRecognizerFactory = factory
    }

    @JvmStatic
    fun setRuntimeNotificationPermission(granted: Boolean?) {
        runtimeNotificationPermission = granted
    }

    @JvmStatic
    fun setNotificationsAllowed(allowed: Boolean?) {
        notificationsAllowed = allowed
    }

    @JvmStatic
    fun setAnkiDroidGateway(gateway: AnkiDroidGateway?) {
        ankiDroidGateway = gateway
    }

    @JvmStatic
    fun setCollectionGateway(gateway: CollectionGateway?) {
        collectionGateway = gateway
    }

    @JvmStatic
    fun setInstallPermission(allowed: Boolean?) {
        installPermission = allowed
    }
}
