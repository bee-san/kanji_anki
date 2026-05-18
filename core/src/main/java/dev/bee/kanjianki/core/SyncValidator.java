package dev.bee.kanjianki.core;

import dev.bee.kanjianki.syncdomain.SyncModelValidator;

import java.util.List;

public final class SyncValidator {
    private SyncValidator() {
    }

    public static List<String> validateModelFields(String actualModelName, List<String> actualFields, RecordsSyncModels.Settings settings) {
        return SyncModelValidator.validateModelFields(actualModelName, actualFields, settings.modelName, settings.requiredFields());
    }

    public static String classifyProviderFailure(Throwable error) {
        return SyncModelValidator.classifyProviderFailure(error);
    }
}
