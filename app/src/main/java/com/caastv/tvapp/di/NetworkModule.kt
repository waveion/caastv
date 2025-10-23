package com.caastv.tvapp.di

import android.content.Context
import com.caastv.tvapp.model.repository.common.WTVNetworkRepositoryImpl
import com.caastv.tvapp.utils.crash.logs.CrashLogger
import com.caastv.tvapp.utils.crash.logs.LogUploader
import com.caastv.tvapp.utils.network.BaseUrlSwitcherInterceptor
import com.caastv.tvapp.utils.network.LoggingInterceptor
import com.caastv.tvapp.utils.network.NetworkApiCallInterface
import com.caastv.tvapp.utils.network.interceptors.ApiStatusInterceptor
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT = 15L
    private const val READ_TIMEOUT    = 10L
    private const val WRITE_TIMEOUT   = 10L
    private const val API_KEY_HEADER = "x-api-key"
    private const val API_KEY_VALUE  = "BUAA8JJkzfMI56y4BhEhU"

    @Singleton
    @Provides
    fun okHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )

        // Install the all-trusting trust manager into an SSLContext
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        val sslSocketFactory = sslContext.socketFactory
        // Build and return the OkHttpClient
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            //.addInterceptor(headerInterceptor)               // header
            //Trust all SSL certificates (for debug/development only)
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }// Bypass hostname verification
            .cache(null)
            .build()
    }

    @Singleton
    @Provides
    fun retrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api-demo.caastv.com/") // replace with your production base URL
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .setLenient()
                        .create()
                )
            )
            .build()
    }

    @Singleton
    @Provides
    fun provideNetworkAPIService(retrofit: Retrofit): NetworkApiCallInterface {
        return retrofit.create(NetworkApiCallInterface::class.java)
    }

    @Singleton
    @Provides
    fun provideWTVNetworkRepository(
        networkApiCallInterface: NetworkApiCallInterface
    ): WTVNetworkRepositoryImpl {
        return WTVNetworkRepositoryImpl(networkApiCallInterface)
    }


    @Provides
    @Singleton
    fun provideCrashLogger(context: Context): CrashLogger = CrashLogger(context)

    @Provides
    @Singleton
    fun provideLogUploader(
        context: Context,
        api: NetworkApiCallInterface
    ): LogUploader = LogUploader(context, api)

}
