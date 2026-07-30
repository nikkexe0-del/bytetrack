package com.zestyy.bytetrack

import android.app.Application
import com.zestyy.bytetrack.worker.SyncWorker

class ByteTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
    }
}
