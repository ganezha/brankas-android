package id.rumahbrankas.app

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Fixed, versioned Argon2id settings for archive-only recovery-key wrapping. */
internal object BackupKeyKdf {
    const val ID = "argon2id-v1"
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32
    private const val MEMORY_KIB = 64 * 1024
    private const val ITERATIONS = 3
    private const val PARALLELISM = 1

    fun derive(recoveryCode: CharArray, salt: ByteArray): ByteArray {
        require(salt.size == SALT_BYTES) { "Salt cadangan tidak valid." }
        val output = ByteArray(KEY_BYTES)
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(ITERATIONS)
            .withMemoryAsKB(MEMORY_KIB)
            .withParallelism(PARALLELISM)
            .withSalt(salt.copyOf())
        val parameters = builder.build()
        try {
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(recoveryCode, output)
            return output
        } catch (error: Exception) {
            output.fill(0)
            throw error
        } finally {
            recoveryCode.fill('\u0000')
            parameters.clear()
            builder.clear()
        }
    }
}
