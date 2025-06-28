package com.ner.wimap.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LocaleHelper {
    
    /**
     * Apply English language to the context (LTR only)
     */
    fun applyLanguage(context: Context): Context {
        return applyLanguage(context, "en")
    }
    
    /**
     * Apply a specific language to the context (English only)
     */
    fun applyLanguage(context: Context, languageCode: String): Context {
        val locale = Locale("en") // Force English only
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }
}