package com.jing.ddys.setting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AltchaChallengeSolverTest {

    @Test
    fun solveNumberFindsSha256ProofOfWorkNumber() {
        val number = AltchaChallengeSolver.solveNumber(
            algorithm = "SHA-256",
            challenge = "77023fc46b20493f00d6851942ce573cea626252f76d7a23e84b3a04a469d40d",
            salt = "ddys-test",
            maxNumber = 200
        )

        assertEquals(123, number)
    }

    @Test
    fun solveNumberRejectsUnsupportedAlgorithm() {
        val number = AltchaChallengeSolver.solveNumber(
            algorithm = "SHA-512",
            challenge = "77023fc46b20493f00d6851942ce573cea626252f76d7a23e84b3a04a469d40d",
            salt = "ddys-test",
            maxNumber = 200
        )

        assertNull(number)
    }
}
