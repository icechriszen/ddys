package com.jing.ddys.compose

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class AppFormFactor {
    Tv,
    Phone
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
    return appFormFactorFromUiMode(configuration.uiMode)
}
