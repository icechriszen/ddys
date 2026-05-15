package com.jing.ddys.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Sha256VerifierTest {

    @Test
    fun acceptsMissingChecksum() {
        val file = File.createTempFile("ddys-update", ".apk")
        file.writeText("apk")

        assertTrue(Sha256Verifier.matches(file, null))
    }

    @Test
    fun matchesExpectedSha256() {
        val file = File.createTempFile("ddys-update", ".apk")
        file.writeText("apk")

        assertTrue(
            Sha256Verifier.matches(
                file,
                "dd37c2d7274f7ea982cb83390c36918fee9ce8889073c44b68cdc00bdb8c3e04"
            )
        )
    }

    @Test
    fun rejectsUnexpectedSha256() {
        val file = File.createTempFile("ddys-update", ".apk")
        file.writeText("apk")

        assertFalse(Sha256Verifier.matches(file, "0000"))
    }
}
