package id.rumahbrankas.app

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * JVM-only cryptographic primitives shared by the Android repository and unit tests.
 * The on-disk format remains AES-GCM with a 12-byte IV prepended to ciphertext.
 */
internal object VaultCrypto {
    const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun deriveKey(secret: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            secret.fill('\u0000')
        }
    }

    fun encrypt(key: ByteArray, clear: ByteArray, random: SecureRandom = SecureRandom()): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv to cipher.doFinal(clear)
    }

    fun encryptCombined(key: ByteArray, clear: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val (iv, encrypted) = encrypt(key, clear, random)
        return iv + encrypted
    }

    fun decrypt(key: ByteArray, iv: ByteArray, encrypted: ByteArray): ByteArray {
        require(iv.size == IV_BYTES) { "IV AES-GCM harus 12 byte." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    fun decryptCombined(key: ByteArray, combined: ByteArray): ByteArray {
        require(combined.size >= IV_BYTES + TAG_BITS / 8) { "Data terenkripsi tidak lengkap." }
        return decrypt(key, combined.copyOfRange(0, IV_BYTES), combined.copyOfRange(IV_BYTES, combined.size))
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

internal fun String.unhex(): ByteArray {
    require(length % 2 == 0) { "Nilai heksadesimal tidak lengkap." }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
