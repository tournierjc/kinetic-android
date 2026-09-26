package dev.kinetick.kinetic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dev.kinetick.kinetic.events.EventStreamService
import dev.kinetick.kinetic.ui.KineticNavGraph
import dev.kinetick.kinetic.ui.KineticTheme
import dev.kinetick.kinetic.ui.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) EventStreamService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotifPermissionAndStartStream()

        val settings = (application as KineticApp).settings
        setContent {
            val scope = rememberCoroutineScope()
            val mode = ThemeMode.fromId(settings.themeMode.collectAsState(initial = "system").value)
            KineticTheme(mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KineticNavGraph(
                        initialSessionId = intent?.getStringExtra("session_id"),
                        themeMode = mode,
                        onSetTheme = { next -> scope.launch { settings.setThemeMode(next.id) } },
                    )
                }
            }
        }
    }

    private fun askNotifPermissionAndStartStream() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            EventStreamService.start(this)
        }
    }
}
