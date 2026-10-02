package dev.satooru.simplewidget

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private const val GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos"

/** 写真の複数選択を起動する関数を返す。キャンセル時は空リストが渡される。 */
@Composable
fun rememberPhotoPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current

    // Google フォトアプリで直接選択する。複数選択時は clipData、単一選択時は data に入る
    val googlePhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val clip = data?.clipData
        val uris = if (clip != null) {
            (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        } else {
            listOfNotNull(data?.data)
        }
        onPicked(uris)
    }

    // Google フォト未インストール時はシステムのフォトピッカーを使う
    val systemPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
        onPicked,
    )

    return {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            .setType("image/*")
            .setPackage(GOOGLE_PHOTOS_PACKAGE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        if (intent.resolveActivity(context.packageManager) != null) {
            googlePhotosLauncher.launch(intent)
        } else {
            systemPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}
