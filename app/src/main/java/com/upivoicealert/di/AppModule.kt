package com.upivoicealert.di

import android.content.Context
import com.upivoicealert.R
import com.upivoicealert.parser.TransactionParser
import com.upivoicealert.parser.bhim.BhimParserV1
import com.upivoicealert.parser.generic.GenericReceivedParserV1
import com.upivoicealert.parser.gpay.GPayParserV1
import com.upivoicealert.parser.kotak.KotakParserV1
import com.upivoicealert.parser.paytm.PaytmParserV1
import com.upivoicealert.parser.phonepe.PhonePeParserV1
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
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
    @Named("filter_keywords")
    fun provideFilterKeywords(@ApplicationContext context: Context): Set<String> =
        context.resources
            .getStringArray(R.array.notification_filter_keywords)
            .map { it.lowercase() }
            .toSet()

    /**
     * Financial signal keywords for source-agnostic filtering: a notification
     * passes the filter if its text contains any of these signals.
     */
    @Provides
    @Singleton
    @Named("financial_signals")
    fun provideFinancialSignals(@ApplicationContext context: Context): Set<String> =
        context.resources
            .getStringArray(R.array.financial_signal_keywords)
            .map { it.lowercase() }
            .toSet()

    /**
     * Package blocklist of obvious non-financial apps (WhatsApp, Instagram,
     * YouTube, ...) whose notifications are always rejected.
     */
    @Provides
    @Singleton
    @Named("blocked_packages")
    fun provideBlockedPackages(@ApplicationContext context: Context): Set<String> =
        context.resources
            .getStringArray(R.array.blocked_notification_packages)
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
        BhimParserV1(),
        KotakParserV1(),
        GenericReceivedParserV1()
    )
}