package dev.bee.kanjianki.core

import dev.bee.kanjianki.syncdomain.SyncModelValidator

object SyncValidator {
    @JvmStatic
    fun validateModelFields(
        actualModelName: String?,
        actualFields: List<String>,
        settings: RecordsSyncModels.Settings,
    ): List<String> {
        return SyncModelValidator.validateModelFields(
            actualModelName,
            actualFields,
            settings.modelName,
            settings.requiredFields(),
        )
    }

    @JvmStatic
    fun classifyProviderFailure(error: Throwable?): String {
        return SyncModelValidator.classifyProviderFailure(error)
    }
}
