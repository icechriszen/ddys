package com.jing.ddys.compose

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import com.jing.ddys.DdysApplication

enum class AppFormFactor {
    Tv,
    Phone
}

fun AppFormFactor.opposite(): AppFormFactor = if (this == AppFormFactor.Tv) {
    AppFormFactor.Phone
} else {
    AppFormFactor.Tv
}

fun appFormFactorFromUiMode(uiMode: Int): AppFormFactor {
    val uiModeType = uiMode and Configuration.UI_MODE_TYPE_MASK
    return if (uiModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        AppFormFactor.Tv
    } else {
        AppFormFactor.Phone
    }
}

@Composable
fun rememberAppFormFactor(): AppFormFactor {
    val configuration = LocalConfiguration.current
    val modeOverride by DdysApplication.context.operationModeSettings.modeOverride.collectAsState()
    return modeOverride ?: appFormFactorFromUiMode(configuration.uiMode)
}
