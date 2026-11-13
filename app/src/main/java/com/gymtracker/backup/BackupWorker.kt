package com.gymtracker.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gymtracker.GymApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Sauvegarde automatique hebdomadaire dans le dossier choisi ; garde les 5 dernières. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GymApp
        val s = app.settings.settings.value
        val folder = s.backupFolderUri?.let(Uri::parse) ?: return Result.success()
        return runCatching {
            backupToFolder(app, folder)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val WORK_NAME = "auto-backup"
        private const val KEEP = 5
        const val PREFIX = "gymtracker-sauvegarde-"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Écrit une sauvegarde datée dans le dossier, puis supprime les plus anciennes. */
        suspend fun backupToFolder(app: GymApp, treeUri: Uri): BackupManager.Summary {
            val resolver = app.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val doc = DocumentsContract.createDocument(resolver, parent, "application/zip", "$PREFIX${LocalDate.now()}.zip")
                ?: error("Impossible de créer le fichier dans le dossier choisi")
            val summary = resolver.openOutputStream(doc)?.use { app.backup.write(it) } ?: error("Dossier inaccessible")
            app.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
            prune(app, treeUri)
            return summary
        }

        private fun prune(app: GymApp, treeUri: Uri) {
            val resolver = app.contentResolver
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val found = mutableListOf<Pair<String, String>>() // (id, nom)
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(PREFIX) && name.endsWith(".zip")) found += c.getString(0) to name
                }
            }
            found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
                runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id)) }
            }
        }
    }
}
