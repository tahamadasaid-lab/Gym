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

    ScreenScaffold(
        title = "Réglages",
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
