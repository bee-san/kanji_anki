package dev.bee.kanjianki.updatecore

import java.util.ArrayList
import java.util.Collections

class GitHubReleaseMetadata(
    private val tagName: String,
    private val htmlUrl: String,
    assets: List<ReleaseAsset>,
) {
    private val assets: List<ReleaseAsset> = Collections.unmodifiableList(ArrayList(assets))

    fun tagName(): String = tagName
    fun htmlUrl(): String = htmlUrl
    fun assets(): List<ReleaseAsset> = assets

    class ReleaseAsset(
        private val name: String,
        private val downloadUrl: String,
    ) {
        fun name(): String = name
        fun downloadUrl(): String = downloadUrl
    }
}
