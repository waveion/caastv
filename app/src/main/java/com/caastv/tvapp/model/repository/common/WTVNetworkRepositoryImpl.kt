package com.caastv.tvapp.model.repository.common

import com.caastv.tvapp.model.data.login.CustomerChannelsInfo
import com.caastv.tvapp.model.data.login.CustomerPackageInfo
import com.caastv.tvapp.model.data.login.LoginInfo
import com.caastv.tvapp.extensions.convertIntoModel
import com.caastv.tvapp.extensions.convertIntoModels
import com.caastv.tvapp.extensions.logReport
import com.caastv.tvapp.extensions.loge
import com.caastv.tvapp.extensions.toJSONArray
import com.caastv.tvapp.extensions.toJSONObject
import com.caastv.tvapp.model.data.appupdate.AppUpdateResponse
import com.caastv.tvapp.model.data.banner.Banner
import com.caastv.tvapp.model.data.customapp.InventoryApp
import com.caastv.tvapp.model.data.epgdata.EPGDataItem
import com.caastv.tvapp.model.data.favourites.FavouritesResponse
import com.caastv.tvapp.model.data.genre.WTVGenre
import com.caastv.tvapp.model.data.hash.HashInfo
import com.caastv.tvapp.model.data.home.HomeData
import com.caastv.tvapp.model.data.language.WTVLanguage
import com.caastv.tvapp.model.data.login.LoginResponseData
import com.caastv.tvapp.model.data.manifest.WTVManifest
import com.caastv.tvapp.model.data.notification.NotificationItem
import com.caastv.tvapp.model.home.WTVHomeCategory
import com.caastv.tvapp.model.repository.database.AppDataRepository
import com.caastv.tvapp.utils.network.NetworkApiCallInterface
import com.caastv.tvapp.utils.network.UrlManager
import com.caastv.tvapp.utils.sealed.WTVListResponse
import com.caastv.tvapp.utils.sealed.WTVResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException
import javax.inject.Inject
import kotlin.toString

class WTVNetworkRepositoryImpl @Inject constructor(private val networkApiCallInterface: NetworkApiCallInterface) {
    suspend fun provideWTVManifest(manifestUrl: String): Flow<WTVResponse<WTVManifest>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(manifestUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                loge("manifestUrl","$manifestUrl ::${response.body()}")
                val manifest = response.body()?.toJSONObject()?.toString()
                    .convertIntoModel(WTVManifest::class.java)
                manifest?.let {
                    // Optionally save manifest data into ContentProvider or DB here
                    emit(WTVResponse.Success(it))
                    loge("manifestUrl","$manifestUrl ::${it}")
                } ?: throw Exception("Failed to parse manifest")
            } else {
                loge("manifestUrl","$manifestUrl ::${Throwable("Invalid response received")}")

                emit(WTVResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {
            emit(WTVResponse.Failure(e))
            loge("manifestUrl","$manifestUrl ::${e}")

        }
    }.flowOn(Dispatchers.IO)


    fun provideNotificationSSE(sseUrl: String): Flow<NotificationItem> = callbackFlow {
        val client = OkHttpClient.Builder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(sseUrl)
            .addHeader("Accept", "text/event-stream")
            .build()

        val gson = Gson()
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: okhttp3.sse.EventSource,
                id: String?, type: String?, data: String
            ) {
                // parse JSON array of NotificationItem
                val listType = object : TypeToken<List<NotificationItem>>() {}.type
                val items: List<NotificationItem> = gson.fromJson(data, listType)
                items.forEach { trySend(it).isSuccess }
            }

            override fun onFailure(
                eventSource: okhttp3.sse.EventSource,
                t: Throwable?, response: okhttp3.Response?
            ) {
                // close the flow on error
                close(t ?: RuntimeException("SSE failure"))
            }
        }

        val source = EventSources.createFactory(client)
            .newEventSource(request, listener)

