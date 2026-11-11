package dev.juicey.juicebox.ui.files

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.juicey.juicebox.core.model.LibraryFile
import dev.juicey.juicebox.core.model.LibraryStatus
import dev.juicey.juicebox.core.model.LinkVariant
import dev.juicey.juicebox.core.nowEpochSeconds
import dev.juicey.juicebox.R
import dev.juicey.juicebox.ui.components.ConfirmDialog
import dev.juicey.juicebox.ui.components.EmptyState
import dev.juicey.juicebox.ui.components.HairlineDivider
import dev.juicey.juicebox.ui.components.LinkCopyRow
import dev.juicey.juicebox.ui.components.LinkVariantSelector
import dev.juicey.juicebox.ui.components.LocalJuiceHaptics
import dev.juicey.juicebox.ui.components.ShimmerBox
import dev.juicey.juicebox.ui.formatBytes
import dev.juicey.juicebox.ui.formatDate
import dev.juicey.juicebox.ui.formatRemaining
import dev.juicey.juicebox.ui.icons.JuiceboxIcons
import dev.juicey.juicebox.ui.icons.juiceboxIcon
import dev.juicey.juicebox.ui.mimeBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var detailId by remember { mutableStateOf<String?>(null) }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    val detail = state.files.firstOrNull { it.localId == detailId }
    val renaming = state.files.firstOrNull { it.localId == renamingId }
    val deleting = state.files.firstOrNull { it.localId == deletingId }

    Column(Modifier.fillMaxSize()) {
        FilesHeader(
            total = state.total,
            totalBytes = state.totalBytes,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            singleLine = true,
            placeholder = { Text("Search") },
            leadingIcon = {
                Icon(
                    juiceboxIcon(JuiceboxIcons.search),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(
                            juiceboxIcon(JuiceboxIcons.close),
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            shape = MaterialTheme.shapes.large,
        )

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.dismissError() }
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
            state = pullState,
            indicator = {
                PullToRefreshDefaults.IndicatorBox(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.refresh),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                if (!state.refreshing) {
                                    rotationZ = pullState.distanceFraction * 180f
                                }
                            },
                    )
                }
            },
        ) {
            if (state.files.isEmpty()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = juiceboxIcon(JuiceboxIcons.files),
                                title = if (state.query.isBlank()) "No files yet"
                                else "Nothing matches",
                                body = if (state.query.isBlank()) "Uploads land here."
                                else "Try a different name.",
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                ) {
                    items(state.files, key = { it.localId }) { file ->
                        Column(Modifier.animateItem()) {
                            FileRow(
                                file = file,
                                onClick = { detailId = file.localId },
                                onCopy = viewModel::copy,
                                onRename = { renamingId = file.localId },
                                onDelete = { deletingId = file.localId },
                            )
                            HairlineDivider(Modifier.padding(start = 82.dp, end = 20.dp))
                        }
                    }
                }
            }
        }
    }

    if (detail != null) {
        FileDetailSheet(
            file = detail,
            variant = state.linkVariant,
            onDismiss = { detailId = null },
            onCopy = viewModel::copy,
            onRename = { renamingId = detail.localId },
            onDelete = { deletingId = detail.localId },
        )
    }

    if (renaming != null) {
        RenameDialog(
            file = renaming,
            onDismiss = { renamingId = null },
            onConfirm = { name ->
                viewModel.rename(renaming, name)
                renamingId = null
            },
        )
    }

    if (deleting != null) {
        ConfirmDialog(
            icon = JuiceboxIcons.trash,
            title = "Delete file?",
            text = "\"${deleting.filename}\" will be removed from the server. Anyone with the link loses access.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.delete(deleting)
                deletingId = null
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
private fun FilesHeader(total: Int, totalBytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Files", style = MaterialTheme.typography.displaySmall, letterSpacing = (-0.8).sp)
            Text(
                if (total == 0) "Nothing here yet" else "$total files - ${formatBytes(totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileRow(
    file: LibraryFile,
    onClick: () -> Unit,
    onCopy: (String) -> Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var copied by remember(file.localId) { mutableStateOf(false) }
    val haptics = LocalJuiceHaptics.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(file, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.filename,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (file.protected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        juiceboxIcon(JuiceboxIcons.lock),
                        contentDescription = "Password protected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(formatBytes(file.sizeBytes))
                    append(" - ")
                    append(mimeBadge(file.mimeType, file.filename))
                    if (file.status == LibraryStatus.READY) {
                        val left = formatRemaining(file.expiresAt)
                        if (left.isNotEmpty()) {
                            append(" - ")
                            append(left)
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (file.isPending) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Uploading",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else if (file.isFailed) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "Failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (file.expiresAt > 0) {
                Spacer(Modifier.height(8.dp))
                RetentionBar(file)
            }
        }
        if (!file.isPending && !file.isFailed) {
            Box {
                IconButton(onClick = {
                    haptics.tick()
                    menu = true
                }) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.moreHorizontal),
                        contentDescription = "More",
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text(if (copied) "Copied" else "Copy preview link") },
                        leadingIcon = {
                            Icon(
                                juiceboxIcon(JuiceboxIcons.link),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            val ok = onCopy(file.linkFor(LinkVariant.PREVIEW))
                            if (ok) haptics.confirm() else haptics.reject()
                            copied = ok
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy raw link") },
                        leadingIcon = {
                            Icon(
                                juiceboxIcon(JuiceboxIcons.file),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            val ok = onCopy(file.linkFor(LinkVariant.RAW))
                            if (ok) haptics.confirm() else haptics.reject()
                            copied = ok
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy download link") },
                        leadingIcon = {
                            Icon(
                                juiceboxIcon(JuiceboxIcons.download),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            val ok = onCopy(file.linkFor(LinkVariant.DOWNLOAD))
                            if (ok) haptics.confirm() else haptics.reject()
                            copied = ok
                            menu = false
                        },
                    )
                    if (file.canManage) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = {
                                Icon(
                                    juiceboxIcon(JuiceboxIcons.edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                haptics.tick()
                                menu = false
                                onRename()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                juiceboxIcon(JuiceboxIcons.trash),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            haptics.tick()
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RetentionBar(file: LibraryFile, height: Dp = 3.dp) {
    val now = remember(file.localId, file.expiresAt) { nowEpochSeconds() }
    val span = (file.expiresAt - file.uploadedAt).coerceAtLeast(1)
    val left = (file.expiresAt - now).coerceAtLeast(0)
    val fraction = (left.toFloat() / span).coerceIn(0f, 1f)
    val color = when {
        fraction > 0.5f -> MaterialTheme.colorScheme.tertiary
        fraction > 0.2f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(height).clip(CircleShape),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
    )
}

@Composable
private fun Thumbnail(file: LibraryFile, size: Dp) {
    val isImage = file.mimeType.startsWith("image/")
    val isVideo = file.mimeType.startsWith("video/")
    val hasPreview = (isImage || isVideo) && file.rawUrl.isNotBlank()
    Box(
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPreview) {
            SubcomposeAsyncImage(
                model = file.rawUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ShimmerBox(Modifier.fillMaxSize()) },
                error = { FileTypeIcon(file, 21.dp) },
            )
        } else {
            FileTypeIcon(file, 21.dp)
        }
    }
}

@Composable
private fun FileTypeIcon(file: LibraryFile, size: Dp) {
    Icon(
        juiceboxIcon(
            when {
                file.mimeType.startsWith("audio/") -> JuiceboxIcons.audioWaveform
                file.mimeType == "application/pdf" -> JuiceboxIcons.fileText
                file.mimeType.startsWith("text/") -> JuiceboxIcons.fileText
                file.mimeType.contains("zip") -> JuiceboxIcons.archive
                else -> JuiceboxIcons.file
            },
        ),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun PreviewFallback() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            juiceboxIcon(JuiceboxIcons.image),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(30.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDetailSheet(
    file: LibraryFile,
    variant: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember(file.localId, variant) { mutableStateOf(variant) }
    var showQr by remember(file.localId) { mutableStateOf(false) }

    val isImage = file.mimeType.startsWith("image/")
    val isVideo = file.mimeType.startsWith("video/")
    val link = file.linkFor(selected)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.filename,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (link.isNotBlank()) {
                    IconButton(onClick = { showQr = true }) {
                        Icon(
                            juiceboxIcon(JuiceboxIcons.qrCode),
                            contentDescription = "QR code",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        juiceboxIcon(JuiceboxIcons.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (isImage && file.rawUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SubcomposeAsyncImage(
                    model = file.rawUrl,
                    contentDescription = file.filename,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    loading = { ShimmerBox(Modifier.fillMaxSize()) },
                    error = { PreviewFallback() },
                )
            } else if (isVideo && file.rawUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { uriHandler.openUri(file.url) },
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = file.rawUrl,
                        contentDescription = file.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ShimmerBox(Modifier.fillMaxSize()) },
                        error = { PreviewFallback() },
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(58.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                juiceboxIcon(JuiceboxIcons.play),
                                contentDescription = "Play in browser",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                buildString {
                    append(formatBytes(file.sizeBytes))
                    append(" - ")
                    append(mimeBadge(file.mimeType, file.filename))
                    if (file.protected) append(" - protected")
                    if (file.isEncrypted) append(" - encrypted")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            DetailLine("Uploaded", formatDate(file.uploadedAt))
            DetailLine("Expires", formatDate(file.expiresAt))
            Spacer(Modifier.height(8.dp))
            RetentionBar(file, height = 6.dp)
            Spacer(Modifier.height(5.dp))
            Text(
                formatRemaining(file.expiresAt).ifBlank { "No expiry" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            file.storageHost?.takeIf { it.isNotBlank() }?.let { DetailLine("Storage", it) }

            Spacer(Modifier.height(18.dp))
            LinkVariantSelector(selected = selected, onSelect = { selected = it })
            Spacer(Modifier.height(10.dp))
            LinkCopyRow(link = link, onCopy = onCopy)

            Spacer(Modifier.height(18.dp))
            HairlineDivider()
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (file.canManage) {
                    TextButton(onClick = onRename) {
                        Icon(
                            juiceboxIcon(JuiceboxIcons.edit),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Rename")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(
                            juiceboxIcon(JuiceboxIcons.trash),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else if (file.hasServerId) {
                    Text(
                        "No delete token on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }

    if (showQr) {
        QrDialog(link = link, onDismiss = { showQr = false })
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun QrDialog(link: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                juiceboxIcon(JuiceboxIcons.qrCode),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text("Scan link") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    QrCode(link, 220.dp)
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(54.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    shortLinkText(link),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
fun QrCode(content: String, size: Dp) {
    val bitmap = remember(content) {
        runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 720, 720, hints)
            val pixels = IntArray(720 * 720)
            for (y in 0 until 720) {
                for (x in 0 until 720) {
                    pixels[y * 720 + x] = if (matrix.get(x, y)) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                }
            }
            Bitmap.createBitmap(pixels, 720, 720, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code for the link",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

/** Human-glanceable form: no scheme, no host prefix noise, no file extension. */
private fun shortLinkText(link: String): String {
    val stripped = link.substringAfter("://", link).removePrefix("www.")
    val slash = stripped.indexOf('/')
    if (slash < 0) return stripped.trimEnd('/')
    val host = stripped.substring(0, slash)
    var path = stripped.substring(slash).substringBefore('#').substringBefore('?')
    val dot = path.lastIndexOf('.')
    if (dot > path.lastIndexOf('/')) path = path.substring(0, dot)
    return (host + path).trimEnd('/')
}

@Composable
private fun RenameDialog(file: LibraryFile, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(file.filename.substringBeforeLast('.', file.filename)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                juiceboxIcon(JuiceboxIcons.edit),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        },
        title = { Text("Rename file") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(64) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("New name") },
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
