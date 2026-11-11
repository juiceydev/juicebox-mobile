package dev.juicey.juicebox.ui.settings

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.juicey.juicebox.BuildConfig
import dev.juicey.juicebox.R
import dev.juicey.juicebox.core.model.LinkVariant
import dev.juicey.juicebox.core.model.ShareLinkMode
import dev.juicey.juicebox.core.model.StorageHosts
import dev.juicey.juicebox.core.model.ThemeMode
import dev.juicey.juicebox.core.nowEpochSeconds
import dev.juicey.juicebox.ui.components.ConfirmDialog
import dev.juicey.juicebox.ui.components.HairlineDivider
import dev.juicey.juicebox.ui.components.LocalJuiceHaptics
import dev.juicey.juicebox.ui.components.SettingsActionRow
import dev.juicey.juicebox.ui.components.SettingsChoiceRow
import dev.juicey.juicebox.ui.components.SettingsGroup
import dev.juicey.juicebox.ui.components.SettingsInfoRow
import dev.juicey.juicebox.ui.components.SettingsNavRow
import dev.juicey.juicebox.ui.components.SettingsToggleRow
import dev.juicey.juicebox.ui.components.TitleWordmark
import dev.juicey.juicebox.ui.formatBytes
import dev.juicey.juicebox.ui.formatTtl
import dev.juicey.juicebox.ui.icons.JuiceboxIcons
import dev.juicey.juicebox.ui.icons.juiceboxIcon
import dev.juicey.juicebox.ui.theme.JuiceOrange
import dev.juicey.juicebox.ui.theme.JuiceOrangeDeep
import dev.juicey.juicebox.ui.theme.SuccessGreenLight
import kotlinx.coroutines.CancellationException

