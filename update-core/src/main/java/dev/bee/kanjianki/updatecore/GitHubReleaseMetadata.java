package dev.bee.kanjianki.updatecore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GitHubReleaseMetadata {
    private final String tagName;
    private final String htmlUrl;
    private final List<ReleaseAsset> assets;

    public GitHubReleaseMetadata(String tagName, String htmlUrl, List<ReleaseAsset> assets) {
        this.tagName = tagName;
        this.htmlUrl = htmlUrl;
        this.assets = Collections.unmodifiableList(new ArrayList<>(assets));
    }

    public String tagName() {
        return tagName;
    }

    public String htmlUrl() {
        return htmlUrl;
    }

    public List<ReleaseAsset> assets() {
        return assets;
    }

    public static final class ReleaseAsset {
        private final String name;
        private final String downloadUrl;

        public ReleaseAsset(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }

        public String name() {
            return name;
        }

        public String downloadUrl() {
            return downloadUrl;
        }
    }
}
