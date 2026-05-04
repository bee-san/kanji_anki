package dev.bee.kanjianki.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.bee.kanjianki.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class ReleaseCheckResult(
    val currentVersion: String,
    val releaseOwner: String,
    val releaseRepo: String,
    val latestTag: String?,
    val latestName: String?,
    val releaseHtmlUrl: String?,
    val releaseNotes: String?,
    val apkAsset: ReleaseAsset?,
    val updateAvailable: Boolean,
    val statusMessage: String,
) {
    val hasApkAsset: Boolean
        get() = apkAsset != null

    val apkAssetName: String
        get() = apkAsset?.name ?: "APK asset"

    val releaseNotesPreview: String
        get() = releaseNotes
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.take(3)
            ?.joinToString("\n")
            ?: ""
}

sealed interface UpdateInstallLaunchResult {
    data class StartedInstaller(val file: File) : UpdateInstallLaunchResult

    data class OpenedPermissionSettings(val message: String) : UpdateInstallLaunchResult
}

class GitHubReleaseUpdater(
    private val context: Context,
) {
    suspend fun checkForUpdate(): ReleaseCheckResult = withContext(Dispatchers.IO) {
        val endpoint =
            "https://api.github.com/repos/${BuildConfig.GITHUB_RELEASE_OWNER}/${BuildConfig.GITHUB_RELEASE_REPO}/releases/latest"
        val payload = requestJson(endpoint)
        val release = JSONObject(payload)
        val latestTag = release.optString("tag_name").ifBlank { null }
        val latestName = release.optString("name").ifBlank { null }
        val releaseHtmlUrl = release.optString("html_url").ifBlank { null }
        val releaseNotes = release.optString("body").ifBlank { null }
        val apkAsset = selectApkAsset(release.optJSONArray("assets"))
        val compare = compareVersions(BuildConfig.VERSION_NAME, latestTag)
        val updateAvailable = when {
            latestTag == null -> false
            compare != null -> compare < 0
            latestTag != BuildConfig.VERSION_NAME -> true
            else -> false
        }
        val statusMessage = when {
            latestTag == null -> "Latest GitHub release has no tag name."
            apkAsset == null -> "Latest GitHub release does not expose an APK asset."
            compare != null && compare < 0 ->
                "Version $latestTag is newer than ${BuildConfig.VERSION_NAME}."

            compare != null -> "Already on ${BuildConfig.VERSION_NAME}."
            latestTag != BuildConfig.VERSION_NAME ->
                "Latest release tag $latestTag differs from ${BuildConfig.VERSION_NAME}; treating it as a newer update."

            else -> "Already on ${BuildConfig.VERSION_NAME}."
        }
        ReleaseCheckResult(
            currentVersion = BuildConfig.VERSION_NAME,
            releaseOwner = BuildConfig.GITHUB_RELEASE_OWNER,
            releaseRepo = BuildConfig.GITHUB_RELEASE_REPO,
            latestTag = latestTag,
            latestName = latestName,
            releaseHtmlUrl = releaseHtmlUrl,
            releaseNotes = releaseNotes,
            apkAsset = apkAsset,
            updateAvailable = updateAvailable && apkAsset != null,
            statusMessage = statusMessage,
        )
    }

    suspend fun downloadLatestApk(release: ReleaseCheckResult): File = withContext(Dispatchers.IO) {
        val asset = release.apkAsset
            ?: error("No APK asset is available for the latest GitHub release.")
        val updateDir = File(context.cacheDir, "release-updates").apply { mkdirs() }
        val tagSlug = release.latestTag?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "latest"
        val fileName = "${tagSlug}-${asset.name}"
        val target = File(updateDir, fileName)
        downloadFile(asset.downloadUrl, target)
        target
    }

    fun launchInstall(apkFile: File): UpdateInstallLaunchResult {
        require(apkFile.exists()) { "Update APK is missing: ${apkFile.absolutePath}" }
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return UpdateInstallLaunchResult.OpenedPermissionSettings(
                "Grant install permission for this app, then tap install again.",
            )
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        @Suppress("DEPRECATION")
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        context.startActivity(installIntent)
        return UpdateInstallLaunchResult.StartedInstaller(apkFile)
    }

    fun openReleasePage(release: ReleaseCheckResult) {
        val target = release.releaseHtmlUrl
            ?: "https://github.com/${release.releaseOwner}/${release.releaseRepo}/releases"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun selectApkAsset(assets: JSONArray?): ReleaseAsset? {
        if (assets == null) {
            return null
        }
        val configuredName = BuildConfig.GITHUB_RELEASE_APK_NAME.trim()
        var fallback: ReleaseAsset? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name").trim()
            val downloadUrl = asset.optString("browser_download_url").trim()
            if (name.isBlank() || downloadUrl.isBlank() || !name.endsWith(".apk", ignoreCase = true)) {
                continue
            }
            val parsed = ReleaseAsset(
                name = name,
                downloadUrl = downloadUrl,
                sizeBytes = asset.optLong("size"),
            )
            if (configuredName.isNotBlank() && name == configuredName) {
                return parsed
            }
            if (fallback == null) {
                fallback = parsed
            }
        }
        return fallback
    }

    private fun requestJson(url: String): String {
        val connection = openConnection(url)
        return try {
            val statusCode = connection.responseCode
            val body = (if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }).bufferedReader().use { it.readText() }

            if (statusCode !in 200..299) {
                val details = extractErrorMessage(body)
                val publicFeedHint =
                    "GitHub release auto-update requires a public release feed or another unauthenticated URL."
                error("GitHub release check failed ($statusCode): $details $publicFeedHint")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(url: String, target: File) {
        val connection = openConnection(url)
        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val body = (connection.errorStream ?: connection.inputStream)
                    .bufferedReader()
                    .use { it.readText() }
                val details = extractErrorMessage(body)
                error("GitHub release APK download failed ($statusCode): $details")
            }

            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "kanji-anki-android/${BuildConfig.VERSION_NAME}")
        }

    private fun compareVersions(current: String, latestTag: String?): Int? {
        val currentParts = parseVersionParts(current) ?: return null
        val latestParts = parseVersionParts(latestTag ?: return null) ?: return null
        val maxSize = maxOf(currentParts.size, latestParts.size)
        for (index in 0 until maxSize) {
            val left = currentParts.getOrElse(index) { 0 }
            val right = latestParts.getOrElse(index) { 0 }
            if (left != right) {
                return left.compareTo(right)
            }
        }
        return 0
    }

    private fun parseVersionParts(raw: String): List<Int>? {
        val normalized = raw.trim()
            .removePrefix("v")
            .substringBefore('+')
            .substringBefore('-')
        if (normalized.isBlank()) {
            return null
        }
        val parts = normalized.split('.')
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return numbers.ifEmpty { null }
    }

    private fun extractErrorMessage(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isBlank()) {
            return "empty response"
        }
        return runCatching {
            JSONObject(trimmed).optString("message").ifBlank { trimmed }
        }.getOrDefault(trimmed)
    }
}
