package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SyncValidator {
    private SyncValidator() {
    }

    public static List<String> validateModelFields(String actualModelName, List<String> actualFields, RecordsSyncModels.Settings settings) {
        List<String> errors = new ArrayList<>();
        if (actualModelName == null || !actualModelName.equalsIgnoreCase(settings.modelName)) {
            errors.add("Expected note type " + settings.modelName + " but found " + actualModelName + ".");
        }
        Set<String> fieldSet = new LinkedHashSet<>(actualFields);
        for (String required : settings.requiredFields()) {
            if (!fieldSet.contains(required)) {
                errors.add("Configured note type " + settings.modelName + " is missing required field " + required + ".");
            }
        }
        return errors;
    }

    public static String classifyProviderFailure(Throwable error) {
        if (error instanceof SecurityException) {
            return "permanent_permission";
        }
        String message = error == null || error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("field") || message.contains("model") || message.contains("note type")) {
            return "permanent_configuration";
        }
        if (message.contains("permission")) {
            return "permanent_permission";
        }
        return "retryable_provider";
    }
}
