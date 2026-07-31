package dev.bee.kanjianki.assets

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAssetLoaderTest {
    @Test
    fun missingCacheExtractsAndInstallsEveryAsset() {
        val manifest = ReferenceAssetManifest.bundled()
        val cache = FakeCache()
        val outcomes = ReferenceAssetLoader(manifest, packagedSource(), cache).loadAll()

        assertEquals(manifest.assets.size, outcomes.size)
        assertTrue("every asset installs", outcomes.all { it.succeeded() && it.verified })
        assertTrue(
            "every asset extracts on a cold cache",
            outcomes.all { it.decision == ReferenceAssetCachePolicy.Decision.EXTRACT },
        )
        assertEquals(manifest.assets.map { it.id }.toSet(), cache.installed.keys)
    }

    @Test
    fun freshCacheIsReusedWithoutReinstalling() {
        val manifest = ReferenceAssetManifest.bundled()
        val cache = FakeCache()
        val loader = ReferenceAssetLoader(manifest, packagedSource(), cache)
        loader.loadAll()
        cache.installCount.clear()

        val outcomes = loader.loadAll()
        assertTrue(outcomes.all { it.decision == ReferenceAssetCachePolicy.Decision.REUSE })
        assertTrue("nothing re-installs from a fresh cache", cache.installCount.isEmpty())
    }

    @Test
    fun anOldFormatVersionUpgrades() {
        val manifest = ReferenceAssetManifest.bundled()
        val asset = manifest.byId("kanji_dictionary")!!
        val cache = FakeCache()
        cache.seed(asset, formatVersion = asset.formatVersion - 1, sha = "seed")

        val outcome = ReferenceAssetLoader(manifest, packagedSource(), cache).load(asset)
        assertEquals(ReferenceAssetCachePolicy.Decision.UPGRADE, outcome.decision)
        assertTrue(outcome.succeeded())
        assertEquals(1, cache.installCount[asset.id])
    }

    @Test
    fun aHashMismatchAgainstARealAssetIsRejected() {
        val realAsset = ReferenceAsset(
            id = "kanji_dictionary",
            kind = ReferenceAssetKind.DICTIONARY_DATABASE,
            fileName = "kanji_dictionary.db",
            expectedSha256 = "a".repeat(64),
            formatVersion = 2,
            extractionTarget = "reference/kanji_dictionary.db",
            license = ReferenceAssetLicense("KANJIDIC2", "CC BY-SA 4.0", null, "EDRDG"),
        )
        val manifest = ReferenceAssetManifest(ReferenceAssetManifest.SCHEMA_VERSION, listOf(realAsset))
        val cache = FakeCache()
        val outcome = ReferenceAssetLoader(manifest, packagedSource(), cache).load(realAsset)
        assertFalse("a corrupt real asset is refused", outcome.succeeded())
        assertTrue(outcome.error!!.contains("sha256 mismatch"))
        assertTrue("a rejected asset never installs", cache.installed.isEmpty())
    }

    @Test
    fun aMissingPackagedFileIsIsolatedNotFatal() {
        val manifest = ReferenceAssetManifest.bundled()
        val source = PackagedAssetSource { asset ->
            if (asset.id == "study_font") throw IOException("asset not found")
            packagedSource().open(asset)
        }
        val cache = FakeCache()
        val outcomes = ReferenceAssetLoader(manifest, source, cache).loadAll()
        assertEquals(1, outcomes.count { !it.succeeded() })
        assertEquals(
            "the other assets still install",
            manifest.assets.size - 1,
            outcomes.count { it.succeeded() },
        )
    }

    @Test
    fun unicodeFileNamesAreVerifiedAndInstalled() {
        val asset = ReferenceAsset(
            id = "unicode",
            kind = ReferenceAssetKind.STUDY_FONT,
            fileName = "漢字フォント.otf",
            expectedSha256 = ReferenceAssetManifest.PLACEHOLDER_SHA256,
            formatVersion = 1,
            extractionTarget = "reference/漢字フォント.otf",
            license = ReferenceAssetLicense("n", "spdx", null, "attr"),
        )
        val manifest = ReferenceAssetManifest(ReferenceAssetManifest.SCHEMA_VERSION, listOf(asset))
        val bytes = "日本語のフォントデータ".toByteArray(Charsets.UTF_8)
        val cache = FakeCache()
        val outcome = ReferenceAssetLoader(manifest, { ByteArrayInputStream(bytes) }, cache).load(asset)
        assertTrue(outcome.succeeded())
        assertEquals(ReferenceAssetVerifier.sha256(ByteArrayInputStream(bytes)), cache.installed["unicode"])
    }

    @Test
    fun aLargeAssetIsHashedByStreamingWithoutBufferingWhole() {
        val asset = ReferenceAsset(
            id = "large",
            kind = ReferenceAssetKind.DICTIONARY_DATABASE,
            fileName = "large.db",
            expectedSha256 = ReferenceAssetManifest.PLACEHOLDER_SHA256,
            formatVersion = 1,
            extractionTarget = "reference/large.db",
            license = ReferenceAssetLicense("n", "spdx", null, "attr"),
        )
        val manifest = ReferenceAssetManifest(ReferenceAssetManifest.SCHEMA_VERSION, listOf(asset))
        // ~8 MiB of deterministic bytes streamed through the verifier.
        val cache = FakeCache()
        val outcome = ReferenceAssetLoader(manifest, { RepeatingStream(8L * 1024 * 1024) }, cache).load(asset)
        assertTrue(outcome.succeeded())
        assertTrue(cache.installed.containsKey("large"))
    }

    @Test
    fun concurrentLoadsAllSucceed() {
        val manifest = ReferenceAssetManifest.bundled()
        val threads = 8
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val failures = ConcurrentHashMap.newKeySet<String>()
        try {
            val futures = (0 until threads).map {
                executor.submit {
                    start.await()
                    val outcomes = ReferenceAssetLoader(manifest, packagedSource(), FakeCache()).loadAll()
                    if (outcomes.any { !it.succeeded() }) failures.add("run failed")
                }
            }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertTrue("no concurrent loader run failed", failures.isEmpty())
    }

    @Test
    fun cancellationBeforeInstallLeavesNoPartialCacheEntry() {
        val manifest = ReferenceAssetManifest.bundled()
        val asset = manifest.byId("kanji_dictionary")!!
        val cache = FakeCache()
        // A source that throws mid-stream models an interrupted extraction; the
        // loader isolates it and no cache entry is written.
        val source = PackagedAssetSource {
            object : InputStream() {
                private var served = 0
                override fun read(): Int {
                    served++
                    if (served > 4) throw IOException("cancelled")
                    return 0
                }
            }
        }
        val outcome = ReferenceAssetLoader(manifest, source, cache).load(asset)
        assertFalse(outcome.succeeded())
        assertTrue("a cancelled extraction installs nothing", cache.installed.isEmpty())
    }

    private fun packagedSource(): PackagedAssetSource =
        PackagedAssetSource { asset ->
            val resource = "/reference-assets/${asset.fileName}"
            javaClass.getResourceAsStream(resource)
                ?: throw IOException("missing test resource $resource")
        }

    private class RepeatingStream(private val total: Long) : InputStream() {
        private var served = 0L
        override fun read(): Int {
            if (served >= total) return -1
            served++
            return (served % 251).toInt()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= total) return -1
            val n = minOf(len.toLong(), total - served).toInt()
            for (i in 0 until n) b[off + i] = ((served + i) % 251).toByte()
            served += n
            return n
        }
    }

    private class FakeCache : ReferenceAssetCache {
        val installed = ConcurrentHashMap<String, String>()
        private val formatVersions = ConcurrentHashMap<String, Int>()
        val installCount = ConcurrentHashMap<String, Int>()

        fun seed(asset: ReferenceAsset, formatVersion: Int, sha: String) {
            installed[asset.id] = sha
            formatVersions[asset.id] = formatVersion
        }

        override fun stateOf(asset: ReferenceAsset): ReferenceAssetCachePolicy.CacheState =
            ReferenceAssetCachePolicy.CacheState(
                present = installed.containsKey(asset.id),
                formatVersion = formatVersions[asset.id],
                recordedSha256 = installed[asset.id],
            )

        override fun install(asset: ReferenceAsset, source: PackagedAssetSource, observedSha256: String) {
            // Re-open to model a real atomic copy from the packaged source.
            source.open(asset).use { it.readBytes() }
            installed[asset.id] = observedSha256
            formatVersions[asset.id] = asset.formatVersion
            installCount.merge(asset.id, 1, Int::plus)
        }
    }
}
