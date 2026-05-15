package com.jing.ddys.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkFileNameTest {

    @Test
    fun keepsNormalApkAssetName() {
        assertEquals("ddys-v1.2.9.apk", ApkFileName.safeName("ddys-v1.2.9.apk"))
    }

    @Test
    fun stripsPathSegmentsFromAssetName() {
        assertEquals("evil.apk", ApkFileName.safeName("../evil.apk"))
        assertEquals("evil.apk", ApkFileName.safeName("nested\\evil.apk"))
    }

    @Test
    fun fallsBackWhenAssetNameIsNotAnApk() {
        assertEquals("ddys-update.apk", ApkFileName.safeName("source.zip"))
        assertEquals("ddys-update.apk", ApkFileName.safeName(""))
    }
}
