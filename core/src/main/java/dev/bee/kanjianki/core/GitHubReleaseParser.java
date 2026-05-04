package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubReleaseParser {
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET = Pattern.compile("\\{[^{}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"[^{}]*}", Pattern.DOTALL);

    private GitHubReleaseParser() {
    }

    public static Records.ReleaseInfo parseLatest(String json) {
        String tag = find(TAG, json);
        String html = find(HTML, json);
        List<Records.ReleaseAsset> assets = new ArrayList<>();
        Matcher matcher = ASSET.matcher(json == null ? "" : json);
        while (matcher.find()) {
            assets.add(new Records.ReleaseAsset(unescape(matcher.group(1)), unescape(matcher.group(2))));
        }
        return new Records.ReleaseInfo(unescape(tag), unescape(html), assets);
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

    private static String find(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json == null ? "" : json);
        return matcher.find() ? matcher.group(1) : "";
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

    private static String unescape(String value) {
        return value == null ? "" : value.replace("\\/", "/").replace("\\\"", "\"");
    }
}
