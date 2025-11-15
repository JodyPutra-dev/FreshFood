package com.jody.freshfood.network

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
        // TODO: Replace with production URL
        private const val BASE_URL = "https://example.com/models/"

        fun create(): ModelUpdateService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
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
