package com.ner.wimap

import android.app.Application
import android.content.Context
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.ner.wimap.ads.AdMobManager
import com.ner.wimap.util.LocaleHelper
import com.ner.wimap.utils.OUILookupManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class WiMapApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase App Check
        initializeAppCheck()
        
        // Initialize AdMob
        AdMobManager.initialize(this)
        
        // Initialize OUI database for MAC vendor lookups
        applicationScope.launch {
            OUILookupManager.getInstance().initialize(this@WiMapApplication)
            
            // Test vendor lookup after initialization
            launch {
                kotlinx.coroutines.delay(2000) // Wait 2 seconds for initialization
                testVendorLookup()
            }
        }
    }
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(base))
    }
    
    private fun initializeAppCheck() {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        
        val providerFactory: AppCheckProviderFactory = if (BuildConfig.DEBUG) {
            // Use Debug provider for debug builds
            DebugAppCheckProviderFactory.getInstance()
        } else {
            // Use Play Integrity provider for release builds
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        
        firebaseAppCheck.installAppCheckProviderFactory(providerFactory)
        android.util.Log.d("AppCheck", "Firebase App Check initialized with ${if (BuildConfig.DEBUG) "Debug" else "Play Integrity"} provider")
    }
    
    private fun testVendorLookup() {
        val testMacs = listOf(
            "00:00:0C:AA:BB:CC", // Should be Cisco
            "3C:5A:B4:11:22:33", // Should be Google
            "A4:2B:B0:44:55:66", // Should be TP-Link
            "00:03:93:77:88:99", // Should be Apple
            "FF:FF:FF:AA:BB:CC"  // Should be Unknown
        )
        
        testMacs.forEach { mac ->
            val vendor = OUILookupManager.getInstance().lookupVendorShort(mac)
            android.util.Log.d("VendorTest", "MAC: $mac -> Vendor: $vendor")
        }
    }
}