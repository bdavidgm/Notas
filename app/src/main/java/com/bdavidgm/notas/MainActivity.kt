package com.bdavidgm.notas

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bdavidgm.notas.navigation.NotasNavHost
import com.bdavidgm.notas.ui.theme.NotasTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val _pendingOpenDocumentUri = MutableStateFlow<Uri?>(null)
    val pendingOpenDocumentUri: StateFlow<Uri?> = _pendingOpenDocumentUri.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solo en el arranque fresco / Abrir con…; no reimportar tras rotación.
        if (savedInstanceState == null) {
            _pendingOpenDocumentUri.value = extractOpenDocumentUri(intent)
        }
        setContent {
            NotasTheme(dynamicColor = false) {
                val pendingUri by pendingOpenDocumentUri.collectAsState()
                NotasNavHost(
                    pendingOpenDocumentUri = pendingUri,
                    onOpenDocumentConsumed = { consumed ->
                        if (_pendingOpenDocumentUri.value == consumed) {
                            _pendingOpenDocumentUri.value = null
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _pendingOpenDocumentUri.value = extractOpenDocumentUri(intent)
    }

    companion object {
        fun extractOpenDocumentUri(intent: Intent?): Uri? {
            if (intent == null) return null
            return when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> {
                    val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    }
                    streamUri ?: intent.clipData?.getItemAt(0)?.uri
                }
                else -> null
            }
        }
    }
}
