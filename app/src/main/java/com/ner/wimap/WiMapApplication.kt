package com.ner.wimap

import android.app.Application
import android.content.Context
import com.ner.wimap.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WiMapApplication : Application() {
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(base))
    }
}