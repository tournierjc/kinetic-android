@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetick.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetick.KinetickApp
import dev.kinetick.kinetick.api.KcodeClient
import dev.kinetick.kinetick.api.*
import dev.kinetick.kinetick.data.ServerEntry
import dev.kinetick.kinetick.data.ServerRegistry
import dev.kinetick.kinetick.data.SettingsStore
import dev.kinetick.kinetick.data.configured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add/edit form. For an existing server an empty token keeps the stored one
 * (token rotation goes through delete + re-add, which is unambiguous).
 */
private data class ServerDraft(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val token: String = "",
) {
    val editing: Boolean get() = id.isNotBlank()
    val canSave: Boolean get() = url.isNotBlank() && (token.isNotBlank() || editing)
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as KinetickApp
    val scope = rememberCoroutineScope()

    val config by app.settings.config.collectAsState(initial = SettingsStore.Config())
    var draft by remember { mutableStateOf<ServerDraft?>(null) }
    var deleteTarget by remember { mutableStateOf<ServerEntry?>(null) }

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
            SectionHeader(
                title = "kcode servers",
                count = config.servers.size,
                trailing = {
                    TextButton(onClick = { draft = ServerDraft() }) { Text("＋ Add") }
                },
            )

            if (config.servers.isEmpty()) {
                Text(
                    "No server registered. Tap ＋ Add and paste a URL plus the bearer " +
                        "token from ~/.kinetick/run/session-server.token. You can register " +
                        "several servers and the app talks to all of them at once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            config.servers.forEach { server ->
                val active = config.active?.id == server.id
                val usable = server.configured()
                SectionCard(
                    borderColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outlineVariant,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                ServerRegistry.label(server),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                server.baseUrl,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = Fonts.Code,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!usable) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Needs a valid bearer token (16-256 printable ASCII, no spaces).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(if (active) "active" else if (usable) "ready" else "needs token")
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconAction(
                                    "★",
                                    contentDescription = "Set active",
                                    onClick = { scope.launch { app.settings.setActive(server.id) } },
                                    tint = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconAction(
                                    "✎",
                                    contentDescription = "Edit server",
                                    onClick = {
                                        draft = ServerDraft(server.id, server.name, server.baseUrl, server.token)
                                    },
                                )
                                IconAction(
                                    "🗑",
                                    contentDescription = "Delete server",
                                    onClick = { deleteTarget = server },
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
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
                    "The server writes a bearer token to ~/.kinetick/run/session-server.token " +
                        "(mode 0600), or use the value you passed as --server-token. " +
                        "Every request needs it, including the health check.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The phone must reach that machine on the same network. " +
                        "Anyone with the token can read Sessions and run turns. " +
                        "The active server (★) is where new sessions are created.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---- add / edit dialog ----
    draft?.let { d ->
        var name by remember(d) { mutableStateOf(d.name) }
        var url by remember(d) { mutableStateOf(d.url) }
        var token by remember(d) { mutableStateOf(d.token) }
        var revealToken by remember(d) { mutableStateOf(false) }
        var status by remember(d) { mutableStateOf<String?>(null) }

        fun save(probe: Boolean, ctx: android.content.Context) {
            val base = url.trim().trimEnd('/')
            val bearer = token.trim()
            scope.launch {
                if (probe) {
                    status = "Testing…"
                    val result = withContext(Dispatchers.IO) {
                        runCatching { Wire.obj(KcodeClient(base, bearer).health()) }
                    }
                    result.fold(
                        onSuccess = { health ->
                            val version = health?.get("version")
                                ?.takeIf { it.isJsonPrimitive }?.asString ?: "?"
                            persist(ctx, d, name, base, bearer)
                            status = "Connected — kcode $version"
                        },
                        onFailure = { status = "Failed: ${it.message}" }
                    )
                } else {
                    persist(ctx, d, name, base, bearer)
                    draft = null
                }
            }
        }

        AlertDialog(
            onDismissRequest = { draft = null },
            title = { Text(if (d.editing) "Edit server" else "Add server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; status = null },
                        label = { Text("Name (optional)") },
                        placeholder = { Text("home, work, lab…") },
                        leadingIcon = { Text("🏷", style = MaterialTheme.typography.titleSmall) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; status = null },
                        label = { Text("Bearer token") },
                        placeholder = { Text("from session-server.token") },
                        leadingIcon = { Text("🔑", style = MaterialTheme.typography.titleSmall) },
                        trailingIcon = {
                            TextButton(onClick = { revealToken = !revealToken }) {
                                Text(if (revealToken) "Hide" else "Show")
                            }
                        },
                        visualTransformation = if (revealToken) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Paste ~/.kinetick/run/session-server.token. A trailing newline is " +
                            "ignored. Leave the token empty when editing to keep the stored one.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                }
            },
            confirmButton = {
                TextButton(
                    enabled = ServerDraft(d.id, name, url, token).canSave,
                    onClick = { save(probe = true, ctx = context) },
                ) { Text(if (d.editing) "Test & save" else "Test & add") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Save without a health probe: an offline server is still
                    // a registration the user knows about.
                    TextButton(
                        enabled = ServerDraft(d.id, name, url, token).canSave,
                        onClick = { save(probe = false, ctx = context) },
                    ) { Text("Save") }
                    TextButton(onClick = { draft = null }) { Text("Cancel") }
                }
            },
        )
    }

    // ---- delete confirmation ----
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${ServerRegistry.label(target)}?") },
            text = {
                Text(
                    "Removes the server and its bearer token from this phone. " +
                        "Sessions on the machine itself are untouched.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            KinetickApp.forget(target)
                            app.settings.deleteServer(target.id)
                            deleteTarget = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

private suspend fun persist(
    ctx: android.content.Context,
    d: ServerDraft,
    name: String,
    base: String,
    bearer: String,
) {
    val app = ctx.applicationContext as KinetickApp
    if (d.editing) {
        app.settings.config.first()?.server(d.id)?.let { KinetickApp.forget(it) }
        app.settings.updateServer(d.id, base, bearer, name.trim())
    } else {
        app.settings.addServer(base, bearer, name.trim())
    }
}
