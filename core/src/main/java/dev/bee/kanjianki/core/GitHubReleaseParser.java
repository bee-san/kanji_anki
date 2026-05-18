package dev.bee.kanjianki.core;

import dev.bee.kanjianki.updatecore.ReleaseVersion;
import dev.bee.kanjianki.updatecore.Sha256Digest;

import java.util.ArrayList;
import java.util.List;

public final class GitHubReleaseParser {
    private static final int UNICODE_ESCAPE_HEX_LENGTH = 4;
    private static final String KEY_ASSETS = "assets";
    private static final String KEY_BROWSER_DOWNLOAD_URL = "browser_download_url";
    private static final String KEY_HTML_URL = "html_url";
    private static final String KEY_NAME = "name";
    private static final String KEY_TAG_NAME = "tag_name";

    private GitHubReleaseParser() {
    }

    public static RecordsSchedulerModels.ReleaseInfo parseLatest(String json) {
        String safeJson = json == null ? "" : json;
        String tag = stringValue(safeJson, KEY_TAG_NAME);
        String html = stringValue(safeJson, KEY_HTML_URL);
        List<RecordsSchedulerModels.ReleaseAsset> assets = new ArrayList<>();
        String assetsJson = arrayValue(safeJson, KEY_ASSETS);
        for (String assetJson : objectValues(assetsJson)) {
            String name = stringValue(assetJson, KEY_NAME);
            String url = stringValue(assetJson, KEY_BROWSER_DOWNLOAD_URL);
            if (!name.isEmpty() && !url.isEmpty()) {
                assets.add(new RecordsSchedulerModels.ReleaseAsset(name, url));
            }
        }
        return new RecordsSchedulerModels.ReleaseInfo(tag, html, assets);
    }

    public static boolean isNewerSemver(String currentVersion, String tagName) {
        return ReleaseVersion.isNewerSemver(currentVersion, tagName);
    }

    public static String parseSha256(String checksumText) {
        return Sha256Digest.findInText(checksumText);
    }

    public static boolean isSha256Digest(String checksumText) {
        return Sha256Digest.isDigest(checksumText);
    }

    private static String stringValue(String json, String key) {
        int colon = findKeyColon(json, key);
        if (colon < 0) {
            return "";
        }
        int valueIndex = nextNonWhitespace(json, colon + 1);
        if (valueIndex < 0 || json.charAt(valueIndex) != '"') {
            return "";
        }
        return readString(json, valueIndex).value;
    }

    private static String arrayValue(String json, String key) {
        int colon = findKeyColon(json, key);
        if (colon < 0) {
            return "";
        }
        int start = nextNonWhitespace(json, colon + 1);
        if (start < 0 || json.charAt(start) != '[') {
            return "";
        }
        int depth = 0;
        int index = start;
        while (index < json.length()) {
            char c = json.charAt(index);
            if (c == '"') {
                index = readString(json, index).endIndex + 1;
            } else if (c == '[') {
                depth++;
                index++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, index + 1);
                }
                index++;
            } else {
                index++;
            }
        }
        return "";
    }

    private static List<String> objectValues(String arrayJson) {
        List<String> out = new ArrayList<>();
        if (arrayJson.isEmpty()) {
            return out;
        }
        int depth = 0;
        int objectStart = -1;
        int index = 0;
        while (index < arrayJson.length()) {
            char c = arrayJson.charAt(index);
            if (c == '"') {
                index = readString(arrayJson, index).endIndex + 1;
            } else if (c == '{') {
                if (depth == 0) {
                    objectStart = index;
                }
                depth++;
                index++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    out.add(arrayJson.substring(objectStart, index + 1));
                    objectStart = -1;
                }
                index++;
            } else {
                index++;
            }
        }
        return out;
    }

    private static int findKeyColon(String json, String key) {
        int index = 0;
        while (index < json.length()) {
            if (json.charAt(index) == '"') {
                ParsedString parsed = readString(json, index);
                if (key.equals(parsed.value)) {
                    int colon = nextColon(json, parsed.endIndex + 1);
                    if (colon >= 0) {
                        return colon;
                    }
                }
                index = parsed.endIndex + 1;
            } else {
                index++;
            }
        }
        return -1;
    }

    private static int nextColon(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ':') {
                return i;
            }
            if (!Character.isWhitespace(c)) {
                return -1;
            }
        }
        return -1;
    }

    private static int nextNonWhitespace(String json, int start) {
        for (int i = Math.max(start, 0); i < json.length(); i++) {
            if (!Character.isWhitespace(json.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static ParsedString readString(String json, int quoteIndex) {
        StringBuilder out = new StringBuilder();
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char c = json.charAt(index);
            if (c == '"') {
                return new ParsedString(out.toString(), index);
            }
            if (c != '\\' || index + 1 >= json.length()) {
                out.append(c);
                index++;
            } else {
                ParsedEscape escape = readEscape(json, index + 1);
                out.append(escape.value);
                index = escape.nextIndex;
            }
        }
        return new ParsedString(out.toString(), json.length() - 1);
    }

    private static ParsedEscape readEscape(String json, int escapeIndex) {
        char escaped = json.charAt(escapeIndex);
        switch (escaped) {
            case '"', '\\', '/':
                return new ParsedEscape(String.valueOf(escaped), escapeIndex + 1);
            case 'b':
                return new ParsedEscape("\b", escapeIndex + 1);
            case 'f':
                return new ParsedEscape("\f", escapeIndex + 1);
            case 'n':
                return new ParsedEscape("\n", escapeIndex + 1);
            case 'r':
                return new ParsedEscape("\r", escapeIndex + 1);
            case 't':
                return new ParsedEscape("\t", escapeIndex + 1);
            case 'u':
                return readUnicodeEscape(json, escapeIndex);
            default:
                return new ParsedEscape(String.valueOf(escaped), escapeIndex + 1);
        }
    }

    private static ParsedEscape readUnicodeEscape(String json, int escapeIndex) {
        if (escapeIndex + UNICODE_ESCAPE_HEX_LENGTH >= json.length()) {
            return new ParsedEscape("\\u", escapeIndex + 1);
        }
        String hex = json.substring(escapeIndex + 1, escapeIndex + 1 + UNICODE_ESCAPE_HEX_LENGTH);
        try {
            char unicode = (char) Integer.parseInt(hex, 16);
            return new ParsedEscape(String.valueOf(unicode), escapeIndex + 1 + UNICODE_ESCAPE_HEX_LENGTH);
        } catch (NumberFormatException error) {
            return new ParsedEscape("\\u" + hex, escapeIndex + 1 + UNICODE_ESCAPE_HEX_LENGTH);
        }
    }

    private static final class ParsedEscape {
        private final String value;
        private final int nextIndex;

        private ParsedEscape(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    private static final class ParsedString {
        private final String value;
        private final int endIndex;

        private ParsedString(String value, int endIndex) {
            this.value = value;
            this.endIndex = endIndex;
        }
    }
}
