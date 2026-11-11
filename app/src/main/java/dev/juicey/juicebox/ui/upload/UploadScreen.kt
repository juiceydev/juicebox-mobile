package dev.juicey.juicebox.ui.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.juicey.juicebox.R
import dev.juicey.juicebox.core.model.LinkVariant
import dev.juicey.juicebox.core.model.SiteAnnouncement
import dev.juicey.juicebox.ui.components.ConfirmDialog
import dev.juicey.juicebox.ui.components.ErrorCard
import dev.juicey.juicebox.ui.components.HairlineDivider
import dev.juicey.juicebox.ui.components.LinkCopyRow
import dev.juicey.juicebox.ui.components.LinkVariantSelector
import dev.juicey.juicebox.ui.components.LocalJuiceHaptics
import dev.juicey.juicebox.ui.components.RetentionSlider
import dev.juicey.juicebox.ui.components.SettingPicker
import dev.juicey.juicebox.ui.components.SettingsGroup
import dev.juicey.juicebox.ui.components.SettingsToggleRow
import dev.juicey.juicebox.ui.components.TitleWordmark
import dev.juicey.juicebox.ui.formatBytes
import dev.juicey.juicebox.ui.icons.JuiceboxIcons
import dev.juicey.juicebox.ui.icons.juiceboxIcon
import dev.juicey.juicebox.upload.TaskStatus
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UploadScreen(
    viewModel: UploadViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var cameraTarget by remember { mutableStateOf<Uri?>(null) }
    var sessionOpen by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SessionItem?>(null) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addFiles(uris) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> viewModel.addFiles(uris) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraTarget
        if (success && uri != null) viewModel.addFiles(listOf(uri))
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraTarget = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(state.canVideo) {
        if (!state.canVideo && state.mode == 1) viewModel.setMode(0)
    }

    val modeKeys = remember(state.canVideo) {
        buildList {
            add(0)
            if (state.canVideo) add(1)
            add(2)
        }
    }
    val modeLabels = modeKeys.map { key ->
        when (key) {
            0 -> "Files"
            1 -> "Media"
            else -> "Text"
        }
    }
    val selectedIndex = modeKeys.indexOf(state.mode).coerceAtLeast(0)
    val pagerState = rememberPagerState(pageCount = { modeKeys.size })

    // Tabs and pages are two views of the same mode. Scroll the pager on a tab
    // tap and mirror the settled page back into the view model on a swipe.
    LaunchedEffect(pagerState, modeKeys) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            modeKeys.getOrNull(page)?.let { key ->
                if (key != state.mode) viewModel.setMode(key)
            }
        }
    }
    LaunchedEffect(modeKeys, selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Masthead(
                    announcement = state.announcement,
                    onOpenAnnouncement = { link ->
                        val resolved = when {
                            link.startsWith("http://") || link.startsWith("https://") -> link
                            link.startsWith("/") -> state.serverUrl.trimEnd('/') + link
                            else -> "https://$link"
                        }
                        runCatching { uriHandler.openUri(resolved) }
                    },
                )
                if (!state.connection.connected && !state.connection.connecting) {
                    Spacer(Modifier.height(12.dp))
                    OfflineNotice(state, viewModel::refreshConnection)
                }
            }

            Spacer(Modifier.height(10.dp))
            ModeTabs(
                labels = modeLabels,
                selected = selectedIndex,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 20.dp, end = 20.dp),
            ) {
                RetentionSlider(
                    values = state.ttls,
                    selected = state.ttlHours,
                    onSelect = viewModel::setTtl,
                    modifier = Modifier.padding(top = 10.dp),
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    key = { modeKeys[it] },
                ) { page ->
                    StagePage {
                        when (modeKeys[page]) {
                            0 -> FileStage(
                                state = state,
                                onPickFiles = { documentPicker.launch(arrayOf("*/*")) },
                                onPickPhotos = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                                    )
                                },
                                onCamera = ::launchCamera,
                                onRemove = viewModel::removeFile,
                            )
                            1 -> MediaStage(state, viewModel)
                            else -> TextStage(state.text, viewModel::setText)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn(tween(180)) + expandVertically(
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                    ),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(140)),
                ) {
                    state.error?.let { message ->
                        Column {
                            ErrorCard(message, viewModel::dismissError)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.items.isNotEmpty(),
                    enter = fadeIn(tween(190)) + expandVertically(
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
                        expandFrom = Alignment.Top,
                    ),
                    exit = fadeOut(tween(120)) + shrinkVertically(
                        animationSpec = tween(150),
                        shrinkTowards = Alignment.Top,
                    ),
                ) {
                    if (state.items.isNotEmpty()) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            SessionPeek(items = state.items, onClick = { sessionOpen = true })
                        }
                    }
                }
                Spacer(Modifier.height(84.dp))
            }
        }

        UploadFab(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp),
        )
    }

    if (sessionOpen && state.items.isNotEmpty()) {
        SessionSheet(
            items = state.items,
            variant = state.linkVariant,
            onDismiss = { sessionOpen = false },
            onCopy = viewModel::copyLink,
            onRetry = viewModel::retry,
            onDelete = { deleteTarget = it },
        )
    }

    deleteTarget?.let { item ->
        ConfirmDialog(
            icon = JuiceboxIcons.trash,
            title = "Delete upload?",
            text = "\"${item.name}\" will be removed from the server. Anyone with the link loses access.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteSession(item)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun StagePage(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Masthead(
    announcement: SiteAnnouncement?,
    onOpenAnnouncement: (String) -> Unit,
) {
    // The wordmark already fills the width on a phone, so the ticker gets its
    // own line instead of being squeezed off the edge.
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(12.dp))
            TitleWordmark(style = MaterialTheme.typography.headlineMedium)
        }
        if (announcement != null && announcement.hasMessage) {
            Spacer(Modifier.height(10.dp))
            AnnouncementTicker(
                announcement = announcement,
                onOpen = {
                    announcement.linkUrl?.takeIf { it.isNotBlank() }?.let(onOpenAnnouncement)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnnouncementTicker(
    announcement: SiteAnnouncement,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (container, content) = when (announcement.mode?.lowercase()) {
        "danger" -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        "status" -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        "info" -> MaterialTheme.colorScheme.surfaceContainerHighest to
            MaterialTheme.colorScheme.onSurface
        "none" -> Color.Transparent to MaterialTheme.colorScheme.onSurface
        // A missing mode is treated as a warning, matching the site.
        else -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        onClick = onOpen,
        enabled = announcement.hasLink,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = announcement.message.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1600,
                        initialDelayMillis = 900,
                        velocity = 26.dp,
                    ),
            )
            if (announcement.hasLink) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    juiceboxIcon(JuiceboxIcons.externalLink),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptics = LocalJuiceHaptics.current
    PrimaryTabRow(
        selectedTabIndex = selected,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        divider = {},
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selected,
                onClick = {
                    haptics.tick()
                    onSelect(index)
                },
                text = {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
            )
        }
    }
}

@Composable
private fun FileStage(
    state: UploadUiState,
    onPickFiles: () -> Unit,
    onPickPhotos: () -> Unit,
    onCamera: () -> Unit,
    onRemove: (Uri) -> Unit,
) {
    val haptics = LocalJuiceHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropZone(
            empty = state.selected.isEmpty(),
            onClick = onPickFiles,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerButton("Files", JuiceboxIcons.files, onPickFiles, Modifier.weight(1f))
            PickerButton("Photos", JuiceboxIcons.image, onPickPhotos, Modifier.weight(1f))
            PickerButton("Camera", JuiceboxIcons.camera, onCamera, Modifier.weight(1f))
        }

        if (state.selected.isNotEmpty()) {
            SettingsGroup {
                state.selected.forEachIndexed { index, file ->
                    if (index > 0) HairlineDivider()
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            juiceboxIcon(JuiceboxIcons.file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatBytes(file.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            haptics.tick()
                            onRemove(file.uri)
                        }) {
                            Icon(
                                juiceboxIcon(JuiceboxIcons.close),
                                contentDescription = "Remove",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropZone(empty: Boolean, onClick: () -> Unit) {
    val haptics = LocalJuiceHaptics.current
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val stripe = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
    val shift by rememberInfiniteTransition(label = "dropzone-stripes").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "stripe-shift",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 228.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBehind {
                // Mirrors the site's -45deg repeating stripes: half a period of
                // colour, half transparent, drifting slowly toward the corner.
                val period = 15.dp.toPx()
                val band = period * 0.42f
                val span = (size.width + size.height) * 1.6f
                rotate(degrees = -45f, pivot = center) {
                    var x = -span + shift * period
                    while (x < span) {
                        drawRect(
                            color = stripe,
                            topLeft = Offset(x, -span),
                            size = Size(band, span * 2f),
                        )
                        x += period
                    }
                }
            }
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(12.dp.toPx(), 9.dp.toPx()),
                            0f,
                        ),
                        cap = StrokeCap.Round,
                    ),
                    cornerRadius = CornerRadius(32.dp.toPx()),
                )
            }
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                juiceboxIcon(JuiceboxIcons.upload),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (empty) "Drop files here" else "Add more",
                style = MaterialTheme.typography.titleLarge,
            )
            if (empty) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to browse",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PickerButton(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalJuiceHaptics.current
    FilledTonalButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.height(46.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(juiceboxIcon(icon), contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun MediaStage(state: UploadUiState, viewModel: UploadViewModel) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        val text = try {
                            clipboard.getClipEntry()
                                ?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                ?.trim()
                        } catch (_: Throwable) {
                            null
                        }
                        if (!text.isNullOrEmpty()) viewModel.setVideoUrl(text)
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    juiceboxIcon(JuiceboxIcons.clipboard),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Paste")
            }
        }

        OutlinedTextField(
            value = state.videoUrl,
            onValueChange = viewModel::setVideoUrl,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("Link") },
            placeholder = { Text("YouTube, TikTok, X, SoundCloud...") },
            shape = MaterialTheme.shapes.large,
        )

        SettingsGroup {
            SettingsToggleRow(
                title = "Audio only",
                checked = state.audioOnly,
                onCheckedChange = viewModel::setAudioOnly,
            )
            HairlineDivider()
            if (state.audioOnly) {
                SettingPicker(
                    label = "Better audio",
                    value = if (state.betterAudio) "enhanced" else "standard",
                    options = listOf("enhanced" to "Enhanced", "standard" to "Standard"),
                    onSelect = { viewModel.setBetterAudio(it == "enhanced") },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
                HairlineDivider()
                SettingPicker(
                    label = "Audio format",
                    value = state.audioFormat,
                    options = listOf(
                        "best" to "Best",
                        "mp3" to "MP3",
                        "opus" to "Opus",
                        "ogg" to "OGG",
                        "wav" to "WAV",
                        "flac" to "FLAC",
                    ),
                    onSelect = viewModel::setAudioFormat,
                )
            } else {
                SettingPicker(
                    label = "Quality",
                    value = state.quality,
                    options = listOf(
                        "max" to "Max",
                        "2160" to "2160p",
                        "1440" to "1440p",
                        "1080" to "1080p",
                        "720" to "720p",
                        "480" to "480p",
                        "360" to "360p",
                    ),
                    onSelect = viewModel::setQuality,
                )
                HairlineDivider()
                SettingPicker(
                    label = "Container",
                    value = state.container,
                    options = listOf(
                        "auto" to "Auto",
                        "mp4" to "MP4",
                        "webm" to "WebM",
                        "mkv" to "MKV",
                    ),
                    onSelect = viewModel::setContainer,
                )
            }
        }
    }
}

@Composable
private fun TextStage(value: String, onValueChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 230.dp),
            placeholder = {
                Text(
                    "Paste text or a link",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            shape = MaterialTheme.shapes.large,
        )
        if (value.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${value.length} characters",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun UploadFab(
    state: UploadUiState,
    viewModel: UploadViewModel,
    modifier: Modifier = Modifier,
) {
    val enabled = when (state.mode) {
        0 -> state.selected.isNotEmpty() && !state.uploading && !state.busy
        1 -> state.videoUrl.isNotBlank() && state.canVideo && !state.uploading && !state.busy
        else -> state.text.isNotBlank() && !state.uploading && !state.busy
    }
    val label = when (state.mode) {
        0 -> if (state.selected.size > 1) "Upload ${state.selected.size}" else "Upload"
        1 -> "Fetch"
        else -> "Save text"
    }
    val icon = when (state.mode) {
        0 -> if (state.lockUploads) JuiceboxIcons.lock else JuiceboxIcons.upload
        1 -> JuiceboxIcons.download
        else -> JuiceboxIcons.check
    }
    val haptics = LocalJuiceHaptics.current
    ExtendedFloatingActionButton(
        onClick = {
            if (enabled) {
                haptics.confirm()
                viewModel.upload()
            }
        },
        modifier = modifier,
        containerColor = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        if (state.busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                juiceboxIcon(icon),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OfflineNotice(state: UploadUiState, onRetry: () -> Unit) {
    val haptics = LocalJuiceHaptics.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                juiceboxIcon(JuiceboxIcons.warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Server unreachable", style = MaterialTheme.typography.titleSmall)
                Text(
                    state.connection.error ?: "Check the server in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = {
                haptics.tick()
                onRetry()
            }) { Text("Retry") }
        }
    }
}

@Composable
private fun SessionPeek(items: List<SessionItem>, onClick: () -> Unit) {
    val latest = items.first()
    val (detail, tint) = when (latest.status) {
        TaskStatus.DONE -> (latest.link ?: "Uploaded") to MaterialTheme.colorScheme.tertiary
        TaskStatus.FAILED -> (latest.error ?: "Failed") to MaterialTheme.colorScheme.error
        TaskStatus.RUNNING -> "${(latest.fraction * 100).toInt()}%" to MaterialTheme.colorScheme.primary
        else -> "Queued" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (latest.status) {
        TaskStatus.DONE -> JuiceboxIcons.check
        TaskStatus.FAILED -> JuiceboxIcons.warning
        else -> JuiceboxIcons.upload
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    juiceboxIcon(icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    latest.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (items.size > 1) {
                Text(
                    "${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                juiceboxIcon(JuiceboxIcons.chevronUp),
                contentDescription = "Open session",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSheet(
    items: List<SessionItem>,
    variant: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Boolean,
    onRetry: (String) -> Unit,
    onDelete: (SessionItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "This session",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    SessionSheetItem(
                        item = item,
                        defaultVariant = variant,
                        onCopy = onCopy,
                        onRetry = { onRetry(item.id) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

/** Mirrors the library's prefix swap when the server didn't send a variant. */
private fun SessionItem.linkFor(variant: String): String? {
    if (variant == LinkVariant.PREVIEW) return link?.takeIf { it.isNotBlank() } ?: rawLink
    val explicit = if (variant == LinkVariant.RAW) rawLink else downloadLink
    if (!explicit.isNullOrBlank()) return explicit
    val base = link?.takeIf { it.isNotBlank() } ?: rawLink ?: return null
    val marker = "/v/"
    val index = base.indexOf(marker)
    if (index < 0) return base
    val prefix = if (variant == LinkVariant.RAW) "f" else "d"
    return base.substring(0, index) + "/" + prefix + "/" + base.substring(index + marker.length)
}

@Composable
private fun SessionSheetItem(
    item: SessionItem,
    defaultVariant: String,
    onCopy: (String) -> Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.status == TaskStatus.DONE) {
                IconButton(onClick = onDelete) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.trash),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        when (item.status) {
            TaskStatus.RUNNING, TaskStatus.QUEUED -> {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        if (item.status == TaskStatus.QUEUED) {
                            append("Queued")
                        } else {
                            append((item.fraction * 100).toInt()).append('%')
                            if (item.speedBytes > 0) {
                                append("  ")
                                append(formatBytes(item.speedBytes))
                                append("/s")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TaskStatus.FAILED -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    item.error ?: "Something went wrong",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.refresh),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Try again")
                }
            }

            else -> {
                var selected by remember(item.id, defaultVariant) { mutableStateOf(defaultVariant) }
                val link = item.linkFor(selected)
                Spacer(Modifier.height(10.dp))
                LinkVariantSelector(selected = selected, onSelect = { selected = it })
                if (!link.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LinkCopyRow(link = link, onCopy = onCopy)
                }
            }
        }
    }
}
