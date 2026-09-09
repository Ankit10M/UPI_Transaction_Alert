package com.upivoicealert.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.upivoicealert.config.EnvironmentProvider
import com.upivoicealert.data.profile.MerchantProfileApi
import com.upivoicealert.data.security.DeviceApi
import com.upivoicealert.data.sync.TransactionSyncApi
import com.upivoicealert.network.AuthApi
import com.upivoicealert.network.AuthAuthenticator
import com.upivoicealert.network.AuthInterceptor
import com.upivoicealert.utils.DeviceIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun deviceIdGenerator(@ApplicationContext context: Context) = DeviceIdGenerator(context)

    @Provides @Singleton fun okHttpClient(
        interceptor: AuthInterceptor,
        authenticator: AuthAuthenticator,
        environmentProvider: EnvironmentProvider
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(interceptor)
            .authenticator(authenticator)
        // Phase 8.1: HTTP body logging ONLY in debug. Release disables entirely.
        // Never log Authorization / Bearer headers — redact even in debug.
        if (environmentProvider.isHttpBodyLoggingEnabled) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                redactHeader("Authorization")
                redactHeader("authorization")
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides @Singleton fun retrofit(
        client: OkHttpClient,
        environmentProvider: EnvironmentProvider
    ): Retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(environmentProvider.baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton fun merchantProfileApi(retrofit: Retrofit): MerchantProfileApi =
        retrofit.create(MerchantProfileApi::class.java)

    @Provides @Singleton fun deviceApi(retrofit: Retrofit): DeviceApi =
        retrofit.create(DeviceApi::class.java)

    @Provides @Singleton fun transactionSyncApi(retrofit: Retrofit): TransactionSyncApi =
        retrofit.create(TransactionSyncApi::class.java)

    @Provides @Singleton fun cloudTransactionApi(retrofit: Retrofit): com.upivoicealert.data.cloudtransaction.CloudTransactionApi =
        retrofit.create(com.upivoicealert.data.cloudtransaction.CloudTransactionApi::class.java)
}
