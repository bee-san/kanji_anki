package dev.bee.kanjianki.updatecore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseVersion {
    private static final int SEMVER_COMPONENTS = 3;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private ReleaseVersion() {
    }

    public static boolean isNewerSemver(String currentVersion, String tagName) {
        int[] current = parseVersion(stripLeadingV(currentVersion));
        int[] remote = parseVersion(stripLeadingV(tagName));
        for (int i = 0; i < SEMVER_COMPONENTS; i++) {
            if (remote[i] != current[i]) {
                return remote[i] > current[i];
            }
        }
        return false;
    }

    private static String stripLeadingV(String version) {
        return version == null ? "" : version.replaceFirst("^v", "");
    }

    private static int[] parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) {
            return new int[]{0, 0, 0};
        }
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }
}
