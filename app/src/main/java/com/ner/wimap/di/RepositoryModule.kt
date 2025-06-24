package com.ner.wimap.di

import com.ner.wimap.data.repository.ExportRepositoryImpl
import com.ner.wimap.data.repository.PinnedNetworkRepositoryImpl
import com.ner.wimap.data.repository.WifiRepositoryImpl
import com.ner.wimap.domain.repository.ExportRepository
import com.ner.wimap.domain.repository.PinnedNetworkRepository
import com.ner.wimap.domain.repository.WifiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindWifiRepository(
        wifiRepositoryImpl: WifiRepositoryImpl
    ): WifiRepository
    
    @Binds
    @Singleton
    abstract fun bindPinnedNetworkRepository(
        pinnedNetworkRepositoryImpl: PinnedNetworkRepositoryImpl
    ): PinnedNetworkRepository
    
    @Binds
    @Singleton
    abstract fun bindExportRepository(
        exportRepositoryImpl: ExportRepositoryImpl
    ): ExportRepository
}