package com.jing.ddys.update

object ApkFileName {
    private const val FALLBACK_NAME = "ddys-update.apk"

    fun safeName(assetName: String): String {
        val baseName = assetName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?.takeIf { it.isNotBlank() }
            ?: return FALLBACK_NAME
        return baseName
    }
}
