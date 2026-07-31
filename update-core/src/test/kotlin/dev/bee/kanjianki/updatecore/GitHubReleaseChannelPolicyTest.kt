package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReleaseChannelPolicyTest {
    @Test
    fun stableChannelUsesGitHubLatestStableRelease() {
        assertEquals("/releases/latest", GitHubReleaseChannelPolicy.apiPath(false))

        val release = GitHubReleaseChannelPolicy.parseRelease(
            false,
            """{"tag_name":"v1.2.3","assets":[]}""",
        )

        assertEquals("v1.2.3", release.tagName())
    }

    @Test
    fun betaChannelSkipsNewerStableReleaseAndSelectsNewestPrerelease() {
        assertEquals("/releases?per_page=30", GitHubReleaseChannelPolicy.apiPath(true))

        val release = GitHubReleaseChannelPolicy.parseRelease(
            true,
            """[{"tag_name":"v1.2.5","prerelease":false,"assets":[]},{"tag_name":"v1.2.4","prerelease":true,"assets":[]},{"tag_name":"v1.2.3","prerelease":true,"assets":[]}]""",
        )

        assertEquals("v1.2.4", release.tagName())
    }

    @Test
    fun emptyBetaFeedReturnsEmptyMetadata() {
        val release = GitHubReleaseChannelPolicy.parseRelease(true, "[]")

        assertEquals("", release.tagName())
        assertEquals(0, release.assets().size)
    }
}
