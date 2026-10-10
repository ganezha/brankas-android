package id.rumahbrankas.app

import android.graphics.BitmapFactory
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun VaultHome(
    repo: VaultRepository,
    entries: List<VaultEntry>,
    message: String,
    onLock: () -> Unit,
    onAddMedia: () -> Unit,
    onRestore: (VaultEntry) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDelete: (VaultEntry) -> Unit,
    onEnableBiometric: () -> Unit,
    onPreview: (VaultEntry, (File?, String?) -> Unit) -> Unit,
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
    val filtered = entries.filter { it.title.contains(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            BrandMark()
            Row {
                IconButton(onClick = { showSecurity = true }) { Icon(Icons.Outlined.Fingerprint, stringResource(R.string.action_security_settings), tint = Gold) }
                IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, stringResource(R.string.action_lock_vault), tint = Paper) }
            }
        }
        Column(Modifier.weight(1f)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID")).format(Date()).uppercase(), color = Gold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(13.dp))
                Text(stringResource(R.string.home_headline), color = Paper, fontSize = 32.sp, lineHeight = 38.sp, fontFamily = FontFamily.Serif)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatCard(stringResource(R.string.stat_photos), entries.count { !it.isNote && !it.isVideo }, Icons.Outlined.Image, Modifier.weight(1f))
                    StatCard(stringResource(R.string.stat_videos), entries.count(VaultEntry::isVideo), Icons.Outlined.Videocam, Modifier.weight(1f))
                    StatCard(stringResource(R.string.stat_notes), entries.count(VaultEntry::isNote), Icons.Outlined.NoteAlt, Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_vault), color = Muted) },
                    leadingIcon = { Icon(Icons.Outlined.Search, stringResource(R.string.search_items), tint = Muted) },
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line, cursorColor = Gold, focusedTextColor = Paper, unfocusedTextColor = Paper),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.collection), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.item_count, entries.size), color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            if (filtered.isEmpty()) EmptyState(query.isBlank()) else LazyVerticalGrid(
                columns = GridCells.Fixed(2), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = VaultEntry::id) { entry ->
                    EntryCard(entry) {
                        previewFile?.delete(); previewFile = null; previewNote = null; viewing = entry; previewLoading = true
                        onPreview(entry) { file, note ->
                            previewLoading = false
                            if (viewing?.id == entry.id) { previewFile = file; previewNote = note } else file?.delete()
                        }
                    }
                }
            }
            AnimatedVisibility(message.isNotBlank()) {
                Text(message, color = Gold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp))
            }
        }
        Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 22.dp, vertical = 12.dp).padding(WindowInsets.navigationBars.asPaddingValues()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAddMedia, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Paper), border = BorderStroke(1.dp, Line)) {
                Icon(Icons.Outlined.Folder, null, tint = Gold); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_import_media))
            }
            Button(onClick = { showNote = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.action_new_note), fontWeight = FontWeight.SemiBold)
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
    viewing?.let { entry -> PreviewDialog(
        entry, previewFile, previewNote, previewLoading,
        onDismiss = { previewFile?.delete(); viewing = null; previewFile = null; previewNote = null; previewLoading = false },
        onRestore = { onRestore(entry); previewFile?.delete(); viewing = null; previewFile = null; previewNote = null; previewLoading = false },
        onDelete = { previewFile?.delete(); viewing = null; previewFile = null; previewNote = null; previewLoading = false; deleting = entry },
    ) }
}

@Composable
private fun PreviewDialog(entry: VaultEntry, file: File?, noteBody: String?, loading: Boolean, onDismiss: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink).padding(WindowInsets.statusBars.asPaddingValues())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), tint = Paper) }
                Text(entry.title, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, stringResource(R.string.action_delete), tint = Muted) }
            }
            when {
                entry.isNote -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(25.dp)) {
                    Text(stringResource(R.string.private_note_label), color = Gold, letterSpacing = 2.sp, fontSize = 10.sp)
                    Spacer(Modifier.height(20.dp)); Text(entry.title, color = Paper, fontSize = 31.sp, fontFamily = FontFamily.Serif)
                    Spacer(Modifier.height(20.dp)); HorizontalDivider(color = Line); Spacer(Modifier.height(20.dp))
                    Text(noteBody.orEmpty(), color = Paper.copy(alpha = .9f), fontSize = 16.sp, lineHeight = 26.sp)
                }
                file == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(if (loading) R.string.status_opening_securely else R.string.preview_unavailable), color = Muted) }
                entry.isVideo -> AndroidView(factory = { context -> VideoView(context).apply { setVideoPath(file.absolutePath); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(18.dp)))
                else -> {
                    val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                    if (bitmap != null) androidx.compose.foundation.Image(bitmap, stringResource(R.string.preview_image_desc, entry.title), Modifier.weight(1f).fillMaxWidth().padding(14.dp), contentScale = ContentScale.Fit)
                    else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.preview_unsupported), color = Muted) }
                }
            }
            Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 20.dp, vertical = 12.dp).padding(WindowInsets.navigationBars.asPaddingValues()), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                    Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(9.dp)); Text(stringResource(R.string.action_restore_device), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(7.dp)); Text(stringResource(R.string.restore_description), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EntryCard(entry: VaultEntry, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = entry.title },
        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Line.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(13.dp)).background(PanelRaised), contentAlignment = Alignment.Center) {
                Icon(when { entry.isNote -> Icons.Outlined.NoteAlt; entry.isVideo -> Icons.Outlined.Movie; else -> Icons.Outlined.Image }, null, tint = Gold, modifier = Modifier.size(32.dp))
                if (!entry.isNote) Icon(Icons.Outlined.Lock, null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(15.dp))
            }
            Spacer(Modifier.height(11.dp)); Text(entry.title, color = Paper, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(if (entry.isNote) R.string.file_note else if (entry.isVideo) R.string.file_video else R.string.file_photo), color = Gold, fontSize = 10.sp)
                Text(SimpleDateFormat("d MMM", Locale("id", "ID")).format(Date(entry.createdAt)), color = Muted, fontSize = 11.sp)
            }
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
private fun StatCard(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Line.copy(alpha = .65f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp)) {
            Icon(icon, null, tint = Gold, modifier = Modifier.size(17.dp)); Spacer(Modifier.height(10.dp))
            Text(count.toString(), color = Paper, fontSize = 21.sp, fontFamily = FontFamily.Serif)
            Text(label, color = Muted, fontSize = 10.sp, letterSpacing = .6.sp, maxLines = 2)
        }
    }
}
