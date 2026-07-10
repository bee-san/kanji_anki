package dev.bee.kanjianki.data

import org.json.JSONObject

internal data class FsrsFitSummary(
    val sampleCount: Int,
    val trainingSampleCount: Int,
    val validationSampleCount: Int,
    val defaultTrainingLoss: Double?,
    val defaultValidationLoss: Double?,
    val fittedTrainingLoss: Double?,
    val fittedValidationLoss: Double?,
    val adopted: Boolean,
    val reason: String,
    val fittedAtMillis: Long,
) {
    fun relativeImprovement(): Double? {
        val baseline = defaultValidationLoss ?: return null
        val fitted = fittedValidationLoss ?: return null
        if (!baseline.isFinite() || !fitted.isFinite() || baseline <= 0.0) return null
        return (baseline - fitted) / baseline
    }
}

internal object FsrsFitSummaryCodec {
    fun encode(summary: FsrsFitSummary): String = JSONObject()
        .put("sampleCount", summary.sampleCount)
        .put("trainingSampleCount", summary.trainingSampleCount)
        .put("validationSampleCount", summary.validationSampleCount)
        .putFinite("defaultTrainingLoss", summary.defaultTrainingLoss)
        .putFinite("defaultValidationLoss", summary.defaultValidationLoss)
        .putFinite("fittedTrainingLoss", summary.fittedTrainingLoss)
        .putFinite("fittedValidationLoss", summary.fittedValidationLoss)
        .put("adopted", summary.adopted)
        .put("reason", summary.reason)
        .put("fittedAtMillis", summary.fittedAtMillis)
        .toString()

    fun decode(encoded: String?): FsrsFitSummary? {
        if (encoded.isNullOrBlank()) return null
        return try {
            val json = JSONObject(encoded)
            FsrsFitSummary(
                sampleCount = json.getInt("sampleCount"),
                trainingSampleCount = json.getInt("trainingSampleCount"),
                validationSampleCount = json.getInt("validationSampleCount"),
                defaultTrainingLoss = json.optionalFinite("defaultTrainingLoss"),
                defaultValidationLoss = json.optionalFinite("defaultValidationLoss"),
                fittedTrainingLoss = json.optionalFinite("fittedTrainingLoss"),
                fittedValidationLoss = json.optionalFinite("fittedValidationLoss"),
                adopted = json.getBoolean("adopted"),
                reason = json.getString("reason"),
                fittedAtMillis = json.getLong("fittedAtMillis"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.putFinite(key: String, value: Double?): JSONObject =
        put(key, if (value != null && value.isFinite()) value else JSONObject.NULL)

    private fun JSONObject.optionalFinite(key: String): Double? {
        if (isNull(key)) return null
        return optDouble(key).takeIf { it.isFinite() }
    }
}
