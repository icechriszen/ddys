package com.jing.ddys.setting

import android.content.SharedPreferences
import com.jing.ddys.compose.AppFormFactor
import com.jing.ddys.compose.appFormFactorFromUiMode
import com.jing.ddys.compose.opposite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OperationModeSettings(private val preferences: SharedPreferences) {
    private val _override = MutableStateFlow(
        when (preferences.getString(KEY, null)) {
            "tv" -> AppFormFactor.Tv
            "phone" -> AppFormFactor.Phone
            else -> null
        }
    )
    val modeOverride: StateFlow<AppFormFactor?> = _override.asStateFlow()

    fun resolve(uiMode: Int): AppFormFactor = _override.value ?: appFormFactorFromUiMode(uiMode)

    fun setMode(mode: AppFormFactor) {
        preferences.edit().putString(KEY, if (mode == AppFormFactor.Tv) "tv" else "phone").apply()
        _override.value = mode
    }

    fun toggle(uiMode: Int) = setMode(resolve(uiMode).opposite())

    private companion object {
        const val KEY = "operation.mode"
    }
}
