package com.carvoice.cockpit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.carvoice.cockpit.ui.CarVoiceApp
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarVoiceCockpitTheme {
                CarVoiceApp()
            }
        }
    }
}