package com.jing.ddys.compose.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.DdysApplication
import com.jing.ddys.R
import com.jing.ddys.compose.AppFormFactor
import com.jing.ddys.compose.appFormFactorFromUiMode
import com.jing.ddys.compose.rememberAppFormFactor

@Composable
fun operationModeName(mode: AppFormFactor): String = stringResource(
    if (mode == AppFormFactor.Tv) R.string.operation_mode_tv else R.string.operation_mode_phone
)

@Composable
fun operationModeSwitchText(mode: AppFormFactor): String = stringResource(
    if (mode == AppFormFactor.Tv) R.string.operation_mode_switch_to_phone else R.string.operation_mode_switch_to_tv
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OperationModeButton(modifier: Modifier = Modifier, tint: Color? = null) {
    val mode = rememberAppFormFactor()
    val configuration = LocalConfiguration.current
    val focusRequester = remember { FocusRequester() }
    val label = operationModeSwitchText(mode)
    val icon = if (mode == AppFormFactor.Tv) Icons.Default.Smartphone else Icons.Default.Tv
    val onClick = { DdysApplication.context.operationModeSettings.toggle(configuration.uiMode) }
    val buttonModifier = modifier.focusRequester(focusRequester)
    if (mode == AppFormFactor.Tv) {
        // TV Material handles D-pad input only; touch devices still need a way back.
        androidx.tv.material3.IconButton(
            onClick = onClick,
            modifier = buttonModifier.pointerInput(onClick) { detectTapGestures { onClick() } }
        ) {
            androidx.tv.material3.Icon(
                icon, contentDescription = label,
                tint = tint ?: androidx.tv.material3.LocalContentColor.current
            )
        }
    } else {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = buttonModifier) {
            androidx.compose.material3.Icon(
                icon, contentDescription = label,
                tint = tint ?: androidx.compose.material3.LocalContentColor.current
            )
        }
    }
    LaunchedEffect(mode) {
        // Keep the way back to TV controls reachable with a remote on a physical TV.
        if (mode == AppFormFactor.Phone &&
            appFormFactorFromUiMode(configuration.uiMode) == AppFormFactor.Tv
        ) {
            focusRequester.requestFocus()
        }
    }
}
