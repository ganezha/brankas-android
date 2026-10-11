package id.rumahbrankas.app

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.spimport androidx.compose.ui.res.stringResource

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.BitmapFactory
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
internal fun UnlockScreen(
    repo: VaultRepository,
    activity: FragmentActivity,
    credentialKind: String,
    error: String,
    busy: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: (ByteArray) -> Unit,
    onBiometricFailure: (Throwable) -> Unit,
    onRecovery: (String, String, String) -> Unit,
    onLeaveRecovery: () -> Unit,
) {
    var secret by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var resetWithPassphrase by remember { mutableStateOf(false) }
    val passphraseMode = credentialKind == "passphrase"
    val resetKind = if (resetWithPassphrase) "passphrase" else "pin"
    val closeRecovery = {
        if (!busy) {
            showRecovery = false
            code = ""
            newSecret = ""
            resetWithPassphrase = false
            onLeaveRecovery()
        }
    }
    BackHandler(enabled = showRecovery && !busy, onBack = closeRecovery)
    val gaze = remember { mutableStateOf<Offset?>(null) }
    val rootCoordinates = remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val typedCharacters = if (showRecovery) code.length + newSecret.length else secret.length
    Column(Modifier.fillMaxSize()
        .onGloballyPositioned { rootCoordinates.value = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val root = rootCoordinates.value
                    val pressed = event.changes.firstOrNull { it.pressed }
                    gaze.value = if (root != null && root.isAttached && pressed != null) {
                        root.localToWindow(pressed.position)
                    } else {
                        null
                    }
                }
            }
        }
        .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)
        .padding(WindowInsets.statusBars.asPaddingValues()).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SettingsGear() }
            Spacer(Modifier.height(28.dp))
            VaultEyeMark(gaze.value, typedCharacters, Modifier.size(176.dp))
            Spacer(Modifier.height(25.dp))
            Text(stringResource(R.string.unlock_title), color = Paper, fontSize = 32.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.unlock_tagline), color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(34.dp))
            if (showRecovery) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = closeRecovery, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), tint = Paper)
                    }
                    Text(
                        stringResource(R.string.recovery_section),
                        color = Gold,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.height(14.dp))
                VaultField(stringResource(R.string.field_recovery_code), code, { code = it }, number = false, secure = false)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { resetWithPassphrase = false }) { Text(stringResource(R.string.credential_pin), color = if (!resetWithPassphrase) Gold else Muted) }
                    TextButton(onClick = { resetWithPassphrase = true }) { Text(stringResource(R.string.credential_passphrase), color = if (resetWithPassphrase) Gold else Muted) }
                }
                VaultField(stringResource(if (resetWithPassphrase) R.string.field_new_passphrase else R.string.field_new_pin),
                    newSecret, { newSecret = it.take(if (resetWithPassphrase) 128 else 12) },
                    number = !resetWithPassphrase, secure = true)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.action_recover_unlock), Icons.Outlined.LockOpen, Modifier.fillMaxWidth()) {
                    if (!busy) onRecovery(code, newSecret, resetKind)
                }
            } else {
                VaultField(stringResource(if (passphraseMode) R.string.field_enter_passphrase else R.string.field_enter_pin),
                    secret, { secret = it.take(if (passphraseMode) 128 else 12) },
                    number = !passphraseMode, secure = true)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.action_unlock), Icons.Outlined.LockOpen, Modifier.fillMaxWidth()) {
                    if (!busy) onUnlock(secret)
                }
                if (repo.hasBiometric()) {
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = {
                        try {
                            val cipher = repo.biometricDecryptCipher()
                            prompt(activity, cipher, activity.getString(R.string.biometric_prompt_title), activity.getString(R.string.biometric_prompt_subtitle),
                                onSuccess = { authenticated ->
                                    try { onBiometric(repo.finishBiometricUnlock(authenticated)) }
                                    catch (failure: Exception) { onBiometricFailure(failure) }
                                },
                                onError = onBiometricFailure)
                        } catch (failure: Exception) { onBiometricFailure(failure) }
                    }) {
                        Icon(Icons.Outlined.Fingerprint, null, tint = Gold)
                        Spacer(Modifier.width(9.dp))
                        Text(stringResource(R.string.action_biometric_unlock), color = Gold)
                    }
                }
                TextButton(onClick = { showRecovery = true }) {
                    Text(stringResource(R.string.action_forgot_credential), color = Muted, fontSize = 13.sp)
                }
            }
            if (busy) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.status_processing_securely), color = Gold, fontSize = 13.sp) }
            if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = Color(0xFFE8A6A0), fontSize = 13.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Security, null, tint = Muted.copy(alpha = .7f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.on_device_encryption), color = Muted.copy(alpha = .7f), fontSize = 12.sp)
        }
    }
}
