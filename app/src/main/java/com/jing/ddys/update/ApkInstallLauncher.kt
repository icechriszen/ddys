package com.jing.ddys.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jing.ddys.BuildConfig
import java.io.File

class ApkInstallLauncher {

    fun canRequestPackageInstalls(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
    }

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    fun launchInstall(activity: Activity, apkFile: File): InstallLaunchResult {
        val validation = validate(activity, apkFile)
        if (validation != ApkValidationResult.Valid) {
            return InstallLaunchResult.InvalidApk(validation)
        }
        if (!canRequestPackageInstalls(activity)) {
            requestInstallPermission(activity)
            return InstallLaunchResult.PermissionRequired
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                activity,
                "${BuildConfig.APPLICATION_ID}.updatefileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            activity.startActivity(intent)
            InstallLaunchResult.Started
        }.getOrElse {
            InstallLaunchResult.Failed(it.message ?: "无法打开系统安装器")
        }
    }

    fun validate(context: Context, apkFile: File): ApkValidationResult {
        val info = readPackageInfo(context, apkFile)
            ?: return ApkValidationResult.UnreadablePackage
        return ApkValidationPolicy.validate(
            packageName = info.packageName,
            versionCode = info.longVersionCodeCompat(),
            expectedPackageName = BuildConfig.APPLICATION_ID,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        )
    }

    @Suppress("DEPRECATION")
    private fun readPackageInfo(context: Context, apkFile: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
