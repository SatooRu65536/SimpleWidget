package dev.satooru.simplewidget

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotoItem(val id: String, val thumbnail: ImageBitmap)

suspend fun loadPhotoItems(context: Context): List<PhotoItem> = withContext(Dispatchers.IO) {
    PhotoLibrary.photoIds(context).mapNotNull { id ->
        PhotoLibrary.loadThumbnail(context, id)?.let { PhotoItem(id, it.asImageBitmap()) }
    }
}

/** 選択された画像をライブラリに取り込み、成功した写真 ID を返す。 */
suspend fun importPhotos(context: Context, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
    uris.mapNotNull { runCatching { PhotoLibrary.add(context, it) }.getOrNull() }
}

/** ライブラリの写真を正方形のサムネイルでグリッドに並べる。 */
fun LazyGridScope.photoItems(
    photos: List<PhotoItem>,
    selectedId: String? = null,
    onClick: (PhotoItem) -> Unit,
) {
    items(photos, key = { it.id }) { photo ->
        val shape = RoundedCornerShape(12.dp)
        var modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
        if (photo.id == selectedId) {
            modifier = modifier.border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary), shape)
        }
        Image(
            bitmap = photo.thumbnail,
            contentDescription = stringResource(R.string.widget_image_description),
            contentScale = ContentScale.Crop,
            modifier = modifier.clickable { onClick(photo) },
        )
    }
}

/** ウィジェット一覧などで使う、写真または「未設定」表示のサムネイル。 */
@Composable
fun PhotoThumbnail(image: ImageBitmap?, modifier: Modifier = Modifier) {
    val thumbModifier = modifier.clip(RoundedCornerShape(16.dp))
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.widget_image_description),
            contentScale = ContentScale.Crop,
            modifier = thumbModifier,
        )
    } else {
        Box(
            thumbModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.no_photo), style = MaterialTheme.typography.bodySmall)
        }
    }
}
