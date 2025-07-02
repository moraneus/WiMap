package com.ner.wimap.di

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.ner.wimap.ads.AdManager
import com.ner.wimap.ads.NativeAdCache
import com.ner.wimap.data.database.AppDatabase
import com.ner.wimap.data.database.ScanSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
    
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    @Provides
    @Singleton
    fun provideNativeAdCache(adManager: AdManager): NativeAdCache {
        return NativeAdCache(adManager)
    }
    
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("wimap_preferences", Context.MODE_PRIVATE)
    }
    
    @Provides
    @Singleton
    fun provideScanSessionDao(@ApplicationContext context: Context): ScanSessionDao {
        return AppDatabase.getDatabase(context).scanSessionDao()
    }
}