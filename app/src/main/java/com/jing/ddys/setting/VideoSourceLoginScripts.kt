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
                var submitSelector = '#wp-submit, input[type="submit"], button[type="submit"]';
                var passwordSelector = 'input[name="password_protected_pwd"]';
                var captchaSelector = 'input[name="captcha_code"]';

                function findForm() {
                    return document.querySelector(formSelector);
                }

                function fields() {
                    var form = findForm();
                    return {
                        form: form,
                        passwordInput: form ? form.querySelector(passwordSelector) : null,
                        captchaInput: form ? form.querySelector(captchaSelector) : null,
                        submits: form ? Array.prototype.slice.call(form.querySelectorAll(submitSelector)) : []
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

                function visibleFieldsReady() {
                    var current = fields();
                    var passwordInput = current.passwordInput;
                    var captchaInput = current.captchaInput;
                    var hasPassword = !!(passwordInput && passwordInput.value && passwordInput.value.length > 0);
                    var hasCaptcha = !captchaInput || !!(captchaInput.value && captchaInput.value.trim().length > 0);
                    return hasPassword && hasCaptcha;
                }

                function enableSubmit(submit) {
                    if (!submit) {
                        return;
                    }
                    if (submit.disabled) {
                        submit.disabled = false;
                    }
                    if (submit.hasAttribute('disabled')) {
                        submit.removeAttribute('disabled');
                    }
                    if (submit.getAttribute('aria-disabled') !== 'false') {
                        submit.setAttribute('aria-disabled', 'false');
                    }
                    if (submit.classList.contains('disabled')) {
                        submit.classList.remove('disabled');
                    }
                    if (submit.classList.contains('is-disabled')) {
                        submit.classList.remove('is-disabled');
                    }
                    if (submit.style.opacity !== '1') {
                        submit.style.opacity = '1';
                    }
                    if (submit.style.pointerEvents !== 'auto') {
                        submit.style.pointerEvents = 'auto';
                    }
                    if (submit.style.cursor !== 'pointer') {
                        submit.style.cursor = 'pointer';
                    }
                }

                function enableSubmitIfReady() {
                    var current = fields();
                    if (!current.form) {
                        return;
                    }
                    if (current.passwordInput && current.passwordInput.value !== password) {
                        setInputValue(current.passwordInput, password);
                    }
                    if (!visibleFieldsReady()) {
                        return;
                    }
                    current.submits.forEach(enableSubmit);
                }

                function submitFormIfReady(event) {
                    var current = fields();
                    if (!current.form || !visibleFieldsReady()) {
                        return;
                    }
                    enableSubmitIfReady();
                    if (event) {
                        event.preventDefault();
                        event.stopPropagation();
                        if (event.stopImmediatePropagation) {
                            event.stopImmediatePropagation();
                        }
                    }
                    HTMLFormElement.prototype.submit.call(current.form);
                }

                function patchForm() {
                    var current = fields();
                    if (!current.form || current.form.getAttribute('data-ddys-submit-patched') === '1') {
                        return;
                    }
                    current.form.setAttribute('data-ddys-submit-patched', '1');
                    current.form.addEventListener('input', enableSubmitIfReady, true);
                    current.form.addEventListener('change', enableSubmitIfReady, true);
                    current.form.addEventListener('keyup', enableSubmitIfReady, true);
                    current.form.addEventListener('click', function(event) {
                        var target = event.target;
                        var submit = target && target.closest ? target.closest(submitSelector) : null;
                        if (!submit) {
                            return;
                        }
                        submitFormIfReady(event);
                    }, true);
                    current.form.addEventListener('submit', function(event) {
                        enableSubmitIfReady();
                        if (!visibleFieldsReady()) {
                            return;
                        }
                        submitFormIfReady(event);
                    }, true);
                }

                patchForm();
                enableSubmitIfReady();
                var checks = 0;
                var timer = window.setInterval(function() {
                    patchForm();
                    enableSubmitIfReady();
                    checks++;
                    if (checks > 480) {
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
                            enableSubmitIfReady();
                        }, 50);
                    }
                    new MutationObserver(function() {
                        scheduleEnhance();
                    }).observe(document.documentElement, {
                        childList: true,
                        subtree: true,
                        attributes: true,
                        attributeFilter: ['disabled', 'class', 'style', 'aria-disabled']
                    });
                }
            })();
        """.trimIndent()
    }
}
