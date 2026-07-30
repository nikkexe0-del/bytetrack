package com.zestyy.bytetrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class AppTotal(
    val packageName: String,
    val totalBytes: Long,
)

data class AppScreenTime(
    val packageName: String,
    val totalTimeMs: Long,
)

data class NetworkTypeTotal(
    val networkType: NetworkType,
    val totalBytes: Long,
)

data class AppNetworkBreakdown(
    val packageName: String,
    val networkType: NetworkType,
    val totalBytes: Long,
)

/**
 * NOTE ON THE BOUNDARY CONDITION: every range query below buckets a sample by its START time
 * (`periodStart >= :from AND periodStart < :to`), not by requiring the whole sample to fit
 * inside the window. The old queries required `periodEnd <= :to` too, which silently dropped
 * any sample that happened to straddle the query boundary (e.g. a poll window that started just
 * before midnight and ended just after it) - that data wasn't double counted, it just vanished
 * from both days' totals. Bucketing by start time means every sample is counted in exactly one
 * period, with nothing lost and nothing duplicated, and it's consistent for day/week/month/year
 * queries alike.
 */
@Dao
interface DataUsageDao {
    @Insert
    suspend fun insertAll(samples: List<DataUsageSample>)

    @Query("SELECT packageName, SUM(rxBytes + txBytes) as totalBytes FROM data_usage_samples WHERE periodStart >= :from AND periodStart < :to GROUP BY packageName ORDER BY totalBytes DESC")
    fun appTotalsBetween(from: Long, to: Long): Flow<List<AppTotal>>

    @Query("SELECT packageName, networkType, SUM(rxBytes + txBytes) as totalBytes FROM data_usage_samples WHERE periodStart >= :from AND periodStart < :to GROUP BY packageName, networkType")
    fun appNetworkBreakdownBetween(from: Long, to: Long): Flow<List<AppNetworkBreakdown>>

    @Query("SELECT networkType, SUM(rxBytes + txBytes) as totalBytes FROM data_usage_samples WHERE packageName = :pkg AND periodStart >= :from AND periodStart < :to GROUP BY networkType")
    fun networkBreakdownForApp(pkg: String, from: Long, to: Long): Flow<List<NetworkTypeTotal>>

    @Query("SELECT networkType, SUM(rxBytes + txBytes) as totalBytes FROM data_usage_samples WHERE periodStart >= :from AND periodStart < :to GROUP BY networkType")
    fun networkTotalsBetween(from: Long, to: Long): Flow<List<NetworkTypeTotal>>

    @Query("SELECT * FROM data_usage_samples WHERE periodStart >= :from AND periodStart < :to ORDER BY periodStart ASC")
    fun samplesBetween(from: Long, to: Long): Flow<List<DataUsageSample>>

    @Query("""
        SELECT (periodStart / 3600000) * 3600000 as hourStart,
               SUM(CASE WHEN networkType = 'WIFI' THEN rxBytes + txBytes ELSE 0 END) as wifiBytes,
               SUM(CASE WHEN networkType = 'MOBILE' THEN rxBytes + txBytes ELSE 0 END) as mobileBytes,
               SUM(CASE WHEN networkType = 'HOTSPOT' THEN rxBytes + txBytes ELSE 0 END) as hotspotBytes
        FROM data_usage_samples
        WHERE periodStart >= :from AND periodStart < :to
        GROUP BY hourStart
        ORDER BY hourStart ASC
    """)
    fun hourlyBuckets(from: Long, to: Long): Flow<List<HourlyBucket>>

    @Query("SELECT COALESCE(SUM(rxBytes + txBytes), 0) FROM data_usage_samples WHERE periodStart >= :from AND periodStart < :to")
    fun totalBytesBetween(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(rxBytes + txBytes), 0) FROM data_usage_samples WHERE periodStart >= :from AND periodStart < :to")
    suspend fun totalBytesBetweenOnce(from: Long, to: Long): Long

    @Query("DELETE FROM data_usage_samples WHERE periodEnd < :before")
    suspend fun pruneOlderThan(before: Long)
}

@Dao
interface ScreenTimeDao {
    @Insert
    suspend fun insert(session: ScreenTimeSession): Long

    @Query("SELECT packageName, SUM(endedAt - startedAt) as totalTimeMs FROM screen_time_sessions WHERE startedAt >= :from AND startedAt < :to GROUP BY packageName ORDER BY totalTimeMs DESC")
    fun screenTimeByAppBetween(from: Long, to: Long): Flow<List<AppScreenTime>>

    @Query("SELECT * FROM screen_time_sessions WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt ASC")
    fun sessionsBetween(from: Long, to: Long): Flow<List<ScreenTimeSession>>

    @Query("SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM screen_time_sessions WHERE startedAt >= :from AND startedAt < :to")
    fun totalScreenTimeBetween(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(endedAt - startedAt), 0) FROM screen_time_sessions WHERE startedAt >= :from AND startedAt < :to")
    suspend fun totalScreenTimeBetweenOnce(from: Long, to: Long): Long

    @Query("DELETE FROM screen_time_sessions WHERE endedAt < :before")
    suspend fun pruneOlderThan(before: Long)
}

@Dao
interface AppInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<AppInfoEntity>)

    @Query("SELECT * FROM app_info WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): AppInfoEntity?

    @Query("SELECT * FROM app_info")
    fun observeAll(): Flow<List<AppInfoEntity>>
}
