package com.zestyy.bytetrack.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Downloads an update APK via the system [DownloadManager] - so the user gets a real progress
 * notification for free instead of us building our own - and hands the result to the system
 * package installer once it lands.
 *
 * Downloading into the public Downloads directory means [DownloadManager.getUriForDownloadedFile]
 * gives back an installable `content://` Uri directly - no custom FileProvider setup needed.
 *
 * WHAT THIS DOES AND DOESN'T DO:
 *  - It does NOT silently install in the background - only Play Store apps (via Play Core) or a
 *    device-owner/rooted setup can do that. This still shows Android's normal package-installer
 *    confirmation screen, and the very first time, an "Allow byte!track to install unknown apps"
 *    settings prompt too - that one-time permission is unavoidable for anything outside the Play
 *    Store.
 *  - What it DOES remove is the user having to leave the app, find the release on GitHub, open
 *    the download, and dig it out of a file manager. Tap "Update" -> download progresses in the
 *    notification shade -> tap the finished download -> confirm install. Same number of taps as
 *    updating a Play Store app that needs manual confirmation.
 *  - Whether the confirm screen says "Update" or "Install as new app" depends entirely on the
 *    signing key matching what's already installed - see app/build.gradle.kts's signingConfigs.
 */
class AppUpdater(private val context: Context) {

    fun downloadAndInstall(downloadUrl: String, versionName: String) {
        val fileName = "byte-track-$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("byte!track update")
            .setDescription("Downloading v$versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != downloadId) return
                context.unregisterReceiver(this)

                val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(installIntent)
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}
