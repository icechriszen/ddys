package com.jing.ddys.setting

import com.google.gson.Gson

object VideoSourceLoginScripts {
    private val gson = Gson()

    fun buildEnhanceLoginFormScript(password: String): String {
        val quotedPassword = gson.toJson(password)
        return """
            (function() {
                var password = $quotedPassword;
                var formSelector = 'form#loginform, form[name="loginform"]';
                var passwordSelector = 'input[name="password_protected_pwd"], input[name="ddys_protect_password"]';
                var captchaSelector = 'input[name="captcha_code"]';

                function findForm() {
                    var form = document.querySelector(formSelector);
                    if (form) {
                        return form;
                    }
                    var passwordInput = document.querySelector(passwordSelector);
                    return passwordInput ? passwordInput.form : null;
                }

                function fields() {
                    var form = findForm();
                    return {
                        form: form,
                        passwordInput: form ? form.querySelector(passwordSelector) : null,
                        captchaInput: form ? form.querySelector(captchaSelector) : null
                    };
                }

                function dispatchAll(input) {
                    if (!input) {
                        return;
                    }
                    ['input', 'change', 'keyup', 'blur'].forEach(function(name) {
                        var event;
                        try {
                            event = new Event(name, { bubbles: true });
                        } catch (error) {
                            event = document.createEvent('Event');
                            event.initEvent(name, true, true);
                        }
                        input.dispatchEvent(event);
                    });
                }

                function setInputValue(input, value) {
                    if (!input) {
                        return;
                    }
                    var descriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                    if (descriptor && descriptor.set) {
                        descriptor.set.call(input, value);
                    } else {
                        input.value = value;
                    }
                    dispatchAll(input);
                }

                function makeCaptchaEditable(captchaInput) {
                    if (!captchaInput) {
                        return;
                    }
                    captchaInput.disabled = false;
                    captchaInput.readOnly = false;
                    captchaInput.removeAttribute('disabled');
                    captchaInput.removeAttribute('readonly');
                    captchaInput.style.pointerEvents = 'auto';
                    captchaInput.style.webkitUserSelect = 'text';
                    captchaInput.style.userSelect = 'text';
                    captchaInput.style.touchAction = 'manipulation';
                    if (!captchaInput.hasAttribute('tabindex')) {
                        captchaInput.setAttribute('tabindex', '0');
                    }
                    if (captchaInput.getAttribute('data-ddys-captcha-patched') === '1') {
                        return;
                    }
                    captchaInput.setAttribute('data-ddys-captcha-patched', '1');
                    ['touchend', 'click'].forEach(function(name) {
                        captchaInput.addEventListener(name, function() {
                            var input = this;
                            window.setTimeout(function() {
                                input.focus();
                            }, 0);
                        }, false);
                    });
                }

                function enhanceVisibleFields() {
                    var current = fields();
                    if (!current.form) {
                        return;
                    }
                    if (current.passwordInput && current.passwordInput.value !== password) {
                        setInputValue(current.passwordInput, password);
                    }
                    makeCaptchaEditable(current.captchaInput);
                    if (current.passwordInput &&
                        current.captchaInput &&
                        document.activeElement === current.passwordInput
                    ) {
                        current.passwordInput.blur();
                    }
                }

                function patchForm() {
                    var current = fields();
                    if (!current.form || current.form.getAttribute('data-ddys-submit-patched') === '1') {
                        return;
                    }
                    current.form.setAttribute('data-ddys-submit-patched', '1');
                    current.form.addEventListener('input', enhanceVisibleFields, true);
                    current.form.addEventListener('change', enhanceVisibleFields, true);
                    current.form.addEventListener('keyup', enhanceVisibleFields, true);
                }

                function altchaInput(form) {
                    if (!form) {
                        return null;
                    }
                    var widget = form.querySelector('.password-protected-gatecha altcha-widget, .ddys-protect-pow altcha-widget, altcha-widget[name="ddys_protect_altcha_gate"]');
                    var inputName = widget ? (widget.getAttribute('name') || 'altcha') : 'altcha';
                    return form.querySelector('input[name="' + inputName + '"]') || form.querySelector('input[name="altcha"]');
                }

                function dispatchVerified(form, input) {
                    var widget = form.querySelector('.password-protected-gatecha altcha-widget, .ddys-protect-pow altcha-widget, altcha-widget[name="ddys_protect_altcha_gate"]');
                    if (!widget) {
                        return;
                    }
                    ['verified', 'statechange'].forEach(function(name) {
                        var event;
                        try {
                            event = new CustomEvent(name, { bubbles: true, detail: { state: 'verified', payload: input.value } });
                        } catch (error) {
                            event = document.createEvent('Event');
                            event.initEvent(name, true, true);
                        }
                        widget.dispatchEvent(event);
                    });
                }

                function restoreSubmitIfAltchaVerified() {
                    var form = findForm();
                    var input = altchaInput(form);
                    var button = form ? form.querySelector('[data-ddys-protect-submit]') : null;
                    if (!form || !input || !input.value || input.value.length <= 10) {
                        return;
                    }
                    dispatchVerified(form, input);
                    if (button && button.disabled && button.textContent === (button.getAttribute('data-waiting-label') || '验证中')) {
                        button.disabled = false;
                        button.textContent = button.getAttribute('data-ready-label') || '进入';
                    }
                }

                patchForm();
                enhanceVisibleFields();
                restoreSubmitIfAltchaVerified();
                var checks = 0;
                var timer = window.setInterval(function() {
                    patchForm();
                    enhanceVisibleFields();
                    restoreSubmitIfAltchaVerified();
                    checks++;
                    if (checks > 40) {
                        window.clearInterval(timer);
                    }
                }, 250);
                if (window.MutationObserver) {
                    var observerScheduled = false;
                    function scheduleEnhance() {
                        if (observerScheduled) {
                            return;
                        }
                        observerScheduled = true;
                        window.setTimeout(function() {
                            observerScheduled = false;
                            patchForm();
                            enhanceVisibleFields();
                            restoreSubmitIfAltchaVerified();
                        }, 50);
                    }
                    new MutationObserver(function() {
                        scheduleEnhance();
                    }).observe(document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                }
            })();
        """.trimIndent()
    }

