package com.zestyy.bytetrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zestyy.bytetrack.data.local.AppDatabase
import com.zestyy.bytetrack.data.local.HourlyBucket
import com.zestyy.bytetrack.data.local.NetworkType
import com.zestyy.bytetrack.data.repository.AppInfoRepository
import com.zestyy.bytetrack.data.repository.NetworkUsageRepository
import com.zestyy.bytetrack.data.repository.UpdateInfo
import com.zestyy.bytetrack.data.repository.UpdateRepository
import com.zestyy.bytetrack.util.AppUpdater
import com.zestyy.bytetrack.util.PeriodBucket
import com.zestyy.bytetrack.util.RangeType
import com.zestyy.bytetrack.util.SelectedRange
import com.zestyy.bytetrack.util.bounds
import com.zestyy.bytetrack.util.bucketAnchors
import com.zestyy.bytetrack.util.bucketFor
import com.zestyy.bytetrack.util.canGoNext
import com.zestyy.bytetrack.util.label
import com.zestyy.bytetrack.util.mergeIntoTimeline
import com.zestyy.bytetrack.util.TimelineEntry
import com.zestyy.bytetrack.util.next
import com.zestyy.bytetrack.util.previous
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AppRow(
    val packageName: String,
    val label: String,
    val dataBytes: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val hotspotBytes: Long,
    val screenTimeMs: Long,
)

/** One row in the Timeline tab: an app, how much data it used, and the time window it used it in. */
data class TimelineRow(
    val packageName: String,
    val label: String,
    val startedAt: Long,
    val endedAt: Long,
    val totalBytes: Long,
    val dominantNetworkType: NetworkType,
    val isMixedNetwork: Boolean,
)

