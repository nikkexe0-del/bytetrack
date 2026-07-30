package com.zestyy.bytetrack.data.repository

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.zestyy.bytetrack.data.local.AppDatabase
import com.zestyy.bytetrack.data.local.DataUsageSample
import com.zestyy.bytetrack.data.local.NetworkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads per-UID network usage from [NetworkStatsManager], the same system API Android's own
 * "Data usage" settings screen is built on. Requires the user to grant the special
 * PACKAGE_USAGE_STATS app-op (see PermissionsRepository) — there's no runtime permission dialog
 * for this, it's a manual toggle in Settings > Special app access > Usage access.
 *
 * IMPORTANT HONEST LIMITATION: Android does not expose a per-connected-device breakdown of who
 * used your hotspot - we can't tell you "your laptop used 400MB, your friend's phone used 100MB".
 * What we *can* track accurately, straight from NetworkStatsManager itself, is the split between
 * (a) bytes your own apps used over Wi-Fi/cellular, and (b) bytes forwarded out through tethering
 * to whatever's connected to your hotspot - Android buckets that second category under its own
 * special uid ([TETHERING_UID]), completely separate from any app's uid, so we don't have to
 * guess based on "is tethering on right now" (that guess is what used to mislabel your own apps'
 * ordinary mobile usage as "Hotspot" whenever tethering happened to also be on).
 */
class NetworkUsageRepository(private val context: Context) {

    companion object {
        // android.net.NetworkStats.UID_TETHERING - the special uid NetworkStatsManager buckets
        // tethered/hotspot-forwarded traffic under. Hardcoded rather than referencing the SDK
        // constant directly since it wasn't public API until API 29 and minSdk here is 26; the
        // value itself (-5) is stable across every Android version that has tethering stats.
        const val TETHERING_UID = -5

        // Pseudo "packageName" used for rows representing data forwarded through this device's
        // hotspot to another device - it's not attributable to any app on THIS device, so it
        // doesn't get a real package name. AppInfoRepository.label() special-cases this constant
        // to show a human label instead of trying (and failing) to resolve it via PackageManager.
        const val HOTSPOT_SHARED_PSEUDO_PACKAGE = "zestyy.bytetrack.hotspot_shared"
    }

