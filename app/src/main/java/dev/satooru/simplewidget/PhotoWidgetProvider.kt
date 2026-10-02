package dev.satooru.simplewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context, it)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // 写真自体はライブラリに残し、割り当てだけ解除する
        appWidgetIds.forEach { PhotoLibrary.unassign(context, it) }
    }

    companion object {
        /** 配置済みのウィジェット ID 一覧。 */
        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, PhotoWidgetProvider::class.java))

        /** 指定したウィジェットを保存済みの画像で更新する。 */
        fun update(context: Context, appWidgetId: Int) {
            AppWidgetManager.getInstance(context)
                .updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_photo)
            val bitmap = PhotoLibrary.loadForWidget(context, appWidgetId)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_image, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            }
            // タップでこのウィジェットに表示する写真を選び直す
            val pickPhoto = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pickPhoto)
            return views
        }
    }
}
