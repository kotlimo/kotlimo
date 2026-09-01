package io.kotlimo.hash

import at.favre.lib.crypto.bcrypt.BCrypt
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Base64

object Hash {
    const val BCRYPT = "bcrypt"
    const val ARGON2 = "argon2"

    private const val BCRYPT_COST = 10
    private const val ARGON2_MEMORY_KB = 8_192
    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_PARALLELISM = 1
    private const val ARGON2_HASH_LENGTH = 32
    private const val ARGON2_SALT_LENGTH = 16

    var driver: String = BCRYPT

    fun make(value: String, algorithm: String = driver): String = when (algorithm.lowercase()) {
        ARGON2, "argon2id" -> argon2(value)
        else -> bcrypt(value)
    }

    fun check(value: String, hashed: String): Boolean {
        if (hashed.isBlank()) return false
        return if (hashed.startsWith("\$argon2")) {
            verifyArgon2(value, hashed)
        } else {
            BCrypt.verifyer().verify(value.toCharArray(), hashed).verified
        }
    }

    fun needsRehash(hashed: String, algorithm: String = driver): Boolean {
        val expected = algorithm.lowercase()
        val actual = if (hashed.startsWith("\$argon2")) ARGON2 else BCRYPT
        return expected != actual
    }

    private fun bcrypt(value: String): String =
        BCrypt.withDefaults().hashToString(BCRYPT_COST, value.toCharArray())

    private fun argon2(value: String): String {
        val salt = ByteArray(ARGON2_SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = argon2Bytes(value, salt)
        val encoder = Base64.getEncoder().withoutPadding()
        return "\$argon2id\$v=19\$m=$ARGON2_MEMORY_KB,t=$ARGON2_ITERATIONS,p=$ARGON2_PARALLELISM\$" +
            encoder.encodeToString(salt) + "\$" + encoder.encodeToString(hash)
    }

    private fun verifyArgon2(value: String, hashed: String): Boolean {
        val parts = hashed.split('$').filter { it.isNotEmpty() }
        if (parts.size < 5) return false
        val params = parts[2].split(',').associate { piece ->
            val key = piece.substringBefore('=')
            val number = piece.substringAfter('=').toIntOrNull() ?: return false
            key to number
        }
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[3])
        val expected = decoder.decode(parts[4])
        val actual = argon2Bytes(
            value,
            salt,
            memoryKb = params["m"] ?: ARGON2_MEMORY_KB,
            iterations = params["t"] ?: ARGON2_ITERATIONS,
            parallelism = params["p"] ?: ARGON2_PARALLELISM,
            length = expected.size
        )
        return java.security.MessageDigest.isEqual(expected, actual)
    }

    private fun argon2Bytes(
        value: String,
        salt: ByteArray,
        memoryKb: Int = ARGON2_MEMORY_KB,
        iterations: Int = ARGON2_ITERATIONS,
        parallelism: Int = ARGON2_PARALLELISM,
        length: Int = ARGON2_HASH_LENGTH
    ): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKb)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        val out = ByteArray(length)
        generator.generateBytes(value.toByteArray(Charsets.UTF_8), out)
        return out
    }
}
