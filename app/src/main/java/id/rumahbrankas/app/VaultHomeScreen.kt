package id.rumahbrankas.app

import android.graphics.BitmapFactory
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun VaultHome(
    repo: VaultRepository,
    entries: List<VaultEntry>,
    message: String,
    importProgress: ImportProgress?,
    onLock: () -> Unit,
    onAddMedia: () -> Unit,
    onRestore: (VaultEntry) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDelete: (VaultEntry) -> Unit,
    onEnableBiometric: () -> Unit,
    onPreview: (VaultEntry, (File?, String?) -> Unit) -> Unit,
    onLoadThumbnail: (VaultEntry, (ImageBitmap?) -> Unit) -> Unit,
    onDismissMessage: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showNote by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<VaultEntry?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewNote by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    var showSecurity by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<VaultCategory?>(null) }
    val filtered = entries.filter { entry ->
        val inCategory = when (category) {
            null -> true
            VaultCategory.Photo -> !entry.isNote && !entry.isVideo
            VaultCategory.Video -> entry.isVideo
            VaultCategory.Note -> entry.isNote
        }
        inCategory && entry.title.contains(query.trim(), ignoreCase = true)
    }
    LaunchedEffect(message) {
        if (message.isBlank()) return@LaunchedEffect
        delay(4_000)
        onDismissMessage(message)
    }
    val entryIds = entries.map(VaultEntry::id)
    var visibleEntryIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(entryIds) { visibleEntryIds = entryIds.toSet() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchBounds = remember { mutableStateOf<Rect?>(null) }
    val rootCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun openPreview(entry: VaultEntry) {
        previewFile?.delete()
        previewFile = null
        previewNote = null
        viewing = entry
        previewLoading = true
        onPreview(entry) { file, note ->
            previewLoading = false
            if (viewing?.id == entry.id) { previewFile = file; previewNote = note } else file?.delete()
        }
    }

    fun closePreview() {
        previewFile?.delete()
        viewing = null
        previewFile = null
        previewNote = null
        previewLoading = false
    }

    Column(
        Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues())
            .onGloballyPositioned { rootCoordinates.value = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() } ?: continue
                        val root = rootCoordinates.value
                        val bounds = searchBounds.value
                        val insideSearch = root != null && root.isAttached && bounds != null &&
                            bounds.contains(root.localToWindow(down.position))
                        if (!insideSearch) {
                            focusManager.clearFocus(force = true)
                            keyboard?.hide()
                        }
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            BrandMark()
            Row {
                IconButton(onClick = { showSecurity = true }) { Icon(Icons.Outlined.Fingerprint, stringResource(R.string.action_security_settings), tint = Gold) }
                IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, stringResource(R.string.action_lock_vault), tint = Paper) }
                SettingsGear()
            }
        }
        Column(Modifier.weight(1f)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(vaultDate("EEEE, d MMMM", System.currentTimeMillis()).uppercase(), color = Gold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(13.dp))
                Text(stringResource(R.string.home_headline), color = Paper, fontSize = 32.sp, lineHeight = 38.sp, fontFamily = FontFamily.Serif)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatCard(stringResource(R.string.stat_photos), entries.count { !it.isNote && !it.isVideo }, Icons.Outlined.Image, category == VaultCategory.Photo, Modifier.weight(1f)) {
                        category = if (category == VaultCategory.Photo) null else VaultCategory.Photo
                    }
                    StatCard(stringResource(R.string.stat_videos), entries.count(VaultEntry::isVideo), Icons.Outlined.Videocam, category == VaultCategory.Video, Modifier.weight(1f)) {
                        category = if (category == VaultCategory.Video) null else VaultCategory.Video
                    }
                    StatCard(stringResource(R.string.stat_notes), entries.count(VaultEntry::isNote), Icons.Outlined.NoteAlt, category == VaultCategory.Note, Modifier.weight(1f)) {
                        category = if (category == VaultCategory.Note) null else VaultCategory.Note
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { searchBounds.value = it.boundsInWindow() },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_vault), color = Muted) },
                    leadingIcon = { Icon(Icons.Outlined.Search, stringResource(R.string.search_items), tint = Muted) },
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, cursorColor = Gold, focusedTextColor = Paper, unfocusedTextColor = Paper),
                )
                importProgress?.let { ImportProgressCard(it) }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.collection), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.item_count, filtered.size), color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        EmptyState(entries.isEmpty() && query.isBlank() && category == null)
                    }
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(2), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = VaultEntry::id) { entry ->
                        androidx.compose.animation.AnimatedVisibility(
                            visible = entry.id in visibleEntryIds,
                            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .96f),
                        ) {
                            EntryCard(entry, onLoadThumbnail) { openPreview(entry) }
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallFloatingActionButton(
                        onClick = { showNote = true }, containerColor = PanelRaised, contentColor = Gold,
                        modifier = Modifier.size(52.dp),
                    ) { Icon(Icons.AutoMirrored.Outlined.NoteAdd, stringResource(R.string.action_new_note)) }
                    ExtendedFloatingActionButton(
                        onClick = onAddMedia, containerColor = Gold, contentColor = Ink,
                        icon = { Icon(Icons.Outlined.FolderOpen, stringResource(R.string.action_import_media)) },
                        text = { Text(stringResource(R.string.action_import_media), fontWeight = FontWeight.SemiBold) },
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = message.isNotBlank(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
                ) {
                    Surface(color = PanelRaised, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line)) {
                        Text(message, color = Gold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                    }
                }
            }
        }
    }

    if (showNote) AlertDialog(
        onDismissRequest = { showNote = false }, containerColor = Panel,
        title = { Text(stringResource(R.string.note_title), color = Paper, fontFamily = FontFamily.Serif, fontSize = 24.sp) },
        text = { Column {
            VaultField(stringResource(R.string.field_note_title), title, { title = it }, number = false)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text(stringResource(R.string.note_body_hint)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 145.dp), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, focusedTextColor = Paper, unfocusedTextColor = Paper, cursorColor = Gold))
        } },
        confirmButton = { TextButton(onClick = { onAddNote(title, body); title = ""; body = ""; showNote = false }) { Text(stringResource(R.string.action_save_note), color = Gold) } },
        dismissButton = { TextButton(onClick = { showNote = false }) { Text(stringResource(R.string.action_cancel), color = Muted) } },
    )
    if (showSecurity) AlertDialog(
        onDismissRequest = { showSecurity = false }, containerColor = Panel,
        title = { Text(stringResource(R.string.security_title), color = Paper, fontFamily = FontFamily.Serif, fontSize = 24.sp) },
        text = { Column {
            SecurityHint(stringResource(R.string.security_local_encryption))
            Spacer(Modifier.height(15.dp))
            Text(stringResource(if (repo.hasBiometric()) R.string.security_biometric_enabled else R.string.security_pin_recovery), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        } },
        confirmButton = { if (!repo.hasBiometric()) TextButton(onClick = { showSecurity = false; onEnableBiometric() }) { Text(stringResource(R.string.action_enable_biometric), color = Gold) } else TextButton(onClick = { showSecurity = false }) { Text(stringResource(R.string.action_done), color = Gold) } },
        dismissButton = { if (!repo.hasBiometric()) TextButton(onClick = { showSecurity = false }) { Text(stringResource(R.string.action_close), color = Muted) } },
    )
    deleting?.let { entry -> AlertDialog(
        onDismissRequest = { deleting = null }, containerColor = Panel,
        title = { Text(stringResource(R.string.delete_title), color = Paper) }, text = { Text(stringResource(R.string.delete_confirmation, entry.title), color = Muted) },
        confirmButton = { TextButton(onClick = { onDelete(entry); deleting = null }) { Text(stringResource(R.string.action_delete), color = Color(0xFFE8A6A0)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel), color = Muted) } },
    ) }
    viewing?.let { entry ->
        val index = filtered.indexOfFirst { it.id == entry.id }
        PreviewDialog(
            entry, previewFile, previewNote, previewLoading, index + 1, filtered.size,
            onNavigate = { delta ->
                val nextIndex = index + delta
                if (nextIndex in filtered.indices) openPreview(filtered[nextIndex])
            },
            onDismiss = ::closePreview,
            onRestore = { onRestore(entry); closePreview() },
            onDelete = { closePreview(); deleting = entry },
        )
    }
}

