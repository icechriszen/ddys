package com.jing.ddys.update

import android.app.Activity
import android.content.Context
import com.jing.ddys.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

class UpdateManager(
    context: Context,
    private val repository: UpdateRepository,
    private val downloader: ApkDownloader,
    private val installer: ApkInstallLauncher
) {

    private val sharedPreferences =
        context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    val state: StateFlow<UpdateState>
        get() = _state

    suspend fun checkDaily() {
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        if (sharedPreferences.getLong(LAST_CHECK_DAY_KEY, -1L) == today) {
            return
        }
        checkNow(markDailyCheck = true)
    }

    suspend fun checkNow(markDailyCheck: Boolean = false) {
        _state.emit(UpdateState.Checking)
        when (val result = repository.fetchLatestRelease()) {
            is UpdateFetchResult.Found -> {
                if (UpdatePolicy.isNewerVersion(
                        result.release.tagName,
                        BuildConfig.VERSION_NAME
                    )
                ) {
                    _state.emit(
                        UpdateState.Available(
                            UpdateInfo(
                                release = result.release,
                                versionName = UpdatePolicy.normalizedVersionName(result.release.tagName)
                            )
                        )
                    )
                } else {
                    _state.emit(UpdateState.UpToDate)
                }
                if (markDailyCheck) {
                    markCheckedToday()
                }
            }

            UpdateFetchResult.NoRelease -> {
                _state.emit(UpdateState.NoRelease)
                if (markDailyCheck) {
                    markCheckedToday()
                }
            }

            is UpdateFetchResult.Failure -> {
                _state.emit(UpdateState.Error(result.message))
            }
        }
    }

    suspend fun downloadAndInstall(activity: Activity) {
        when (val current = _state.value) {
            is UpdateState.Available -> downloadThenInstall(activity, current.update)
            is UpdateState.Ready -> install(activity, current.update, current.apkFile)
            is UpdateState.InstallStarted -> Unit
            is UpdateState.Error -> checkNow()
            UpdateState.Idle, UpdateState.NoRelease, UpdateState.UpToDate -> checkNow()
            is UpdateState.Checking, is UpdateState.Downloading -> Unit
        }
    }

    suspend fun resumePendingInstall(activity: Activity) {
        val current = _state.value
        if (UpdateResumePolicy.shouldResumeInstall(current) &&
            current is UpdateState.Ready &&
            installer.canRequestPackageInstalls(activity)
        ) {
            install(activity, current.update, current.apkFile)
        }
    }

    private suspend fun downloadThenInstall(activity: Activity, update: UpdateInfo) {
        _state.emit(UpdateState.Downloading(update, 0))
        runCatching {
            downloader.download(update) { progress ->
                _state.emit(UpdateState.Downloading(update, progress))
            }
        }.onSuccess { apkFile ->
            install(activity, update, apkFile)
        }.onFailure {
            _state.emit(UpdateState.Error(it.message ?: "下载更新失败"))
        }
    }

    private suspend fun install(activity: Activity, update: UpdateInfo, apkFile: java.io.File) {
        when (val result = installer.launchInstall(activity, apkFile)) {
            InstallLaunchResult.Started -> _state.emit(UpdateState.InstallStarted(update, apkFile))
            InstallLaunchResult.PermissionRequired ->
                _state.emit(UpdateState.Ready(update, apkFile, permissionRequired = true))

            is InstallLaunchResult.InvalidApk ->
                _state.emit(UpdateState.Error(result.validation.toMessage()))

            is InstallLaunchResult.Failed ->
                _state.emit(UpdateState.Error(result.message))
        }
    }

    private fun markCheckedToday() {
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        sharedPreferences.edit().putLong(LAST_CHECK_DAY_KEY, today).apply()
    }

    private fun ApkValidationResult.toMessage(): String = when (this) {
        ApkValidationResult.PackageMismatch -> "安装包不是当前应用"
        ApkValidationResult.VersionNotNewer -> "安装包版本不高于当前版本"
        ApkValidationResult.UnreadablePackage -> "无法读取安装包信息"
        ApkValidationResult.Valid -> "安装包可用"
    }

    companion object {
        private const val LAST_CHECK_DAY_KEY = "last_check_day"
    }
}
