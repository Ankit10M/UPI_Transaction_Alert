package com.upivoicealert.di

import android.content.Context
import com.upivoicealert.R
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.parser.bhim.BhimParserV1
import com.upivoicealert.parser.gpay.GPayParserV1
import com.upivoicealert.parser.paytm.PaytmParserV1
import com.upivoicealert.parser.phonepe.PhonePeParserV1
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Configurable filter keyword list (CLAUDE.md Module 2 Component 1), loaded
     * from a resource so it can be extended without code changes.
     */
    @Provides
    @Singleton
    fun provideFilterKeywords(@ApplicationContext context: Context): Set<String> =
        context.resources
            .getStringArray(R.array.notification_filter_keywords)
            .map { it.lowercase() }
            .toSet()

    /**
     * Registered parser versions per UPI app. New versions are appended here
     * (most-recent-first ordering is honoured by the resolver).
     */
    @Provides
    @Singleton
    fun provideParsers(): List<@JvmSuppressWildcards TransactionParser> = listOf(
        GPayParserV1(),
        PhonePeParserV1(),
        PaytmParserV1(),
        BhimParserV1()
    )
}