@Composable
private fun PreviewDialog(
    entry: VaultEntry,
    file: File?,
    noteBody: String?,
    loading: Boolean,
    position: Int,
    itemCount: Int,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink).padding(WindowInsets.statusBars.asPaddingValues())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), tint = Paper) }
                Column(Modifier.weight(1f)) {
                    Text(entry.title, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (itemCount > 1) Text(stringResource(R.string.preview_position, position, itemCount), color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_delete), tint = Color(0xFFD7A09A)) }
            }
            EncryptedBadge(Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 8.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().pointerInput(entry.id, itemCount) {
                    var dragDistance = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount -> dragDistance += amount; change.consume() },
                        onDragEnd = {
                            if (dragDistance < -72f) onNavigate(1)
                            else if (dragDistance > 72f) onNavigate(-1)
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                    )
                },
            ) {
                when {
                    entry.isNote -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(25.dp)) {
                        Text(stringResource(R.string.private_note_label), color = Gold, letterSpacing = 2.sp, fontSize = 10.sp)
                        Spacer(Modifier.height(20.dp)); Text(entry.title, color = Paper, fontSize = 31.sp, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(20.dp)); HorizontalDivider(color = Line); Spacer(Modifier.height(20.dp))
                        Text(noteBody.orEmpty(), color = Paper.copy(alpha = .9f), fontSize = 16.sp, lineHeight = 26.sp)
                    }
                    file == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(if (loading) R.string.status_opening_securely else R.string.preview_unavailable), color = Muted) }
                    entry.isVideo -> key(entry.id, file.absolutePath) {
                        AndroidView(
                            factory = { context -> VideoView(context).apply { setVideoPath(file.absolutePath); setOnPreparedListener { it.isLooping = true; start() } } },
                            modifier = Modifier.fillMaxSize().padding(16.dp).clip(RoundedCornerShape(18.dp)),
                            onRelease = VideoView::stopPlayback,
                        )
                    }
                    else -> {
                        var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(file) {
                            bitmap = try {
                                withContext(Dispatchers.IO) { decodePreviewImage(file) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                        }
                        val displayBitmap = bitmap
                        if (displayBitmap != null) androidx.compose.foundation.Image(displayBitmap, stringResource(R.string.preview_image_desc, entry.title), Modifier.fillMaxSize().padding(14.dp), contentScale = ContentScale.Fit)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.preview_unsupported), color = Muted) }
                    }
                }
                if (itemCount > 1) {
                    if (position > 1) IconButton(
                        onClick = { onNavigate(-1) }, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)
                            .clip(CircleShape).background(Panel.copy(alpha = .86f)),
                    ) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.preview_previous), tint = Paper) }
                    if (position < itemCount) IconButton(
                        onClick = { onNavigate(1) }, modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)
                            .clip(CircleShape).background(Panel.copy(alpha = .86f)),
                    ) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.preview_next), tint = Paper) }
                }
            }
            Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 20.dp, vertical = 12.dp).padding(WindowInsets.navigationBars.asPaddingValues()), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Icon(Icons.Outlined.FileDownload, stringResource(R.string.action_restore_device)); Spacer(Modifier.width(9.dp)); Text(stringResource(R.string.action_restore_device), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(7.dp)); Text(stringResource(R.string.restore_description), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                if (itemCount > 1) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.preview_swipe_hint), color = Muted, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun EncryptedBadge(modifier: Modifier = Modifier) {
    Surface(modifier, color = PanelRaised, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = Gold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.preview_encrypted_badge), color = Paper, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun EntryCard(entry: VaultEntry, onLoadThumbnail: (VaultEntry, (ImageBitmap?) -> Unit) -> Unit, onClick: () -> Unit) {
    var thumbnail by remember(entry.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.id) {
        if (!entry.isNote) onLoadThumbnail(entry) { thumbnail = it }
    }
    Card(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = entry.title },
        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Line.copy(alpha = .7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 7.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.18f).clip(RoundedCornerShape(13.dp)).background(PanelRaised), contentAlignment = Alignment.Center) {
                if (thumbnail != null) {
                    androidx.compose.foundation.Image(
                        thumbnail!!, stringResource(R.string.preview_image_desc, entry.title), Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                    if (entry.isVideo) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Ink.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PlayArrow, stringResource(R.string.preview_video_desc), tint = Paper, modifier = Modifier.size(28.dp))
                        }
                    }
                } else {
                    Icon(when { entry.isNote -> Icons.Outlined.NoteAlt; entry.isVideo -> Icons.Outlined.Movie; else -> Icons.Outlined.Image }, null, tint = Gold, modifier = Modifier.size(32.dp))
                    if (entry.isVideo) Icon(Icons.Outlined.PlayArrow, stringResource(R.string.preview_video_desc), tint = Paper, modifier = Modifier.size(42.dp))
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(7.dp), color = Ink.copy(alpha = .72f), shape = CircleShape) {
                    Icon(Icons.Outlined.Lock, stringResource(R.string.preview_encrypted_badge), tint = Gold, modifier = Modifier.padding(6.dp).size(13.dp))
                }
            }
            Spacer(Modifier.height(11.dp)); Text(entry.title, color = Paper, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(if (entry.isNote) R.string.file_note else if (entry.isVideo) R.string.file_video else R.string.file_photo), color = Gold, fontSize = 10.sp)
                Text(vaultDate("d MMM", entry.createdAt), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ImportProgressCard(progress: ImportProgress) {
    val fraction = if (progress.total > 0) progress.completed.toFloat() / progress.total else 0f
    Card(
        Modifier.fillMaxWidth().padding(top = 15.dp), colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line.copy(alpha = .8f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.import_progress_title), color = Paper, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                Text(stringResource(R.string.import_progress_count, progress.current.coerceAtMost(progress.total), progress.total), color = Gold, fontSize = 11.sp)
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = Gold, trackColor = PanelRaised,
            )
        }
    }
}

