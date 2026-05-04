package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubReleaseParser {
    private GitHubReleaseParser() {
    }

    public static Records.ReleaseInfo parseLatest(String json) {
        String safeJson = json == null ? "" : json;
        String tag = stringValue(safeJson, "tag_name");
        String html = stringValue(safeJson, "html_url");
        List<Records.ReleaseAsset> assets = new ArrayList<>();
        String assetsJson = arrayValue(safeJson, "assets");
        for (String assetJson : objectValues(assetsJson)) {
            String name = stringValue(assetJson, "name");
            String url = stringValue(assetJson, "browser_download_url");
            if (!name.isEmpty() && !url.isEmpty()) {
                assets.add(new Records.ReleaseAsset(name, url));
            }
        }
        return new Records.ReleaseInfo(tag, html, assets);
    }

    public static boolean isNewerSemver(String currentVersion, String tagName) {
        int[] current = parseVersion(currentVersion == null ? "" : currentVersion.replaceFirst("^v", ""));
        int[] remote = parseVersion(tagName == null ? "" : tagName.replaceFirst("^v", ""));
        for (int i = 0; i < 3; i++) {
            if (remote[i] != current[i]) {
                return remote[i] > current[i];
            }
        }
        return false;
    }

    public static String parseSha256(String checksumText) {
        if (checksumText == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("(?i)\\b([a-f0-9]{64})\\b").matcher(checksumText);
        return matcher.find() ? matcher.group(1).toLowerCase() : "";
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
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                i = readString(json, i).endIndex;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private static List<String> objectValues(String arrayJson) {
        List<String> out = new ArrayList<>();
        if (arrayJson == null || arrayJson.isEmpty()) {
            return out;
        }
        int depth = 0;
        int objectStart = -1;
        for (int i = 0; i < arrayJson.length(); i++) {
            char c = arrayJson.charAt(i);
            if (c == '"') {
                i = readString(arrayJson, i).endIndex;
            } else if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    out.add(arrayJson.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }
        return out;
    }

    private static int findKeyColon(String json, String key) {
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != '"') {
                continue;
            }
            ParsedString parsed = readString(json, i);
            if (key.equals(parsed.value)) {
                int colon = nextColon(json, parsed.endIndex + 1);
                if (colon >= 0) {
                    return colon;
                }
            }
            i = parsed.endIndex;
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

    private static int[] parseVersion(String version) {
        Matcher matcher = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$").matcher(version);
        if (!matcher.find()) {
            return new int[]{0, 0, 0};
        }
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }

    private static ParsedString readString(String json, int quoteIndex) {
        StringBuilder out = new StringBuilder();
        for (int i = quoteIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return new ParsedString(out.toString(), i);
            }
            if (c != '\\' || i + 1 >= json.length()) {
                out.append(c);
                continue;
            }
            char escaped = json.charAt(++i);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    out.append(escaped);
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (i + 4 < json.length()) {
                        String hex = json.substring(i + 1, i + 5);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException error) {
                            out.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        out.append("\\u");
                    }
                    break;
                default:
                    out.append(escaped);
                    break;
            }
        }
        return new ParsedString(out.toString(), json.length() - 1);
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
