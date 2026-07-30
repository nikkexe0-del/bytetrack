package com.zestyy.bytetrack.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import com.zestyy.bytetrack.data.local.AppDatabase
import com.zestyy.bytetrack.data.local.AppInfoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppInfoRepository(private val context: Context) {
    private val db by lazy { AppDatabase.get(context) }

    suspend fun label(packageName: String): String = withContext(Dispatchers.IO) {
        if (packageName == NetworkUsageRepository.HOTSPOT_SHARED_PSEUDO_PACKAGE) {
            return@withContext "Shared via hotspot"
        }
        db.appInfoDao().get(packageName)?.label ?: try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(info).toString()
            db.appInfoDao().upsertAll(listOf(
                AppInfoEntity(packageName, label, (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            ))
            label
        } catch (_: Exception) {
            packageName
        }
    }
}