private fun decodePreviewImage(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )?.asImageBitmap()
}

@Composable
internal fun LockTransitionScreen(complete: Boolean, previewCacheCleared: Boolean) {
    Column(
        Modifier.fillMaxSize().background(Ink).padding(WindowInsets.statusBars.asPaddingValues())
            .padding(WindowInsets.navigationBars.asPaddingValues()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(116.dp).clip(CircleShape).background(Panel).border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(targetState = complete, label = "vault-lock-transition") { closed ->
                Icon(
                    if (closed) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(58.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.lock_transition_title), color = Paper, fontSize = 25.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (complete) R.string.lock_transition_complete else R.string.status_locking),
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (complete) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(PanelRaised).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (previewCacheCleared) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (previewCacheCleared) Gold else Color(0xFFE8A6A0),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (previewCacheCleared) R.string.lock_cache_cleared else R.string.status_lock_cache_warning),
                    color = if (previewCacheCleared) Paper else Color(0xFFE8A6A0), fontSize = 11.sp,
                )
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Gold, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun EmptyState(isEmptyVault: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 34.dp).padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Folder, null, tint = Gold, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(15.dp)); Text(stringResource(if (isEmptyVault) R.string.empty_title else R.string.no_match_title), color = Paper, fontFamily = FontFamily.Serif, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp)); Text(stringResource(if (isEmptyVault) R.string.empty_body else R.string.no_match_body), color = Muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun vaultDate(pattern: String, timeMillis: Long): String {
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
    return SimpleDateFormat(pattern, locale).format(Date(timeMillis))
}

private enum class VaultCategory { Photo, Video, Note }

@Composable
private fun StatCard(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 0.96f else 1f, tween(160), label = "category-press")
    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = if (selected) 2.dp.toPx() else 0f
        }.semantics { this.selected = selected },
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF090A08) else Panel),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = .72f) else Line.copy(alpha = .65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 3.dp, pressedElevation = 0.dp),
    ) {
        Box {
            if (selected) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent)),
                    ),
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (selected) 12.dp else 13.dp)) {
                Icon(icon, null, tint = Gold, modifier = Modifier.size(17.dp)); Spacer(Modifier.height(10.dp))
                Text(count.toString(), color = Paper, fontSize = 21.sp, fontFamily = FontFamily.Serif)
                Text(label, color = if (selected) Gold else Muted, fontSize = 10.sp, letterSpacing = .6.sp, maxLines = 2)
            }
        }
    }
}
