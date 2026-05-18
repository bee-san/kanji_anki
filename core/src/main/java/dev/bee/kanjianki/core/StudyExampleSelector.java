package dev.bee.kanjianki.core;

public final class StudyExampleSelector {
    private static final String SOURCE_ACTIVE = "active";
    private static final String SOURCE_SUSPENDED = "suspended";

    private StudyExampleSelector() {
    }

    public static RecordsImportModels.Example firstExample(RecordsImportModels.DashboardRow row) {
        if (row == null || row.examples.isEmpty()) {
            return null;
        }
        for (RecordsImportModels.Example example : row.examples) {
            if (SOURCE_ACTIVE.equals(example.sourceType)) {
                return example;
            }
        }
        return row.examples.get(0);
    }

    public static RecordsImportModels.Example wordReadingExample(RecordsImportModels.DashboardRow row) {
        if (row == null || row.examples.isEmpty()) {
            return null;
        }
        RecordsImportModels.Example active = null;
        for (RecordsImportModels.Example example : row.examples) {
            if (SOURCE_SUSPENDED.equals(example.sourceType)) {
                return example;
            }
            if (active == null && SOURCE_ACTIVE.equals(example.sourceType)) {
                active = example;
            }
        }
        return active == null ? row.examples.get(0) : active;
    }

    public static RecordsImportModels.Example exampleForSession(RecordsSchedulerModels.StudySession session) {
        if (session != null && StudyTaskTypes.WORD_READING.equals(session.taskType)) {
            return wordReadingExample(session.row);
        }
        return firstExample(session == null ? null : session.row);
    }
}
