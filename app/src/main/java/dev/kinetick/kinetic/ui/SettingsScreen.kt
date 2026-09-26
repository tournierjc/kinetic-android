@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetic.KineticApp
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KineticApp
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        url = app.settings.baseUrl.first()
    }

    fun testAndSave() {
        scope.launch {
            status = "Testing…"
            val result = withContext(Dispatchers.IO) {
                runCatching { dev.kinetick.kinetic.api.Wire.obj(KcodeClient(url).health()) }
            }
            result.fold(
                onSuccess = { health ->
                    val version = health?.get("version")?.takeIf { it.isJsonPrimitive }?.asString ?: "?"
                    app.settings.setBaseUrl(url)
                    status = "Connected — kcode $version"
                    editing = false
                },
                onFailure = { status = "Failed: ${it.message}" }
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopAppBar(
            title = { Text("kcode server") },
            navigationIcon = { TextButton(onClick = onDone) { Text("Back") } }
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; status = null },
            label = { Text("Base URL (http://192.168.x.x:8788)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = ::testAndSave, modifier = Modifier.fillMaxWidth()) {
            Text("Test & save")
        }
        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Start the server on your machine with: kcode --server --port 8788\n" +
                "The phone must reach that machine on the same network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
