package com.gymtracker.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copie les photos choisies dans le stockage privé de l'appli (elles restent disponibles même si l'original est supprimé). */
class ImageStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "exercise_images").apply { mkdirs() }

    suspend fun saveExerciseImage(exerciseId: Long, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, "exercise_${exerciseId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path != null) runCatching { File(path).takeIf { it.parentFile == dir }?.delete() }
    }
}
