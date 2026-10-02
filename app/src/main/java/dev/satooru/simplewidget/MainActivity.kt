package dev.satooru.simplewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.satooru.simplewidget.ui.theme.SimpleWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // アプリ外でウィジェットが追加・削除・変更されても戻ってきたとき一覧を更新する
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        refreshKey = resumeCount,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }
}

private data class WidgetItem(val appWidgetId: Int, val image: ImageBitmap?)

private fun loadWidgetItems(context: Context): List<WidgetItem> =
    PhotoWidgetProvider.widgetIds(context).map { id ->
        WidgetItem(id, PhotoLibrary.loadForWidget(context, id)?.asImageBitmap())
    }

@Composable
fun MainScreen(refreshKey: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var widgets by remember { mutableStateOf<List<WidgetItem>?>(null) }
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PhotoItem?>(null) }

    suspend fun reload() {
        widgets = withContext(Dispatchers.IO) { loadWidgetItems(context) }
        photos = loadPhotoItems(context)
    }

    LaunchedEffect(refreshKey) { reload() }

    val pickPhotos = rememberPhotoPicker { uris ->
        if (uris.isEmpty()) return@rememberPhotoPicker
        scope.launch {
            importing = true
            val added = importPhotos(context, uris)
            importing = false
            if (added.size < uris.size) {
                Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
            reload()
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            text = { Text(stringResource(R.string.delete_photo_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { PhotoLibrary.delete(context, target.id) }
                        reload()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val currentWidgets = widgets ?: return
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fullWidth { SectionTitle(stringResource(R.string.section_widgets)) }
        if (currentWidgets.isEmpty()) {
            fullWidth { Text(stringResource(R.string.no_widgets)) }
        }
        items(currentWidgets, key = { "widget_${it.appWidgetId}" }, span = { GridItemSpan(maxLineSpan) }) { item ->
            WidgetRow(
                item = item,
                onChangeClick = {
                    context.startActivity(
                        Intent(context, WidgetConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, item.appWidgetId)
                    )
                },
            )
        }

        fullWidth {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(stringResource(R.string.section_library), Modifier.weight(1f))
                Button(onClick = pickPhotos, enabled = !importing) {
                    Text(stringResource(if (importing) R.string.importing else R.string.add_photos))
                }
            }
        }
        if (photos.isEmpty()) {
            fullWidth { Text(stringResource(R.string.no_photos)) }
        } else {
            fullWidth {
                Text(stringResource(R.string.library_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        photoItems(photos, onClick = { deleteTarget = it })
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun WidgetRow(item: WidgetItem, onChangeClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PhotoThumbnail(item.image, Modifier.size(96.dp))
        Button(onClick = onChangeClick, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.change_photo))
        }
    }
}