    fun buildReadAltchaChallengeUrlScript(): String {
        return """
            (function() {
                function findForm() {
                    var form = document.querySelector('form#loginform, form[name="loginform"]');
                    if (form) {
                        return form;
                    }
                    var passwordInput = document.querySelector('input[name="ddys_protect_password"]');
                    return passwordInput ? passwordInput.form : null;
                }
                var form = findForm();
                if (!form) {
                    return '';
                }
                var widget = form.querySelector('.password-protected-gatecha altcha-widget, .ddys-protect-pow altcha-widget, altcha-widget[name="ddys_protect_altcha_gate"]');
                var inputName = widget ? widget.getAttribute('name') : 'altcha';
                var input = form.querySelector('input[name="' + inputName + '"]') || form.querySelector('input[name="altcha"]');
                if (input && input.value && input.value.length > 10) {
                    return '';
                }
                if (!widget) {
                    return '';
                }
                return widget.getAttribute('challengeurl') || widget.getAttribute('challengeUrl') || '';
            })();
        """.trimIndent()
    }

    fun buildApplyAltchaPayloadScript(payload: String): String {
        val quotedPayload = gson.toJson(payload)
        return """
            (function() {
                var payload = $quotedPayload;
                function findForm() {
                    var form = document.querySelector('form#loginform, form[name="loginform"]');
                    if (form) {
                        return form;
                    }
                    var passwordInput = document.querySelector('input[name="ddys_protect_password"]');
                    return passwordInput ? passwordInput.form : null;
                }
                var form = findForm();
                if (!form || !payload) {
                    return;
                }
                var widget = form.querySelector('.password-protected-gatecha altcha-widget, .ddys-protect-pow altcha-widget, altcha-widget[name="ddys_protect_altcha_gate"]');
                var inputName = widget ? (widget.getAttribute('name') || 'altcha') : 'altcha';
                var input = form.querySelector('input[name="' + inputName + '"]');
                if (!input) {
                    input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = inputName;
                    form.appendChild(input);
                }
                var descriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                if (descriptor && descriptor.set) {
                    descriptor.set.call(input, payload);
                } else {
                    input.value = payload;
                }
                function dispatchFieldEvent(target, name) {
                    var event;
                    try {
                        event = new Event(name, { bubbles: true });
                    } catch (error) {
                        event = document.createEvent('Event');
                        event.initEvent(name, true, true);
                    }
                    target.dispatchEvent(event);
                }
                ['input', 'change'].forEach(function(name) {
                    dispatchFieldEvent(input, name);
                    dispatchFieldEvent(form, name);
                });
                if (widget) {
                    ['verified', 'statechange'].forEach(function(name) {
                        var event;
                        try {
                            event = new CustomEvent(name, { bubbles: true, detail: { state: 'verified', payload: payload } });
                        } catch (error) {
                            event = document.createEvent('Event');
                            event.initEvent(name, true, true);
                        }
                        widget.dispatchEvent(event);
                    });
                }
                var button = form.querySelector('[data-ddys-protect-submit]');
                if (button && button.disabled && button.textContent === (button.getAttribute('data-waiting-label') || '验证中')) {
                    button.disabled = false;
                    button.textContent = button.getAttribute('data-ready-label') || '进入';
                }
            })();
        """.trimIndent()
    }
}
