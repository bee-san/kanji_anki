package dev.bee.kanjianki.updatecore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Sha256Digest {
    private static final int SHA256_HEX_LENGTH = 64;
    private static final Pattern SHA256_PATTERN = Pattern.compile("(?i)\\b([a-f0-9]{" + SHA256_HEX_LENGTH + "})\\b");
    private static final Pattern SHA256_DIGEST_PATTERN = Pattern.compile("(?i)[0-9a-f]{" + SHA256_HEX_LENGTH + "}");

    private Sha256Digest() {
    }

    public static String findInText(String checksumText) {
        if (checksumText == null) {
            return "";
        }
        Matcher matcher = SHA256_PATTERN.matcher(checksumText);
        return matcher.find() ? normalize(matcher.group(1)) : "";
    }

    public static boolean isDigest(String expected) {
        return expected != null && SHA256_DIGEST_PATTERN.matcher(expected.trim()).matches();
    }

    private static String normalize(String digest) {
        return digest.toLowerCase(Locale.ROOT);
    }
}
