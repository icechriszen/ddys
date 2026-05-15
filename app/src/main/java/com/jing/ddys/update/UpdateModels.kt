package com.jing.ddys.update

import java.io.File

data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String?
)

data class UpdateRelease(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val apkAsset: UpdateAsset
)

data class UpdateInfo(
    val release: UpdateRelease,
    val versionName: String
)

sealed interface UpdateFetchResult {
    data class Found(val release: UpdateRelease) : UpdateFetchResult
    data object NoRelease : UpdateFetchResult
    data class Failure(val message: String) : UpdateFetchResult
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NoRelease : UpdateState
    data class Available(val update: UpdateInfo) : UpdateState
    data class Downloading(val update: UpdateInfo, val progress: Int?) : UpdateState
    data class Ready(
        val update: UpdateInfo,
        val apkFile: File,
        val permissionRequired: Boolean = false
    ) : UpdateState

    data class InstallStarted(
        val update: UpdateInfo,
        val apkFile: File
    ) : UpdateState

    data class Error(val message: String) : UpdateState
}

sealed interface ApkValidationResult {
    data object Valid : ApkValidationResult
    data object PackageMismatch : ApkValidationResult
    data object VersionNotNewer : ApkValidationResult
    data object UnreadablePackage : ApkValidationResult
}

sealed interface InstallLaunchResult {
    data object Started : InstallLaunchResult
    data object PermissionRequired : InstallLaunchResult
    data class InvalidApk(val validation: ApkValidationResult) : InstallLaunchResult
    data class Failed(val message: String) : InstallLaunchResult
}
