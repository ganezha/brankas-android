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
import androidx.compose.foundation.layout.heightimport androidx.compose.foundation.layout.heightIn

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
internal fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Gold), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Shield, null, tint = Ink, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.brand_name), color = Paper, fontSize = 12.sp, letterSpacing = 2.6.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun VaultField(label: String, value: String, onChange: (String) -> Unit, number: Boolean = true, secure: Boolean = number) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), singleLine = true,
        label = { Text(label, fontSize = 13.sp) },
        visualTransformation = if (secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when {
            number -> KeyboardType.NumberPassword
            secure -> KeyboardType.Password
            else -> KeyboardType.Ascii
        }),
        shape = RoundedCornerShape(15.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line,
            focusedLabelColor = Gold, unfocusedLabelColor = Muted, focusedTextColor = Paper,
            unfocusedTextColor = Paper, cursorColor = Gold),
    )
}

@Composable
internal fun SecurityHint(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Security, null, tint = Gold, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun PrimaryButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
        Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp)); Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

internal fun prompt(
    activity: FragmentActivity, cipher: Cipher, title: String, subtitle: String,
    onSuccess: (Cipher) -> Unit, onError: (Throwable) -> Unit,
) {
    val host = activity as? MainActivity
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            host?.retainTaskForPrompt(false)
            result.cryptoObject?.cipher?.let(onSuccess)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            host?.retainTaskForPrompt(false)
            onError(IllegalStateException(errString.toString()))
        }
    }
    val biometricPrompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setNegativeButtonText(activity.getString(R.string.action_cancel)).build()
    host?.retainTaskForPrompt(true)
    try {
        biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    } catch (error: RuntimeException) {
        host?.retainTaskForPrompt(false)
        throw error
    }
}
