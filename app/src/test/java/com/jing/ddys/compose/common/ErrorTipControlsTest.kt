package com.jing.ddys.compose.common

import com.jing.ddys.compose.AppFormFactor
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorTipControlsTest {

    @Test
    fun phoneFormFactorUsesPhoneTouchControls() {
        assertEquals(ErrorTipControls.PhoneTouch, errorTipControlsFor(AppFormFactor.Phone))
    }

    @Test
    fun tvFormFactorUsesTvFocusControls() {
        assertEquals(ErrorTipControls.TvFocus, errorTipControlsFor(AppFormFactor.Tv))
    }
}
