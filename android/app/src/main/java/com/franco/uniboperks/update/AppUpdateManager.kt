package com.franco.uniboperks.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.franco.uniboperks.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val errorMessage: String? = null
)

object AppUpdateManager {
    private const val GITHUB_REPO = "Martiri/unibo-perks"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_REPO/releases"

    suspend fun checkForUpdates(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "UniBoPerks-App")
                connectTimeout = 6000
                readTimeout = 6000
            }

            val code = conn.responseCode
            if (code == 404) {
                return@withContext UpdateInfo(
                    hasUpdate = false,
                    latestVersion = currentVersion,
                    releaseNotes = "",
                    apkDownloadUrl = null,
                    htmlUrl = RELEASES_PAGE_URL,
                    errorMessage = null
                )
            } else if (code !in 200..299) {
                return@withContext UpdateInfo(
                    hasUpdate = false,
                    latestVersion = currentVersion,
                    releaseNotes = "",
                    apkDownloadUrl = null,
                    htmlUrl = RELEASES_PAGE_URL,
                    errorMessage = "Errore di connessione GitHub (HTTP $code)"
                )
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val rawTag = json.optString("tag_name", "").trim()
            val cleanTag = rawTag.removePrefix("v").trim()
            val bodyNotes = json.optString("body", "Nuovo aggiornamento disponibile con sconti aggiornati.")
            val htmlUrl = json.optString("html_url", RELEASES_PAGE_URL)

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(remote = cleanTag, current = currentVersion)
            UpdateInfo(
                hasUpdate = isNewer,
                latestVersion = "v$cleanTag",
                releaseNotes = bodyNotes,
                apkDownloadUrl = apkUrl,
                htmlUrl = htmlUrl
            )
        } catch (e: Exception) {
            UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersion,
                releaseNotes = "",
                apkDownloadUrl = null,
                htmlUrl = RELEASES_PAGE_URL,
                errorMessage = "Impossibile verificare: ${e.localizedMessage ?: "Nessuna connessione"}"
            )
        }
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        val rClean = remote.removePrefix("v").trim()
        val cClean = current.removePrefix("v").trim()
        val rParts = rClean.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = cClean.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun openBrowserReleasePage(context: Context, url: String = RELEASES_PAGE_URL) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
