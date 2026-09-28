package com.skohscripts.kairos.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform

/** Seule activité : l'interface Compose commune, bord à bord (docs/spec-v3/distribution.md § Android). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { KairosApp(Platform.ANDROID) }
    }
}
