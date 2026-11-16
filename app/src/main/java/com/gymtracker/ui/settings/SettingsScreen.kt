@file:OptIn(ExperimentalMaterial3Api::class)

package com.gymtracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.gymtracker.util.formatLongDate
import com.gymtracker.util.formatTime
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymtracker.settings.ThemeMode
import com.gymtracker.ui.AppViewModelProvider
import com.gymtracker.ui.components.LocalCalories
import com.gymtracker.ui.components.NumberStepper
import com.gymtracker.ui.components.PlateCalculatorDialog
import androidx.compose.material.icons.filled.Calculate
import com.gymtracker.ui.components.ScreenScaffold
import com.gymtracker.ui.components.SectionHeader
import com.gymtracker.util.formatWeight
import com.gymtracker.util.parseWeight

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val calories = LocalCalories.current
    var weightText by remember { mutableStateOf(formatWeight(s.profileWeightKg)) }
    var showPlates by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var healthGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { healthGranted = runCatching { vm.health.grantedAll() }.getOrDefault(false) }
    val healthLauncher = rememberLauncherForActivityResult(vm.health.permissionContract()) { granted ->
        healthGranted = granted.containsAll(vm.health.permissions)
        if (healthGranted) vm.update { it.copy(healthSyncEnabled = true) }
    }
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg, withDismissAction = true) } }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backupTo(uri, notify)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreFrom(uri, notify)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.enableAutoBackup(uri, notify)
    }
    if (showPlates) PlateCalculatorDialog(initialWeight = "", onDismiss = { showPlates = false })

    ScreenScaffold(
        title = "Réglages",
        snackbarHostState = snackbar,
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Apparence") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Thème", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val modes = ThemeMode.entries
                            modes.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = s.themeMode == mode,
                                    onClick = { vm.update { it.copy(themeMode = mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = s.themeMode == mode) {
                                            Icon(
                                                when (mode) {
                                                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                            )
                                        }
                                    },
                                ) { Text(mode.label) }
                            }
                        }
                        Text(
                            "« Système » suit le réglage clair / sombre de ton téléphone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.themeMode != ThemeMode.LIGHT) {
                        HorizontalDivider()
                        SwitchRow(
                            "Noir intégral",
                            "Fond entièrement noir en thème sombre : plus contrasté et économise la batterie sur les écrans OLED.",
                            s.pureBlack,
                        ) { v -> vm.update { it.copy(pureBlack = v) } }
                    }
                }
            }

            item { SectionHeader("Fin du repos") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        SwitchRow(
                            "Signal sonore",
                            "Bips à la fin du repos, joués dans tes écouteurs s'ils sont branchés (la musique baisse un instant).",
                            s.soundEnabled,
                        ) { v -> vm.update { it.copy(soundEnabled = v) } }
                        HorizontalDivider()
                        SwitchRow(
                            "Annonce vocale",
                            "« Repos terminé. Série suivante : Squat »",
                            s.voiceEnabled,
                        ) { v -> vm.update { it.copy(voiceEnabled = v) } }
                        HorizontalDivider()
                        SwitchRow(
                            "Prévenir 10 secondes avant",
                            "Annonce « 10 secondes » (ou un bip court) pour te préparer.",
                            s.warningEnabled,
                        ) { v -> vm.update { it.copy(warningEnabled = v) } }
                        HorizontalDivider()
                        SwitchRow("Vibration", null, s.vibrationEnabled) { v -> vm.update { it.copy(vibrationEnabled = v) } }
                        HorizontalDivider()
                        SwitchRow(
                            "Sonner même en mode silencieux",
                            "Utilise le volume « alarme ». Attention : le son sort aussi par le haut-parleur du téléphone.",
                            s.useAlarmChannel,
                        ) { v -> vm.update { it.copy(useAlarmChannel = v) } }
                    }
                }
            }
            item {
                FilledTonalButton(onClick = { vm.testAlert() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tester l'alerte")
                }
            }
            item {
                Text(
                    "Le volume suit le volume média du téléphone (celui de ta musique). " +
                        "Si tu n'entends rien, monte le volume pendant la lecture d'une musique.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { SectionHeader("Disques") }
            item {
                FilledTonalButton(onClick = { showPlates = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Calculate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Calculateur de disques · barre ${formatWeight(s.barWeightKg)} kg")
                }
            }

            item { SectionHeader("Planning et rappels") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        SwitchRow(
                            "Rappel les jours d'entraînement",
                            "Selon le planning de l'accueil ; pas de rappel si tu as déjà fait ta séance.",
                            s.reminderEnabled,
                        ) { on -> vm.updateReminders { it.copy(reminderEnabled = on) } }
                        TextButton(onClick = { showTimePicker = true }, modifier = Modifier.padding(start = 8.dp)) {
                            Text("Heure du rappel : ${s.reminderMinutes / 60}h${(s.reminderMinutes % 60).toString().padStart(2, '0')}")
                        }
                    }
                }
            }

            item { SectionHeader("Santé (Health Connect)") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Envoie tes séances et calories vers Google Fit, Samsung Health… et récupère la fréquence cardiaque " +
                                "de ta montre pour un calcul des calories plus fiable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        when {
                            vm.health.needsInstall -> TextButton(
                                onClick = { uriHandler.openUri("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata") },
                                modifier = Modifier.padding(start = 8.dp),
                            ) { Text("Installer Health Connect") }
                            !vm.health.isAvailable -> Text(
                                "Health Connect n'est pas disponible sur ce téléphone.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            !healthGranted -> FilledTonalButton(
                                onClick = { healthLauncher.launch(vm.health.permissions) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) { Text("Connecter Health Connect") }
                            else -> {
                                SwitchRow("Envoyer chaque séance terminée", "✅ Connecté", s.healthSyncEnabled) { on ->
                                    vm.update { it.copy(healthSyncEnabled = on) }
                                }
                                TextButton(onClick = { vm.syncAllHealth(notify) }, modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Envoyer toutes mes séances passées")
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            "Calories à partir du cœur (montre) : renseigne ton âge et ton sexe.",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberStepper(
                                "Âge",
                                s.age?.toString() ?: "",
                                { t -> vm.update { it.copy(age = t.toIntOrNull()?.takeIf { a -> a in 10..100 }) } },
                                step = 1.0,
                                decimal = false,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            listOf("H" to "Homme", "F" to "Femme").forEach { (k, label) ->
                                FilterChip(
                                    selected = s.sex == k,
                                    onClick = { vm.update { it.copy(sex = if (it.sex == k) null else k) } },
                                    label = { Text(label) },
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            item { SectionHeader("Sauvegarde complète") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Toutes tes données dans un fichier : séances, programmes, catégories, objectifs, " +
                                "mensurations, photos et réglages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { saveLauncher.launch("gymtracker-sauvegarde-${java.time.LocalDate.now()}.zip") },
                                modifier = Modifier.weight(1f),
                            ) { Text("Sauvegarder") }
                            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.weight(1f)) { Text("Restaurer") }
                        }
                        Text(
                            "Astuce : en choisissant Google Drive comme emplacement, ta sauvegarde est en ligne.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        HorizontalDivider()
                        SwitchRow(
                            "Sauvegarde automatique chaque semaine",
                            s.backupFolderUri?.let { "Dossier : " + folderName(it) + " · garde les 5 dernières" }
                                ?: "Choisis un dossier du téléphone (ex. Documents).",
                            s.autoBackupEnabled,
                        ) { on ->
                            if (!on) vm.disableAutoBackup()
                            else if (s.backupFolderUri == null) folderLauncher.launch(null)
                            else vm.enableAutoBackup(Uri.parse(s.backupFolderUri), notify)
                        }
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { folderLauncher.launch(null) }) { Text("Choisir le dossier") }
                            if (s.backupFolderUri != null) {
                                TextButton(onClick = { vm.backupNowToFolder(notify) }) { Text("Sauvegarder maintenant") }
                            }
                        }
                        Text(
                            s.lastBackupAt?.let { "Dernière sauvegarde : ${formatLongDate(it)} à ${formatTime(it)}" } ?: "Aucune sauvegarde pour l'instant",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        )
                    }
                }
            }

            item { SectionHeader("Calories") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberStepper(
                            label = "Ton poids (kg)",
                            value = weightText,
                            onValueChange = { t ->
                                weightText = t
                                parseWeight(t)?.takeIf { it in 30.0..300.0 }?.let { w -> vm.update { it.copy(profileWeightKg = w) } }
                            },
                            step = 0.5,
                            decimal = true,
                        )
                        Text(
                            "Poids utilisé actuellement : ${formatWeight(Math.round(calories.bodyWeightKg * 10) / 10.0)} kg " +
                                "(ta dernière pesée de l'onglet Progrès si tu en as une, sinon cette valeur).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Estimation : calories = intensité (MET) de la catégorie × poids × temps passé sur l'exercice " +
                                "(séries + repos). L'intensité se règle dans Exercices › Catégories. " +
                                "C'est une approximation : seule une montre cardio donne une mesure fiable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = s.reminderMinutes / 60,
            initialMinute = s.reminderMinutes % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Heure du rappel") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateReminders { it.copy(reminderMinutes = state.hour * 60 + state.minute) }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annuler") } },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restaurer une sauvegarde ?") },
            text = {
                Text(
                    "Toutes les données actuelles de l'appli seront remplacées par celles du fichier choisi. " +
                        "Fais d'abord une sauvegarde si tu as un doute."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                }) { Text("Choisir le fichier") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Nom lisible d'un dossier choisi (ex. « primary:Documents/GymTracker » → « Documents/GymTracker »). */
private fun folderName(uri: String): String =
    Uri.decode(uri).substringAfterLast("/tree/").substringAfter(':').ifBlank { "stockage interne" }
