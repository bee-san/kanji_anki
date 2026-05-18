package dev.bee.kanjianki.core;

import java.util.Locale;
import java.util.regex.Pattern;

final class DictionaryTextUtil {
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private DictionaryTextUtil() {
    }

    static String stripHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return MULTI_WHITESPACE.matcher(htmlEntities(stripHtmlTags(value))).replaceAll(" ").trim();
    }

    static boolean isKanji(int cp) {
        return (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x20000 && cp <= 0x2EBEF);
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
