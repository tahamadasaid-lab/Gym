package com.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gymtracker.ui.GymTrackerApp
import com.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymTrackerTheme {
                GymTrackerApp()
            }
        }
    }
}
