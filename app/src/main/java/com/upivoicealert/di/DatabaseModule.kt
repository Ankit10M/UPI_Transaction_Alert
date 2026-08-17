package com.upivoicealert.di

import android.content.Context
import androidx.room.Room
import com.upivoicealert.data.database.AppDatabase
import com.upivoicealert.data.database.TransactionDao
import com.upivoicealert.data.database.UnparsedNotificationDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "upi_voice_alert.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .build()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideUnparsedNotificationDao(db: AppDatabase): UnparsedNotificationDao = db.unparsedNotificationDao()
}