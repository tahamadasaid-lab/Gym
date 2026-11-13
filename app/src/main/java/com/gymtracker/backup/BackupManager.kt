package com.gymtracker.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.gymtracker.data.AppDatabase
import com.gymtracker.data.ImageStore
import com.gymtracker.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sauvegarde complète : toutes les tables (séances, programmes, catégories, objectifs, mensurations…),
 * les photos (exercices et progression) et les réglages, dans un fichier ZIP.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    data class Summary(val sessions: Int, val exercises: Int, val programs: Int, val photos: Int)

    /** Ordre parents → enfants (insertion) ; l'inverse pour la suppression. */
    private val tables = listOf(
        "muscle_groups", "exercises", "sessions", "session_exercises", "sets",
        "programs", "program_exercises", "body_weights", "goals", "measurements", "progress_photos",
    )
    private val imageDirs = listOf(ImageStore.EXERCISE_DIR, ImageStore.PHOTO_DIR)

    suspend fun write(out: OutputStream): Summary = withContext(Dispatchers.IO) {
        val sdb = db.openHelper.readableDatabase
        val tablesJson = JSONObject()
        val counts = mutableMapOf<String, Int>()
        tables.forEach { t ->
            val arr = JSONArray()
            sdb.query("SELECT * FROM `$t`").use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> o.put(c.getColumnName(i), JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> o.put(c.getColumnName(i), c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> o.put(c.getColumnName(i), c.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> o.put(c.getColumnName(i), c.getString(i))
                        }
                    }
                    arr.put(o)
                }
            }
            tablesJson.put(t, arr)
            counts[t] = arr.length()
        }
        val root = JSONObject()
            .put("format", 1)
            .put("dbVersion", sdb.version)
            .put("createdAt", System.currentTimeMillis())
            .put("filesDir", context.filesDir.absolutePath)
            .put("tables", tablesJson)
            .put("settings", settings.exportJson())

        var photos = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            imageDirs.forEach { dir ->
                File(context.filesDir, dir).listFiles()?.filter { it.isFile }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("files/$dir/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    photos++
                }
            }
        }
        Summary(counts["sessions"] ?: 0, counts["exercises"] ?: 0, counts["programs"] ?: 0, photos)
    }

    /** Remplace toutes les données par celles de la sauvegarde. */
    suspend fun restore(input: InputStream): Summary = withContext(Dispatchers.IO) {
        var json: String? = null
        val filesRoot = context.filesDir.canonicalFile
        var photos = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == JSON_ENTRY -> json = zip.readBytes().toString(Charsets.UTF_8)
                    name.startsWith("files/") -> {
                        val rel = name.removePrefix("files/")
                        val dir = rel.substringBefore('/')
                        val target = File(filesRoot, rel).canonicalFile
                        // Sécurité : uniquement les dossiers de photos, sans sortir du stockage de l'appli.
                        if (dir in imageDirs && target.parentFile == File(filesRoot, dir).canonicalFile) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            photos++
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val root = JSONObject(json ?: error("Ce fichier n'est pas une sauvegarde GymTracker"))
        val version = root.optInt("dbVersion", 0)
        val current = db.openHelper.readableDatabase.version
        require(version <= current) { "Sauvegarde créée par une version plus récente de l'application : mets-la à jour d'abord." }
        val oldFiles = root.optString("filesDir", "")
        val newFiles = context.filesDir.absolutePath
        val data = root.getJSONObject("tables")

        db.runInTransaction(Runnable {
            val sdb = db.openHelper.writableDatabase
            sdb.execSQL("PRAGMA defer_foreign_keys = ON")
            tables.asReversed().forEach { sdb.execSQL("DELETE FROM `$it`") }
            tables.forEach { t ->
                val arr = data.optJSONArray(t) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cv = ContentValues()
                    o.keys().forEach { k ->
                        when (val v = o.get(k)) {
                            JSONObject.NULL -> cv.putNull(k)
                            is String -> cv.put(k, if (oldFiles.isNotEmpty() && v.startsWith(oldFiles)) newFiles + v.removePrefix(oldFiles) else v)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Double -> cv.put(k, v)
                            is Boolean -> cv.put(k, if (v) 1L else 0L)
                            is Number -> cv.put(k, v.toDouble())
                        }
                    }
                    sdb.insert(t, SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        })
        root.optJSONObject("settings")?.let { settings.importJson(it) }
        Summary(
            data.optJSONArray("sessions")?.length() ?: 0,
            data.optJSONArray("exercises")?.length() ?: 0,
            data.optJSONArray("programs")?.length() ?: 0,
            photos,
        )
    }

    companion object {
        const val JSON_ENTRY = "gymtracker-backup.json"
    }
}
