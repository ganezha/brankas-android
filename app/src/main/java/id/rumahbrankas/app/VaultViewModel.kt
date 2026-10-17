package id.rumahbrankas.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class ImportProgress(val completed: Int, val total: Int, val current: Int)

/** Screen state and in-memory vault key survive configuration changes without living in a composable. */
class VaultViewModel(val repository: VaultRepository) : ViewModel() {
    val master = mutableStateOf<ByteArray?>(null)
    val pendingCreation = mutableStateOf<CreatedVault?>(null)
    val recoveryToShow = mutableStateOf<String?>(null)
    val pendingBackupExport = mutableStateOf<BackupKeyEnvelope?>(null)
    val pendingBackupRestore = mutableStateOf<BackupRestoreSession?>(null)
    val biometricSetup = mutableStateOf(false)
    val biometricSetupKey = mutableStateOf<ByteArray?>(null)
    val credentialType = mutableStateOf(
        if (repository.exists()) runCatching { repository.credentialType() }.getOrDefault("pin") else "pin",
    )
    val credentialBusy = mutableStateOf(false)
    val locking = mutableStateOf(false)
    val lockComplete = mutableStateOf(false)
    val previewCacheCleared = mutableStateOf(false)
    val keyGeneration = mutableStateOf(0)
    val isForeground = mutableStateOf(true)
    /** True while the system document picker is covering this activity, so leaving must not remove the task. */
    val externalPickerOpen = mutableStateOf(false)
    /** True while the biometric prompt is showing. That dialog must not end the task. */
    val systemPromptOpen = mutableStateOf(false)
    val entries = mutableStateListOf<VaultEntry>()
    val message = mutableStateOf("")
    val notice = mutableStateOf("")
    val currentOperation = mutableStateOf<String?>(null)
    val importProgress = mutableStateOf<ImportProgress?>(null)
    val vaultOperations = Mutex()

    /** Drops the unlocked session immediately, before the activity is destroyed. */
    fun discardSession() {
        keyGeneration.value += 1
        master.value?.fill(0)
        pendingCreation.value?.masterKey?.fill(0)
        biometricSetupKey.value?.fill(0)
        pendingBackupExport.value?.clear()
        pendingBackupRestore.value?.let { session ->
            viewModelScope.launch(Dispatchers.IO) { runCatching { repository.cancelBackupRestore(session) } }
        }
        master.value = null
        pendingCreation.value = null
        recoveryToShow.value = null
        pendingBackupExport.value = null
        pendingBackupRestore.value = null
        biometricSetupKey.value = null
        biometricSetup.value = false
        credentialBusy.value = false
        locking.value = false
        lockComplete.value = false
        previewCacheCleared.value = false
        currentOperation.value = null
        importProgress.value = null
        message.value = ""
        notice.value = ""
        entries.clear()
        repository.clearPreviewCache()
    }

    override fun onCleared() {
        discardSession()
    }

    class Factory(private val repository: VaultRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VaultViewModel::class.java))
            return VaultViewModel(repository) as T
        }
    }
}
