package dev.bee.kanjianki.core

import dev.bee.kanjianki.updatecore.GitHubReleaseMetadataParser
import dev.bee.kanjianki.updatecore.ReleaseVersion
import dev.bee.kanjianki.updatecore.Sha256Digest

object GitHubReleaseParser {
    @JvmStatic
    fun parseLatest(json: String?): RecordsSchedulerModels.ReleaseInfo {
        val parsed = GitHubReleaseMetadataParser.parseLatest(json)
        val assets = parsed.assets().map { asset ->
            RecordsSchedulerModels.ReleaseAsset(asset.name(), asset.downloadUrl())
        }
        return RecordsSchedulerModels.ReleaseInfo(parsed.tagName(), parsed.htmlUrl(), assets)
    }

    @JvmStatic
    fun isNewerSemver(currentVersion: String?, tagName: String?): Boolean {
        return ReleaseVersion.isNewerSemver(currentVersion, tagName)
    }

    @JvmStatic
    fun parseSha256(checksumText: String?): String {
        return Sha256Digest.findInText(checksumText)
    }

    @JvmStatic
    fun isSha256Digest(checksumText: String?): Boolean {
        return Sha256Digest.isDigest(checksumText)
    }
}
