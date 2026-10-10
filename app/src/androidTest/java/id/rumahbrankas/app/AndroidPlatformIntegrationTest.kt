package id.rumahbrankas.app

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlatformIntegrationTest {
    @Test
    fun biometricKeystoreIsAuthenticationGatedAndDocumentPickerActionsResolve() {
        val alias = "brankas-test-" + UUID.randomUUID()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
            val key = generator.generateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val denial = assertThrows(IllegalBlockSizeException::class.java) {
                cipher.doFinal(ByteArray(32))
            }
            var cause: Throwable? = denial
            var authenticationDenied = false
            while (cause != null) {
                if (cause.message?.contains("not authenticated", ignoreCase = true) == true) {
                    authenticationDenied = true
                }
                cause = cause.cause
            }
            assertTrue("Keystore must report that user authentication is required.", authenticationDenied)
        } finally {
            keyStore.deleteEntry(alias)
        }

        val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        val createDocument = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_TITLE, "brankas-test.jpg")

        val openResolution = resolveIntentInSystem(openDocument)
        val createResolution = resolveIntentInSystem(createDocument)
        assertTrue("Android must provide a handler for importing a document: " + openResolution, openResolution.contains("/"))
        assertTrue("Android must provide a handler for restoring a document: " + createResolution, createResolution.contains("/"))
    }

    private fun resolveIntentInSystem(intent: Intent): String {
        val command = "cmd package resolve-activity --brief -a " + intent.action +
            " -c " + Intent.CATEGORY_OPENABLE + " -t " + intent.type
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }
}
