package com.baccours.ekho

import android.app.Application
import timber.log.Timber
import com.baccours.ekho.BuildConfig

class EkhoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
