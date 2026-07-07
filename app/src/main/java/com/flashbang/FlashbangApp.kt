package com.flashbang

import android.app.Application
import com.flashbang.alarm.ring.AlarmNotifications
import com.flashbang.data.DebugSeeder

class FlashbangApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.registerChannel(this)
        if (BuildConfig.DEBUG) {
            DebugSeeder.seedIfEmpty(this)
        }
    }
}
