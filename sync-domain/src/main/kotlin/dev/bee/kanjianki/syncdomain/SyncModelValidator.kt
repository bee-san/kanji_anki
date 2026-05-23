package dev.bee.kanjianki.syncdomain

import java.util.Locale

class SyncModelValidator private constructor() {
    companion object {
        @JvmStatic
        fun validateModelFields(
            actualModelName: String?,
            actualFields: List<String>,
            expectedModelName: String,
            requiredFields: List<String>,
        ): List<String> {
            val errors = ArrayList<String>()
            if (actualModelName == null || !actualModelName.equals(expectedModelName, ignoreCase = true)) {
                errors.add("Expected note type $expectedModelName but found $actualModelName.")
            }
            val fieldSet = LinkedHashSet(actualFields)
            for (required in requiredFields) {
                if (!fieldSet.contains(required)) {
                    errors.add("Configured note type $expectedModelName is missing required field $required.")
                }
            }
            return errors
        }

        @JvmStatic
        fun classifyProviderFailure(error: Throwable?): String {
            if (error is SecurityException) {
                return "permanent_permission"
            }
            val message = error?.message?.lowercase(Locale.ROOT) ?: ""
            if (message.contains("field") || message.contains("model") || message.contains("note type")) {
                return "permanent_configuration"
            }
            if (message.contains("permission")) {
                return "permanent_permission"
            }
            return "retryable_provider"
        }
    }
}
