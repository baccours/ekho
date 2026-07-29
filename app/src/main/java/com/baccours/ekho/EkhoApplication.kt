package com.baccours.ekho

import android.app.Application
import timber.log.Timber

class EkhoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
