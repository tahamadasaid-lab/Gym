package com.gymtracker.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.GymApp
import com.gymtracker.backup.BackupManager
import com.gymtracker.backup.BackupWorker
import com.gymtracker.settings.AppSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: GymApp) : ViewModel() {
    private val store = app.settings
    val settings: StateFlow<AppSettings> = store.settings

    fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)

    fun testAlert() = app.restTimer.alert("Développé couché")

    private fun describe(s: BackupManager.Summary) =
        "${s.sessions} séance(s), ${s.exercises} exercices, ${s.programs} programme(s), ${s.photos} photo(s)"

    fun backupTo(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(
            runCatching {
                val s = app.contentResolver.openOutputStream(uri)?.use { app.backup.write(it) } ?: error("Fichier inaccessible")
                store.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
                "Sauvegarde terminée : ${describe(s)}"
            }.getOrElse { "Échec de la sauvegarde : ${it.message}" }
        )
    }

    fun restoreFrom(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(
            runCatching {
                val s = app.contentResolver.openInputStream(uri)?.use { app.backup.restore(it) } ?: error("Fichier inaccessible")
                "Restauration terminée : ${describe(s)}"
            }.getOrElse { "Échec de la restauration : ${it.message}" }
        )
    }

    /** Dossier choisi pour la sauvegarde automatique : on garde l'autorisation d'y écrire, puis on sauvegarde tout de suite. */
    fun enableAutoBackup(folder: Uri, onResult: (String) -> Unit) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        store.update { it.copy(autoBackupEnabled = true, backupFolderUri = folder.toString()) }
        BackupWorker.schedule(app, true)
        backupNowToFolder(onResult)
    }

    fun disableAutoBackup() {
        store.update { it.copy(autoBackupEnabled = false) }
        BackupWorker.schedule(app, false)
    }

    fun backupNowToFolder(onResult: (String) -> Unit) = viewModelScope.launch {
        val folder = store.settings.value.backupFolderUri?.let(Uri::parse) ?: return@launch
        onResult(
            runCatching { "Sauvegarde enregistrée : ${describe(BackupWorker.backupToFolder(app, folder))}" }
                .getOrElse { "Échec de la sauvegarde : ${it.message}" }
        )
    }
}
