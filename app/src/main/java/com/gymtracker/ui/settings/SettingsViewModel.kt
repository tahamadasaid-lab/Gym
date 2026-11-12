package com.gymtracker.ui.settings

import androidx.lifecycle.ViewModel
import com.gymtracker.settings.AppSettings
import com.gymtracker.settings.SettingsStore
import com.gymtracker.timer.RestTimer
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val store: SettingsStore,
    private val timer: RestTimer,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings

    fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)

    fun testAlert() = timer.alert("Développé couché")
}
