package com.jing.ddys.setting

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jing.ddys.R
import com.jing.ddys.update.UpdateState
import com.jing.ddys.update.UpdateViewModel

@Composable
fun PhoneSettingsScreen(viewModel: SettingsViewModel, updateViewModel: UpdateViewModel) {
    val proxySettings by viewModel.networkProxySettings.collectAsState()
    val sourceLoggedIn by viewModel.sourceLoggedIn.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var showProxySettingsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
        item {
            PhoneSettingsItem(
                title = stringResource(R.string.video_source_login_title),
                supportText = if (sourceLoggedIn) {
                    stringResource(R.string.video_source_login_ready)
                } else {
                    stringResource(R.string.video_source_login_required)
                }
            ) {
                context.startActivity(Intent(context, VideoSourceLoginActivity::class.java))
            }
        }
        item {
            PhoneSettingsItem(
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
        }
        item {
            val proxyText = if (proxySettings.proxyEnabled) {
                "${proxySettings.proxyHost}:${proxySettings.proxyPort}"
            } else {
                stringResource(R.string.network_proxy_setting_none)
            }
            PhoneSettingsItem(
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showProxySettingsDialog) {
        PhoneProxySettingsDialog(proxySettings = proxySettings) { newSettings ->
            showProxySettingsDialog = false
            if (newSettings != null) {
                viewModel.applySetting(newSettings)
            }
        }
    }
}

@Composable
private fun PhoneSettingsItem(title: String, supportText: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = supportText) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
    Divider()
}

@Composable
private fun PhoneProxySettingsDialog(
    proxySettings: NetworkProxySettings,
    onDialogClose: (newSettings: NetworkProxySettings?) -> Unit
) {
    var host by remember { mutableStateOf(proxySettings.proxyHost) }
    var port by remember { mutableStateOf(proxySettings.proxyPort.toString()) }
    val hostError = host.isEmpty()
    val portError = !isValidPort(port)

    AlertDialog(
        onDismissRequest = { onDialogClose(null) },
        title = { Text(text = stringResource(id = R.string.network_proxy_setting_title)) },
        confirmButton = {
            Button(
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
                }
            ) {
                Text(text = stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDialogClose(null) }) {
                Text(text = "取消")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    label = { Text(text = stringResource(R.string.network_proxy_setting_host_title)) },
                    singleLine = true,
                    isError = hostError,
                    onValueChange = { host = it.trim() }
                )
                OutlinedTextField(
                    value = port,
                    label = { Text(text = stringResource(R.string.network_proxy_setting_port_title)) },
                    singleLine = true,
                    isError = portError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = { port = it.trim() }
                )
            }
        }
    )
}
