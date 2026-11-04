package dev.bee.kanjianki.updatecore

import java.util.Locale

/**
 * Decides what a user is asked, and what the app is allowed to do, once a desktop update
 * is staged (Goal 202).
 *
 * Kani never installs a desktop update silently. Handing an installer to the OS asks for
 * elevation, replaces the running application, and on Windows and macOS may restart the
 * machine's session — so it happens only after an explicit confirmation for that exact
 * version, and never as a side effect of a background check.
 *
 * The prompt names the version and the staged artifact so the user is confirming a
 * concrete file rather than an abstract "update available". A manual channel (the portable
 * tarball) is never offered an install button at all: Kani did not place that install and
 * cannot replace it, so the honest offer is "here is the download".
 */
object DesktopUpdateHandoffPolicy {
    private const val JAPANESE_LANGUAGE = "ja"

    /** What the app may do with a staged artifact, and what it must ask first. */
    sealed interface Handoff {
        /**
         * Ask for confirmation, then open [artifactPath] with the OS installer.
         *
         * [confirmationTitle]/[confirmationBody] name the version and file being
         * installed; a confirmation that does not is not informed consent.
         */
        data class ConfirmThenOpenInstaller(
            val version: String,
            val artifactPath: String,
            val confirmationTitle: String,
            val confirmationBody: String,
        ) : Handoff

        /**
         * Reveal [artifactPath] and let the user install it. Used for the portable
         * tarball, whose install Kani did not create and must not overwrite.
         */
        data class RevealForManualInstall(
            val version: String,
            val artifactPath: String,
            val guidance: String,
        ) : Handoff

        /** Nothing may be handed over: no confirmation is even offered. */
        data class Blocked(val reason: String) : Handoff
    }

    /**
     * The handoff for a [DesktopUpdatePolicy.Outcome.UpdateAvailable] whose artifact was
     * published by [DesktopUpdateStager].
     *
     * [confirmedVersion] is the version the user actually confirmed, or null when nothing
     * has been confirmed yet. It is compared against the offer's version so a
     * confirmation cannot carry over to a different release that arrived in between —
     * consent is per-version, not a standing permission.
     */
    fun handoffFor(
        offer: DesktopUpdatePolicy.Outcome.UpdateAvailable,
        stagedArtifactPath: String?,
    ): Handoff {
        if (stagedArtifactPath.isNullOrBlank()) {
            return Handoff.Blocked(blockedNothingStagedMessage())
        }
        if (offer.manualInstall) {
            return Handoff.RevealForManualInstall(
                version = offer.semanticVersion,
                artifactPath = stagedArtifactPath,
                guidance = manualInstallGuidance(offer.semanticVersion),
            )
        }
        return Handoff.ConfirmThenOpenInstaller(
            version = offer.semanticVersion,
            artifactPath = stagedArtifactPath,
            confirmationTitle = confirmationTitle(offer.semanticVersion),
            confirmationBody = confirmationBody(stagedArtifactPath),
        )
    }

    /**
     * Whether the installer may be opened now.
     *
     * True only when the user confirmed this exact version. A background check, an
     * earlier confirmation of a different version, and a blank confirmation all return
     * false, so no path reaches the OS installer without consent for these bytes.
     */
    fun mayOpenInstaller(handoff: Handoff, confirmedVersion: String?): Boolean {
        if (handoff !is Handoff.ConfirmThenOpenInstaller) return false
        val confirmed = confirmedVersion?.trim().orEmpty()
        return confirmed.isNotEmpty() && confirmed == handoff.version
    }

    fun confirmationTitle(version: String): String = localizedText(
        "Install Kani $version?",
        "Kani $version をインストールしますか？",
    )

    fun confirmationBody(artifactPath: String): String = localizedText(
        "Kani will close and the system installer will open $artifactPath.",
        "Kaniを終了し、システムのインストーラーで $artifactPath を開きます。",
    )

    fun manualInstallGuidance(version: String): String = localizedText(
        "Kani $version was downloaded. Replace your portable install with it to update.",
        "Kani $version をダウンロードしました。ポータブル版を置き換えて更新してください。",
    )

    fun blockedNothingStagedMessage(): String = localizedText(
        "The update has not finished downloading yet.",
        "更新のダウンロードがまだ完了していません。",
    )

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
}
