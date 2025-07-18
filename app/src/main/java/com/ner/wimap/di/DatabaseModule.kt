package com.ner.wimap.di

import android.content.Context
import androidx.room.Room
import com.ner.wimap.data.database.AppDatabase
import com.ner.wimap.data.database.PinnedNetworkDao
import com.ner.wimap.data.database.TemporaryNetworkDataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    @Provides
    fun providePinnedNetworkDao(database: AppDatabase): PinnedNetworkDao {
        return database.pinnedNetworkDao()
    }
    
    @Provides
    fun provideTemporaryNetworkDataDao(database: AppDatabase): TemporaryNetworkDataDao {
        return database.temporaryNetworkDataDao()
    }
}