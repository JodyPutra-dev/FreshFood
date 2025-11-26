package com.jody.freshfood.network

import com.jody.freshfood.BuildConfig
import com.jody.freshfood.network.dto.ModelManifestDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

interface ModelUpdateService {
    @GET("manifest.json")
    suspend fun getManifest(): ModelManifestDto

    companion object {
        private const val BASE_URL = BuildConfig.MODEL_UPDATE_BASE_URL

        fun create(): ModelUpdateService {
            // API key interceptor for server authentication
            val apiKeyInterceptor = ApiKeyInterceptor(BuildConfig.MODEL_UPDATE_API_KEY)
            
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(apiKeyInterceptor) // Add API key before logging
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ModelUpdateService::class.java)
        }
    }
}
