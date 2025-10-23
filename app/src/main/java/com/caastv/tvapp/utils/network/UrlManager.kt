package com.caastv.tvapp.utils.network

import okhttp3.HttpUrl

object UrlManager {
    const val headerToken = "BUAA8JJkzfMI56y4BhEhU"
    const val drmHeaderToken = "BUAA8JJkzfMI56y4BhEhU"//
    const val drmLicenseBaseUrl = "https://cryptoguard.waveiontechnologies.com:4443"////"https://10.22.254.46:4443"//
    const val loginDRMBaseUrl = "https://cryptoguard.waveiontechnologies.com/"////"https://10.22.254.46:9443/"//"https://iptvtest.panmetro.in/"//
    const val primaryBaseUrl = "https://api-demo.caastv.com/api/"
    const val alternateBaseUrl = "https://api-demo.caastv.com/api/"
    @Volatile
    private var currentBaseUrl = primaryBaseUrl

    fun getCurrentBaseUrl(): String = currentBaseUrl

    fun shouldSwitchUrl(responseCode: Int): Boolean {
        return responseCode in listOf(
            204, 205, 400, 401, 403, 404, 405, 408,
            444, 495, 496, 500, 502, 503, 504,
            520, 521, 522, 523, 524, 525, 526, 530
        )
    }

    fun switchBaseUrl() {
        currentBaseUrl = if (currentBaseUrl == primaryBaseUrl) alternateBaseUrl else primaryBaseUrl
    }

    fun replaceBaseUrl(originalUrl: HttpUrl): String {
        val oldBaseUrl = originalUrl.scheme + "://" + originalUrl.host
        return originalUrl.toString().replace(oldBaseUrl, currentBaseUrl)
    }
}