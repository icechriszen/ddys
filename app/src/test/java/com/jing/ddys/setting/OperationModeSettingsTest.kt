package com.jing.ddys.setting

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.jing.ddys.compose.AppFormFactor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class OperationModeSettingsTest {
    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("operation-mode-test", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun firstLaunchAutomaticallyUsesTheDeviceType() {
        val settings = OperationModeSettings(preferences)
        assertNull(settings.modeOverride.value)
        assertEquals(AppFormFactor.Tv, settings.resolve(Configuration.UI_MODE_TYPE_TELEVISION))
        assertEquals(AppFormFactor.Phone, settings.resolve(Configuration.UI_MODE_TYPE_NORMAL))
    }

    @Test
    fun toggleUpdatesObserversImmediatelyAndCanSwitchBackOnATv() {
        val settings = OperationModeSettings(preferences)
        val uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES
        settings.toggle(uiMode)
        assertEquals(AppFormFactor.Phone, settings.modeOverride.value)
        assertEquals(AppFormFactor.Phone, settings.resolve(uiMode))
        settings.toggle(uiMode)
        assertEquals(AppFormFactor.Tv, settings.modeOverride.value)
    }

    @Test
    fun manualModeSurvivesRestartAndOverridesDeviceChanges() {
        OperationModeSettings(preferences).setMode(AppFormFactor.Tv)
        val restartedSettings = OperationModeSettings(preferences)
        assertEquals(AppFormFactor.Tv, restartedSettings.resolve(Configuration.UI_MODE_TYPE_NORMAL))
        restartedSettings.setMode(AppFormFactor.Phone)
        assertEquals(
            AppFormFactor.Phone,
            OperationModeSettings(preferences).resolve(Configuration.UI_MODE_TYPE_TELEVISION)
        )
    }

    @Test
    fun unknownStoredValueFallsBackToAutomaticDetection() {
        preferences.edit().putString("operation.mode", "unknown").commit()
        val settings = OperationModeSettings(preferences)
        assertNull(settings.modeOverride.value)
        assertEquals(AppFormFactor.Tv, settings.resolve(Configuration.UI_MODE_TYPE_TELEVISION))
    }
}
