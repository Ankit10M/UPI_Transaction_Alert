package com.upivoicealert.network

import com.upivoicealert.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    fun create(retrofitBuilder: Retrofit.Builder): AuthApi = retrofitBuilder.baseUrl(BuildConfig.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(AuthApi::class.java)
}
