package com.jing.ddys.setting

import com.google.gson.Gson

object VideoSourceLoginScripts {
    private val gson = Gson()

    fun buildEnhanceLoginFormScript(password: String): String {
        val quotedPassword = gson.toJson(password)
        return """
            (function() {
                var password = $quotedPassword;
                var form = document.querySelector('form#loginform') || document.querySelector('form[name="loginform"]');
                if (!form) {
                    return;
                }

                var passwordInput = form.querySelector('input[name="password_protected_pwd"]');
                var captchaInput = form.querySelector('input[name="captcha_code"]');
                var submit = form.querySelector('#wp-submit, input[type="submit"], button[type="submit"]');

                function dispatchAll(input) {
                    if (!input) {
                        return;
                    }
                    ['input', 'change', 'keyup', 'blur'].forEach(function(name) {
                        input.dispatchEvent(new Event(name, { bubbles: true }));
                    });
                }

                if (passwordInput) {
                    passwordInput.focus();
                    passwordInput.value = password;
                    dispatchAll(passwordInput);
                }

                function visibleFieldsReady() {
                    var hasPassword = !!(passwordInput && passwordInput.value && passwordInput.value.length > 0);
                    var hasCaptcha = !captchaInput || !!(captchaInput.value && captchaInput.value.trim().length > 0);
                    return hasPassword && hasCaptcha;
                }

                function enableSubmitIfReady() {
                    if (!submit || !visibleFieldsReady()) {
                        return;
                    }
                    submit.disabled = false;
                    submit.removeAttribute('disabled');
                    submit.style.opacity = '1';
                    submit.style.pointerEvents = 'auto';
                }

                if (captchaInput) {
                    captchaInput.addEventListener('input', enableSubmitIfReady);
                    captchaInput.addEventListener('change', enableSubmitIfReady);
                    captchaInput.addEventListener('keyup', enableSubmitIfReady);
                }

                if (submit && !submit.dataset.ddysSubmitPatched) {
                    submit.dataset.ddysSubmitPatched = '1';
                    submit.addEventListener('click', function(event) {
                        enableSubmitIfReady();
                        if (!visibleFieldsReady()) {
                            return;
                        }
                        event.preventDefault();
                        event.stopPropagation();
                        event.stopImmediatePropagation();
                        HTMLFormElement.prototype.submit.call(form);
                    }, true);
                }

                enableSubmitIfReady();
                var checks = 0;
                var timer = window.setInterval(function() {
                    enableSubmitIfReady();
                    checks++;
                    if (checks > 120 || visibleFieldsReady()) {
                        window.clearInterval(timer);
                    }
                }, 250);
            })();
        """.trimIndent()
    }
}