    private val statsManager by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    }
    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    private val db by lazy { AppDatabase.get(context) }

    private fun subscriberId(): String? = try {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) telephonyManager.subscriberId else null
    } catch (_: SecurityException) {
        null
    }

    /**
     * Polls usage strictly within [sinceMillis, nowMillis) for both Wi-Fi and mobile transports
     * and persists it as new rows. This method does NOT dedupe against existing rows - it trusts
     * the caller to pass a [sinceMillis, nowMillis) window that hasn't been polled before. Every
     * caller (UsageTrackingService, SyncWorker) gets that window from PollScheduleStore.claimWindow,
     * which is the single source of truth for "what's already been recorded" - see its doc comment
     * for why that's what actually prevents double counting, not this method.
     */
    suspend fun pollAndStore(sinceMillis: Long, nowMillis: Long) = withContext(Dispatchers.IO) {
        val samples = mutableListOf<DataUsageSample>()
        samples += queryTransport(ConnectivityManager.TYPE_WIFI, sinceMillis, nowMillis, NetworkType.WIFI)
        samples += queryMobileTransport(sinceMillis, nowMillis)

        if (samples.isNotEmpty()) {
            db.dataUsageDao().insertAll(samples)
        }
    }

    private fun queryTransport(
        connectivityType: Int,
        since: Long,
        now: Long,
        tagAs: NetworkType,
    ): List<DataUsageSample> {
        val result = mutableListOf<DataUsageSample>()
        try {
            val bucket = NetworkStats.Bucket()
            val stats: NetworkStats = statsManager.querySummary(connectivityType, subscriberId(), since, now)
            stats.use {
                while (it.hasNextBucket()) {
                    it.getNextBucket(bucket)
                    // uid < 0 covers removed/tombstoned apps and OS aggregate buckets - skip for per-app view
                    if (bucket.uid < 0) continue
                    val pkg = context.packageManager.getPackagesForUid(bucket.uid)?.firstOrNull()
                        ?: "uid:${bucket.uid}"
                    if (bucket.rxBytes == 0L && bucket.txBytes == 0L) continue
                    result += DataUsageSample(
                        packageName = pkg,
                        networkType = tagAs,
                        rxBytes = bucket.rxBytes,
                        txBytes = bucket.txBytes,
                        periodStart = since,
                        periodEnd = now,
                    )
                }
            }
        } catch (e: SecurityException) {
            // Usage-access permission not granted yet - caller should route the user to the
            // permission screen; we swallow here so a missed poll doesn't crash the service.
        } catch (e: Exception) {
            // NetworkStatsManager can throw RemoteException-wrapped errors transiently on some
            // OEM skins; skip this poll cycle and try again next interval.
        }
        return result
    }

    /**
     * THE BUG THIS FIXES ("why is hotspot counting apps on mobile"): the old code tagged EVERY
     * mobile-transport byte in a poll window as HOTSPOT the instant [isTetheringActive] was true
     * - including your own apps' ordinary mobile usage happening at the same time. Turn on your
     * hotspot to share internet with a laptop, then scroll Instagram on your own phone over the
     * same cellular connection, and Instagram's bytes got relabeled "Hotspot" even though nothing
     * left the device through the hotspot for that app - that's the exact "why is it counting on
     * apps on mobile" behavior being reported.
     *
     * The real per-app/per-shared split is IN the data Android already tracks: traffic actually
     * forwarded through tethering is bucketed under the special uid [TETHERING_UID], separate
     * from every real app's own uid. So instead of relabeling based on our own
     * "is tethering on right now" guess, we split by what NetworkStatsManager itself says:
     *  - bucket.uid == TETHERING_UID -> genuinely shared-out data, stored as its own pseudo-row
     *    tagged HOTSPOT (see [HOTSPOT_SHARED_PSEUDO_PACKAGE]).
     *  - any real app uid -> always tagged MOBILE, regardless of whether tethering happens to be
     *    on, because it's still that app using your phone's own cellular connection.
     */
    private fun queryMobileTransport(since: Long, now: Long): List<DataUsageSample> {
        val result = mutableListOf<DataUsageSample>()
        try {
            val bucket = NetworkStats.Bucket()
            val stats: NetworkStats = statsManager.querySummary(ConnectivityManager.TYPE_MOBILE, subscriberId(), since, now)
            stats.use {
                while (it.hasNextBucket()) {
                    it.getNextBucket(bucket)
                    if (bucket.rxBytes == 0L && bucket.txBytes == 0L) continue
                    when {
                        bucket.uid == TETHERING_UID -> {
                            result += DataUsageSample(
                                packageName = HOTSPOT_SHARED_PSEUDO_PACKAGE,
                                networkType = NetworkType.HOTSPOT,
                                rxBytes = bucket.rxBytes,
                                txBytes = bucket.txBytes,
                                periodStart = since,
                                periodEnd = now,
                            )
                        }
                        bucket.uid < 0 -> {
                            // Other OS aggregate/tombstoned buckets - not a real app, skip.
                        }
                        else -> {
                            val pkg = context.packageManager.getPackagesForUid(bucket.uid)?.firstOrNull()
                                ?: "uid:${bucket.uid}"
                            result += DataUsageSample(
                                packageName = pkg,
                                networkType = NetworkType.MOBILE,
                                rxBytes = bucket.rxBytes,
                                txBytes = bucket.txBytes,
                                periodStart = since,
                                periodEnd = now,
                            )
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // Usage-access permission not granted yet.
        } catch (e: Exception) {
            // Transient OEM NetworkStatsManager error - skip this cycle, retry next poll.
        }
        return result
    }

    /** True if this device currently has any tethering (Wi-Fi hotspot, USB, or Bluetooth) active. */
    fun isTetheringActive(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val method = ConnectivityManager::class.java.getMethod("getTetheredIfaces")
                val ifaces = method.invoke(cm) as? Array<*>
                val hasTetheredIfaces = ifaces != null && ifaces.isNotEmpty()
                hasTetheredIfaces
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
