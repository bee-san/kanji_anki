package dev.bee.kanjianki.platform

import java.net.URI

fun interface ClipboardService {
    fun setText(label: String, text: String): Boolean
}

data class ShareRequest(
    val title: String,
    val text: String? = null,
    val attachments: List<PlatformFileReference> = emptyList(),
    val mimeType: String? = null,
) {
    init {
        require(title.isNotBlank()) { "share title must not be blank" }
        require(!text.isNullOrBlank() || attachments.isNotEmpty()) {
            "share request must contain text or an attachment"
        }
    }
}

fun interface ShareService {
    fun share(request: ShareRequest): Boolean
}

interface ExternalNavigator {
    fun openUrl(uri: URI): Boolean

    fun openCollectionBrowser(query: String): Boolean
}

enum class NotificationCategory {
    REMINDER,
    SYNC,
    UPDATE,
}

data class NotificationRequest(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
) {
    init {
        require(id.isNotBlank()) { "notification id must not be blank" }
        require(title.isNotBlank()) { "notification title must not be blank" }
        require(body.isNotBlank()) { "notification body must not be blank" }
    }
}

interface NotificationService {
    fun isAvailable(): Boolean

    fun post(request: NotificationRequest): Boolean

    fun cancel(id: String): Boolean
}
