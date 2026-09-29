@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetic.KineticApp
import dev.kinetick.kinetic.api.KcodeClient
import dev.kinetick.kinetic.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as KineticApp
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val server = app.settings.server.first()
        url = server.baseUrl
        token = server.token
    }

    fun testAndSave() {
        scope.launch {
            status = "Testing…"
            val result = withContext(Dispatchers.IO) {
                runCatching { dev.kinetick.kinetic.api.Wire.obj(KcodeClient(url, token).health()) }
            }
            result.fold(
                onSuccess = { health ->
                    val version = health?.get("version")?.takeIf { it.isJsonPrimitive }?.asString ?: "?"
                    app.settings.setServer(url, token)
                    token = KcodeClient.normalizeServerToken(token)
                    status = "Connected — kcode $version"
                },
                onFailure = { status = "Failed: ${it.message}" }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Text("←", style = MaterialTheme.typography.titleLarge) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader("Appearance")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(
                        selected = m == themeMode,
                        onClick = { onSetTheme(m) },
                        label = { Text("${m.glyph} ${m.label}", style = MaterialTheme.typography.labelLarge) },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Auto follows the phone; the others pin light or dark.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            SectionHeader("kcode server")
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; status = null },
                label = { Text("Base URL") },
                placeholder = { Text("http://192.168.x.x:8788") },
                leadingIcon = { Text("🖥", style = MaterialTheme.typography.titleSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; status = null },
                label = { Text("Access token") },
                placeholder = { Text("session-server.token") },
                leadingIcon = { Text("🔑", style = MaterialTheme.typography.titleSmall) },
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "Hide" else "Show")
                    }
                },
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { testAndSave() }),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Every request needs this token. On the server machine it is the one line in ~/.kinetick/run/session-server.token.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = ::testAndSave,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Test & save", style = MaterialTheme.typography.titleSmall)
            }
            status?.let {
                val ok = it.startsWith("Connected")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        (if (ok) "✓ " else "⚠ ") + it,
                        color = if (ok) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            SectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(
                    "Start the server on your machine with:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "kcode --server --host 0.0.0.0 --port 8788",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = Fonts.Code,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The phone must reach that machine on the same network. Copy the token file onto the phone; the server never prints it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
