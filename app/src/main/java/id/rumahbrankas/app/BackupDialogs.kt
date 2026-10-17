package id.rumahbrankas.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun BackupRecoveryCodeDialog(
    export: Boolean,
    value: String,
    error: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(if (export) R.string.backup_export_code_title else R.string.backup_restore_code_title)) },
        text = {
            Column {
                Text(stringResource(if (export) R.string.backup_export_code_description else R.string.backup_restore_code_description))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_recovery_code)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = ColorError)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && value.isNotBlank()) {
                Text(stringResource(if (export) R.string.backup_export else R.string.action_continue), color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel), color = Muted) }
        },
        containerColor = Panel,
        titleContentColor = Paper,
        textContentColor = Muted,
    )
}

@Composable
internal fun BackupRestoreVerifiedDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_restore_verified_title)) },
        text = { Text(stringResource(R.string.backup_restore_verified_message)) },
        confirmButton = {},
        containerColor = Panel,
        titleContentColor = Paper,
        textContentColor = Muted,
    )
}

@Composable
internal fun BackupNewPinDialog(
    pin: String,
    repeatedPin: String,
    error: String,
    busy: Boolean,
    onPinChange: (String) -> Unit,
    onRepeatedPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.backup_new_pin_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_new_pin_description))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { onPinChange(it.filter { character -> character in '0'..'9' }.take(12)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_new_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeatedPin,
                    onValueChange = { onRepeatedPinChange(it.filter { character -> character in '0'..'9' }.take(12)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_repeat_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = ColorError)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && pin.isNotBlank() && repeatedPin.isNotBlank()) {
                Text(stringResource(R.string.backup_new_pin_confirm), color = Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel), color = Muted) }
        },
        containerColor = Panel,
        titleContentColor = Paper,
        textContentColor = Muted,
    )
}

private val ColorError = androidx.compose.ui.graphics.Color(0xFFE8A6A0)
