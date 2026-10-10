package id.rumahbrankas.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    private lateinit var repository: VaultRepository
    private lateinit var vaultViewModel: VaultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        repository = DefaultVaultRepository(this)
        vaultViewModel = ViewModelProvider(this, VaultViewModel.Factory(repository))[VaultViewModel::class.java]
        setContent { BrankasTheme { BrankasApp(repository, this, vaultViewModel) } }
    }

    /**
     * Leaving the app ends the task. Hiding it from recents is not enough: the launcher would
     * resume the same unlocked activity. The document picker is exempt so a file choice can finish.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        endTaskAfterLeave()
    }

    override fun onStop() {
        super.onStop()
        endTaskAfterLeave()
    }

    private fun endTaskAfterLeave() {
        if (isChangingConfigurations || isFinishing) return
        if (!::vaultViewModel.isInitialized) return
        if (vaultViewModel.externalPickerOpen.value || vaultViewModel.systemPromptOpen.value) return
        vaultViewModel.discardSession()
        finishAndRemoveTask()
    }

    internal fun retainTaskForPrompt(retain: Boolean) {
        if (!::vaultViewModel.isInitialized) return
        vaultViewModel.systemPromptOpen.value = retain
        if (!retain && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) endTaskAfterLeave()
    }
}