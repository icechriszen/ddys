package com.jing.ddys.update

import java.io.File
import java.security.MessageDigest

object Sha256Verifier {
    fun matches(file: File, expectedSha256: String?): Boolean {
        val expected = expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return true
        val actual = MessageDigest.getInstance("SHA-256").run {
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        break
                    }
                    update(buffer, 0, read)
                }
            }
            digest().joinToString("") { "%02x".format(it) }
        }
        return actual == expected
    }
}
