package com.jing.ddys.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateRepository(
    private val client: OkHttpClient = UpdateHttpClientFactory.build()
) {

    suspend fun fetchLatestRelease(): UpdateFetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                parseResponse(response.code, response.body?.string().orEmpty())
            }
        }.getOrElse {
            UpdateFetchResult.Failure(it.message ?: "检查更新失败")
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL =
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
