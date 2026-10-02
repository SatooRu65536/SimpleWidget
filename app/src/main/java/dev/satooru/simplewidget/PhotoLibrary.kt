package dev.satooru.simplewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.edit
import java.io.File
import java.util.UUID

/**
 * 取り込んだ写真のライブラリと、ウィジェットごとにどの写真を表示するかの割り当てを管理する。
 * 写真はアプリ内部ストレージに保存するため、ウィジェットの有無に関係なく保持される。
 */
object PhotoLibrary {
    // RemoteViews の Bitmap サイズ上限に収まるよう長辺を制限する
    private const val MAX_SIZE = 1024
    private const val PREFS = "widget_photos"

    private fun dir(context: Context) = File(context.filesDir, "library").apply { mkdirs() }

    private fun file(context: Context, photoId: String) = File(dir(context), "$photoId.jpg")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"

    /** ライブラリの写真 ID 一覧（新しい順）。 */
    fun photoIds(context: Context): List<String> =
        dir(context).listFiles { f -> f.extension == "jpg" }.orEmpty()
            .sortedByDescending { it.name }
            .map { it.nameWithoutExtension }

    /** 画像を取り込んでライブラリに追加し、写真 ID を返す。 */
    fun add(context: Context, uri: Uri): String {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (w, h) = info.size.width to info.size.height
            val scale = MAX_SIZE.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // 先頭を固定桁の時刻にしてファイル名順 = 追加順にする
        val photoId = "%013d_%s".format(System.currentTimeMillis(), UUID.randomUUID().toString().take(8))
        val dest = file(context, photoId)
        val tmp = File(dest.path + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(dest)
        return photoId
    }

    fun load(context: Context, photoId: String): Bitmap? {
        val f = file(context, photoId)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** 一覧表示用に縮小して読み込む。 */
    fun loadThumbnail(context: Context, photoId: String): Bitmap? {
        val f = file(context, photoId)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })
    }

    /** 写真を削除する。表示していたウィジェットは未設定に戻る。 */
    fun delete(context: Context, photoId: String) {
        file(context, photoId).delete()
        val affected = PhotoWidgetProvider.widgetIds(context).filter { photoIdFor(context, it) == photoId }
        prefs(context).edit { affected.forEach { remove(key(it)) } }
        affected.forEach { PhotoWidgetProvider.update(context, it) }
    }

    fun assign(context: Context, appWidgetId: Int, photoId: String) {
        prefs(context).edit { putString(key(appWidgetId), photoId) }
    }

    fun photoIdFor(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(key(appWidgetId), null)

    /** ウィジェットに割り当てられた画像。未設定や削除済みなら null。 */
    fun loadForWidget(context: Context, appWidgetId: Int): Bitmap? =
        photoIdFor(context, appWidgetId)?.let { load(context, it) }

    fun unassign(context: Context, appWidgetId: Int) {
        prefs(context).edit { remove(key(appWidgetId)) }
    }
}
