package id.rumahbrankas.app

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher

@Composable
internal fun BrankasApp(repo: VaultRepository, activity: FragmentActivity, viewModel: VaultViewModel) {
    var master by viewModel.master
    var pendingCreation by viewModel.pendingCreation
    var recoveryToShow by viewModel.recoveryToShow
    var biometricSetup by viewModel.biometricSetup
    var biometricSetupKey by viewModel.biometricSetupKey
    var credentialType by viewModel.credentialType
    var credentialBusy by viewModel.credentialBusy
    var locking by viewModel.locking
    var lockComplete by viewModel.lockComplete
    var previewCacheCleared by viewModel.previewCacheCleared
    var keyGeneration by viewModel.keyGeneration
    var isForeground by viewModel.isForeground
    val entries = viewModel.entries
    var currentOperation by viewModel.currentOperation
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vaultOperations = viewModel.vaultOperations
    var toast by viewModel.message
    var notice by viewModel.notice
    var backgroundLockJob by remember { mutableStateOf<Job?>(null) }

    fun unlock(key: ByteArray) {
        if (!isForeground) {
            key.fill(0)
            toast = context.getString(R.string.status_locked_key_dropped)
            return
        }
        keyGeneration += 1
        master?.fill(0)
        master = key
        locking = false
        lockComplete = false
        toast = ""
    }

    fun copiedKeyOrMessage(): ByteArray? {
        if (locking) {
            toast = context.getString(R.string.status_lock_in_progress)
            return null
        }
        return master?.copyOf().also { if (it == null) toast = context.getString(R.string.status_locked) }
    }

    fun requestLock() {
        if (locking) return
        locking = true
        lockComplete = false
        previewCacheCleared = false
        keyGeneration += 1
        toast = context.getString(R.string.status_locking)
        scope.launch {
            vaultOperations.withLock {
                master?.fill(0)
                pendingCreation?.masterKey?.fill(0)
                biometricSetupKey?.fill(0)
                master = null
                pendingCreation = null
                recoveryToShow = null
                biometricSetupKey = null
                biometricSetup = false
                entries.clear()
                previewCacheCleared = runCatching {
                    withContext(Dispatchers.IO) { repo.clearPreviewCache() }
                }.getOrDefault(false)
                lockComplete = true
                toast = if (previewCacheCleared) "" else context.getString(R.string.status_lock_cache_warning)
                delay(700L)
                lockComplete = false
                locking = false
            }
        }
    }

    val lockAction by rememberUpdatedState(newValue = { requestLock() })
    DisposableEffect(activity.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                    backgroundLockJob?.cancel()
                    backgroundLockJob = scope.launch {
                        delay(30_000L)
                        lockAction()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    isForeground = true
                    backgroundLockJob?.cancel()
                    backgroundLockJob = null
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            backgroundLockJob?.cancel()
        }
    }

    LaunchedEffect(master) {
        val activeKey = master ?: return@LaunchedEffect
        val key = activeKey.copyOf()
        val expectedGeneration = keyGeneration
        try {
            vaultOperations.withLock {
                if (!locking && keyGeneration == expectedGeneration && master === activeKey) {
                    val data = withContext(Dispatchers.IO) { repo.entries(key) }
                    if (!locking && keyGeneration == expectedGeneration && master === activeKey) {
                        entries.clear()
                        entries.addAll(data)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            toast = repo.messageForFailure(context.getString(R.string.status_reading_vault), error)
        } finally {
            key.fill(0)
        }
    }

    fun launchWithKey(
        operation: String,
        key: ByteArray,
        expectedGeneration: Int = keyGeneration,
        reportFailure: Boolean = true,
        action: suspend (ByteArray) -> Unit,
    ) {
        scope.launch {
            try {
                vaultOperations.withLock {
                    if (locking || master == null || keyGeneration != expectedGeneration) return@withLock
                    currentOperation = operation
                    try {
                        action(key)
                    } finally {
                        currentOperation = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (reportFailure) toast = repo.messageForFailure(operation, error)
            } finally {
                currentOperation = null
                key.fill(0)
            }
        }
    }

    fun launchExternalPicker(launch: () -> Unit) {
        viewModel.externalPickerOpen.value = true
        try {
            launch()
        } catch (error: RuntimeException) {
            viewModel.externalPickerOpen.value = false
            throw error
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        viewModel.externalPickerOpen.value = false
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vaultOperations.withLock {
                notice = context.getString(R.string.backup_working)
                try {
                    withContext(Dispatchers.IO) { repo.exportBackup(uri.toString()) }
                    notice = context.getString(R.string.backup_exported)
                } catch (cancelled: CancellationException) {
                    notice = ""
                    throw cancelled
                } catch (error: Exception) {
                    notice = ""
                    toast = repo.messageForFailure(context.getString(R.string.operation_export_backup), error)
                }
            }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.externalPickerOpen.value = false
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vaultOperations.withLock {
                notice = context.getString(R.string.backup_restoring)
                try {
                    withContext(Dispatchers.IO) { repo.restoreBackup(uri.toString()) }
                    viewModel.discardSession()
                    credentialType = runCatching { repo.credentialType() }.getOrDefault("pin")
                    notice = context.getString(R.string.backup_restored)
                } catch (cancelled: CancellationException) {
                    notice = ""
                    throw cancelled
                } catch (error: Exception) {
                    notice = ""
                    toast = repo.messageForFailure(context.getString(R.string.operation_restore_backup), error)
                }
            }
        }
    }
    fun startBackupExport() {
        if (!repo.exists()) {
            notice = context.getString(R.string.backup_empty)
            return
        }
        launchExternalPicker { exportPicker.launch("brankas-backup.brankas") }
    }
    fun startBackupRestore() {
        launchExternalPicker { backupPicker.launch(arrayOf("*/*")) }
    }
    LaunchedEffect(notice) {
        val shown = notice
        if (shown.isBlank()) return@LaunchedEffect
        val working = context.getString(R.string.backup_working)
        val restoring = context.getString(R.string.backup_restoring)
        if (shown == working || shown == restoring) return@LaunchedEffect
        delay(4_000)
        if (notice == shown) notice = ""
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.externalPickerOpen.value = false
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val key = copiedKeyOrMessage() ?: return@rememberLauncherForActivityResult
        launchWithKey(context.getString(R.string.operation_import), key) { operationKey ->
            toast = context.getString(R.string.status_importing)
            val imported = mutableListOf<ImportOutcome>()
            var failed = 0
            var firstFailure: String? = null
            viewModel.importProgress.value = ImportProgress(0, uris.size, 1)
            try {
                for ((index, uri) in uris.withIndex()) {
                    viewModel.importProgress.value = ImportProgress(index, uris.size, index + 1)
                    try {
                        imported += withContext(Dispatchers.IO) {
                            repo.importAndMove(operationKey, uri.toString())
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failed++
                        val message = repo.messageForFailure(context.getString(R.string.operation_import), error)
                        if (firstFailure == null) firstFailure = message
                    }
                }
            } finally {
                viewModel.importProgress.value = null
            }
            entries.addAll(0, imported.map { it.entry })
            val sourceRetained = imported.count { !it.sourceRemoved }
            toast = when {
                imported.isEmpty() -> firstFailure ?: context.getString(R.string.import_nothing_succeeded)
                sourceRetained > 0 || failed > 0 -> context.getString(
                    R.string.import_retained, imported.size, sourceRetained + failed, firstFailure.orEmpty(),
                )
                else -> context.getString(R.string.import_completed, imported.size)
            }
        }
    }

    var pendingRestore by remember { mutableStateOf<VaultEntry?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.externalPickerOpen.value = false
        val item = pendingRestore
        pendingRestore = null
        val destination = result.data?.data
        if (item == null) return@rememberLauncherForActivityResult
        if (result.resultCode != android.app.Activity.RESULT_OK || destination == null) {
            toast = context.getString(R.string.status_restore_cancelled)
            return@rememberLauncherForActivityResult
        }
        val key = copiedKeyOrMessage() ?: return@rememberLauncherForActivityResult
        launchWithKey(context.getString(R.string.operation_restore), key) { operationKey ->
            toast = context.getString(R.string.status_restoring)
            val cleaned = withContext(Dispatchers.IO) {
                repo.restoreAndMove(operationKey, item, destination.toString())
            }
            entries.removeAll { it.id == item.id }
            toast = context.getString(if (cleaned) R.string.restore_complete else R.string.restore_kept_copy, item.title)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        CompositionLocalProvider(
            LocalVaultBackup provides VaultBackupActions(::startBackupExport, ::startBackupRestore),
        ) {
        Box(Modifier.fillMaxSize()) {
            val current = master
            when {
                locking || lockComplete -> LockTransitionScreen(lockComplete, previewCacheCleared)
                recoveryToShow != null && pendingCreation != null -> RecoveryScreen(
                    recoveryToShow!!,
                    toast,
                    credentialBusy,
                    onContinue = {
                        val prepared = pendingCreation ?: return@RecoveryScreen
                        if (credentialBusy || locking) return@RecoveryScreen
                        credentialBusy = true
                        val expectedGeneration = keyGeneration
                        val operationKey = prepared.masterKey.copyOf()
                        scope.launch {
                            try {
                                vaultOperations.withLock {
                                    if (!locking && keyGeneration == expectedGeneration) {
                                        withContext(Dispatchers.IO) { repo.commitCreate(prepared) }
                                        credentialType = prepared.credentialKind
                                        prepared.masterKey.fill(0)
                                        pendingCreation = null
                                        recoveryToShow = null
                                        unlock(operationKey.copyOf())
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                toast = repo.messageForFailure(context.getString(R.string.operation_save_vault), error)
                            } finally {
                                operationKey.fill(0)
                                credentialBusy = false
                            }
                        }
                    },
                )
                current != null -> VaultHome(
                    repo, entries, toast,
                    onDismissMessage = { shown -> if (toast == shown) toast = "" },
                    importProgress = viewModel.importProgress.value,
                    onLock = { requestLock() },
                    onAddMedia = {
                        if (!locking) launchExternalPicker { picker.launch(arrayOf("image/*", "video/*")) }
                    },
                    onRestore = { item ->
                        if (!locking) {
                            val fileName = if (item.isNote && !item.title.endsWith(".txt", true)) item.title + ".txt" else item.title
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = if (item.isNote) "text/plain" else item.mimeType
                                putExtra(Intent.EXTRA_TITLE, fileName)
                            }
                            pendingRestore = item
                            try {
                                launchExternalPicker { restorePicker.launch(intent) }
                            } catch (error: RuntimeException) {
                                pendingRestore = null
                                throw error
                            }
                        }
                    },
                    onAddNote = { title, body ->
                        val key = copiedKeyOrMessage() ?: return@VaultHome
                        launchWithKey(context.getString(R.string.operation_save_note), key) { operationKey ->
                            val resolvedTitle = title.trim().ifBlank { context.getString(R.string.default_note_title) }
                            val item = withContext(Dispatchers.IO) { repo.addNote(operationKey, resolvedTitle, body) }
                            entries.add(0, item)
                            toast = context.getString(R.string.status_note_saved)
                        }
                    },
                    onDelete = { item ->
                        val key = copiedKeyOrMessage() ?: return@VaultHome
                        launchWithKey(context.getString(R.string.operation_delete_item), key) { operationKey ->
                            withContext(Dispatchers.IO) { repo.delete(operationKey, item) }
                            entries.removeAll { it.id == item.id }
                            toast = context.getString(R.string.status_item_deleted)
                        }
                    },
                    onEnableBiometric = {
                        val key = copiedKeyOrMessage() ?: return@VaultHome
                        val expectedGeneration = keyGeneration
                        try {
                            biometricSetupKey?.fill(0)
                            biometricSetupKey = key
                            biometricSetup = true
                            val cipher = repo.newBiometricEncryptCipher()
                            prompt(
                            activity, cipher,
                            activity.getString(R.string.biometric_enable_title),
                            activity.getString(R.string.biometric_enable_subtitle),
                                onSuccess = { authenticated ->
                                    launchWithKey(context.getString(R.string.operation_enable_biometric), key, expectedGeneration) { operationKey ->
                                        try {
                                            withContext(Dispatchers.IO) { repo.saveBiometricWrap(authenticated, operationKey) }
                                            toast = context.getString(R.string.status_biometric_ready)
                                        } finally {
                                            biometricSetup = false
                                            biometricSetupKey = null
                                        }
                                    }
                                },
                                onError = { error ->
                                    key.fill(0)
                                    biometricSetupKey = null
                                    biometricSetup = false
                                    toast = if (error.message.orEmpty().contains("canceled", true) ||
                                        error.message.orEmpty().contains("cancel", true)) {
                                        context.getString(R.string.status_biometric_cancelled)
                                    } else repo.messageForFailure(context.getString(R.string.operation_biometric_activation), error)
                                },
                            )
                        } catch (error: Exception) {
                            key.fill(0)
                            biometricSetupKey = null
                            biometricSetup = false
                            toast = repo.messageForFailure(context.getString(R.string.operation_biometric_activation), error)
                        }
                    },
                    onPreview = { item, onReady ->
                        val key = copiedKeyOrMessage()
                        if (key == null) onReady(null, null)
                        else launchWithKey(context.getString(R.string.operation_open_file), key) { operationKey ->
                            try {
                                if (item.isNote) {
                                    val body = withContext(Dispatchers.IO) { repo.readNote(operationKey, item) }
                                    onReady(null, body)
                                } else {
                                    val file = withContext(Dispatchers.IO) { repo.decryptToCache(operationKey, item) }
                                    onReady(file, null)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                onReady(null, null)
                                throw error
                            }
                        }
                    },
                    onLoadThumbnail = { item, onReady ->
                        val key = copiedKeyOrMessage()
                        if (key == null) onReady(null)
                        else launchWithKey(
                            context.getString(R.string.operation_load_thumbnail), key, reportFailure = false,
                        ) { operationKey ->
                            val thumbnail = try {
                                withContext(Dispatchers.IO) {
                                    val clearFile = repo.decryptToCache(operationKey, item)
                                    val readable = if (item.isVideo) videoFileForFrame(clearFile, item.mimeType) else clearFile
                                    try {
                                        if (item.isVideo) videoThumbnail(readable) else {
                                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                            BitmapFactory.decodeFile(readable.absolutePath, bounds)
                                            var sample = 1
                                            while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) sample *= 2
                                            BitmapFactory.decodeFile(
                                                readable.absolutePath,
                                                BitmapFactory.Options().apply { inSampleSize = sample },
                                            )?.asImageBitmap()
                                        }
                                    } finally {
                                        readable.delete()
                                        if (readable != clearFile) clearFile.delete()
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                            onReady(thumbnail)
                        }
                    },
                )
                !repo.exists() -> SetupScreen(
                    error = toast,
                    busy = credentialBusy,
                    onCreate = { secret, confirm, kind ->
                        if (credentialBusy) return@SetupScreen
                        val valid = if (kind == "pin") {
                            secret.length in 6..12 && secret.all { it in '0'..'9' }
                        } else secret.length in 12..128
                        if (!valid) {
                            toast = context.getString(if (kind == "pin") R.string.error_pin_format else R.string.error_passphrase_min)
                        } else if (secret != confirm) {
                            toast = context.getString(R.string.error_confirmation_mismatch)
                        } else {
                            credentialBusy = true
                            toast = context.getString(R.string.status_preparing_vault)
                            scope.launch {
                                try {
                                    val prepared = withContext(Dispatchers.IO) {
                                        repo.create(secret.toCharArray(), kind)
                                    }
                                    if (isForeground) {
                                        pendingCreation = prepared
                                        recoveryToShow = prepared.recoveryCode
                                        toast = ""
                                    } else {
                                        prepared.masterKey.fill(0)
                                        toast = context.getString(R.string.status_creation_cancelled_background)
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    toast = repo.messageForFailure(context.getString(R.string.operation_create_vault), error)
                                } finally {
                                    credentialBusy = false
                                }
                            }
                        }
                    },
                )
                else -> UnlockScreen(
                    repo, activity, credentialType, toast, credentialBusy,
                    onUnlock = { secret ->
                        if (!credentialBusy) {
                            credentialBusy = true
                            val expectedGeneration = keyGeneration
                            toast = context.getString(R.string.status_unlocking)
                            scope.launch {
                                try {
                                    val key = withContext(Dispatchers.IO) {
                                        repo.unlock(secret.toCharArray())
                                    }
                                    if (!isForeground || keyGeneration != expectedGeneration) {
                                        key.fill(0)
                                        toast = context.getString(R.string.status_unlock_discarded)
                                    } else unlock(key)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    toast = repo.messageForFailure(context.getString(R.string.operation_unlock_vault), error)
                                } finally {
                                    credentialBusy = false
                                }
                            }
                        }
                    },
                    onBiometric = ::unlock,
                    onBiometricFailure = { error ->
                        toast = repo.messageForFailure(context.getString(R.string.operation_biometric_auth), error)
                    },
                    onLeaveRecovery = { toast = "" },
                    onRecovery = { code, newSecret, kind ->
                        if (!credentialBusy) {
                            credentialBusy = true
                            val expectedGeneration = keyGeneration
                            scope.launch {
                                try {
                                    val key = withContext(Dispatchers.IO) {
                                        val recovered = repo.unlockWithRecovery(code.toCharArray())
                                        try {
                                            repo.resetCredential(recovered, newSecret.toCharArray(), kind)
                                            recovered
                                        } catch (error: Exception) {
                                            recovered.fill(0)
                                            throw error
                                        }
                                    }
                                    if (!isForeground || keyGeneration != expectedGeneration) {
                                        key.fill(0)
                                        toast = context.getString(R.string.status_recovery_discarded)
                                    } else {
                                        credentialType = kind
                                        unlock(key)
                                        toast = context.getString(R.string.status_credential_updated)
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    toast = repo.messageForFailure(context.getString(R.string.recovery_failure), error)
                                } finally {
                                    credentialBusy = false
                                }
                            }
                        }
                    },
                )
            }
            if (notice.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                        .padding(start = 20.dp, end = 20.dp, bottom = 88.dp),
                    color = PanelRaised,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Line),
                ) {
                    Text(notice, color = Gold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                }
            }
        }
        }
    }
}

private fun videoFileForFrame(file: File, mimeType: String): File {
    val extension = when {
        mimeType.contains("webm") -> "webm"
        mimeType.contains("3gpp") || mimeType.contains("3gp") -> "3gp"
        mimeType.contains("matroska") || mimeType.contains("mkv") -> "mkv"
        else -> "mp4"
    }
    if (file.extension.equals(extension, ignoreCase = true)) return file
    val named = File(file.parentFile, "${file.name}.$extension")
    return if (file.renameTo(named)) named else file
}

private fun videoThumbnail(file: File): androidx.compose.ui.graphics.ImageBitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: return null
        frameForCard(frame).asImageBitmap()
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun frameForCard(frame: Bitmap): Bitmap {
    val longest = maxOf(frame.width, frame.height)
    if (longest <= 720) return frame
    val scale = 720f / longest
    val width = (frame.width * scale).toInt().coerceAtLeast(1)
    val height = (frame.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(frame, width, height, true).also { if (it != frame) frame.recycle() }
}
