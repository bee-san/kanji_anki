package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.UpdateRunScreenCopy

internal class MainActivitySettingsUpdateFlow(private val activity: MainActivitySettings) {
    fun runUpdate(cachedPending: Boolean) {
        activity.base(MainActivityBase.NAV_SETTINGS_ROUTE)
        val updateUiRun = ++activity.updateUiRunCounter
        activity.activeUpdateUiRunToken = updateUiRun
        val copy = UpdateRunScreenCopy.forRun(cachedPending)
        activity.content.addView(
            settingsUpdateRunView(
                activity = activity,
                title = copy.title(),
                body = copy.body(),
                progressLabel = copy.progressLabel()
            )
        )
        activity.io.execute {
            val updater = GitHubUpdater(activity)
            val result = if (cachedPending) {
                updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
            } else {
                updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
            }
            activity.main.post {
                if (activity.activeUpdateUiRunToken != updateUiRun) {
                    return@post
                }
                Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
                result.intent?.let(activity::startActivity)
                activity.renderUpdate()
            }
        }
    }
}