        // tear down when the collector disappears
        awaitClose { source.cancel() }
    }.flowOn(Dispatchers.IO)

    suspend fun provideWTVEPGData(epgContentUrl: String, dataRepository: AppDataRepository?): Flow<WTVListResponse<EPGDataItem>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(epgContentUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                dataRepository?.saveAllEpgData(response.body()?.toJSONArray().toString())
                val epgData: List<EPGDataItem>? = response.body()?.toJSONArray().toString()
                    .convertIntoModels(object : TypeToken<List<EPGDataItem>>() {})

                loge("epgContentUrl","$epgContentUrl ::${epgData.toString()}")
                // Optionally save EPG data into ContentProvider or DB here
                emit(WTVListResponse.Success(epgData!!))
            } else {

                loge("epgContentUrl","$epgContentUrl ::${Throwable("Invalid response received")}")
                emit(WTVListResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {

            loge("epgContentUrl","$epgContentUrl ::${e}")
            emit(WTVListResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun provideAppUpdateInfo(updateUrl: String): Flow<WTVResponse<AppUpdateResponse>> = flow {
        try {
            val resp = networkApiCallInterface.makeHttpGetRequest(updateUrl).execute()
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!.toJSONObject().toString()
                val parsed = body.convertIntoModel(AppUpdateResponse::class.java)
                parsed?.let { emit(WTVResponse.Success(it)) }
                    ?: emit(WTVResponse.Failure(Throwable("Parsing error")))
            } else {
                emit(WTVResponse.Failure(Throwable("HTTP ${resp.code()}")))
            }
        } catch (e: Exception) {
            emit(WTVResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)


    suspend fun provideWTVGenreData(genreUrl: String): Flow<WTVListResponse<WTVGenre>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(genreUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                val epgData: List<WTVGenre>? = response.body()?.toJSONArray().toString()
                    .convertIntoModels(object : TypeToken<List<WTVGenre>>() {})
                // Optionally save EPG data into ContentProvider or DB here
                emit(WTVListResponse.Success(epgData!!))
            } else {
                emit(WTVListResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {
            emit(WTVListResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun provideWTVLanguageData(languageUrl: String): Flow<WTVListResponse<WTVLanguage>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(languageUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                val epgData: List<WTVLanguage>? = response.body()?.toJSONArray().toString()
                    .convertIntoModels(object : TypeToken<List<WTVLanguage>>() {})
                // Optionally save EPG data into ContentProvider or DB here
                emit(WTVListResponse.Success(epgData!!))
            } else {
                emit(WTVListResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {
            emit(WTVListResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun provideWTVHomeData(homeUrl: String): Flow<WTVListResponse<WTVHomeCategory>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(homeUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                val epgData: List<WTVHomeCategory>? = response.body()?.toJSONArray().toString()
                    .convertIntoModels(object : TypeToken<List<WTVHomeCategory>>() {})
                // Optionally save EPG data into ContentProvider or DB here
                emit(WTVListResponse.Success(epgData!!))
            } else {
                emit(WTVListResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {
            emit(WTVListResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun provideWTVInventoryApps(url: String): Flow<WTVListResponse<InventoryApp>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(url).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()
                if (body is Map<*, *>) {
                    val dataList = body["data"]
                    if (dataList is List<*>) {
                        val json = Gson().toJson(dataList)
                        val apps: List<InventoryApp> = Gson().fromJson(
                            json,
                            object : TypeToken<List<InventoryApp>>() {}.type
                        )
                        emit(WTVListResponse.Success(apps))
                    } else {
                        emit(WTVListResponse.Failure(Throwable("Missing 'data' list in body")))
                    }
                } else {
                    emit(WTVListResponse.Failure(Throwable("Body is not a map")))
                }
            } else {
                emit(WTVListResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {
            emit(WTVListResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getBanners(bannerUrl: String): Flow<WTVListResponse<Banner>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(bannerUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                val banners = response.body()?.toJSONArray()?.toString()?.convertIntoModels(object : TypeToken<List<Banner>>() {})
                banners?.let { data ->
                    emit(WTVListResponse.Success(data))
                } ?: emit(WTVListResponse.Failure(Throwable("Parsing error: data is null")))
            } else {
                emit(WTVListResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {
            emit(WTVListResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun getFavorites(userId: String): Flow<WTVListResponse<String>> = flow {
        try {
            val url = "${UrlManager.getCurrentBaseUrl()}app/favchannel/$userId"
            val resp = networkApiCallInterface
                .makeHttpGetRequest(url)
                .execute()
            if (resp.isSuccessful && resp.body() != null) {
                val wrapper = resp.body()!!.toJSONObject()
                    .toString()
                    .let { Gson().fromJson(it, FavouritesResponse::class.java) }
                emit(WTVListResponse.Success(wrapper.data.channelId))
            } else {
                emit(WTVListResponse.Failure(Throwable("HTTP ${resp.code()}")))
            }
        } catch (e: Exception) {
            emit(WTVListResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun addFavorite(userId: String, channelId: String): Flow<WTVResponse<Boolean>> = flow {
        val url = "${UrlManager.getCurrentBaseUrl()}app/favchannel/add"
        val body = hashMapOf(
            "userID" to userId,
            "channelId" to channelId
        )
        val resp = networkApiCallInterface
            .makeHttpPostRequest(url, body)
            .execute()
        if (resp.isSuccessful) {
            emit(WTVResponse.Success(true))
        } else {
            emit(WTVResponse.Failure(Throwable("HTTP ${resp.code()}")))
        }
    }.flowOn(Dispatchers.IO)

    fun removeFavorite(userId: String, channelId: String): Flow<WTVResponse<Boolean>> = flow {
        val url = "${UrlManager.getCurrentBaseUrl()}app/favchannel/remove"
        val body = mapOf(
            "userID"    to userId,
            "channelId" to channelId
        )
        val resp = networkApiCallInterface
            .makeHttpDeleteRequest(url, body)  // ← include the JSON body
            .execute()
        if (resp.isSuccessful) {
            emit(WTVResponse.Success(true))
        } else {
            val err = resp.errorBody()?.string()
            emit(WTVResponse.Failure(Throwable("HTTP ${resp.code()}")))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun provideHomeContent(homeContentUrl:String): Flow<List<HomeData>> = flow {
        try {
            val response = networkApiCallInterface.makeHttpGetRequest(homeContentUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()?.toString().convertIntoModels(object : TypeToken<List<HomeData>>() {})?.let{ data->
                    // Optionally save EPG data into ContentProvider or DB here
                    emit(data)
                }
            } else {
                logReport("Banner", "Error fetching Banner content")
            }
        } catch (e: Exception) {
            logReport("Banner", "Error fetching Banner content", e)
        }
    }.flowOn(Dispatchers.IO) // <-- This moves the emission to the IO thread


    suspend fun provideUserHash(hashUrl: String): Flow<WTVResponse<HashInfo>> =
        flow {
            try {
                val response = networkApiCallInterface
                    .makeHttpGetRequest(hashUrl)
                    .execute()
                if (response.isSuccessful) {
                    val hashInfo = response.body()?.toJSONObject()?.toString()
                        .convertIntoModel(HashInfo::class.java)
                    hashInfo?.let {
                        loge("hashUrl>",hashUrl+hashInfo.toString())

                        // Optionally save manifest data into ContentProvider or DB here
                        emit(WTVResponse.Success(hashInfo))
                    } ?: throw Exception("Failed to parse hashInfo")
                } else {
                    emit(WTVResponse.Failure(Throwable("Invalid response received")))
                }
            } catch (e: Exception) {
                emit(WTVResponse.Failure(e))
            }
        }.flowOn(Dispatchers.IO)

    suspend fun registerUserHash(hashUrl: String,requestBody: HashMap<String, String>): Flow<WTVResponse<HashInfo>> =
        flow {
            try {
                val response = networkApiCallInterface
                    .makeHttpPostRequest(hashUrl,requestBody)
                    .execute()
                if (response.isSuccessful) {
                    val hashInfo = response.body()?.toJSONObject()?.toString()
                        .convertIntoModel(HashInfo::class.java)
                    hashInfo?.let {
                        loge("hashUrl>",hashUrl+hashInfo.toString())

                        // Optionally save manifest data into ContentProvider or DB here
                        emit(WTVResponse.Success(hashInfo))
                    } ?: throw Exception("Failed to parse hashInfo")
                } else {
                    emit(WTVResponse.Failure(Throwable("Invalid response received")))
                }
            } catch (e: Exception) {
                emit(WTVResponse.Failure(e))
            }
        }.flowOn(Dispatchers.IO)

    suspend fun provideCMSUserLogin(
        loginUrl: String,
        requestBody: HashMap<String, String>
    ): Flow<WTVResponse<LoginResponseData>> = flow {
        try {
            loge("response:","$loginUrl ${requestBody}")
            val response = networkApiCallInterface.makeHttpPostRequest(url=loginUrl, body = requestBody).execute()
            if (response.isSuccessful && response.body() != null) {
                val login = response.body()?.toJSONObject()?.toString()
                    .convertIntoModel(LoginResponseData::class.java)
                loge("response:","$loginUrl ${login}")
                login?.let {
                    // Optionally save manifest data into ContentProvider or DB here
                    emit(WTVResponse.Success(it))
                } ?: throw Exception("Failed to parse manifest")
            }else{
                val errorBody = response.errorBody()?.string()
                if (!errorBody.isNullOrEmpty()) {
                    try {
                        val jsonObject = JSONObject(errorBody)
                        val message = jsonObject.getString("message")
                        // Use the message: "Password incorrect"
                        loge("response:","$loginUrl ${message}")
                        emit(WTVResponse.Failure(Throwable(message)))
                    } catch (e: JSONException) {
                        // Handle JSON parsing error
                        loge("Error", "Failed to parse error JSON: $errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            emit(WTVResponse.Failure(Throwable(e.message)))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun provideDRMUserLogin(
        loginUrl: String,
        requestBody: HashMap<String, String>
    ): Flow<WTVResponse<LoginInfo>> = flow {
        try {
            loge("response:","$loginUrl ${requestBody}")
            val response = networkApiCallInterface.makeHttpPostLoginRequest(
                url = loginUrl,
                body = requestBody
            ).execute()
            if(response.isSuccessful && response.body() !=null){
                response.body()?.toJSONObject()?.toString().convertIntoModel(LoginInfo::class.java)?.let {
                    emit(WTVResponse.Success(it))
                }
            }else{
                val errorBody = response.errorBody()?.string()
                if (!errorBody.isNullOrEmpty()) {
                    try {
                        val jsonObject = JSONObject(errorBody)
                        val message = jsonObject.getString("message")
                        // Use the message: "Password incorrect"

                        emit(WTVResponse.Failure(Throwable(message)))
                        loge("Error", "Failed to parse error JSON: $message")
                    } catch (e: JSONException) {
                        // Handle JSON parsing error
                        loge("Error", "Failed to parse error JSON: $errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            emit(WTVResponse.Failure(Throwable(e.message)))
            loge("Error", "Failed to parse error JSON: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)



    suspend fun getCustomerPackageInfo(requestUrl: String): Flow<WTVResponse<CustomerPackageInfo>> = flow {
        try {
            val response = networkApiCallInterface.makeDRMHttpGetRequest(requestUrl).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()?.toJSONObject()?.toString().convertIntoModel(CustomerPackageInfo::class.java)?.let {
                    emit(WTVResponse.Success(it))
                }
            }
        } catch (e: Exception) {
            emit(WTVResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getCustomerChannelInfo(pkgName: String) : Flow<WTVResponse<Set<String>>> = flow {
        try {
            val requestUrl = "${UrlManager.loginDRMBaseUrl}src/api/v1/services-assets/livechannels/$pkgName?page=1&limit=1000"
            val response = withContext(Dispatchers.IO) {
                networkApiCallInterface.makeDRMHttpGetRequest(requestUrl).execute()
            }

            if (response.isSuccessful) {
                val channels = response.body()?.let { body ->
                    try {
                        val jsonObject = body.toJSONObject()
                        val channelInfo = jsonObject?.toString()?.convertIntoModel(CustomerChannelsInfo::class.java)
                        channelInfo?.results?.mapNotNull { it.contentId }?.toSet() ?: emptySet()
                    } catch (e: Exception) {
                        loge("ChannelFetch", "JSON parsing error for $pkgName: ${e.message}")
                        emptySet()
                    }
                } ?: emptySet()

                emit(WTVResponse.Success(channels))
            } else {
                val errorMessage = "Failed to fetch channels for package $pkgName: HTTP ${response.code()} - ${response.message()}"
                loge("ChannelFetch", errorMessage)
                emit(WTVResponse.Failure(HttpException(response)))
            }
        } catch (e: Exception) {
            val errorMessage = "Network error fetching channels for package $pkgName: ${e.message}"
            loge("ChannelFetch", errorMessage)
            emit(WTVResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun provideUserProfileCMS(
        requestBody: HashMap<String, Any>
    ): Flow<WTVResponse<Boolean>> = flow {
        try {
            loge("url:","app/package-update> ${requestBody}")
            val response = networkApiCallInterface.makeHttpAnyPostRequest(url= UrlManager.getCurrentBaseUrl()+"app/package-update", body = requestBody).execute()
            if (response.isSuccessful  && response.body() != null) {
                emit(WTVResponse.Success(true))
            } else {
                emit(WTVResponse.Failure(Throwable("Invalid response received")))
            }
        } catch (e: Exception) {
            emit(WTVResponse.Failure(e))
        }
    }.flowOn(Dispatchers.IO)
}