private enum class SettingsPage(val title: String) {
    ROOT(""),
    SERVER("Server"),
    STORAGE("Storage"),
    APPEARANCE("Appearance"),
    UPLOADS("Uploads"),
    LINKS("Default Link"),
    LIBRARY("Library"),
    ABOUT("About"),
    SESSION("This session"),
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var includeSession by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExport
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.encodeToByteArray())
                }
            }
        }
        pendingExport = null
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (raw != null) viewModel.importLibrary(raw) {}
        }
    }

    PredictiveBackHandler(enabled = page != SettingsPage.ROOT) { progress ->
        try {
            progress.collect { event -> backProgress = event.progress }
            page = SettingsPage.ROOT
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            backProgress = 0f
        }
    }

    AnimatedContent(
        targetState = page,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState == SettingsPage.SESSION || initialState == SettingsPage.SESSION) {
                (fadeIn(tween(220)) + scaleIn(
                    animationSpec = tween(260),
                    initialScale = 0.94f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                )) togetherWith
                    (fadeOut(tween(150)) + scaleOut(
                        animationSpec = tween(180),
                        targetScale = 1.03f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ))
            } else if (targetState == SettingsPage.ROOT) {
                (slideInHorizontally(tween(240)) { -it / 5 } + fadeIn(tween(190))) togetherWith
                    (slideOutHorizontally(
                        spring(dampingRatio = 0.8f, stiffness = 420f),
                    ) { it } + fadeOut(tween(120)))
            } else {
                (slideInHorizontally(
                    spring(dampingRatio = 0.82f, stiffness = 400f),
                ) { it } + fadeIn(tween(190))) togetherWith
                    (slideOutHorizontally(tween(170)) { -it / 5 } + fadeOut(tween(110)))
            }
        },
        label = "settings-page",
    ) { target ->
        if (target == SettingsPage.ROOT) {
            SettingsRoot(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onOpen = { page = it },
                onDismissMessage = viewModel::dismissMessage,
            )
        } else {
            SettingsSubPage(
                title = target.title,
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = backProgress * size.width * 0.22f
                        scaleX = 1f - backProgress * 0.03f
                        scaleY = 1f - backProgress * 0.03f
                        alpha = 1f - backProgress * 0.1f
                    },
                onBack = { page = SettingsPage.ROOT },
                onDismissMessage = viewModel::dismissMessage,
            ) { scopeModifier ->
                when (target) {
                    SettingsPage.SERVER -> ServerPage(state, viewModel, scopeModifier)
                    SettingsPage.STORAGE -> StoragePage(state, viewModel, scopeModifier)
                    SettingsPage.APPEARANCE -> AppearancePage(state, viewModel, scopeModifier)
                    SettingsPage.UPLOADS -> UploadsPage(state, viewModel, scopeModifier)
                    SettingsPage.LINKS -> LinksPage(state, viewModel, scopeModifier)
                    SettingsPage.LIBRARY -> LibraryPage(
                        state = state,
                        modifier = scopeModifier,
                        includeSession = includeSession,
                        onIncludeSessionChange = { includeSession = it },
                        onExport = {
                            viewModel.exportLibrary(includeSession) { json ->
                                pendingExport = json
                                createDocument.launch("juicebox-library-${nowEpochSeconds()}.json")
                            }
                        },
                        onImport = { openDocument.launch(arrayOf("application/json", "text/plain")) },
                        onClear = viewModel::clearLibrary,
                    )
                    SettingsPage.ABOUT -> AboutPage(
                        state = state,
                        modifier = scopeModifier,
                        onOpenSession = { page = SettingsPage.SESSION },
                        onForget = viewModel::forgetIdentity,
                        onOpenSite = { uriHandler.openUri(state.savedServer) },
                    )
                    SettingsPage.SESSION -> SessionPage(
                        state = state,
                        modifier = scopeModifier,
                        onForget = viewModel::forgetIdentity,
                    )
                    SettingsPage.ROOT -> Unit
                }
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    state: SettingsUiState,
    modifier: Modifier,
    onOpen: (SettingsPage) -> Unit,
    onDismissMessage: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                "Settings",
                style = MaterialTheme.typography.displaySmall,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.height(2.dp))
            TitleWordmark(
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Banner(state.error, true, onDismissMessage)
        Banner(state.message, false, onDismissMessage)

        SettingsGroup {
            SettingsNavRow(
                icon = JuiceboxIcons.globe,
                title = "Server",
                onClick = { onOpen(SettingsPage.SERVER) },
            )
            HairlineDivider()
            SettingsNavRow(
                icon = JuiceboxIcons.cloud,
                title = "Storage",
                onClick = { onOpen(SettingsPage.STORAGE) },
            )
            HairlineDivider()
            SettingsNavRow(
                icon = JuiceboxIcons.moon,
                title = "Appearance",
                onClick = { onOpen(SettingsPage.APPEARANCE) },
            )
            HairlineDivider()
            SettingsNavRow(
                icon = JuiceboxIcons.upload,
                title = "Uploads",
                onClick = { onOpen(SettingsPage.UPLOADS) },
            )
            HairlineDivider()
            SettingsNavRow(
                icon = JuiceboxIcons.link,
                title = "Default Link",
                onClick = { onOpen(SettingsPage.LINKS) },
            )
            HairlineDivider()
            SettingsNavRow(
                icon = JuiceboxIcons.files,
                title = "Library",
                onClick = { onOpen(SettingsPage.LIBRARY) },
            )
            HairlineDivider()
            SettingsNavRow(
                icon = JuiceboxIcons.info,
                title = "About",
                onClick = { onOpen(SettingsPage.ABOUT) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubPage(
    title: String,
    state: SettingsUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onDismissMessage: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.arrowLeft),
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        val bodyModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)

        Banner(state.error, true, onDismissMessage, Modifier.padding(horizontal = 20.dp))
        Banner(state.message, false, onDismissMessage, Modifier.padding(horizontal = 20.dp))

        content(bodyModifier)
    }
}

@Composable
private fun ServerPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            Column(Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = state.serverInput,
                    onValueChange = viewModel::setServerInput,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Server URL") },
                    placeholder = { Text("https://box.juicey.dev") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        state.connection.connecting -> "Checking"
                        state.connection.connected ->
                            state.config?.let { "Connected - v${it.version}" } ?: "Connected"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::saveServer,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.serverInput.isNotBlank() && !state.busy,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            juiceboxIcon(JuiceboxIcons.check),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }
        }

        state.config?.let { config ->
            SettingsGroup {
                SettingsInfoRow("Largest file", formatBytes(config.maxFileSizeBytes))
                HairlineDivider()
                SettingsInfoRow("Retention", "up to ${formatTtl(config.maxTtlHours)}")
            }
        }
    }
}

