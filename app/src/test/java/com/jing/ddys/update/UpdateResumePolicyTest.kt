package com.jing.ddys.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateResumePolicyTest {

    @Test
    fun resumesOnlyWhenWaitingForInstallPermission() {
        val update = UpdateInfo(
            release = UpdateRelease(
                tagName = "v1.2.9",
                releaseName = "v1.2.9",
                body = "",
                htmlUrl = "https://example.com",
                apkAsset = UpdateAsset(
                    name = "ddys-v1.2.9.apk",
                    downloadUrl = "https://example.com/ddys-v1.2.9.apk",
                    size = 1,
                    sha256 = null
                )
            ),
            versionName = "1.2.9"
        )
        val apk = File("ddys-update.apk")

        assertTrue(UpdateResumePolicy.shouldResumeInstall(UpdateState.Ready(update, apk, true)))
        assertFalse(UpdateResumePolicy.shouldResumeInstall(UpdateState.Ready(update, apk, false)))
        assertFalse(UpdateResumePolicy.shouldResumeInstall(UpdateState.InstallStarted(update, apk)))
    }
}
