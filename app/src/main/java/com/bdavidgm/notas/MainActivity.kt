package com.bdavidgm.notas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bdavidgm.notas.navigation.NotasNavHost
import com.bdavidgm.notas.ui.theme.NotasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotasTheme(dynamicColor = false) {
                NotasNavHost()
            }
        }
    }
}
