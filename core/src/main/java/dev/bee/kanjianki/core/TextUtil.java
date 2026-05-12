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
    private static final Pattern RT_TAG = Pattern.compile("(?is)<rt[^>]*>.*?</rt>");
    private static final Pattern STYLE_TAG = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern SCRIPT_TAG = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern GENERIC_TAG = Pattern.compile("(?is)<[^>]+>");

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
        String stripped = value;
        String withoutRt = RT_TAG.matcher(stripped).replaceAll("");
        String withoutStyle = STYLE_TAG.matcher(withoutRt).replaceAll(" ");
        String withoutScript = SCRIPT_TAG.matcher(withoutStyle).replaceAll(" ");
        String withoutTags = GENERIC_TAG.matcher(withoutScript).replaceAll(" ");
        return MULTI_WHITESPACE.matcher(htmlEntities(withoutTags)).replaceAll(" ").trim();
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
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
        return out.toString();
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
}
