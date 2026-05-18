package com.jing.ddys.setting

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSourceLoginScriptsTest {

    @Test
    fun loginScriptBypassesBrokenGatechaSubmitHandler() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("HTMLFormElement.prototype.submit.call(form)"))
        assertTrue(script.contains("input[name=\"captcha_code\"]"))
        assertTrue(script.contains("stopImmediatePropagation"))
    }
}
