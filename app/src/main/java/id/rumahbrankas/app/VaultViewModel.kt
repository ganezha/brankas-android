package id.rumahbrankas.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.sync.Mutex

/** Screen state and in-memory vault key survive configuration changes without living in a composable. */
class VaultViewModel(val repository: VaultRepository) : ViewModel() {
    val master = mutableStateOf<ByteArray?>(null)
    val pendingCreation = mutableStateOf<CreatedVault?>(null)
    val recoveryToShow = mutableStateOf<String?>(null)
    val biometricSetup = mutableStateOf(false)
    val biometricSetupKey = mutableStateOf<ByteArray?>(null)
    val credentialType = mutableStateOf(
        if (repository.exists()) runCatching { repository.credentialType() }.getOrDefault("pin") else "pin",
    )
    val credentialBusy = mutableStateOf(false)
    val locking = mutableStateOf(false)
    val keyGeneration = mutableStateOf(0)
    val isForeground = mutableStateOf(true)
    val entries = mutableStateListOf<VaultEntry>()
    val message = mutableStateOf("")
    val currentOperation = mutableStateOf<String?>(null)
    val vaultOperations = Mutex()

    override fun onCleared() {
        master.value?.fill(0)
        pendingCreation.value?.masterKey?.fill(0)
        biometricSetupKey.value?.fill(0)
        master.value = null
        pendingCreation.value = null
        biometricSetupKey.value = null
        currentOperation.value = null
        entries.clear()
        repository.clearPreviewCache()
    }

    class Factory(private val repository: VaultRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VaultViewModel::class.java))
            return VaultViewModel(repository) as T
        }
    }
}
