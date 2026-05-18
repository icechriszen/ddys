package com.jing.ddys.setting

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSourceLoginScriptsTest {

    @Test
    fun loginScriptBypassesBrokenGatechaSubmitHandler() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("HTMLFormElement.prototype.submit.call(current.form)"))
        assertTrue(script.contains("input[name=\"captcha_code\"]"))
        assertTrue(script.contains("stopImmediatePropagation"))
    }

    @Test
    fun loginScriptKeepsAsyncSubmitButtonsEnabled() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("MutationObserver"))
        assertTrue(script.contains("querySelectorAll(submitSelector)"))
        assertTrue(script.contains("submit.hasAttribute('disabled')"))
        assertTrue(script.contains("setAttribute('aria-disabled', 'false')"))
        assertTrue(script.contains("checks > 480"))
    }

    @Test
    fun loginScriptSupportsOldWebViewEventConstruction() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("document.createEvent('Event')"))
        assertTrue(script.contains("event.initEvent(name, true, true)"))
        assertTrue(script.contains("Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')"))
    }

    @Test
    fun loginScriptDoesNotStealCaptchaInputFocus() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(!script.contains(".focus()"))
        assertTrue(script.contains("observerScheduled"))
        assertTrue(script.contains("window.setTimeout(function()"))
    }
}
