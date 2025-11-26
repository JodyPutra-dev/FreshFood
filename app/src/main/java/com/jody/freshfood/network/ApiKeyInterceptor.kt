package com.jody.freshfood.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds API key authentication to all HTTP requests.
 * 
 * This interceptor automatically injects the X-API-Key header into every request
 * for secure communication with the model distribution server. The API key is
 * required for accessing model manifest and download endpoints.
 * 
 * @param apiKey The API key to use for authentication. Must match the CLIENT_API_KEY
 *               configured on the server. Cannot be null or empty.
 * @throws IllegalArgumentException if apiKey is null or empty
 * 
 * Usage:
 * ```
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(ApiKeyInterceptor(BuildConfig.MODEL_UPDATE_API_KEY))
 *     .build()
 * ```
 */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    
    init {
        require(apiKey.isNotEmpty()) {
            "API key cannot be empty. Please configure MODEL_UPDATE_API_KEY in BuildConfig."
        }
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Add X-API-Key header to the request
        val authenticatedRequest = originalRequest.newBuilder()
            .header("X-API-Key", apiKey)
            .build()
        
        return chain.proceed(authenticatedRequest)
    }
}
