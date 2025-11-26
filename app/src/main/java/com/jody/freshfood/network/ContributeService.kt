package com.jody.freshfood.network

import com.jody.freshfood.BuildConfig
import com.jody.freshfood.network.dto.ContributeRequestDto
import com.jody.freshfood.network.dto.ContributeResponseDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ContributeService {
    
    @POST("contribute")
    suspend fun uploadContribution(@Body request: ContributeRequestDto): ContributeResponseDto
    
    companion object {
        private const val BASE_URL = BuildConfig.CONTRIBUTE_BASE_URL
        
        fun create(): ContributeService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            // TODO: Add API key authentication if server implements /api/contribute with authentication
            // val apiKeyInterceptor = ApiKeyInterceptor(BuildConfig.MODEL_UPDATE_API_KEY)
            
            val okHttpClient = OkHttpClient.Builder()
                // .addInterceptor(apiKeyInterceptor) // Uncomment when server requires auth
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            return retrofit.create(ContributeService::class.java)
        }
    }
}
