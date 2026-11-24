package dev.bee.kanjianki.kani.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.bee.kanjianki.R
import dev.bee.kanjianki.kani.KaniActivity
import dev.bee.kanjianki.kani.data.KaniStore
import dev.bee.kanjianki.kani.data.Reference
import dev.bee.kanjianki.kani.reminder.DailyReminder
import dev.bee.kanjianki.kani.ui.japaneseSpan
import kotlin.concurrent.thread

/** One look-alike pair on the home screen. Tap to sprint it. */
class PairWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread(name = "kani-widget") {
            try {
                renderSafely(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Re-renders every placed widget, e.g. after a session changes the top pair. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PairWidget::class.java))
            if (ids.isEmpty()) return
            thread(name = "kani-widget") { renderSafely(context, manager, ids) }
        }

        /** An uncaught error on a plain thread kills the whole app; a widget can wait a render. */
        private fun renderSafely(context: Context, manager: AppWidgetManager, ids: IntArray) {
            try {
                render(context, manager, ids)
            } catch (error: Exception) {
                android.util.Log.w("KaniWidget", "Widget render skipped.", error)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val store = KaniStore(context)
            val top = try {
                store.mixups().firstOrNull()
            } finally {
                store.close()
            }
            val ref = Reference(context)
            val views = RemoteViews(context.packageName, R.layout.widget_pair)
            if (top == null) {
                views.setTextViewText(R.id.widget_pair, japaneseSpan("未 · 末"))
                views.setTextViewText(R.id.widget_hint, "Your look-alikes show up here")
            } else {
                views.setTextViewText(R.id.widget_pair, japaneseSpan("${top.target} · ${top.picked}"))
                views.setTextViewText(R.id.widget_hint, "${ref.meaning(top.target)} · ${ref.meaning(top.picked)}")
            }
            val intent = Intent(context, KaniActivity::class.java)
                .putExtra(KaniActivity.EXTRA_SPRINT, top != null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, DailyReminder.REQUEST_WIDGET, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}