@Composable
private fun StoragePage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier,
) {
    val customHosts = state.settings.customHosts
        .filterNot { url -> StorageHosts.official.any { it.url == url } }
        .filterNot { url -> state.communityHosts.any { it.url == url } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            ChoiceOption(
                label = "Auto",
                selected = state.settings.storageHost == null,
                onClick = { viewModel.selectHost(null) },
            )
            StorageHosts.official.forEach { host ->
                HairlineDivider()
                ChoiceOption(
                    label = host.name,
                    selected = state.settings.storageHost == host.url,
                    onClick = { viewModel.selectHost(host.url) },
                )
            }
        }

        Column {
            Text(
                "Community",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            SettingsGroup {
                when {
                    state.communityHosts.isNotEmpty() -> {
                        state.communityHosts.forEachIndexed { index, host ->
                            if (index > 0) HairlineDivider()
                            ChoiceOption(
                                label = host.name,
                                selected = state.settings.storageHost == host.url,
                                onClick = { viewModel.selectHost(host.url) },
                            )
                        }
                    }
                    state.communityHostsLoaded -> Text(
                        "No community nodes listed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                    else -> Text(
                        "Checking for community nodes...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }

        if (customHosts.isNotEmpty()) {
            Column {
                Text(
                    "Saved nodes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
                SettingsGroup {
                    customHosts.forEachIndexed { index, url ->
                        if (index > 0) HairlineDivider()
                        ChoiceOption(
                            label = url.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                            selected = state.settings.storageHost == url,
                            onClick = { viewModel.selectHost(url) },
                        )
                    }
                }
            }
        }

        SettingsGroup {
            Column(Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = state.hostInput,
                    onValueChange = viewModel::setHostInput,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Custom node") },
                    placeholder = { Text("https://f-eu3.juicey.dev") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!state.hostMessage.isNullOrBlank()) {
                        Text(
                            state.hostMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.hostOk) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = viewModel::validateAndSaveHost,
                        enabled = !state.hostValidating,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        if (state.hostValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                juiceboxIcon(JuiceboxIcons.cloud),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("Check")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearancePage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            ThemeMode.all.forEachIndexed { index, mode ->
                if (index > 0) HairlineDivider()
                ThemeOption(
                    mode = mode,
                    selected = state.settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(mode: String, selected: Boolean, onClick: () -> Unit) {
    val swatches = when (mode) {
        ThemeMode.YOU -> listOf(Color(0xFF6750A4), Color(0xFF7D5260), Color(0xFF386A20))
        ThemeMode.VIOLET -> listOf(Color(0xFF6750A4), Color(0xFF7E5260), Color(0xFF386A20))
        ThemeMode.OCEAN -> listOf(Color(0xFF2F628C), Color(0xFF68587A), Color(0xFF386666))
        ThemeMode.FOREST -> listOf(Color(0xFF446732), Color(0xFF386666), Color(0xFF7C5635))
        ThemeMode.ROSE -> listOf(Color(0xFF8C4A60), Color(0xFF7C5635), Color(0xFF386A20))
        else -> listOf(JuiceOrange, JuiceOrangeDeep, SuccessGreenLight)
    }
    val haptics = LocalJuiceHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Text(
            ThemeMode.label(mode),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            swatches.forEach { color ->
                Box(
                    Modifier
                        .size(14.dp)
                        .background(color, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun UploadsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier,
) {
    var shareDialog by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            SettingsToggleRow(
                title = "Quick link",
                checked = state.settings.quickLink,
                onCheckedChange = viewModel::setQuickLink,
            )
            HairlineDivider()
            SettingsToggleRow(
                title = "Copy link automatically",
                checked = state.settings.autoCopy,
                onCheckedChange = viewModel::setAutoCopy,
            )
            HairlineDivider()
            SettingsToggleRow(
                title = "Haptics",
                checked = state.settings.haptics,
                onCheckedChange = viewModel::setHaptics,
            )
            HairlineDivider()
            SettingsChoiceRow(
                title = "Shared links",
                value = shareModeLabel(state.settings.shareLinkMode),
                onClick = { shareDialog = true },
            )
        }

        SettingsGroup {
            SettingsToggleRow(
                title = "Lock uploads",
                checked = state.settings.lockUploads,
                onCheckedChange = { checked ->
                    if (checked) {
                        passwordDialog = true
                    } else {
                        viewModel.setLockUploads(false)
                    }
                },
            )
            if (state.settings.lockUploads && state.settings.uploadPassword.isNotBlank()) {
                HairlineDivider()
                SettingsActionRow(
                    icon = JuiceboxIcons.key,
                    title = "Change password",
                    onClick = { passwordDialog = true },
                )
            }
        }
    }

    if (shareDialog) {
        ShareModeDialog(
            selected = state.settings.shareLinkMode,
            onSelect = viewModel::setShareLinkMode,
            onDismiss = { shareDialog = false },
        )
    }

    if (passwordDialog) {
        PasswordDialog(
            initial = state.settings.uploadPassword,
            onDismiss = { passwordDialog = false },
            onConfirm = { password ->
                if (password.isNotBlank()) {
                    viewModel.setUploadPassword(password)
                    viewModel.setLockUploads(true)
                }
                passwordDialog = false
            },
        )
    }
}

@Composable
private fun LinksPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            LinkVariant.all.forEachIndexed { index, variant ->
                if (index > 0) HairlineDivider()
                ChoiceOption(
                    label = LinkVariant.label(variant),
                    selected = state.settings.linkVariant == variant,
                    onClick = { viewModel.setLinkVariant(variant) },
                )
            }
        }
    }
}

@Composable
private fun LibraryPage(
    state: SettingsUiState,
    modifier: Modifier,
    includeSession: Boolean,
    onIncludeSessionChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            Column(Modifier.padding(18.dp)) {
                Text("${state.libraryCount} files", style = MaterialTheme.typography.titleMedium)
                Text(
                    "On this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HairlineDivider()
            SettingsToggleRow(
                title = "Include identity",
                checked = includeSession,
                onCheckedChange = onIncludeSessionChange,
            )
            HairlineDivider()
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Export")
                }
                FilledTonalButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.upload),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Import")
                }
            }
        }

        SettingsGroup {
            SettingsActionRow(
                icon = JuiceboxIcons.trash,
                title = "Clear local library",
                destructive = true,
                onClick = { confirmClear = true },
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            icon = JuiceboxIcons.trash,
            title = "Clear local library?",
            text = "Every saved file and link token leaves this phone. Files already " +
                "uploaded stay online until they expire.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun AboutPage(
    state: SettingsUiState,
    modifier: Modifier,
    onOpenSession: () -> Unit,
    onForget: () -> Unit,
    onOpenSite: () -> Unit,
) {
    var confirmForget by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    TitleWordmark(style = MaterialTheme.typography.titleLarge)
                    Text(
                        "for Android - ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HairlineDivider()
            SettingsNavRow(
                icon = JuiceboxIcons.smartphone,
                title = "This session",
                value = if (state.hasSession) "Active" else "None",
                onClick = onOpenSession,
            )
            HairlineDivider()
            SettingsActionRow(
                icon = JuiceboxIcons.globe,
                title = "Open site",
                onClick = onOpenSite,
            )
            HairlineDivider()
            SettingsActionRow(
                icon = JuiceboxIcons.smartphone,
                title = "Forget device",
                destructive = true,
                onClick = { confirmForget = true },
            )
        }
    }

    if (confirmForget) {
        ConfirmDialog(
            icon = JuiceboxIcons.smartphone,
            title = "Forget this device?",
            text = "A new anonymous session starts on this phone. Existing links stay online.",
            confirmLabel = "Forget",
            destructive = true,
            onConfirm = {
                confirmForget = false
                onForget()
            },
            onDismiss = { confirmForget = false },
        )
    }
}

@Composable
private fun SessionPage(
    state: SettingsUiState,
    modifier: Modifier,
    onForget: () -> Unit,
) {
    var confirmForget by remember { mutableStateOf(false) }
    val active = state.hasSession

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            juiceboxIcon(JuiceboxIcons.smartphone),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (active) "Active session" else "No session yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (active) {
                            "Anonymous identity saved on this phone"
                        } else {
                            "Connect to start one"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HairlineDivider()
            SettingsInfoRow(
                "Server",
                state.savedServer.removePrefix("https://").removePrefix("http://").trimEnd('/'),
            )
            HairlineDivider()
            SettingsInfoRow("Library", "${state.libraryCount} files")
            HairlineDivider()
            SettingsInfoRow("App", "v${BuildConfig.VERSION_NAME}")
        }

        Text(
            "This session is an anonymous cookie stored on this device. " +
                "It keeps your library across restarts; forgetting it starts a fresh identity " +
                "and leaves existing links online.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        SettingsGroup {
            SettingsActionRow(
                icon = JuiceboxIcons.smartphone,
                title = "Forget device",
                destructive = true,
                onClick = { confirmForget = true },
            )
        }
    }

    if (confirmForget) {
        ConfirmDialog(
            icon = JuiceboxIcons.smartphone,
            title = "Forget this device?",
            text = "A new anonymous session starts on this phone. Existing links stay online.",
            confirmLabel = "Forget",
            destructive = true,
            onConfirm = {
                confirmForget = false
                onForget()
            },
            onDismiss = { confirmForget = false },
        )
    }
}

@Composable
private fun ChoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalJuiceHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PasswordDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                juiceboxIcon(JuiceboxIcons.key),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text("Upload password") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = if (reveal) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            juiceboxIcon(if (reveal) JuiceboxIcons.eyeOff else JuiceboxIcons.eye),
                            contentDescription = if (reveal) "Hide" else "Show",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ShareModeDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                juiceboxIcon(JuiceboxIcons.link),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text("Shared links") },
        text = {
            Column {
                ChoiceOption("Fetch video", selected == ShareLinkMode.FETCH) {
                    onSelect(ShareLinkMode.FETCH)
                    onDismiss()
                }
                ChoiceOption("Upload as text", selected == ShareLinkMode.TEXT) {
                    onSelect(ShareLinkMode.TEXT)
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun Banner(
    text: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text.isNullOrBlank()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                juiceboxIcon(if (isError) JuiceboxIcons.warning else JuiceboxIcons.info),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("Dismiss")
            }
        }
    }
}

private fun storageLabel(state: SettingsUiState): String {
    val url = state.settings.storageHost ?: return "Auto"
    return (StorageHosts.official + state.communityHosts).firstOrNull { it.url == url }?.name
        ?: url.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

private fun shareModeLabel(mode: String): String = if (mode == ShareLinkMode.TEXT) {
    "Upload as text"
} else {
    "Fetch video"
}
