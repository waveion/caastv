package com.caastv.tvapp.utils.network

import com.caastv.tvapp.model.timestamp.ServerTimeStamp
import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url


interface NetworkApiCallInterface {
    @GET
    suspend fun makeHttpSingleDataRequest( @Url url: String): String

    /*@GET
    @Headers
    fun makeHttpGetRequest(@Url url: String): Call<Any>*/

    @GET
    fun makeHttpGetRequest(
        @Url url: String,
        @Header("x-api-key") apiKey: String = UrlManager.headerToken,
        @Header("Content-Type") contentType: String = "application/json"
    ): Call<Any>


    @POST
    fun makeHttpAnyPostRequest(@Url url: String, @Body body: HashMap<String, Any>,
                               @Header("x-api-key") apiKey: String = UrlManager.headerToken,
                               @Header("Content-Type") contentType: String = "application/json"): Call<Any>

    @HTTP(method = "DELETE", path = "", hasBody = true)
    fun makeHttpDeleteRequest(@Url url: String, @Body body: Map<String, String>,
                              @Header("x-api-key") apiKey: String = UrlManager.headerToken,
                              @Header("Content-Type") contentType: String = "application/json"): Call<Any>


    @Multipart
    @POST("upload-logs")
    suspend fun uploadLogs(
        @Part file: MultipartBody.Part,
        @Header("x-api-key") apiKey: String = UrlManager.headerToken,
        @Header("Content-Type") contentType: String = "application/json"
    ): Response<Unit>

    @GET
    fun makeTimestampRequest(@Url url: String): ServerTimeStamp


    /**
     * Make a POST request to a dynamic URL, with a JSON body and custom headers.
     * @param url the full endpoint URL
     * @param headers a map of header names to values
     * @param body a map of key/value pairs to send as JSON in the request body
     */
    @POST
    fun makeHttpPostRequest(
        @Url url: String,
        @Body body: HashMap<String, String>,
        @Header("x-api-key") apiKey: String = UrlManager.headerToken,
        @Header("Content-Type") contentType: String = "application/json",
    ): Call<Any>

    @POST
    fun makeHttpPostLoginRequest(
        @Url url: String,
        @Body body: HashMap<String, String>,
        @Header("x-api-key") apiKey: String? = UrlManager.headerToken,
        @Header("Content-Type") contentType: String? = "application/json"
    ): Call<Any>//: Response<LoginApiResponse> // Changed from Call<Any> to Response<LoginResponse>




    @POST
    fun makeHttpAnyPostRequest(@Url url: String, @Body body: HashMap<String, Any>): Call<Any>

    @GET
    fun makeDRMHttpGetRequest(
        @Url url: String,
        @Header("x-api-key") apiKey: String = UrlManager.drmHeaderToken,
        @Header("Content-Type") contentType: String = "application/json"
    ): Call<Any>

    @POST
    fun makeMultipartJsonResRequest(@Url url: String, @Body requestBody: RequestBody): Call<JsonObject>

    @GET
    @Headers("Content-Type:application/json")
    fun getClientIpInfo(@Url url: String): Call<JsonObject>

}