data class DashboardUiState(
    val selectedRange: SelectedRange = SelectedRange(RangeType.DAY),
    val rangeLabel: String = "Today",
    val canGoNext: Boolean = false,
    val totalDataToday: Long = 0,
    val totalWifiToday: Long = 0,
    val totalMobileToday: Long = 0,
    val totalHotspotToday: Long = 0,
    val totalScreenTimeToday: Long = 0,
    val hourlyBuckets: List<HourlyBucket> = emptyList(),
    val topApps: List<AppRow> = emptyList(),
    val timeline: List<TimelineRow> = emptyList(),
    /** Populated only for WEEK/MONTH/YEAR ranges: one tappable row per day (week/month) or per
     * month (year). Empty for DAY, since day is already the finest grain. */
    val periodBreakdown: List<PeriodBucket> = emptyList(),
    val isLoadingBreakdown: Boolean = false,
    /** Non-null when a newer signed release is available - see UpdateRepository. */
    val updateInfo: UpdateInfo? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val appInfoRepo = AppInfoRepository(application)
    private val updateRepo = UpdateRepository()
    private val appUpdater = AppUpdater(application)

    // Cache resolved app labels in-memory so we don't re-hit PackageManager every recomposition
    private val labelCache = mutableMapOf<String, String>()

    private val _selectedRange = MutableStateFlow(SelectedRange(RangeType.DAY, LocalDate.now()))

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var breakdownJob: Job? = null

    init {
        observeSelectedRange()
        checkForUpdate()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val update = updateRepo.checkForUpdate() ?: return@launch
            _uiState.value = _uiState.value.copy(updateInfo = update)
        }
    }

    /** Kicks off the download; the system installer prompt appears once it finishes (see
     * AppUpdater). Doesn't clear updateInfo here - the banner should keep offering to install
     * until it actually succeeds, in case the user backs out of the installer confirmation. */
    fun installUpdate() {
        val update = _uiState.value.updateInfo ?: return
        appUpdater.downloadAndInstall(update.downloadUrl, update.versionName)
    }

    fun dismissUpdate() {
        _uiState.value = _uiState.value.copy(updateInfo = null)
    }

    // --- Range navigation -----------------------------------------------------------------

    fun selectRangeType(type: RangeType) {
        _selectedRange.value = SelectedRange(type, _selectedRange.value.anchor)
    }

    fun goToPrevious() {
        _selectedRange.value = _selectedRange.value.previous()
    }

    fun goToNext() {
        val current = _selectedRange.value
        if (current.canGoNext()) {
            _selectedRange.value = current.next()
        }
    }

    fun goToToday() {
        _selectedRange.value = SelectedRange(RangeType.DAY, LocalDate.now())
    }

    /** Drill into a specific bucket tapped in the period breakdown list - a day row (from a
     * week/month view) opens that Day; a month row (from a year view) opens that Month. */
    fun drillInto(bucket: PeriodBucket) {
        _selectedRange.value = SelectedRange(bucket.drillInto, bucket.anchor)
    }

    /** Re-pull the currently selected range's data. Cheap to call on every onResume() - the
     * underlying Room Flows already push live updates, this just re-triggers the one-shot
     * period-breakdown computation in case new data landed while the app was backgrounded. */
    fun refresh() {
        loadBreakdown(_selectedRange.value)
    }

    // --- Reactive per-range state -----------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedRange() {
        viewModelScope.launch {
            _selectedRange.flatMapLatest { range ->
                val (from, to) = range.bounds()

                val totalsFlow = combine(
                    db.dataUsageDao().totalBytesBetween(from, to),
                    db.screenTimeDao().totalScreenTimeBetween(from, to),
                    db.dataUsageDao().hourlyBuckets(from, to),
                    db.dataUsageDao().networkTotalsBetween(from, to),
                ) { totalData, totalScreen, hourly, networkTotals ->
                    var wifiSum = 0L; var mobileSum = 0L; var hotspotSum = 0L
                    networkTotals.forEach {
                        when (it.networkType) {
                            NetworkType.WIFI -> wifiSum += it.totalBytes
                            NetworkType.MOBILE -> mobileSum += it.totalBytes
                            NetworkType.HOTSPOT -> hotspotSum += it.totalBytes
                        }
                    }
                    RangeTotals(totalData, totalScreen, hourly, wifiSum, mobileSum, hotspotSum)
                }

                val appsFlow = combine(
                    db.dataUsageDao().appTotalsBetween(from, to),
                    db.dataUsageDao().appNetworkBreakdownBetween(from, to),
                    db.screenTimeDao().screenTimeByAppBetween(from, to),
                ) { dataTotals, breakdown, screenTotals ->
                    val breakdownByPkg = breakdown.groupBy { it.packageName }
                    val screenByPkg = screenTotals.associate { it.packageName to it.totalTimeMs }
                    // The hotspot-shared pseudo-row (see NetworkUsageRepository.HOTSPOT_SHARED_PSEUDO_PACKAGE)
                    // represents data forwarded to another device over your hotspot, not usage by
                    // any app on THIS phone - no app you have installed "uses hotspot", so it doesn't
                    // belong in a per-app list. It still counts in the Dashboard's aggregate Hotspot
                    // total; it's just excluded from this ranked-by-app view specifically.
                    dataTotals
                        .filter { it.packageName != NetworkUsageRepository.HOTSPOT_SHARED_PSEUDO_PACKAGE }
                        .take(30)
                        .map { d ->
                            val perType = breakdownByPkg[d.packageName].orEmpty()
                            AppRow(
                                packageName = d.packageName,
                                label = resolveLabel(d.packageName),
                                dataBytes = d.totalBytes,
                                wifiBytes = perType.firstOrNull { it.networkType == NetworkType.WIFI }?.totalBytes ?: 0L,
                                mobileBytes = perType.firstOrNull { it.networkType == NetworkType.MOBILE }?.totalBytes ?: 0L,
                                hotspotBytes = perType.firstOrNull { it.networkType == NetworkType.HOTSPOT }?.totalBytes ?: 0L,
                                screenTimeMs = screenByPkg[d.packageName] ?: 0L,
                            )
                        }
                }

                // Timeline (per-session merge) only makes sense - and is only cheap enough - for
                // a single Day. For week/month/year the period-breakdown list is what's shown
                // instead, loaded separately in loadBreakdown().
                val timelineFlow: Flow<List<TimelineEntry>> = if (range.type == RangeType.DAY) {
                    db.dataUsageDao().samplesBetween(from, to).map { samples -> samples.mergeIntoTimeline() }
                } else {
                    flowOf(emptyList())
                }

                combine(totalsFlow, appsFlow, timelineFlow) { totals, apps, timeline ->
                    val timelineRows = timeline.map { entry ->
                        TimelineRow(
                            packageName = entry.packageName,
                            label = resolveLabel(entry.packageName),
                            startedAt = entry.startedAt,
                            endedAt = entry.endedAt,
                            totalBytes = entry.totalBytes,
                            dominantNetworkType = entry.dominantNetworkType,
                            isMixedNetwork = entry.isMixedNetwork,
                        )
                    }
                    DashboardUiState(
                        selectedRange = range,
                        rangeLabel = range.label(),
                        canGoNext = range.canGoNext(),
                        totalDataToday = totals.totalData,
                        totalWifiToday = totals.wifi,
                        totalMobileToday = totals.mobile,
                        totalHotspotToday = totals.hotspot,
                        totalScreenTimeToday = totals.totalScreen,
                        hourlyBuckets = totals.hourly,
                        topApps = apps,
                        timeline = timelineRows,
                        periodBreakdown = _uiState.value.periodBreakdown,
                        isLoadingBreakdown = _uiState.value.isLoadingBreakdown,
                        updateInfo = _uiState.value.updateInfo,
                    )
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }

        viewModelScope.launch {
            _selectedRange.collect { range -> loadBreakdown(range) }
        }
    }

    /** One-shot computation of the tappable day/month rows for week/month/year views. Not a
     * reactive Flow like the rest of the state - re-run explicitly on range change and on
     * refresh() (e.g. app resume), which is cheap since it's at most 12 (year) or 31 (month)
     * small aggregate queries. */
    private fun loadBreakdown(range: SelectedRange) {
        breakdownJob?.cancel()
        val anchors = range.bucketAnchors()
        if (anchors.isEmpty()) {
            _uiState.value = _uiState.value.copy(periodBreakdown = emptyList(), isLoadingBreakdown = false)
            return
        }
        breakdownJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBreakdown = true)
            val buckets = anchors.map { bucketAnchor ->
                val bucketRangeType = if (range.type == RangeType.YEAR) RangeType.MONTH else RangeType.DAY
                val (from, to) = SelectedRange(bucketRangeType, bucketAnchor).bounds()
                val dataBytes = db.dataUsageDao().totalBytesBetweenOnce(from, to)
                val screenTimeMs = db.screenTimeDao().totalScreenTimeBetweenOnce(from, to)
                range.bucketFor(bucketAnchor, dataBytes, screenTimeMs)
            }
            // Only commit if this is still the range the user is looking at - avoids a slow
            // year-view computation clobbering a faster subsequent week-view one.
            if (_selectedRange.value == range) {
                _uiState.value = _uiState.value.copy(periodBreakdown = buckets, isLoadingBreakdown = false)
            }
        }
    }

    private suspend fun resolveLabel(packageName: String): String {
        labelCache[packageName]?.let { return it }
        val label = appInfoRepo.label(packageName)
        labelCache[packageName] = label
        return label
    }

    private data class RangeTotals(
        val totalData: Long,
        val totalScreen: Long,
        val hourly: List<HourlyBucket>,
        val wifi: Long,
        val mobile: Long,
        val hotspot: Long,
    )
}
