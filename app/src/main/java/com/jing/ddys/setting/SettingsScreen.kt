package com.jing.ddys.setting

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.ddys.BuildConfig
import com.jing.ddys.R
import com.jing.ddys.compose.AppFormFactor
import com.jing.ddys.compose.rememberAppFormFactor
import com.jing.ddys.update.UpdateState
import com.jing.ddys.update.UpdateViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, updateViewModel: UpdateViewModel) {
    if (rememberAppFormFactor() == AppFormFactor.Phone) {
        PhoneSettingsScreen(viewModel = viewModel, updateViewModel = updateViewModel)
        return
    }

    val proxySettings by viewModel.networkProxySettings.collectAsState()
    val sourceLoggedIn by viewModel.sourceLoggedIn.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val defaultFocusRequester = remember {
        FocusRequester()
    }
    var showProxySettingsDialog by remember {
        mutableStateOf(false)
    }
    Box(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxSize()
    ) {

        TvLazyColumn(content = {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            item {
                Column {
                    SettingsItem(
                        modifier = Modifier.focusRequester(defaultFocusRequester),
                        title = stringResource(R.string.video_source_login_title),
                        supportText = if (sourceLoggedIn) {
                            stringResource(R.string.video_source_login_ready)
                        } else {
                            stringResource(R.string.video_source_login_required)
                        }
                    ) {
                        context.startActivity(
                            android.content.Intent(context, VideoSourceLoginActivity::class.java)
                        )
                    }
                    SettingsItem(
                        title = stringResource(R.string.software_update_title),
                        supportText = softwareUpdateSupportText(updateState)
                    ) {
                        if (activity != null) {
                            when (updateState) {
                                is UpdateState.Available,
                                is UpdateState.Ready -> updateViewModel.downloadAndInstall(activity)

                                is UpdateState.InstallStarted,
                                is UpdateState.Downloading,
                                UpdateState.Checking -> Unit

                                is UpdateState.Error,
                                UpdateState.Idle,
                                UpdateState.NoRelease,
                                UpdateState.UpToDate -> updateViewModel.checkNow()
                            }
                        }
                    }
                    val proxyText = if (proxySettings.proxyEnabled) {
                        "${proxySettings.proxyHost}:${proxySettings.proxyPort}"
                    } else {
                        stringResource(R.string.network_proxy_setting_none)
                    }
                    SettingsItem(
                        title = stringResource(R.string.network_proxy_setting_title),
                        supportText = proxyText
                    ) {
                        if (proxySettings.proxyEnabled) {
                            viewModel.applySetting(proxySettings.copy(proxyEnabled = false))
                        } else {
                            showProxySettingsDialog = true
                        }
                    }
                }
            }
        })
        LaunchedEffect(Unit) {
            defaultFocusRequester.requestFocus()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSourceAuthState()
                if (activity != null) {
                    updateViewModel.resumePendingInstall(activity)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    if (showProxySettingsDialog) {
        ProxySettingsDialog(proxySettings = proxySettings) { newSettings ->
            showProxySettingsDialog = false
            if (newSettings != null) {
                viewModel.applySetting(newSettings)
            }
        }
    }
}

@Composable
internal fun softwareUpdateSupportText(updateState: UpdateState): String {
    return when (updateState) {
        UpdateState.Idle -> stringResource(
            R.string.software_update_idle,
            BuildConfig.VERSION_NAME
        )

        UpdateState.Checking -> stringResource(R.string.software_update_checking)
        UpdateState.UpToDate -> stringResource(
            R.string.software_update_up_to_date,
            BuildConfig.VERSION_NAME
        )

        UpdateState.NoRelease -> stringResource(
            R.string.software_update_no_release,
            BuildConfig.VERSION_NAME
        )

        is UpdateState.Available -> stringResource(
            R.string.software_update_available,
            updateState.update.versionName
        )

        is UpdateState.Downloading -> {
            val progress = updateState.progress
            if (progress == null) {
                stringResource(R.string.software_update_downloading_unknown)
            } else {
                stringResource(R.string.software_update_downloading, progress)
            }
        }

        is UpdateState.Ready -> if (updateState.permissionRequired) {
            stringResource(R.string.software_update_permission_required)
        } else {
            stringResource(R.string.software_update_ready, updateState.update.versionName)
        }

        is UpdateState.InstallStarted -> stringResource(
            R.string.software_update_install_started,
            updateState.update.versionName
        )

        is UpdateState.Error -> stringResource(
            R.string.software_update_error,
            updateState.message
        )
    }
}

internal fun isValidPort(port: String): Boolean {

    if (port.isEmpty()) {
        return false
    }
    val num = runCatching { port.toInt() }.getOrNull()
    return num != null && num >= 1 && num <= 65535
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProxySettingsDialog(
    modifier: Modifier = Modifier,
    proxySettings: NetworkProxySettings,
    onDialogClose: (newSettings: NetworkProxySettings?) -> Unit
) {
    var host by remember {
        mutableStateOf(proxySettings.proxyHost)
    }
    var port by remember {
        mutableStateOf(proxySettings.proxyPort.toString())
    }
    val focusRequesterList = remember {
        List(3) { FocusRequester() }
    }
    val hostError by remember(host) {
        mutableStateOf(host.isEmpty())
    }
    val portError by remember(port) {
        mutableStateOf(!isValidPort(port))
    }
    AlertDialog(modifier = modifier,
        onDismissRequest = { onDialogClose(null) },
        title = { Text(text = stringResource(id = R.string.network_proxy_setting_title)) },
        confirmButton = {
            Button(modifier = Modifier.focusRequester(focusRequester = focusRequesterList[2]),
                enabled = !hostError && !portError,
                onClick = {
                    val portNum = runCatching { port.toInt() }.getOrNull()
                    if (!hostError && !portError && portNum != null) {
                        onDialogClose(
                            NetworkProxySettings(
                                proxyEnabled = true,
                                proxyHost = host,
                                proxyPort = portNum
                            )
                        )
                    }
                }) {
                Text(text = stringResource(R.string.button_save))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(modifier = Modifier.focusRequester(focusRequester = focusRequesterList[0]),
                    value = host,
                    label = { Text(text = stringResource(R.string.network_proxy_setting_host_title)) },
                    singleLine = true,
                    isError = hostError,
                    onValueChange = {
                        host = it.trim()
                    })
                OutlinedTextField(modifier = Modifier.focusRequester(focusRequester = focusRequesterList[1]),
                    value = port,
                    label = { Text(text = stringResource(R.string.network_proxy_setting_port_title)) },
                    singleLine = true,
                    isError = portError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = {
                        port = it.trim()
                    })
            }
        })
    LaunchedEffect(Unit) {
        val defaultFocusRequesterIndex = if (host.isEmpty()) {
            0
        } else if (port.isEmpty()) {
            1
        } else {
            2
        }
        focusRequesterList[defaultFocusRequesterIndex].requestFocus()
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsItem(
    modifier: Modifier = Modifier, title: String, supportText: String, onClick: () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }
    ListItem(modifier = modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        selected = focused,
        onClick = onClick,
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = supportText) })

}
