package com.zestyy.bytetrack.data.repository

import com.zestyy.bytetrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
)

/**
 * The "push updates without asking users to reinstall" mechanism, for an app that isn't on the
 * Play Store: since there's no Play Core in-app-update API available to us, we roll our own -
 * poll GitHub's Releases API for something newer than what's installed, and if there is, hand the
 * user a one-tap "Update" button instead of making them go find the APK themselves.
 *
 * This only reports an update as available if there's a signed release asset to point at - see
 * [UpdateRepository] doc below for why signing consistency is what actually makes this work.
 */
class UpdateRepository {

    companion object {
        // TODO: set this to your actual "owner/repo" GitHub slug before this does anything -
        // left as an explicit placeholder rather than guessed, since a wrong value just 404s
        // forever with no visible error (checkForUpdate() fails quiet on purpose, see below).
        private const val GITHUB_REPO = "YOUR_GITHUB_USERNAME/bytetrack"
        private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    /**
     * Returns [UpdateInfo] if the "latest" GitHub Release (see the CI workflow's rolling-release
     * step) is newer than [BuildConfig.VERSION_NAME], or null if there's nothing newer, no
     * network, the repo slug above isn't set correctly, or GitHub's API rate-limited us. Failing
     * quiet on every error path is deliberate - a background update check going wrong should
     * never surface as an error to the user, just "no update available right now."
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "") // e.g. "v0.2.0" or "latest"
            val remoteVersion = tagName.removePrefix("v")
            if (remoteVersion.isBlank() || !isNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                return@withContext null
            }

            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                // "release" (not "debug") is the build worth auto-updating end users to - it's
                // the one CI signs with the stable keystore (see app/build.gradle.kts).
                if (name.endsWith(".apk") && name.contains("release")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            val downloadUrl = apkUrl ?: return@withContext null

            UpdateInfo(
                versionName = remoteVersion,
                downloadUrl = downloadUrl,
                releaseNotes = json.optString("body", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Simple dotted-numeric comparison ("0.10.0" > "0.9.0") - not a full semver parser, but
     * this project's own versioning (see build.gradle.kts) is plain "major.minor.patch". */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
