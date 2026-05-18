package com.jing.ddys.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateRepository(
    private val client: OkHttpClient = UpdateHttpClientFactory.build()
) {

    suspend fun fetchLatestRelease(): UpdateFetchResult = withContext(Dispatchers.IO) {
        val manifestResult = fetchRelease(UPDATE_MANIFEST_URL)
        if (manifestResult is UpdateFetchResult.Found) {
            manifestResult
        } else {
            val apiResult = fetchRelease(RELEASES_API_URL)
            when {
                apiResult is UpdateFetchResult.Found -> apiResult
                manifestResult is UpdateFetchResult.Failure && apiResult is UpdateFetchResult.NoRelease -> manifestResult
                else -> apiResult
            }
        }
    }

    private fun fetchRelease(url: String): UpdateFetchResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                parseResponse(response.code, response.body?.string().orEmpty())
            }
        }.getOrElse {
            UpdateFetchResult.Failure(it.message ?: "检查更新失败")
        }
    }

    companion object {
        private const val UPDATE_MANIFEST_URL =
            "https://github.com/icechriszen/ddys/releases/latest/download/update.json"
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/icechriszen/ddys/releases?per_page=10"

        fun parseResponse(code: Int, body: String): UpdateFetchResult {
            if (code == 404) {
                return UpdateFetchResult.NoRelease
            }
            if (code !in 200..299) {
                return UpdateFetchResult.Failure("检查更新失败: HTTP $code")
            }
            val release = GithubReleaseParser.parseFirstWithApk(body)
                ?: return UpdateFetchResult.NoRelease
            return UpdateFetchResult.Found(release)
        }
    }
}
