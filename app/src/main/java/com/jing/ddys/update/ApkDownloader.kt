package com.jing.ddys.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ApkDownloader(
    context: Context,
    private val client: OkHttpClient = UpdateHttpClientFactory.build()
) {

    private val updatesDir = File(context.cacheDir, "updates")

    suspend fun download(
        update: UpdateInfo,
        onProgress: suspend (Int?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        updatesDir.mkdirs()
        updatesDir.listFiles()
            ?.filter { it.extension.equals("apk", ignoreCase = true) }
            ?.forEach { it.delete() }

        val targetFile = File(updatesDir, ApkFileName.safeName(update.release.apkAsset.name))
        val canonicalUpdatesDir = updatesDir.canonicalFile
        val canonicalTargetFile = targetFile.canonicalFile
        if (!canonicalTargetFile.path.startsWith(canonicalUpdatesDir.path + File.separator)) {
            throw RuntimeException("安装包文件名无效")
        }
        val request = Request.Builder().url(update.release.apkAsset.downloadUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("下载失败: HTTP ${response.code}")
            }
            val body = response.body ?: throw RuntimeException("下载失败: 响应为空")
            val total = body.contentLength().takeIf { it > 0 } ?: update.release.apkAsset.size
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastProgress: Int? = null
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (total > 0) {
                            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
        }

        if (!Sha256Verifier.matches(targetFile, update.release.apkAsset.sha256)) {
            targetFile.delete()
            throw RuntimeException("下载文件校验失败")
        }
        targetFile
    }
}
