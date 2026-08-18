package com.upivoicealert.di

import com.upivoicealert.data.datastore.UserProfileStore
import com.upivoicealert.data.datastore.SettingsDataStore
import com.upivoicealert.data.repository.PaymentVerificationRepositoryImpl
import com.upivoicealert.data.repository.ServiceStateRepositoryImpl
import com.upivoicealert.data.repository.SettingsRepositoryImpl
import com.upivoicealert.data.repository.SubscriptionRepositoryImpl
import com.upivoicealert.data.repository.TransactionRepositoryImpl
import com.upivoicealert.data.repository.UserRepositoryImpl
import com.upivoicealert.domain.repository.PaymentVerificationRepository
import com.upivoicealert.domain.repository.ServiceStateRepository
import com.upivoicealert.domain.repository.SettingsRepository
import com.upivoicealert.domain.repository.SubscriptionRepository
import com.upivoicealert.domain.repository.TransactionRepository
import com.upivoicealert.domain.repository.UserRepository
import com.upivoicealert.voice.VoiceAnnouncement
import com.upivoicealert.voice.VoiceAnnouncementEngine
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
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPaymentVerificationRepository(impl: PaymentVerificationRepositoryImpl): PaymentVerificationRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileStore(impl: SettingsDataStore): UserProfileStore

    @Binds
    @Singleton
    abstract fun bindServiceStateRepository(impl: ServiceStateRepositoryImpl): ServiceStateRepository

    /**
     * Pipeline-facing voice engine: the transaction pipeline depends on the
     * narrow [VoiceAnnouncement] contract (unit-testable); the same singleton
     * [VoiceAnnouncementEngine] remains the concrete implementation.
     */
    @Binds
    @Singleton
    abstract fun bindVoiceAnnouncement(impl: VoiceAnnouncementEngine): VoiceAnnouncement
}
