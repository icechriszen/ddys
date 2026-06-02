package com.jing.ddys.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSourceAuthTest {

    @Test
    fun detectsDdysProtectGateHtmlAsPasswordProtected() {
        val html = """
            <body class="ddys-protect-gate">
                <form method="post" action="/">
                    <input name="ddys_protect_password" type="password">
                    <input type="hidden" name="ddys_protect_action" value="gate_login">
                </form>
            </body>
        """.trimIndent()

        assertTrue(VideoSourceAuth.isPasswordProtectedHtml(html))
    }
}
