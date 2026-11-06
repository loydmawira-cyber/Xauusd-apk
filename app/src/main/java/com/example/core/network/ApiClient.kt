package com.example.core.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl = "https://api.xaupilot.com/api/v1/"
    private var authToken: String? = null
    private var currentRetrofit: Retrofit? = null
    private var cachedApiService: ApiService? = null

    fun setAuthToken(token: String?) {
        this.authToken = token
    }

    fun getAuthToken(): String? = authToken

    fun setBaseUrl(url: String) {
        val sanitized = if (url.endsWith("/")) url else "$url/"
        if (this.baseUrl != sanitized) {
            this.baseUrl = sanitized
            currentRetrofit = null
            cachedApiService = null
        }
    }

    fun getBaseUrl(): String = baseUrl

    private fun buildOkHttpClient(): OkHttpClient {
        // Redact sensitive headers in network logs
        val logging = HttpLoggingInterceptor { message ->
            val sanitized = if (message.startsWith("Authorization:", ignoreCase = true)) {
                "Authorization: Bearer [REDACTED_SECURE_TOKEN]"
            } else if (message.contains("password", ignoreCase = true)) {
                "[PAYLOAD REDACTED FOR SECURITY]"
            } else {
                message
            }
            android.util.Log.d("XauPilotNetwork", sanitized)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "XauPilot-Android/2.4.0")

                authToken?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService
        get() {
            if (cachedApiService == null) {
                cachedApiService = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(buildOkHttpClient())
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
            }
            return cachedApiService!!
        }
}
