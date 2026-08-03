package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.FilePickerPurpose
import dev.bee.kanjianki.platform.FilePickerRequest
import dev.bee.kanjianki.platform.PlatformFileReference
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopFilePickerTest {
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
    fun aChosenFileIsRegisteredAndResolvableThroughFileAccess() {
        val access = DesktopFileAccess()
        val chosen = Files.writeString(temporaryRoot().resolve("backup.kani"), "data")
        val picker = DesktopFilePicker(access) { chosen }

        var result: PlatformFileReference? = null
        picker.launch(FilePickerRequest(FilePickerPurpose.OPEN)) { result = it }

        assertNotNull("a chosen file yields a reference", result)
        assertEquals("backup.kani", result!!.displayName)
        // The reference is usable only because launch() registered it.
        access.openInput(result!!).use { stream ->
            assertNotNull("registered reference resolves to a readable stream", stream)
        }
    }

    @Test
    fun aCancelledDialogYieldsNullAndRegistersNothing() {
        val access = DesktopFileAccess()
        val picker = DesktopFilePicker(access) { null }

        var called = false
        var result: PlatformFileReference? = PlatformFileReference.create("x", "x")
        picker.launch(FilePickerRequest(FilePickerPurpose.SAVE, suggestedName = "export.csv")) {
            called = true
            result = it
        }

        assertTrue("the callback still fires on cancel", called)
        assertNull("a cancelled pick is no file", result)
    }

    @Test
    fun aDialogThatThrowsIsTreatedAsCancellation() {
        val access = DesktopFileAccess()
        val picker = DesktopFilePicker(access) { error("native dialog blew up") }

        var result: PlatformFileReference? = PlatformFileReference.create("x", "x")
        picker.launch(FilePickerRequest(FilePickerPurpose.OPEN)) { result = it }

        assertNull("a dialog failure becomes a quiet no-file, not a crash", result)
    }

    @Test
    fun aNamelessRootPathIsTreatedAsNoFileRatherThanCrashing() {
        val access = DesktopFileAccess()
        // A root path has no file name; register() rejects it, and launch() must
        // absorb that as a null result rather than let the exception cross onResult.
        val picker = DesktopFilePicker(access) { Path.of("/") }

        var called = false
        var result: PlatformFileReference? = PlatformFileReference.create("x", "x")
        picker.launch(FilePickerRequest(FilePickerPurpose.OPEN)) {
            called = true
            result = it
        }

        assertTrue(called)
        assertNull("a name-less path is not a picked file", result)
    }

    private fun temporaryRoot(): Path =
        Files.createTempDirectory("kani-desktop-picker").also(temporaryRoots::add)
}
