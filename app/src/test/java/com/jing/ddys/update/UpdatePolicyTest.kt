package com.jing.ddys.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun acceptsVPrefixedNewerVersion() {
        assertTrue(UpdatePolicy.isNewerVersion("v1.2.9", "1.2.8"))
    }

    @Test
    fun acceptsPlainNewerVersion() {
        assertTrue(UpdatePolicy.isNewerVersion("1.3.0", "1.2.8"))
    }

    @Test
    fun rejectsSameOrOlderVersion() {
        assertFalse(UpdatePolicy.isNewerVersion("v1.2.8", "1.2.8"))
        assertFalse(UpdatePolicy.isNewerVersion("v1.2.7", "1.2.8"))
    }

    @Test
    fun rejectsInvalidTags() {
        assertFalse(UpdatePolicy.isNewerVersion("nightly", "1.2.8"))
        assertFalse(UpdatePolicy.isNewerVersion("", "1.2.8"))
    }

    @Test
    fun validatesDownloadedApkPackageAndVersion() {
        assertEquals(
            ApkValidationResult.Valid,
            ApkValidationPolicy.validate(
                packageName = "com.jing.ddys",
                versionCode = 13,
                expectedPackageName = "com.jing.ddys",
                currentVersionCode = 12
            )
        )
    }

    @Test
    fun rejectsDownloadedApkWithWrongPackage() {
        assertEquals(
            ApkValidationResult.PackageMismatch,
            ApkValidationPolicy.validate(
                packageName = "com.example.other",
                versionCode = 13,
                expectedPackageName = "com.jing.ddys",
                currentVersionCode = 12
            )
        )
    }

    @Test
    fun rejectsDownloadedApkWithoutHigherVersionCode() {
        assertEquals(
            ApkValidationResult.VersionNotNewer,
            ApkValidationPolicy.validate(
                packageName = "com.jing.ddys",
                versionCode = 12,
                expectedPackageName = "com.jing.ddys",
                currentVersionCode = 12
            )
        )
    }
}
