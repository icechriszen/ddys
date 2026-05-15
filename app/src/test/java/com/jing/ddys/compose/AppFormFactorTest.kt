package com.jing.ddys.compose

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFormFactorTest {

    @Test
    fun televisionUiModeUsesTvFormFactor() {
        val uiMode = Configuration.UI_MODE_TYPE_TELEVISION

        assertEquals(AppFormFactor.Tv, appFormFactorFromUiMode(uiMode))
    }

    @Test
    fun normalUiModeUsesPhoneFormFactor() {
        val uiMode = Configuration.UI_MODE_TYPE_NORMAL

        assertEquals(AppFormFactor.Phone, appFormFactorFromUiMode(uiMode))
    }

    @Test
    fun uiModeWithOtherFlagsStillReadsTypeMask() {
        val uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES

        assertEquals(AppFormFactor.Tv, appFormFactorFromUiMode(uiMode))
    }
}
