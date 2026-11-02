package com.yume.studio.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Сохранение готовых треков в «Музыку» и шеринг через системное меню. */
object ExportStore {
    fun authority(context: Context) = "${context.packageName}.files"

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun shareIntent(context: Context, file: File, mime: String = "audio/wav"): Intent {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Поделиться треком").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Копирует WAV в Music/YumeStudio (Android 10+ — без разрешений). */
    suspend fun saveToMusic(context: Context, file: File): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/YumeStudio")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun listExports(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && (f.extension == "wav" || f.extension == "yume") }?.sortedByDescending { it.lastModified() }.orEmpty()
}
