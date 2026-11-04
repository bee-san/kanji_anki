package dev.bee.kanjianki.platform.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import dev.bee.kanjianki.platform.AppDirectories
import dev.bee.kanjianki.platform.AppDirectoriesProvider
import dev.bee.kanjianki.platform.AppLifecycle
import dev.bee.kanjianki.platform.AppLifecycleState
import dev.bee.kanjianki.platform.ClipboardService
import dev.bee.kanjianki.platform.ExternalNavigator
import dev.bee.kanjianki.platform.FilePicker
import dev.bee.kanjianki.platform.FilePickerPurpose
import dev.bee.kanjianki.platform.FilePickerRequest
import dev.bee.kanjianki.platform.PlatformFileAccess
import dev.bee.kanjianki.platform.PlatformFileReference
import dev.bee.kanjianki.platform.PlatformSubscription
import dev.bee.kanjianki.platform.ReadingMediaMetadata
import dev.bee.kanjianki.platform.ReadingMediaSource
import dev.bee.kanjianki.platform.ShareRequest
import dev.bee.kanjianki.platform.ShareService
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.CopyOnWriteArraySet

class AndroidAppDirectoriesProvider(
    context: Context,
    private val backupDirectoryName: String = "backups",
) : AppDirectoriesProvider {
    private val context = context.applicationContext

    override fun directories(): AppDirectories =
        AppDirectories(
            data = context.filesDir.toPath(),
            cache = context.cacheDir.toPath(),
            backups = File(context.filesDir, backupDirectoryName).toPath(),
        )
}

class AndroidPlatformFileAccess(
    context: Context,
) : PlatformFileAccess {
    private val resolver = context.applicationContext.contentResolver

    override fun openInput(file: PlatformFileReference): InputStream? =
        file.contentUriOrNull()?.let { uri ->
            runCatching { resolver.openInputStream(uri) }.getOrNull()
        }

    override fun openOutput(file: PlatformFileReference): OutputStream? =
        file.contentUriOrNull()?.let { uri ->
            runCatching { resolver.openOutputStream(uri, "w") }.getOrNull()
        }
}

/**
 * Bridges the two position-sensitive Activity Result launchers registered by
 * the host to the platform-neutral file picker contract.
 */
class AndroidFilePicker(
    context: Context,
    private val launchSaveDocument: (String) -> Unit,
    private val launchOpenDocument: (Array<String>) -> Unit,
    private val openMimeTypes: Array<String> = DEFAULT_OPEN_MIME_TYPES,
) : FilePicker {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val lock = Any()
    private var pendingPurpose: FilePickerPurpose? = null
    private var pendingResult: ((PlatformFileReference?) -> Unit)? = null

    override fun launch(
        request: FilePickerRequest,
        onResult: (PlatformFileReference?) -> Unit,
    ) {
        launchForResult(request, onResult)
    }

    fun launchForResult(
        request: FilePickerRequest,
        onResult: (PlatformFileReference?) -> Unit,
    ): Boolean {
        synchronized(lock) {
            if (pendingResult != null) {
                return false
            }
            pendingPurpose = request.purpose
            pendingResult = onResult
        }
        return try {
            when (request.purpose) {
                FilePickerPurpose.SAVE -> launchSaveDocument(
                    requireNotNull(request.suggestedName) {
                        "Android save requests require a suggested file name"
                    },
                )
                FilePickerPurpose.OPEN -> launchOpenDocument(openMimeTypes.copyOf())
            }
            true
        } catch (_: RuntimeException) {
            complete(request.purpose, null)
            false
        }
    }

    fun onSaveResult(uri: Uri?): Boolean =
        complete(FilePickerPurpose.SAVE, uri)

    fun onOpenResult(uri: Uri?): Boolean =
        complete(FilePickerPurpose.OPEN, uri)

    fun referenceFor(uri: Uri?): PlatformFileReference? =
        uri?.let(::referenceForUri)

    private fun complete(purpose: FilePickerPurpose, uri: Uri?): Boolean {
        val callback = synchronized(lock) {
            if (pendingPurpose != purpose) {
                return false
            }
            pendingPurpose = null
            pendingResult.also { pendingResult = null }
        } ?: return false
        callback(referenceFor(uri))
        return true
    }

    private fun referenceForUri(uri: Uri): PlatformFileReference? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return null
        }
        val displayName = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(::isSafeDisplayName)
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf(::isSafeDisplayName)
            ?: "selected-file"
        return PlatformFileReference.create(uri.toString(), displayName)
    }

    companion object {
        private val DEFAULT_OPEN_MIME_TYPES = arrayOf(
            "application/gzip",
            "application/octet-stream",
            "*/*",
        )
    }
}

