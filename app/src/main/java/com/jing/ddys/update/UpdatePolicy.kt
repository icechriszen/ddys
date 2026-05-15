package com.jing.ddys.update

object UpdatePolicy {

    fun isNewerVersion(latestTag: String, currentVersionName: String): Boolean {
        val latest = parseVersion(latestTag) ?: return false
        val current = parseVersion(currentVersionName) ?: return false
        val maxSize = maxOf(latest.size, current.size)
        for (index in 0 until maxSize) {
            val latestPart = latest.getOrElse(index) { 0 }
            val currentPart = current.getOrElse(index) { 0 }
            if (latestPart > currentPart) {
                return true
            }
            if (latestPart < currentPart) {
                return false
            }
        }
        return false
    }

    fun normalizedVersionName(tagName: String): String =
        tagName.trim().removePrefix("v").removePrefix("V")

    private fun parseVersion(value: String): List<Int>? {
        val normalized = normalizedVersionName(value)
        if (normalized.isBlank()) {
            return null
        }
        val parts = normalized.split('.')
        if (parts.any { it.isBlank() || it.any { ch -> !ch.isDigit() } }) {
            return null
        }
        return parts.map { it.toInt() }
    }
}

object ApkValidationPolicy {
    fun validate(
        packageName: String?,
        versionCode: Long,
        expectedPackageName: String,
        currentVersionCode: Long
    ): ApkValidationResult {
        if (packageName.isNullOrBlank()) {
            return ApkValidationResult.UnreadablePackage
        }
        if (packageName != expectedPackageName) {
            return ApkValidationResult.PackageMismatch
        }
        if (versionCode <= currentVersionCode) {
            return ApkValidationResult.VersionNotNewer
        }
        return ApkValidationResult.Valid
    }
}
