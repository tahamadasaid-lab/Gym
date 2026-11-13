package com.gymtracker.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copie les photos choisies dans le stockage privé de l'appli (elles restent disponibles même si l'original est supprimé). */
class ImageStore(private val context: Context) {
    private fun dir(name: String): File = File(context.filesDir, name).apply { mkdirs() }

    suspend fun saveExerciseImage(exerciseId: Long, source: Uri): String? =
        copy(source, dir(EXERCISE_DIR), "exercise_${exerciseId}_${System.currentTimeMillis()}.jpg")

    suspend fun saveProgressPhoto(source: Uri): String? =
        copy(source, dir(PHOTO_DIR), "photo_${System.currentTimeMillis()}.jpg")

    private suspend fun copy(source: Uri, dir: File, name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, name)
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path == null) return@withContext
        val f = File(path)
        if (f.parentFile?.name in listOf(EXERCISE_DIR, PHOTO_DIR) && f.parentFile?.parentFile == context.filesDir) {
            runCatching { f.delete() }
        }
    }

    companion object {
        const val EXERCISE_DIR = "exercise_images"
        const val PHOTO_DIR = "progress_photos"
    }
}
