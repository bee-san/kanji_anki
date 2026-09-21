package dev.bee.kanjianki.platform

import java.nio.file.Path

enum class UpdatePackageKind {
    APK,
    MSI,
    DEB,
    DMG,
}

data class VerifiedUpdatePackage(
    val file: Path,
    val version: String,
    val kind: UpdatePackageKind,
    val sha256: String,
) {
    init {
        require(version.isNotBlank()) { "update version must not be blank" }
        require(SHA256_DIGEST.matches(sha256)) {
            "update digest must be lowercase SHA-256"
        }
    }
}

enum class UpdateDeliveryResult {
    OPENED,
    UNSUPPORTED,
    FAILED,
}

fun interface UpdateDelivery {
    fun deliver(update: VerifiedUpdatePackage): UpdateDeliveryResult
}

private val SHA256_DIGEST = Regex("[0-9a-f]{64}")
