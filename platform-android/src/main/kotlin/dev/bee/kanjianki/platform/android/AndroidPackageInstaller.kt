package dev.bee.kanjianki.platform.android

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

class AndroidPackageInstaller(
    context: Context,
    private val backend: Backend =
        FrameworkBackend(context.applicationContext.packageManager.packageInstaller),
    private val runtimeSdk: Int = Build.VERSION.SDK_INT,
) {
    private val context = context.applicationContext

    data class InstallRequest(
        val apkFile: File,
        val packageName: String,
        val sessionName: String,
        val callbackIntent: Intent,
        val allowWithoutUserAction: Boolean,
    ) {
        init {
            require(packageName.isNotBlank()) { "package name must not be blank" }
            require(sessionName.isNotBlank()) { "session name must not be blank" }
        }
    }

    interface Backend {
        @Throws(IOException::class)
        fun createSession(params: PackageInstaller.SessionParams): Int

        @Throws(IOException::class)
        fun openSession(sessionId: Int): Session

        fun abandonSession(sessionId: Int)
    }

    interface Session : AutoCloseable {
        @Throws(IOException::class)
        fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream

        @Throws(IOException::class)
        fun fsync(output: OutputStream)

        fun commit(statusReceiver: IntentSender)

        override fun close()
    }

    @Throws(IOException::class)
    fun install(request: InstallRequest) {
        val params = sessionParams(
            packageName = request.packageName,
            allowWithoutUserAction = request.allowWithoutUserAction,
            runtimeSdk = runtimeSdk,
        )
        val sessionId = backend.createSession(params)
        var session: Session? = null
        var committed = false
        try {
            session = backend.openSession(sessionId)
            BufferedInputStream(FileInputStream(request.apkFile)).use { input ->
                session.openWrite(
                    request.sessionName,
                    0L,
                    request.apkFile.length(),
                ).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    session.fsync(output)
                }
            }
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                request.callbackIntent,
                installStatusPendingIntentFlags(),
            )
            session.commit(pending.intentSender)
            committed = true
        } finally {
            try {
                session?.close()
            } finally {
                if (!committed) {
                    backend.abandonSession(sessionId)
                }
            }
        }
    }

    private class FrameworkBackend(
        private val installer: PackageInstaller,
    ) : Backend {
        override fun createSession(params: PackageInstaller.SessionParams): Int =
            installer.createSession(params)

        override fun openSession(sessionId: Int): Session =
            FrameworkSession(installer.openSession(sessionId))

        override fun abandonSession(sessionId: Int) {
            installer.abandonSession(sessionId)
        }
    }

    private class FrameworkSession(
        private val session: PackageInstaller.Session,
    ) : Session {
        override fun openWrite(
            name: String,
            offsetBytes: Long,
            lengthBytes: Long,
        ): OutputStream = session.openWrite(name, offsetBytes, lengthBytes)

        override fun fsync(output: OutputStream) {
            session.fsync(output)
        }

        override fun commit(statusReceiver: IntentSender) {
            session.commit(statusReceiver)
        }

        override fun close() {
            session.close()
        }
    }

    companion object {
        @JvmStatic
        fun installStatusPendingIntentFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE

        @JvmStatic
        fun installPermissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        @JvmStatic
        fun sessionParams(
            packageName: String,
            allowWithoutUserAction: Boolean,
            runtimeSdk: Int = Build.VERSION.SDK_INT,
        ): PackageInstaller.SessionParams {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            params.setAppPackageName(packageName)
            if (runtimeSdk >= Build.VERSION_CODES.S && allowWithoutUserAction) {
                setNoUserActionRequired(params)
            }
            return params
        }

        @SuppressLint("NewApi", "InlinedApi")
        private fun setNoUserActionRequired(params: PackageInstaller.SessionParams) {
            // sessionParams checks the injectable runtime SDK before reaching this API 31 call.
            params.setRequireUserAction(
                PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED,
            )
        }
    }
}
