package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppDirectories
import dev.bee.kanjianki.platform.PlatformFileReference
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUserServicesTest {
    private val temporaryRoots = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryRoots.asReversed().forEach { root ->
            if (!Files.exists(root)) return@forEach
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun profileDirectoriesPutBackupsInsideTheProfileAndCacheOutsideIt() {
        // Backups are the profile's own recovery state and must travel with it;
        // caches live where the OS may evict them.
        val profile = Path.of("/data/Kani/profiles/id")
        val cache = Path.of("/cache/Kani")

        val directories = DesktopAppDirectories
            .forProfile(profile, cache, backupsDirectoryName = "backups")
            .directories()

        assertEquals(
            AppDirectories(
                data = profile,
                cache = cache,
                backups = profile.resolve("backups"),
            ),
            directories,
        )
    }

    @Test
    fun aBlankBackupsDirectoryNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopAppDirectories.forProfile(
                Path.of("/data"),
                Path.of("/cache"),
                backupsDirectoryName = " ",
            )
        }
    }

    @Test
    fun directoriesAreReportedWithoutCreatingAnything() {
        // A provider that created directories on read could recreate one the startup
        // gate deliberately refused.
        val root = temporaryRoot()
        val profile = root.resolve("profiles/absent")

        DesktopAppDirectories
            .forProfile(profile, root.resolve("cache"), "backups")
            .directories()

        assertFalse(Files.exists(profile))
    }

    @Test
    fun aPreBuiltDirectoriesValueIsReportedVerbatim() {
        val value = AppDirectories(
            data = Path.of("/d"),
            cache = Path.of("/c"),
            backups = Path.of("/b"),
        )

        assertEquals(value, DesktopAppDirectories(value).directories())
    }

    @Test
    fun aRegisteredFileCanBeReadAndWritten() {
        val access = DesktopFileAccess()
        val file = temporaryRoot().resolve("export.kani.gz")
        Files.writeString(file, "original")
        val reference = access.register(file)

        access.openOutput(reference)!!.use { it.write("replaced".toByteArray()) }
        val read = access.openInput(reference)!!.use { it.readBytes() }

        assertEquals("export.kani.gz", reference.displayName)
        assertEquals("replaced", String(read, StandardCharsets.UTF_8))
    }

    @Test
    fun anUnregisteredReferenceResolvesToNothingEvenWhenTheFileExists() {
        // On desktop the reference *is* the path, so registration is the access
        // control: a reference fabricated from a restore manifest or a sync payload
        // must not become a read of an arbitrary user file.
        val access = DesktopFileAccess()
        val file = temporaryRoot().resolve("secret.txt")
        Files.writeString(file, "private")
        val forged = PlatformFileReference.create(
            opaqueId = file.toAbsolutePath().toString(),
            displayName = "secret.txt",
        )

        assertNull(access.openInput(forged))
        assertNull(access.openOutput(forged))
    }

    @Test
    fun revokingAGrantEndsAccessToThatFileOnly() {
        val access = DesktopFileAccess()
        val root = temporaryRoot()
        val kept = access.register(Files.writeString(root.resolve("kept.txt"), "a"))
        val dropped = access.register(Files.writeString(root.resolve("dropped.txt"), "b"))

        access.revoke(dropped)

        assertNull(access.openInput(dropped))
        assertEquals("a", access.openInput(kept)!!.use { String(it.readBytes()) })
    }

    @Test
    fun revokingEverythingEndsAllAccess() {
        val access = DesktopFileAccess()
        val root = temporaryRoot()
        val first = access.register(Files.writeString(root.resolve("a.txt"), "a"))
        val second = access.register(Files.writeString(root.resolve("b.txt"), "b"))

        access.revokeAll()

        assertNull(access.openInput(first))
        assertNull(access.openInput(second))
    }

    @Test
    fun aRegisteredPathThatCannotBeOpenedReportsNullRatherThanThrowing() {
        val access = DesktopFileAccess()
        val missing = access.register(temporaryRoot().resolve("never-created.txt"))

        assertNull(access.openInput(missing))
    }

    @Test
    fun openingAnOutputInsideAMissingDirectoryReportsNull() {
        val access = DesktopFileAccess()
        val reference = access.register(
            temporaryRoot().resolve("absent-dir/file.txt"),
        )

        assertNull(access.openOutput(reference))
    }

    @Test
    fun aPathWithNoFileNameCannotBeRegistered() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopFileAccess().register(Path.of("/"))
        }
    }

    @Test
    fun registrationNormalizesSoTheSameFileIsOneGrant() {
        val access = DesktopFileAccess()
        val root = temporaryRoot()
        Files.writeString(root.resolve("file.txt"), "x")

        val direct = access.register(root.resolve("file.txt"))
        val indirect = access.register(root.resolve("./file.txt"))

        assertEquals(direct.opaqueId, indirect.opaqueId)
    }

    @Test
    fun onlyHttpAndHttpsUrlsWithAHostAreOpened() {
        // A desktop browser-open is a shell "open with whatever handles this", so an
        // unfiltered scheme is a command-execution surface.
        val attempted = ArrayList<URI>()
        val navigator = DesktopExternalNavigator(browse = { attempted.add(it); true })

        assertTrue(navigator.openUrl(URI("https://github.com/bee-san/kanji_anki")))
        assertTrue(navigator.openUrl(URI("http://127.0.0.1:8765")))
        for (rejected in listOf(
            "file:///etc/passwd",
            "javascript:alert(1)",
            "jar:file:///tmp/x.jar!/",
            "mailto:someone@example.invalid",
            "HTTPS:/no-host-here",
            "/relative/path",
        )) {
            assertFalse(rejected, navigator.openUrl(URI(rejected)))
        }

        assertEquals(2, attempted.size)
    }

    @Test
    fun schemeMatchingIsCaseInsensitiveForRealUrls() {
        assertTrue(DesktopExternalNavigator.isOpenableWebUrl(URI("HTTPS://example.invalid")))
        assertFalse(DesktopExternalNavigator.isOpenableWebUrl(URI("FILE:///etc/passwd")))
    }

    @Test
    fun aFailingOrThrowingBrowserOpenIsReportedAsFailure() {
        val refusing = DesktopExternalNavigator(browse = { false })
        val throwing = DesktopExternalNavigator(
            browse = { throw IOException("no handler registered") },
        )
        val url = URI("https://example.invalid")

        assertFalse(refusing.openUrl(url))
        assertFalse(throwing.openUrl(url))
    }

    @Test
    fun theCollectionBrowserIsHandedTheQueryAndNotTreatedAsAUrl() {
        // guiBrowse is an AnkiConnect action, injected because the provider owns the
        // outbound action allowlist.
        val queries = ArrayList<String>()
        val navigator = DesktopExternalNavigator(
            browse = { false },
            guiBrowse = { queries.add(it); true },
        )

        assertTrue(navigator.openCollectionBrowser("tag:kani_repaired is:suspended"))
        assertFalse(navigator.openCollectionBrowser(" "))

        assertEquals(listOf("tag:kani_repaired is:suspended"), queries)
    }

    @Test
    fun aFailingCollectionBrowserCallIsReportedRatherThanThrowing() {
        val unavailable = DesktopExternalNavigator(browse = { false })
        val throwing = DesktopExternalNavigator(
            browse = { false },
            guiBrowse = { throw IOException("anki not running") },
        )

        // Default guiBrowse: no provider wired yet.
        assertFalse(unavailable.openCollectionBrowser("deck:Kiku"))
        assertFalse(throwing.openCollectionBrowser("deck:Kiku"))
    }

    @Test
    fun copyingTextReportsWhetherTheHostAcceptedIt() {
        // The Home hand-off shows the query on screen too, so a false success would
        // be worse than a false failure.
        val copied = ArrayList<String>()
        val clipboard = DesktopClipboardService { copied.add(it); true }

        assertTrue(clipboard.setText("Anki search", "tag:kani_repaired"))
        assertFalse(clipboard.setText("Anki search", ""))

        assertEquals(listOf("tag:kani_repaired"), copied)
    }

    @Test
    fun aClipboardOwnedByAnotherApplicationIsAFailureNotACrash() {
        val refusing = DesktopClipboardService { false }
        val throwing = DesktopClipboardService {
            throw IllegalStateException("clipboard unavailable")
        }

        assertFalse(refusing.setText("Anki search", "tag:kani_repaired"))
        assertFalse(throwing.setText("Anki search", "tag:kani_repaired"))
    }

    private fun temporaryRoot(): Path =
        Files.createTempDirectory("kani-desktop-services").also(temporaryRoots::add)
}
