package dev.bee.kanjianki.platform.android

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.platform.AppLifecycleState
import dev.bee.kanjianki.platform.FilePickerPurpose
import dev.bee.kanjianki.platform.FilePickerRequest
import dev.bee.kanjianki.platform.PlatformFileReference
import dev.bee.kanjianki.platform.ShareRequest
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidContentServicesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun directoriesAndClipboardImplementPortableContracts() {
        val directories = AndroidAppDirectoriesProvider(context).directories()
        assertEquals(context.filesDir.toPath(), directories.data)
        assertEquals(context.cacheDir.toPath(), directories.cache)
        assertEquals(File(context.filesDir, "backups").toPath(), directories.backups)

        assertTrue(AndroidClipboardService(context).setText("Kani", "tag:kani"))
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "tag:kani",
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString(),
        )
    }

    @Test
    fun pickerPreservesSaveAndOpenLauncherShapesAndReturnsOpaqueReferences() {
        var savedName: String? = null
        var openTypes: Array<String>? = null
        var selected: PlatformFileReference? = null
        val picker = AndroidFilePicker(
            context,
            launchSaveDocument = { savedName = it },
            launchOpenDocument = { openTypes = it },
        )

        assertTrue(
            picker.launchForResult(
                FilePickerRequest(
                    purpose = FilePickerPurpose.SAVE,
                    suggestedName = "kani-backup.db.gz",
                ),
            ) { selected = it },
        )
        assertEquals("kani-backup.db.gz", savedName)
        picker.onSaveResult(Uri.parse("content://documents/document/kani-backup.db.gz"))
        assertEquals("kani-backup.db.gz", selected?.displayName)
        assertFalse(selected.toString().contains("content://"))

        assertTrue(
            picker.launchForResult(FilePickerRequest(FilePickerPurpose.OPEN)) {
                selected = it
            },
        )
        assertArrayEquals(
            arrayOf("application/gzip", "application/octet-stream", "*/*"),
            openTypes,
        )
        picker.onOpenResult(null)
        assertNull(selected)
    }

    @Test
    fun pickerRejectsConcurrentRequestsWithoutReplacingFirstCallback() {
        val picker = AndroidFilePicker(context, {}, {})
        var firstCalled = false
        var secondCalled = false

        assertTrue(
            picker.launchForResult(
                FilePickerRequest(FilePickerPurpose.SAVE, "first.gz"),
            ) { firstCalled = true },
        )
        assertFalse(
            picker.launchForResult(
                FilePickerRequest(FilePickerPurpose.SAVE, "second.gz"),
            ) { secondCalled = true },
        )

        picker.onSaveResult(null)
        assertTrue(firstCalled)
        assertFalse(secondCalled)
    }

    @Test
    fun pickerCanAdaptAResultRestoredIntoANewActivityInstance() {
        val picker = AndroidFilePicker(context, {}, {})
        val uri = Uri.parse("content://documents/document/restored-backup.db.gz")

        assertFalse(picker.onOpenResult(uri))
        assertEquals("restored-backup.db.gz", picker.referenceFor(uri)?.displayName)
    }

    @Test
    fun fileAccessRejectsNonContentReferences() {
        val access = AndroidPlatformFileAccess(context)
        val file = PlatformFileReference.create("file:///private/data", "data.db")

        assertNull(access.openInput(file))
        assertNull(access.openOutput(file))
    }

    @Test
    fun shareIntentCarriesReadOnlyAttachmentsAndText() {
        val attachment = PlatformFileReference.create(
            "content://dev.bee.kanjianki/export/1",
            "missing.csv",
        )

        val intent = AndroidShareService.intentFor(
            ShareRequest(
                title = "Kani Missing Kanji",
                text = "Import this CSV.",
                attachments = listOf(attachment),
                mimeType = "text/csv",
            ),
        )

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent?.action)
        assertEquals("text/csv", intent?.type)
        assertEquals("Import this CSV.", intent?.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(
            Uri.parse(attachment.opaqueId),
            intent?.clipData?.getItemAt(0)?.uri,
        )
        assertTrue(
            (intent?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        assertEquals(
            0,
            (intent?.flags ?: 0) and Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    @Test
    fun shareAndNavigationServicesLaunchHostIntents() {
        val share = AndroidShareService(context)
        val navigator = AndroidExternalNavigator(context) { query ->
            Intent("dev.bee.kanjianki.TEST_COLLECTION_BROWSER")
                .putExtra("query", query)
        }

        assertTrue(
            share.share(
                ShareRequest(
                    title = "Kani debug log",
                    text = "diagnostics",
                ),
            ),
        )
        assertTrue(navigator.openUrl(java.net.URI("https://example.com/kani")))
        assertTrue(navigator.openCollectionBrowser("tag:kani_repaired"))
    }

    @Test
    fun mediaSourceRejectsTraversalAndEnforcesReadLimit() {
        val directory = temporaryFolder.newFolder("collection.media")
        File(directory, "stats.json").writeText("12345")
        File(directory.parentFile, "outside.json").writeText("private")
        val source = AndroidReadingMediaSource(directory)

        assertEquals(5L, source.metadata("stats.json")?.sizeBytes)
        assertEquals("12345", source.read("stats.json", 5)?.decodeToString())
        assertNull(source.read("stats.json", 4))
        assertNull(source.metadata("../outside.json"))
        assertNull(source.read("../outside.json", 100))
    }

    @Test
    fun lifecyclePublishesForegroundBackgroundAndStoppingStates() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val lifecycle = AndroidAppLifecycle(application)
        val observed = mutableListOf<AppLifecycleState>()
        val activity = Activity()

        lifecycle.observe(observed::add).use {
            lifecycle.onActivityStarted(activity)
            lifecycle.onActivityStopped(activity)
            lifecycle.markStopping()
        }

        assertEquals(
            listOf(
                AppLifecycleState.BACKGROUND,
                AppLifecycleState.FOREGROUND,
                AppLifecycleState.BACKGROUND,
                AppLifecycleState.STOPPING,
            ),
            observed,
        )
        assertEquals(AppLifecycleState.STOPPING, lifecycle.currentState())
        lifecycle.close()
    }
}
