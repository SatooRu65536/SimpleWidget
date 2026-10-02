package dev.satooru.simplewidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.satooru.simplewidget.ui.theme.SimpleWidgetTheme
import kotlinx.coroutines.launch

/**
 * ウィジェット配置時の設定画面、およびウィジェットタップ時の写真選び直し画面。
 * ライブラリから写真を選ぶか、Google フォトから追加して対象ウィジェットに設定したら閉じる。
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // 配置時に写真を選ばずに戻った場合はウィジェットを追加しない
        setResult(RESULT_CANCELED, resultIntent)

        fun applyPhoto(photoId: String) {
            PhotoLibrary.assign(this, appWidgetId, photoId)
            PhotoWidgetProvider.update(this, appWidgetId)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        enableEdgeToEdge()
        setContent {
            SimpleWidgetTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var photos by remember { mutableStateOf<List<PhotoItem>?>(null) }
                var importing by remember { mutableStateOf(false) }
                val selectedId = remember { PhotoLibrary.photoIdFor(context, appWidgetId) }

                LaunchedEffect(Unit) { photos = loadPhotoItems(context) }

                val pickPhotos = rememberPhotoPicker { uris ->
                    if (uris.isEmpty()) return@rememberPhotoPicker
                    scope.launch {
                        importing = true
                        val added = importPhotos(context, uris)
                        importing = false
                        if (added.size < uris.size) {
                            Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
                        }
                        // 1 枚だけ選んだならそのまま設定、複数ならライブラリから選ばせる
                        if (uris.size == 1 && added.size == 1) {
                            applyPhoto(added.first())
                        } else {
                            photos = loadPhotoItems(context)
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val current = photos ?: return@Scaffold
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.select_photo_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = pickPhotos,
                                enabled = !importing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(if (importing) R.string.importing else R.string.add_from_photos))
                            }
                        }
                        if (current.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(stringResource(R.string.no_photos))
                            }
                        }
                        photoItems(current, selectedId = selectedId, onClick = { applyPhoto(it.id) })
                    }
                }
            }
        }
    }
}
