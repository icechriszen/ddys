package com.jing.ddys.setting

import java.security.MessageDigest

object AltchaChallengeSolver {
    fun solveNumber(
        algorithm: String,
        challenge: String,
        salt: String,
        maxNumber: Int
    ): Int? {
        if (!algorithm.equals("SHA-256", ignoreCase = true) || maxNumber < 0) {
            return null
        }
        val digest = MessageDigest.getInstance("SHA-256")
        for (number in 0..maxNumber) {
            val actual = digest.digest("$salt$number".toByteArray(Charsets.UTF_8)).toHex()
            if (actual.equals(challenge, ignoreCase = true)) {
                return number
            }
        }
        return null
    }

    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        var index = 0
        for (byte in this) {
            val value = byte.toInt() and 0xff
            chars[index++] = HEX[value ushr 4]
            chars[index++] = HEX[value and 0x0f]
        }
        return String(chars)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
