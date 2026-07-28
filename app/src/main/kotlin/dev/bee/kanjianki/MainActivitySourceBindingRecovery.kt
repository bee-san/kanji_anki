package dev.bee.kanjianki

import dev.bee.kanjianki.backup.FreshProfilePreparationResult
import dev.bee.kanjianki.backup.SourceBindingRecoveryStorage
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.SourceBindingEvidence
import dev.bee.kanjianki.sync.SourceBindingFailure
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.SourceBindingAction
import dev.bee.kanjianki.syncapi.SourceBindingReason
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

internal class MainActivitySourceBindingRecovery(
    private val activity: MainActivityHome,
) {
    private val running = AtomicBoolean(false)

    fun renderIfRequired(result: ManualSyncEngine.SyncResult): Boolean {
        val reason = result.sourceBindingReason ?: return false
        renderRecovery(reason, result.sourceBindingEvidence)
        return true
    }

    private fun renderRecovery(
        reason: SourceBindingReason,
        evidence: SourceBindingEvidence?,
    ) {
        activity.pendingHomeSyncDialog = null
        val presentation = SourceBindingRecoveryUi.presentation(
            reason,
            evidence,
            storage().operationsAllowed(),
        )
        val primary = when {
            presentation.firstBindAllowed -> SyncResultActionModel(
                SourceBindingRecoveryUi.firstBindLabel(),
                Runnable {
                    startBindingRecovery(
                        action = SourceBindingAction.FIRST_BIND,
                        requireBackup = false,
                    )
                },
            )
            presentation.rebindAllowed -> SyncResultActionModel(
                SourceBindingRecoveryUi.rebindLabel(),
                Runnable(::confirmRebind),
            )
            else -> null
        }
        val additionalActions = buildList {
            if (presentation.newProfileAllowed) {
                add(
                    SyncResultActionModel(
                        SourceBindingRecoveryUi.newProfileLabel(),
                        Runnable(::confirmFreshProfile),
                    ),
                )
            }
        }
        activity.renderSyncResultScreen(
            SyncResultScreenModel(
                title = presentation.title,
                headline = presentation.headline,
                lines = presentation.lines,
                accentColor = MainActivityBase.CORAL,
                primaryLabel = primary?.label,
                primaryColor = MainActivityBase.TEAL,
                onPrimary = primary?.onAction,
                secondaryLabel = StudyTextCopy.backHomeLabel(),
                onSecondary = Runnable(activity::renderHome),
                additionalActions = additionalActions,
            ),
        )
    }

    private fun confirmRebind() {
        showConfirmation(
            title = SourceBindingRecoveryUi.rebindConfirmTitle(),
            message = SourceBindingRecoveryUi.rebindConfirmMessage(),
            confirmLabel = SourceBindingRecoveryUi.rebindConfirmLabel(),
            onConfirm = Runnable {
                startBindingRecovery(
                    action = SourceBindingAction.REBIND,
                    requireBackup = true,
                )
            },
        )
    }

    private fun confirmFreshProfile() {
        showConfirmation(
            title = SourceBindingRecoveryUi.newProfileConfirmTitle(),
            message = SourceBindingRecoveryUi.newProfileConfirmMessage(),
            confirmLabel = SourceBindingRecoveryUi.newProfileConfirmLabel(),
            onConfirm = Runnable(::startFreshProfile),
        )
    }

    private fun showConfirmation(
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: Runnable,
    ) {
        activity.pendingHomeSyncDialog = HomeSyncConfirmDialogModel(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            dismissLabel = HomeTextCopy.cancelLabel(),
            onConfirm = Runnable {
                activity.pendingHomeSyncDialog = null
                onConfirm.run()
            },
            onDismiss = Runnable {
                activity.pendingHomeSyncDialog = null
                activity.rerenderLatestHomeRoute()
            },
        )
        activity.rerenderLatestHomeRoute()
    }

    private fun startBindingRecovery(
        action: SourceBindingAction,
        requireBackup: Boolean,
    ) {
        if (!running.compareAndSet(false, true)) return
        renderProgress(SourceBindingRecoveryUi.bindingProgressTitle())
        val storage = storage()
        try {
            activity.io.execute {
                try {
                    if (requireBackup && !storage.createSafetyBackup(System.currentTimeMillis())) {
                        postFailure(SourceBindingRecoveryUi.safetyBackupFailed())
                        return@execute
                    }
                    val settingsSnapshot = runBlocking { activity.syncUseCases.loadSettings() }
                    val provider = gateway().readProviderCollection(
                        settingsSnapshot.sync,
                        CollectionProgressListener.NONE,
                    )
                    val storedState = runBlocking { activity.syncUseCases.loadStoredState() }
                    activity.sourceBindingGate.recover(
                        provider = provider,
                        storedState = storedState,
                        action = action,
                        backupConfirmed = requireBackup,
                        nowMillis = System.currentTimeMillis(),
                    )
                    activity.postToMainIfActive {
                        running.set(false)
                        activity.runSync()
                    }
                } catch (failure: SourceBindingFailure) {
                    activity.postToMainIfActive {
                        running.set(false)
                        renderRecovery(failure.reason, failure.evidence)
                    }
                } catch (_: Exception) {
                    postFailure(SourceBindingRecoveryUi.verificationFailed())
                }
            }
        } catch (_: RejectedExecutionException) {
            running.set(false)
            renderFailure(SourceBindingRecoveryUi.verificationFailed())
        }
    }

    private fun startFreshProfile() {
        if (!running.compareAndSet(false, true)) return
        renderProgress(SourceBindingRecoveryUi.freshProfileProgressTitle())
        val storage = storage()
        try {
            activity.io.execute {
                try {
                    when (storage.prepareFreshProfile(System.currentTimeMillis())) {
                        FreshProfilePreparationResult.BACKUP_FAILED ->
                            postFailure(SourceBindingRecoveryUi.safetyBackupFailed())
                        FreshProfilePreparationResult.STAGING_FAILED ->
                            postFailure(SourceBindingRecoveryUi.freshProfileFailed())
                        FreshProfilePreparationResult.STAGED -> activity.postToMainIfActive {
                            running.set(false)
                            activity.closeForStagedRestore()
                        }
                    }
                } catch (_: Exception) {
                    postFailure(SourceBindingRecoveryUi.freshProfileFailed())
                }
            }
        } catch (_: RejectedExecutionException) {
            running.set(false)
            renderFailure(SourceBindingRecoveryUi.freshProfileFailed())
        }
    }

    private fun postFailure(message: String) {
        activity.postToMainIfActive {
            running.set(false)
            renderFailure(message)
        }
    }

    private fun renderFailure(message: String) {
        activity.renderSyncResultScreen(
            SyncResultScreenModel(
                title = SourceBindingRecoveryUi.recoveryFailedTitle(),
                headline = null,
                lines = listOf(message),
                accentColor = MainActivityBase.CORAL,
                primaryLabel = null,
                primaryColor = MainActivityBase.TEAL,
                onPrimary = null,
                secondaryLabel = StudyTextCopy.backHomeLabel(),
                onSecondary = Runnable(activity::renderHome),
            ),
        )
    }

    private fun renderProgress(title: String) {
        activity.renderHomeRoute {
            SyncProgressScreen(
                title = title,
                progressPanel = SyncProgressPanel(),
            )
        }
    }

    private fun storage(): SourceBindingRecoveryStorage =
        SourceBindingRecoveryStorage(activity, activity::snapshotBackupInto)

    private fun gateway(): CollectionGateway =
        MainActivityRuntimeOverrides.collectionGateway ?: activity.gateway
}
