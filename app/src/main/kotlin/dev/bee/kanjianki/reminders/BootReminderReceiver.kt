package dev.bee.kanjianki.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.backup.DatabaseBackupScheduler
import dev.bee.kanjianki.core.ReminderReceiverPolicy
import dev.bee.kanjianki.receivers.ReceiverAsyncWork
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.update.AutoUpdateScheduler

class BootReminderReceiver internal constructor(
    private val actions: RescheduleActions,
) : BroadcastReceiver() {
    constructor() : this(ANDROID_RESCHEDULE_ACTIONS)

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (!shouldReschedule(action)) {
            return
        }
        // Reschedules four subsystems, each opening its own LocalStore; run off the
        // main thread and keep the broadcast alive until it completes.
        ReceiverAsyncWork.run(
            this,
            executorProvider = {
                requireNotNull(context).requireKaniContainer().maintenanceExecutor
            },
        ) {
            handle(context, action, actions)
        }
    }

    fun interface ActionReader<T> {
        fun read(source: T): String?
    }

    fun interface RescheduleActions {
        fun schedule(context: Context?)
    }

    companion object {
        private val ANDROID_RESCHEDULE_ACTIONS = RescheduleActions { context ->
            val receiverContext = context!!
            receiverContext.requireKaniContainer().openLocalStore().use { store ->
                ReminderScheduler.schedule(receiverContext, store)
                AutoSyncScheduler.schedule(receiverContext, store, store.autoSyncSettings())
                AutoUpdateScheduler.schedule(receiverContext, store)
            }
            DatabaseBackupScheduler.schedule(receiverContext)
        }

        private val INTENT_ACTION_READER: ActionReader<Intent> = ActionReader { source -> source.action }

        @JvmStatic
        fun handle(context: Context?, intent: Intent?, actions: RescheduleActions) {
            handle(context, actionOrEmpty(intent, INTENT_ACTION_READER), actions)
        }

        @JvmStatic
        fun handle(context: Context?, action: String?, actions: RescheduleActions) {
            if (shouldReschedule(action)) {
                actions.schedule(context)
            }
        }

        @JvmStatic
        fun <T> actionOrEmpty(source: T?, reader: ActionReader<T>): String {
            if (source == null) {
                return ""
            }
            return reader.read(source) ?: ""
        }

        @JvmStatic
        fun shouldReschedule(action: String?): Boolean {
            return ReminderReceiverPolicy.shouldReschedule(action)
        }
    }
}
