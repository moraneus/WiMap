package com.ner.wimap

import android.app.Application
import android.content.Context
import com.ner.wimap.ads.AdInitializer
import com.ner.wimap.util.LocaleHelper
import com.ner.wimap.utils.OUILookupManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WiMapApplication : Application() {
    
    @Inject
    lateinit var adInitializer: AdInitializer
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob
        adInitializer.initialize(this)
        
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