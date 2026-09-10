package com.pupr.kalsel.riverop.data.remote

import com.pupr.kalsel.riverop.BuildConfig
import com.pupr.kalsel.riverop.utils.SessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Jangan diubah di sini. Alamatnya diatur di app/build.gradle.kts,
    // diisi lewat local.properties, dan berbeda antara debug dan rilis.
    private var baseUrl: String = BuildConfig.BASE_URL

    private var retrofit: Retrofit? = null

    fun setBaseUrl(url: String) {
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        if (baseUrl != formattedUrl) {
            baseUrl = formattedUrl
            retrofit = null
        }
    }

    fun getBaseUrl(): String {
        return baseUrl.removeSuffix("/")
    }

    fun resolvePhotoUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("http")) {
            path
        } else if (path.startsWith("/")) {
            getBaseUrl() + path
        } else {
            getBaseUrl() + "/" + path
        }
    }

    fun getApiService(): ApiService {
        if (retrofit == null) {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.code == 401) {
                        SessionManager.notifyUnauthorized()
                    }
                    response
                }
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}
