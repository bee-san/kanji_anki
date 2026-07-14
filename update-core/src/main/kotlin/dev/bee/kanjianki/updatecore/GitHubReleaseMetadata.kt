package dev.bee.kanjianki.updatecore

import java.util.ArrayList
import java.util.Collections

class GitHubReleaseMetadata(
    private val tagName: String,
    private val htmlUrl: String,
    assets: List<ReleaseAsset>,
    body: String? = null,
) {
    private val assets: List<ReleaseAsset> = Collections.unmodifiableList(ArrayList(assets))
    private val body: String? = capBody(body)

    fun tagName(): String = tagName
    fun htmlUrl(): String = htmlUrl
    fun assets(): List<ReleaseAsset> = assets
    fun body(): String? = body

    companion object {
        private const val MAX_BODY_LENGTH = 4000

        private fun capBody(body: String?): String? {
            val trimmed = body?.trim()
            if (trimmed.isNullOrEmpty()) return null
            return if (trimmed.length > MAX_BODY_LENGTH) trimmed.substring(0, MAX_BODY_LENGTH) else trimmed
        }
    }

    class ReleaseAsset(
        private val name: String,
        private val downloadUrl: String,
    ) {
        fun name(): String = name
        fun downloadUrl(): String = downloadUrl
    }
}
