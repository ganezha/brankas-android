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
internal fun SetupScreen(error: String, busy: Boolean, onCreate: (String, String, String) -> Unit) {
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var usePassphrase by remember { mutableStateOf(false) }
    val kind = if (usePassphrase) "passphrase" else "pin"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)
        .padding(WindowInsets.statusBars.asPaddingValues()).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SettingsGear() }
            BrandMark()
            Spacer(Modifier.height(54.dp))
            Text(stringResource(R.string.setup_headline), color = Paper, fontSize = 36.sp, lineHeight = 42.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.setup_description), color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { usePassphrase = false }) { Text(stringResource(R.string.credential_pin), color = if (!usePassphrase) Gold else Muted) }
                TextButton(onClick = { usePassphrase = true }) { Text(stringResource(R.string.credential_passphrase), color = if (usePassphrase) Gold else Muted) }
            }
            VaultField(stringResource(if (usePassphrase) R.string.field_passphrase_min else R.string.field_pin_create),
                secret, { secret = it.take(if (usePassphrase) 128 else 12) },
                number = !usePassphrase, secure = true)
            Spacer(Modifier.height(12.dp))
            VaultField(stringResource(if (usePassphrase) R.string.field_repeat_passphrase else R.string.field_repeat_pin),
                confirm, { confirm = it.take(if (usePassphrase) 128 else 12) },
                number = !usePassphrase, secure = true)
            Spacer(Modifier.height(12.dp))
            SecurityHint(stringResource(if (usePassphrase) R.string.hint_passphrase else R.string.hint_pin))
            if (busy) { Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.status_preparing_vault), color = Gold, fontSize = 13.sp) }
            if (error.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(error, color = Color(0xFFE8A6A0), fontSize = 13.sp) }
        }
        PrimaryButton(stringResource(R.string.action_create_vault), Icons.Outlined.Lock, Modifier.fillMaxWidth()) {
            if (!busy) onCreate(secret, confirm, kind)
        }
    }
}
