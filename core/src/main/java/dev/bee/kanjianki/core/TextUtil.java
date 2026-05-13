package dev.bee.kanjianki.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TextUtil {
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern HTML_ENTITY_REGEX = Pattern.compile("[A-Za-z0-9_\\-]+");

    private TextUtil() {
    }

    public static String normalizeJapanese(String value) {
        if (value == null) {
            return "";
        }
        String noHtml = stripHtml(value);
        String normalized = Normalizer.normalize(noHtml, Normalizer.Form.NFKC);
        String withSpaces = normalized.replace('\u3000', ' ');
        return MULTI_WHITESPACE.matcher(withSpaces).replaceAll(" ").trim();
    }

    public static String stripHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return MULTI_WHITESPACE.matcher(htmlEntities(stripHtmlTags(value))).replaceAll(" ").trim();
    }

    private static String stripHtmlTags(String value) {
        StringBuilder out = new StringBuilder(value.length());
        int index = 0;
        boolean done = false;
        while (!done && index < value.length()) {
            TagBounds tag = nextTag(value, index);
            if (tag.missingStart()) {
                out.append(value, index, value.length());
                done = true;
            } else {
                out.append(value, index, tag.start);
                if (tag.missingEnd()) {
                    out.append(value, tag.start, value.length());
                    done = true;
                } else {
                    index = appendTagReplacement(value, out, tag);
                }
            }
        }
        return out.toString();
    }

    private static TagBounds nextTag(String value, int index) {
        int tagStart = value.indexOf('<', index);
        int tagEnd = tagStart < 0 ? -1 : value.indexOf('>', tagStart + 1);
        return new TagBounds(tagStart, tagEnd);
    }

    private static int appendTagReplacement(String value, StringBuilder out, TagBounds tag) {
        if (tag.empty()) {
            out.append(value, tag.start, tag.afterEnd());
            return tag.afterEnd();
        }
        String tagName = openingTagName(value, tag.start + 1, tag.end);
        int skippedContentEnd = skippableContentEnd(value, tag.afterEnd(), tagName);
        if (skippedContentEnd >= 0) {
            appendSkipReplacement(out, tagName);
            return skippedContentEnd;
        }
        out.append(' ');
        return tag.afterEnd();
    }

    private static int skippableContentEnd(String value, int fromIndex, String tagName) {
        if ("rt".equals(tagName) || "style".equals(tagName) || "script".equals(tagName)) {
            return closingTagEnd(value, fromIndex, tagName);
        }
        return -1;
    }

    private static void appendSkipReplacement(StringBuilder out, String tagName) {
        if (!"rt".equals(tagName)) {
            out.append(' ');
        }
    }

    private static String openingTagName(String value, int index, int tagEnd) {
        char first = value.charAt(index);
        if (!Character.isLetter(first)) {
            return "";
        }
        int nameEnd = index + 1;
        while (nameEnd < tagEnd && isTagNameChar(value.charAt(nameEnd))) {
            nameEnd++;
        }
        return value.substring(index, nameEnd).toLowerCase(Locale.ROOT);
    }

    private static boolean isTagNameChar(char value) {
        return Character.isLetterOrDigit(value);
    }

    private static int closingTagEnd(String value, int fromIndex, String tagName) {
        String closingTag = "</" + tagName + ">";
        int maxStart = value.length() - closingTag.length();
        for (int index = fromIndex; index <= maxStart; index++) {
            if (value.regionMatches(true, index, closingTag, 0, closingTag.length())) {
                return index + closingTag.length();
            }
        }
        return -1;
    }

    public static String firstMeaningLine(String value) {
        String stripped = stripHtml(value);
        if (stripped.isEmpty()) {
            return "";
        }
        String[] separators = {"|", ";", "\n", "。"};
        int cut = stripped.length();
        for (String separator : separators) {
            int index = stripped.indexOf(separator);
            if (index >= 0) {
                cut = Math.min(cut, index);
            }
        }
        String result = stripped.substring(0, cut).trim();
        if (result.length() > 96) {
            result = result.substring(0, 93).trim() + "...";
        }
        return result;
    }

    public static List<String> extractKanji(String value) {
        String normalized = normalizeJapanese(value);
        Set<String> out = new LinkedHashSet<>();
        int index = 0;
        while (index < normalized.length()) {
            int cp = normalized.codePointAt(index);
            if (isKanji(cp)) {
                out.add(new String(Character.toChars(cp)));
            }
            index += Character.charCount(cp);
        }
        return new ArrayList<>(out);
    }

    public static boolean isKanji(int cp) {
        return (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x20000 && cp <= 0x2EBEF);
    }

    public static String browserSearchForKanji(String kanji, Records.Settings settings) {
        return String.format(
                Locale.ROOT,
                "note:%s %s:*%s*",
                ankiSearchToken(settings.modelName),
                ankiSearchToken(settings.expressionField),
                ankiSearchValue(kanji)
        );
    }

    private static String ankiSearchToken(String value) {
        String safe = ankiSearchValue(value == null ? "" : value.trim());
        if (HTML_ENTITY_REGEX.matcher(safe).matches()) {
            return safe;
        }
        return "\"" + safe + "\"";
    }

    private static String ankiSearchValue(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    public static String jsonQuote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            appendJsonQuotedChar(out, value.charAt(i));
        }
        out.append('"');
        return out.toString();
    }

    private static void appendJsonQuotedChar(StringBuilder out, char c) {
        switch (c) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> appendJsonDefaultChar(out, c);
        }
    }

    private static void appendJsonDefaultChar(StringBuilder out, char c) {
        if (c < 0x20) {
            out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
        } else {
            out.append(c);
        }
    }

    private static String htmlEntities(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private record TagBounds(int start, int end) {
        private boolean missingStart() {
            return start < 0;
        }

        private boolean missingEnd() {
            return end < 0;
        }

        private boolean empty() {
            return end == start + 1;
        }

        private int afterEnd() {
            return end + 1;
        }
    }
}
