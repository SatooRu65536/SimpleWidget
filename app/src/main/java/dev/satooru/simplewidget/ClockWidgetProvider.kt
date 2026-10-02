package dev.satooru.simplewidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 時刻・西暦・和暦・日付・曜日を表示するウィジェット。
 * 時刻と月日・曜日は TextClock が自動で更新するため、ここでは年の行だけを管理する。
 */
class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context)) }
        scheduleNewYearUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // 時刻・タイムゾーン変更で年がずれる可能性があるので全体を更新し直す
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> updateAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(updatePendingIntent(context))
    }

    companion object {
        private val warekiFormatter = DateTimeFormatter.ofPattern("GGGGy年", Locale.JAPAN)

        private fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
            scheduleNewYearUpdate(context)
        }

        private fun buildViews(context: Context): RemoteViews {
            val today = LocalDate.now()
            val wareki = warekiFormatter.format(JapaneseDate.from(today))
            return RemoteViews(context.packageName, R.layout.widget_clock).apply {
                setTextViewText(R.id.clock_year, "${today.year}年（$wareki）")
            }
        }

        /** 年が変わったら年の行を更新する。正確さより省電力を優先して非 exact アラームを使う。 */
        private fun scheduleNewYearUpdate(context: Context) {
            val nextYear = LocalDate.now().withDayOfYear(1).plusYears(1)
            val triggerAt = nextYear.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java)
                .set(AlarmManager.RTC, triggerAt, updatePendingIntent(context))
        }

        private fun updatePendingIntent(context: Context): PendingIntent {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            val intent = Intent(context, ClockWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
