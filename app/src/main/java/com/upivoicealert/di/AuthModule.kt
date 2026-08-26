package com.upivoicealert.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.upivoicealert.data.auth.AuthSessionStore
import com.upivoicealert.BuildConfig
import com.upivoicealert.data.profile.MerchantProfileApi
import com.upivoicealert.data.security.DeviceApi
import com.upivoicealert.network.ApiClient
import com.upivoicealert.network.AuthApi
import com.upivoicealert.network.AuthInterceptor
import com.upivoicealert.network.AuthAuthenticator
import com.upivoicealert.utils.DeviceIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun deviceIdGenerator(@ApplicationContext context: Context) = DeviceIdGenerator(context)
    @Provides @Singleton fun okHttpClient(interceptor: AuthInterceptor, authenticator: AuthAuthenticator): OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).authenticator(authenticator).build()
    @Provides @Singleton fun retrofitBuilder(client: OkHttpClient): Retrofit.Builder = Retrofit.Builder().client(client)
    @Provides @Singleton fun authApi(builder: Retrofit.Builder): AuthApi = ApiClient.create(builder)
    @Provides @Singleton fun merchantProfileApi(builder: Retrofit.Builder): MerchantProfileApi =
        builder.baseUrl(BuildConfig.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(MerchantProfileApi::class.java)
    @Provides @Singleton fun deviceApi(builder: Retrofit.Builder): DeviceApi =
        builder.baseUrl(BuildConfig.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(DeviceApi::class.java)
}
