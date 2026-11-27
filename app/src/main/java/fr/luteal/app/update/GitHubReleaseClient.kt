package fr.luteal.app.update

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubReleaseClient(
    private val httpClient: OkHttpClient,
    private val latestReleaseUrl: String,
    private val userAgent: String
) {

    suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub returned HTTP ${response.code}")
            GitHubReleaseParser.parse(response.body.string())
        }
    }

    suspend fun download(url: String, destination: File, maxBytes: Long = MAX_APK_BYTES): Unit =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
            destination.parentFile?.mkdirs()
            val partial = File(destination.path + ".part")
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download returned HTTP ${response.code}")
                    val body = response.body
                    if (body.contentLength() > maxBytes) throw IOException("Download is larger than expected")
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) throw IOException("Download is larger than expected")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                }
            } finally {
                partial.delete()
            }
        }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/JoeriKaiser/luteal/releases/latest"
        const val MAX_APK_BYTES = 100L * 1024 * 1024
    }
}
