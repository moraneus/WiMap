package com.ner.wimap.di

import android.app.Application
import android.content.Context
import com.ner.wimap.FirebaseRepository
import com.ner.wimap.LocationProvider
import com.ner.wimap.data.database.AppDatabase
import com.ner.wimap.ui.viewmodel.ConnectionManager
import com.ner.wimap.ui.viewmodel.ScanManager
import com.ner.wimap.wifi.WifiScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {
    
    @Provides
    @Singleton
    fun provideLocationProvider(@ApplicationContext context: Context): LocationProvider {
        return LocationProvider(context as Application)
    }
    
    @Provides
    @Singleton
    fun provideWifiScanner(
        @ApplicationContext context: Context,
        locationProvider: LocationProvider
    ): WifiScanner {
        return WifiScanner(context as Application, locationProvider.currentLocation)
    }
    
    @Provides
    @Singleton
    fun provideFirebaseRepository(): FirebaseRepository {
        return FirebaseRepository()
    }
    
    @Provides
    @Singleton
    fun provideScanManager(
        @ApplicationContext context: Context,
        wifiScanner: WifiScanner,
        locationProvider: LocationProvider,
        coroutineScope: CoroutineScope
    ): ScanManager {
        val scanManager = ScanManager(context as Application, wifiScanner, locationProvider, coroutineScope)
        scanManager.initialize()
        return scanManager
    }
    
    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationContext context: Context,
        wifiScanner: WifiScanner,
        firebaseRepository: FirebaseRepository,
        database: AppDatabase,
        coroutineScope: CoroutineScope
    ): ConnectionManager {
        val connectionManager = ConnectionManager(
            wifiScanner, 
            firebaseRepository, 
            database.pinnedNetworkDao(), 
            coroutineScope,
            context
        )
        connectionManager.initializePasswordsFromSettings()
        return connectionManager
    }
}