class AndroidClipboardService(
    context: Context,
) : ClipboardService {
    private val context = context.applicationContext

    override fun setText(label: String, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)
    }
}

class AndroidShareService(
    context: Context,
) : ShareService {
    private val context = context.applicationContext

    override fun share(request: ShareRequest): Boolean {
        val shareIntent = intentFor(request) ?: return false
        val chooser = Intent.createChooser(shareIntent, request.title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    companion object {
        @JvmStatic
        fun intentFor(request: ShareRequest): Intent? {
            val attachments = request.attachments.map { reference ->
                reference.contentUriOrNull() ?: return null
            }
            val action = if (attachments.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
            return Intent(action).apply {
                type = request.mimeType ?: if (attachments.isEmpty()) "text/plain" else "*/*"
                putExtra(Intent.EXTRA_SUBJECT, request.title)
                request.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                when (attachments.size) {
                    0 -> Unit
                    1 -> putExtra(Intent.EXTRA_STREAM, attachments.single())
                    else -> putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        ArrayList(attachments),
                    )
                }
                if (attachments.isNotEmpty()) {
                    clipData = ClipData.newRawUri(request.title, attachments.first()).apply {
                        attachments.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }
}

class AndroidExternalNavigator(
    context: Context,
    private val collectionBrowserIntent: (String) -> Intent?,
) : ExternalNavigator {
    private val context = context.applicationContext

    override fun openUrl(uri: URI): Boolean =
        launch(Intent(Intent.ACTION_VIEW, parsePlatformUri(uri.toASCIIString())))

    override fun openCollectionBrowser(query: String): Boolean =
        collectionBrowserIntent(query)?.let(::launch) == true

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

class AndroidReadingMediaSource(
    private val directory: File,
) : ReadingMediaSource {
    override fun cacheIdentity(): String? =
        runCatching { directory.canonicalPath }.getOrNull()

    override fun metadata(name: String): ReadingMediaMetadata? {
        val file = resolve(name) ?: return null
        return ReadingMediaMetadata(
            name = name,
            sizeBytes = file.length(),
            modifiedAtMillis = file.lastModified().coerceAtLeast(0L),
        )
    }

    override fun read(name: String, maximumBytes: Int): ByteArray? {
        if (maximumBytes <= 0) {
            return null
        }
        val file = resolve(name) ?: return null
        if (file.length() > maximumBytes.toLong()) {
            return null
        }
        return runCatching {
            file.inputStream().use { input -> input.readBounded(maximumBytes) }
        }.getOrNull()
    }

    private fun resolve(name: String): File? {
        if (!isSafeDisplayName(name)) {
            return null
        }
        return runCatching {
            val root = directory.canonicalFile
            File(root, name).canonicalFile.takeIf { file ->
                file.parentFile == root && file.isFile
            }
        }.getOrNull()
    }
}

class AndroidAppLifecycle(
    private val application: Application,
) : AppLifecycle, Application.ActivityLifecycleCallbacks, AutoCloseable {
    private val observers = CopyOnWriteArraySet<(AppLifecycleState) -> Unit>()
    private val lock = Any()
    private var startedActivities = 0
    @Volatile
    private var state = AppLifecycleState.BACKGROUND

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun currentState(): AppLifecycleState = state

    override fun observe(observer: (AppLifecycleState) -> Unit): PlatformSubscription {
        observers.add(observer)
        observer(state)
        return PlatformSubscription { observers.remove(observer) }
    }

    override fun onActivityStarted(activity: Activity) {
        val becameForeground = synchronized(lock) {
            startedActivities += 1
            startedActivities == 1
        }
        if (becameForeground) {
            moveTo(AppLifecycleState.FOREGROUND)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        val becameBackground = synchronized(lock) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            startedActivities == 0
        }
        if (becameBackground) {
            moveTo(AppLifecycleState.BACKGROUND)
        }
    }

    fun markStopping() {
        moveTo(AppLifecycleState.STOPPING)
    }

    override fun close() {
        application.unregisterActivityLifecycleCallbacks(this)
        markStopping()
        observers.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun moveTo(next: AppLifecycleState) {
        state = next
        observers.forEach { observer -> observer(next) }
    }
}

private fun PlatformFileReference.contentUriOrNull(): Uri? =
    runCatching { parsePlatformUri(opaqueId) }
        .getOrNull()
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }

@SuppressLint("UseKtx")
private fun parsePlatformUri(value: String): Uri = Uri.parse(value)

private fun isSafeDisplayName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name &&
        '\u0000' !in name

private fun InputStream.readBounded(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) {
            break
        }
        if (read == 0) {
            continue
        }
        if (total > maximumBytes - read) {
            throw IllegalStateException("Reading media changed beyond its size limit")
        }
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}
