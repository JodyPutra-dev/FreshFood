package com.jody.freshfood.network

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
        fun create(): ContributeService {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
            
            // TODO: Replace with production URL
            val retrofit = Retrofit.Builder()
                .baseUrl("https://example.com/api/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            return retrofit.create(ContributeService::class.java)
        }
    }
}
