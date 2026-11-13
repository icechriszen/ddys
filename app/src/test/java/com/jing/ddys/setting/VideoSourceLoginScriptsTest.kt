package com.jing.ddys.setting

import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSourceLoginScriptsTest {

    @Test
    fun capUsesOnlyConvenienceFillAndDoesNotRunLegacyPatches() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")
        val cap = script.substringAfter("if (document.querySelector('cap-widget'))").substringBefore("var formSelector")
        assertTrue(cap.contains("return;"))
        assertTrue(cap.contains("!input.value"))
        assertTrue(!cap.contains("dispatchEvent"))
        assertTrue(!cap.contains(".click()"))
        assertTrue(!cap.contains(".solve("))
        assertTrue(!cap.contains("disabled = false"))
        assertTrue(VideoSourceLoginScripts.buildReadPageStateScript().contains("catalog:"))
    }

    @Test
    fun loginScriptUsesNativeSubmitFlow() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("input[name=\"captcha_code\"]"))
        assertTrue(!script.contains("HTMLFormElement.prototype.submit.call"))
        assertTrue(!script.contains("stopImmediatePropagation"))
        assertTrue(!script.contains("preventDefault()"))
    }

    @Test
    fun loginScriptDoesNotForceSubmitButtonState() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("MutationObserver"))
        assertTrue(!script.contains("querySelectorAll(submitSelector)"))
        assertTrue(!script.contains("submit.hasAttribute('disabled')"))
        assertTrue(!script.contains("setAttribute('aria-disabled', 'false')"))
        assertTrue(script.contains("checks > 40"))
        assertTrue(!script.contains("attributeFilter"))
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

        assertTrue(!script.contains("passwordInput.focus()"))
        assertTrue(script.contains("current.passwordInput.blur()"))
        assertTrue(script.contains("observerScheduled"))
        assertTrue(script.contains("window.setTimeout(function()"))
    }

    @Test
    fun loginScriptMakesCaptchaExplicitlyEditable() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("makeCaptchaEditable"))
        assertTrue(script.contains("captchaInput.readOnly = false"))
        assertTrue(script.contains("data-ddys-captcha-patched"))
        assertTrue(script.contains("input.focus()"))
    }

    @Test
    fun loginScriptSupportsDdysProtectGatePasswordField() {
        val script = VideoSourceLoginScripts.buildEnhanceLoginFormScript("ddys")

        assertTrue(script.contains("input[name=\"ddys_protect_password\"]"))
        assertTrue(script.contains("passwordInput ? passwordInput.form : null"))
    }

    @Test
    fun loginScriptCanApplyAltchaPayloadWithoutForcingSubmit() {
        val statusScript = VideoSourceLoginScripts.buildReadAltchaChallengeUrlScript()
        val applyScript = VideoSourceLoginScripts.buildApplyAltchaPayloadScript("payload")

        assertTrue(statusScript.contains("input[name=\"altcha\"]"))
        assertTrue(statusScript.contains("ddys_protect_altcha_gate"))
        assertTrue(statusScript.contains("challengeurl"))
        assertTrue(applyScript.contains("var inputName"))
        assertTrue(applyScript.contains("input.name = inputName"))
        assertTrue(applyScript.contains("dispatchFieldEvent(input, name)"))
        assertTrue(applyScript.contains("dispatchFieldEvent(form, name)"))
        assertTrue(applyScript.contains("CustomEvent(name"))
        assertTrue(applyScript.contains("state: 'verified'"))
        assertTrue(!applyScript.contains("wp-submit"))
        assertTrue(!applyScript.contains(".submit()"))
        assertTrue(!applyScript.contains(".click()"))
    }
}
