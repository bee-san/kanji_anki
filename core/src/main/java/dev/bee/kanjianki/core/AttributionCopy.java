package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;

public final class AttributionCopy {
    public static final String DICTIONARY_FALLBACK = "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.";
    public static final String KANJIVG_FALLBACK = "KanjiVG stroke data, CC BY-SA 3.0.";

    private AttributionCopy() {
    }

    public static String dictionarySources(String generatedAt, List<Source> sources, List<String> notes) {
        if (sources == null || sources.isEmpty()) {
            return "Dictionary manifest is empty.";
        }
        List<String> lines = new ArrayList<>();
        if (!safe(generatedAt).isEmpty()) {
            lines.add("Generated: " + safe(generatedAt));
        }
        for (Source source : sources) {
            appendSource(lines, source);
        }
        appendNotes(lines, notes);
        return String.join("\n", lines).trim();
    }

    public static void appendSource(List<String> lines, Source source) {
        if (source == null) {
            return;
        }
        lines.add("");
        lines.add(firstNonEmpty(source.name, source.id));
        addSourceLine(lines, "License", source.license);
        addSourceLine(lines, "URL", source.upstreamUrl);
        addSourceLine(lines, "Source", source.sourcePath);
        addSourceLine(lines, "Fetched", source.fetchDate);
        addSourceLine(lines, "Version", firstNonEmpty(
                source.databaseVersion,
                source.version,
                source.dateOfCreation
        ));
        addSourceLine(lines, "SHA-256", source.sourceSha256);
    }

    public static void appendNotes(List<String> lines, List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        lines.add("");
        for (String note : notes) {
            lines.add(safe(note));
        }
    }

    private static void addSourceLine(List<String> lines, String label, String value) {
        String safeValue = safe(value);
        if (!safeValue.isEmpty()) {
            lines.add(label + ": " + safeValue);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            String safeValue = safe(value);
            if (!safeValue.isEmpty()) {
                return safeValue;
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Source {
        public final String id;
        public final String name;
        public final String license;
        public final String upstreamUrl;
        public final String sourcePath;
        public final String fetchDate;
        public final String databaseVersion;
        public final String version;
        public final String dateOfCreation;
        public final String sourceSha256;

        public Source(
                String id,
                String name,
                String license,
                String upstreamUrl,
                String sourcePath,
                String fetchDate,
                String databaseVersion,
                String version,
                String dateOfCreation,
                String sourceSha256
        ) {
            this.id = id;
            this.name = name;
            this.license = license;
            this.upstreamUrl = upstreamUrl;
            this.sourcePath = sourcePath;
            this.fetchDate = fetchDate;
            this.databaseVersion = databaseVersion;
            this.version = version;
            this.dateOfCreation = dateOfCreation;
            this.sourceSha256 = sourceSha256;
        }

    }
}
