package id.rumahbrankas.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : FragmentActivity() {
    private lateinit var repository: VaultRepository
    private lateinit var vaultViewModel: VaultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        repository = DefaultVaultRepository(this)
        vaultViewModel = ViewModelProvider(this, VaultViewModel.Factory(repository))[VaultViewModel::class.java]
        setContent { BrankasTheme { BrankasApp(repository, this, vaultViewModel) } }
    }
}