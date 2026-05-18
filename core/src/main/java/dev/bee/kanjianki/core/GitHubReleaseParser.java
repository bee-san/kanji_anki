package dev.bee.kanjianki.core;

import dev.bee.kanjianki.updatecore.GitHubReleaseMetadata;
import dev.bee.kanjianki.updatecore.GitHubReleaseMetadataParser;
import dev.bee.kanjianki.updatecore.ReleaseVersion;
import dev.bee.kanjianki.updatecore.Sha256Digest;

import java.util.ArrayList;
import java.util.List;

public final class GitHubReleaseParser {
    private GitHubReleaseParser() {
    }

    public static RecordsSchedulerModels.ReleaseInfo parseLatest(String json) {
        GitHubReleaseMetadata parsed = GitHubReleaseMetadataParser.parseLatest(json);
        List<RecordsSchedulerModels.ReleaseAsset> assets = new ArrayList<>();
        for (GitHubReleaseMetadata.ReleaseAsset asset : parsed.assets()) {
            assets.add(new RecordsSchedulerModels.ReleaseAsset(asset.name(), asset.downloadUrl()));
        }
        return new RecordsSchedulerModels.ReleaseInfo(parsed.tagName(), parsed.htmlUrl(), assets);
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
}
