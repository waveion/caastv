package com.caastv.tvapp.viewmodels


import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.caastv.tvapp.WTVAppCaastv
import com.caastv.tvapp.extensions.applyAppInventoryApp
import com.caastv.tvapp.extensions.convertIntoModel
import com.caastv.tvapp.extensions.coreEPGLiveData
import com.caastv.tvapp.extensions.logReport
import com.caastv.tvapp.extensions.loge
import com.caastv.tvapp.extensions.provideMacAddress
import com.caastv.tvapp.model.data.DataStoreManager
import com.caastv.tvapp.model.data.FilterPreferences
import com.caastv.tvapp.model.data.FilterState
import com.caastv.tvapp.model.data.banner.Banner
import com.caastv.tvapp.model.data.customapp.InventoryApp
import com.caastv.tvapp.model.data.epgdata.Channel
import com.caastv.tvapp.model.data.epgdata.EPGDataItem
import com.caastv.tvapp.model.data.epgdata.Programme
import com.caastv.tvapp.model.data.filter.PanMetroGenreFilter
import com.caastv.tvapp.model.data.message.ScrollMessageInfo
import com.caastv.tvapp.model.data.sseresponse.GlobalSSEResponse
import com.caastv.tvapp.model.data.sseresponse.PlayerSSEResponse
import com.caastv.tvapp.model.repository.common.WTVNetworkRepositoryImpl
import com.caastv.tvapp.model.repository.database.AppDataRepository
import com.caastv.tvapp.model.repository.login.LoginPrefsRepository
import com.caastv.tvapp.model.wtvdatabase.EPGContract
import com.caastv.tvapp.utils.Constants
import com.caastv.tvapp.utils.network.UrlManager
import com.caastv.tvapp.utils.sealed.WTVListResponse
import com.caastv.tvapp.utils.sealed.WTVResponse
import com.caastv.tvapp.utils.uistate.PreferenceManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
open class SharedViewModel @Inject constructor(
    private val wtvNetworkRepositoryImpl: WTVNetworkRepositoryImpl,
    private val application: Application,
    private val dataStoreManager: DataStoreManager,
    private val filterPreferences: FilterPreferences,
    private val dataRepository: AppDataRepository,
    private val loginPrefsRepository: LoginPrefsRepository,
) : WTVViewModel(application = application, networkApiCallInterfaceImpl = wtvNetworkRepositoryImpl,dataRepository = dataRepository,loginPrefsRepository=loginPrefsRepository) {
    fun provideApplicationInstance() = application.applicationContext as? WTVAppCaastv

    var isFromSplash = MutableStateFlow<Boolean>(false)

    private var globalEventSource: EventSource? = null
    private val _globalSSERules = MutableStateFlow<GlobalSSEResponse?>(null)
    val globalSSERules: StateFlow<GlobalSSEResponse?> = _globalSSERules

    private var playerEventSource: EventSource? = null
    private val _playerSSERules = MutableStateFlow<PlayerSSEResponse?>(null)
    val playerSSERules: StateFlow<PlayerSSEResponse?> = _playerSSERules

    private var prePlayerEventSource: EventSource? = null
    private val _prePlayerSSERules = MutableStateFlow<PlayerSSEResponse?>(null)
    val prePlayerSSERules: StateFlow<PlayerSSEResponse?> = _prePlayerSSERules

    private var scrollEventSource: EventSource? = null
    private val _scrollMessageItemsFlow = MutableStateFlow<List<ScrollMessageInfo>>(emptyList())
    val scrollMessageItemsFlow: StateFlow<List<ScrollMessageInfo>> = _scrollMessageItemsFlow


    private val _filteredEPGList = MutableStateFlow<List<EPGDataItem>>(emptyList())
    val filteredEPGList: StateFlow<List<EPGDataItem>> = _filteredEPGList.asStateFlow()

    private val _epgChannels = MutableStateFlow<List<Channel>>(emptyList())
    val epgChannels: StateFlow<List<Channel>> = _epgChannels.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Channel>>(emptyList())
    val searchResults: StateFlow<List<Channel>> = _searchResults.asStateFlow()

    private val _wishlistPopupProgram = MutableStateFlow<Programme?>(null)
    val wishlistPopupProgram: StateFlow<Programme?> = _wishlistPopupProgram.asStateFlow()

    private val _wishlistAlertProgram = MutableStateFlow<Programme?>(null)
    val wishlistAlertProgram: StateFlow<Programme?> = _wishlistAlertProgram.asStateFlow()

    private val _wishlist = MutableStateFlow<List<Programme>>(emptyList())
    val wishlist: StateFlow<List<Programme>> = _wishlist.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _panMetroGenreState = MutableStateFlow(PanMetroGenreFilter())
    val panMetroGenreState: StateFlow<PanMetroGenreFilter> = _panMetroGenreState.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<EPGDataItem>>(emptyList())
    val currentPlaylist: StateFlow<List<EPGDataItem>> = _currentPlaylist

    private val _lastSelectedChannelIndex = MutableStateFlow<Int>(-1)
    val lastSelectedChannelIndex: StateFlow<Int> = _lastSelectedChannelIndex

    private val _genreScreenLastGenreIndex = MutableStateFlow(0)
    val genreScreenLastGenreIndex: StateFlow<Int> = _genreScreenLastGenreIndex

    private val _genreScreenLastChannelIndex = MutableStateFlow(0)
    val genreScreenLastChannelIndex: StateFlow<Int> = _genreScreenLastChannelIndex

    private val _lastSearchSelectedIndex = MutableStateFlow(0)
    val lastSearchSelectedIndex: StateFlow<Int> = _lastSearchSelectedIndex

    private val _lastHomeCategory = MutableStateFlow(0)
    val lastHomeCategory: StateFlow<Int> = _lastHomeCategory

    private val _lastHomeChannel = MutableStateFlow(0)
    val lastHomeChannel: StateFlow<Int> = _lastHomeChannel

    var ChannelScreenlastSelectedChannelIndex = mutableStateOf(0)
        private set

    private val _availableProgram = MutableStateFlow<List<Programme>>(emptyList())
    val availableProgram: StateFlow<List<Programme>> = _availableProgram.asStateFlow()
    var lastFocusedChannelIndex = mutableStateOf(0)
        private set


    private val _recentlyWatched = MutableStateFlow<List<EPGDataItem>>(emptyList())
    val recentlyWatched: StateFlow<List<EPGDataItem>> = _recentlyWatched

    // StateFlow for raw IDs
    private val _favoriteChannelIds = MutableStateFlow<List<String>>(emptyList())
    val favoriteChannelIds: StateFlow<List<String>> = _favoriteChannelIds.asStateFlow()

    private val _favoriteChannels = MutableStateFlow<List<Channel>>(emptyList())
    val favoriteChannels: StateFlow<List<Channel>> = _favoriteChannels.asStateFlow()

    init {
        //provideGlobalFingerprintInfo()
        //provideScrollMessageInfo()
        // only load once, no continuous observation to avoid overriding

        viewModelScope.launch {
            isInitializeData
                .filter { it }        // only when it becomes true
                .first()
            val saved = filterPreferences.filterFlow.first() // <-- one-time load only
            _filterState.value = saved
            applyFilters()
        }
        viewModelScope.launch {
            wtvEPGList.collectLatest { epgList ->
                val savedIds = PreferenceManager.recentChannelIds
                _recentlyWatched.value = savedIds.mapNotNull { id ->
                    epgList.firstOrNull { it.channelId == id }
                }
            }
        }
        val savedIds = PreferenceManager.recentChannelIds
        _recentlyWatched.value = savedIds.mapNotNull { id ->
            wtvEPGList.value.firstOrNull { it.channelId == id }
        }
        // load banners
        viewModelScope.launch {
            provideBanners()
        }
        viewModelScope.launch {
            fetchInventoryApps()
        }
        viewModelScope.launch {
            fetchFavorites()
        }
    }

    fun requiredOfflineDataInitialization(){
        viewModelScope.launch {
            val saved = filterPreferences.filterFlow.first() // <-- one-time load only
            _filterState.value = saved
            applyFilters()
        }
        viewModelScope.launch {
            wtvEPGList.collectLatest { epgList ->
                val savedIds = PreferenceManager.recentChannelIds
                _recentlyWatched.value = savedIds.mapNotNull { id ->
                    epgList.firstOrNull { it.channelId == id }
                }
            }
        }
        val savedIds = PreferenceManager.recentChannelIds
        _recentlyWatched.value = savedIds.mapNotNull { id ->
            wtvEPGList.value.firstOrNull { it.channelId == id }
        }

        filterPanMetroChannelsByGenre()
    }

    fun setCurrentPlaylist(list: List<EPGDataItem>) {
        _currentPlaylist.value = list
    }

    fun clearWishlistPopup() { _wishlistPopupProgram.value = null }

    fun addToWishlist(program: Programme) {
        program.watchedAt = System.currentTimeMillis()
        _wishlist.value += program
        clearWishlistPopup()
    }


    fun fetchFavorites() {
        viewModelScope.launch {
            val userId = PreferenceManager.getCMSLoginResponse()?.loginData?.userId
                ?: return@launch
            wtvNetworkRepositoryImpl
                .getFavorites(userId)
                .collect { result ->
                    when (result) {
                        is WTVListResponse.Success -> {
                            _favoriteChannelIds.value = result.data
                            _favoriteChannels.value = wtvEPGList.value
                                .orEmpty()
                                .filter  { it.channelId in result.data }
                                .mapNotNull { it.tv?.channel }
                        }
                        is WTVListResponse.Failure -> {
                            logReport("fetchFavorites failed: ${result.error.message}")
                        }
                    }
                }
        }
    }

    fun addFavorite(channelId: String) {
        viewModelScope.launch {
            val userId = PreferenceManager.getCMSLoginResponse()?.loginData?.userId ?: return@launch
            wtvNetworkRepositoryImpl.addFavorite(userId, channelId)
                .collect { result ->
                    if (result is WTVResponse.Success) fetchFavorites()
                    else logReport("addFavorite failed: $result")
                }
        }
    }

    fun removeFavorite(channelId: String) {
        viewModelScope.launch {
            val userId = PreferenceManager.getCMSLoginResponse()?.loginData?.userId ?: return@launch
            wtvNetworkRepositoryImpl.removeFavorite(userId, channelId)
                .collect { result ->
                    if (result is WTVResponse.Success) fetchFavorites()
                    else logReport("removeFavorite failed: $result")
                }
        }
    }

    fun onShowWishlistPopup(program: Programme) { _wishlistPopupProgram.value = program }

    fun clearWishlistAlert() { _wishlistAlertProgram.value = null }


    val authToken = dataStoreManager.authToken

    fun checkUserLoginStatus(onLoggedIn: () -> Unit, onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authToken.collectLatest { token ->
                if (token.isNullOrEmpty()) onLoggedOut() else onLoggedIn()
            }
        }
    }


    fun updateLastFocusedChannel(index: Int) {
        lastFocusedChannelIndex.value = index
    }

    suspend fun fetchEPGList(context: Context): List<EPGDataItem> {
        return withContext(Dispatchers.IO) {
            val cursor = context.contentResolver.query(
                EPGContract.EPGEntry.CONTENT_URI, null, null, null, null
            )
            val list = mutableListOf<EPGDataItem>()
            cursor?.use {
                while (it.moveToNext()) {
                    val dataJson = it.getString(it.getColumnIndexOrThrow(EPGContract.EPGEntry.COLUMN_DATA))
                    Gson().fromJson(dataJson, EPGDataItem::class.java)?.let { item ->
                        list.add(item)
                    }
                }
            }
            list
        }
    }

   /* fun observeEPGChanges(context: Context): Flow<List<EPGDataItem>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                launch {
                    if (isOfflineEnable.value.not()) { // Only fetch if not in offline mode
                        val epgList = fetchEPGList(context)
                        _epgChannels.value = epgList.mapNotNull { it.tv?.channel }
                        applyFilters()
                        trySend(epgList)
                    }
                }
            }
        }

        context.contentResolver.registerContentObserver(
            EPGContract.EPGEntry.CONTENT_URI, true, observer
        )

        // Initial fetch only if not in offline mode
        if (_isOfflineEnable.value.not()) {
            val epgList = fetchEPGList(context)
            _epgChannels.value = epgList.mapNotNull { it.tv?.channel }
            applyFilters()
            trySend(epgList)
        }

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)*/

    fun updateGenre(genre: String?) {
        val newGenre = if (genre.equals("All",true) ) null else genre
        _filterState.value = _filterState.value.copy(genre = newGenre)
        saveFilters()
        applyFilters()
    }
    fun updatePanMetroGenre(genre: String?,videoUrl:String?) {
        _panMetroGenreState.value = _panMetroGenreState.value.copy(genre = genre,videoUrl=videoUrl)
    }

    fun updateLanguage(language: String?) {
        val newLanguage = if (language.equals("All",true) ) null else language
        _filterState.value = _filterState.value.copy(language = newLanguage)
        saveFilters()
        applyFilters()
    }

    private val _currentPlaylistName = MutableStateFlow("All Channels")
    val currentPlaylistName: StateFlow<String> = _currentPlaylistName

    fun setCurrentPlaylist(list: List<EPGDataItem>, name: String) {
        _currentPlaylist.value = list
        _currentPlaylistName.value = name
    }

    private fun saveFilters() {
        filterPreferences.saveFilter(viewModelScope, _filterState.value)
    }

    private fun savePanMetroGenre() {
        filterPreferences.saveGenreSelection(scope=viewModelScope, genreFilter =  _panMetroGenreState.value)
    }




    private fun applyFilters() {
        val fullList = provideApplicationContext().coreEPGLiveData().value?:wtvEPGList.value
        val filter = _filterState.value

        val filtered = fullList?.filter { epgItem ->
            val genreList = epgItem.genre?.map { it.name }.orEmpty()
            val language = epgItem.language?.name.orEmpty()

            val genreMatch = filter.genre == null || genreList.any { it.equals(filter.genre, true) }
            val languageMatch = filter.language == null || language.equals(filter.language, true)

            genreMatch && languageMatch
        }

        _filteredEPGList.value = filtered?: arrayListOf()
    }

    fun searchChannels(query: String) {
        viewModelScope.launch {
            // 1) Grab the full EPGDataItem list
            val epgList: List<EPGDataItem> = wtvEPGList.value ?: emptyList()

            // 2) Shortcut: if blank, return everything (but map to program titles)
            if (query.isBlank()) {
                val all = epgList.mapNotNull { item ->
                    val title = item.title ?: return@mapNotNull null
                    item.tv?.channel?.copy(
                        displayName = title,
                        logoUrl     = item.thumbnailUrl,
                        videoUrl    = item.videoUrl,
                        genreId     = item.genreId ?: "Unknown",
                        channelNo   = item.channelNo
                    )
                }
                _searchResults.value = all
                return@launch
            }

            // 3) Filter by program title only
            val filtered = epgList.mapNotNull { item ->
                val progTitle = item.title.orEmpty()
                val matches   = progTitle.contains(query, ignoreCase = true)
                if (!matches) return@mapNotNull null
                // 4) Build a Channel whose displayName is the program title
                item.tv?.channel?.copy(
                    displayName = progTitle,
                    logoUrl     = item.thumbnailUrl,
                    videoUrl    = item.videoUrl,
                    genreId     = item.genreId ?: "Unknown",
                    channelNo   = item.channelNo
                )
            }
            _searchResults.value = filtered
        }
    }





    fun provideAvailableProgram(programs: List<Programme>): List<Programme> {
        val now = System.currentTimeMillis()
        return programs
            .filter { program ->
                val start = program.startTime
                val end   = program.endTime
                // Only include if both times are non-null and end is strictly in the future:
                if (start == null || end == null) return@filter false
                // 1) Currently running: start <= now < end
                // 2) Upcoming: now < start
                (start <= now && now < end) || (now < start)
            }
            .distinctBy { it.startTime to it.endTime }
            .sortedBy { it.startTime }
    }

    /** Persist into SharedPreferences on minimize */
    fun persistToGenrePrefs(prefs: PreferenceManager,selectedGenreIndex:Int,selectedChannelIndex:Int) {
        prefs.selectedGenreIndex = selectedGenreIndex
        prefs.selectedChannelIndex = selectedChannelIndex
    }
    fun persistToPlayerPrefs(prefs: PreferenceManager,selectedChannel:EPGDataItem) {
        prefs.lastEpgDataItem = selectedChannel
    }


    /** Persist into SharedPreferences on minimize */
    fun restorePlayerPrefs(prefs: PreferenceManager) {
        prefs.lastEpgDataItem?.let { updateSelectedChannel(it) }
    }

    fun provideGlobalSSERequest() {
        val loginInfo = PreferenceManager.getCMSLoginResponse()
        var packageInfo:String?=null
        var userInfo:String?=null
        loginInfo?.let {
            packageInfo = it.loginData?.packages?.joinToString(
                separator = ","
            ) { it.packageName }

            userInfo = "${it.loginData?.userId}:${it.loginData?.username}"
        }

        val queryBuilder = (UrlManager.getCurrentBaseUrl() + "app/combined-sse?")
            .toUri()
            .buildUpon()
        // Only append if values are not null or blank
        packageInfo?.takeIf { it.isNotBlank() }?.let {
            queryBuilder.appendQueryParameter("package", it)
        }

        userInfo?.takeIf { it.isNotBlank() }?.let {
            queryBuilder.appendQueryParameter("user", it)
        }
        loginInfo?.loginData?.provideUserRegionCode()?.takeIf { it.isNotBlank() }?.let {
            queryBuilder.appendQueryParameter("region", it)
        }?: run {
            queryBuilder.appendQueryParameter("region", "01")
        }

        queryBuilder.appendQueryParameter("appVersion","caastv_${application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName}")

        application.provideMacAddress()?.let {
            queryBuilder.appendQueryParameter("macId", it)
        }

        /*application.getIPAddress()?.let {
            Log.d("deviceIP>",it)
            queryBuilder.appendQueryParameter("deviceIP", it)
        }*/

        val sseUrl = queryBuilder.build().toString()
        loge("finalUrl>",sseUrl)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val withHeaders = original.newBuilder()
                    .header("x-api-key", Constants.HEADER_TOKEN)
                    .build()
                chain.proceed(withHeaders)
            }
            .retryOnConnectionFailure(true)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Required for SSE!
            .build()

        val request = Request.Builder()
            .url(sseUrl) // replace with your endpoint URL
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // Log or perform actions on open
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                // Update the global state with new event data.
                loge("SSE>",sseUrl+data.toString())
                try {
                    data.toString()
                        .convertIntoModel(GlobalSSEResponse::class.java)?.let {
                            _globalSSERules.value = it
                        }
                } catch (e: Exception) {
                    loge("SSE>", "Error parsing JSON: ${e.message}")
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // Optionally handle close events.
                logReport("SSE>", "Connection closed")
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                // Handle failures (and consider restarting the connection).
                logReport("SSE SSE", "Connection failed: ${t?.message}")
            }
        }

        // Start the SSE connection.
        globalEventSource = EventSources.createFactory(client).newEventSource(request, listener)
    }

    fun providePlayerSSERequest(
        channel: String?=null,
    ) {

        val loginInfo = PreferenceManager.getCMSLoginResponse()
        playerEventSource?.let {
            playerEventSource?.cancel()
            playerEventSource = null
        }
        val queryBuilder = (UrlManager.getCurrentBaseUrl() +"app/combined-sse?")
            .toUri()
            .buildUpon()
        channel?.takeIf { it.isNotBlank() }?.let {
            queryBuilder.appendQueryParameter("liveChannel", it)
        }

        loginInfo?.loginData?.provideUserRegionCode()?.takeIf { it.isNotBlank() }?.let {
            queryBuilder.appendQueryParameter("region", it)
        }

        queryBuilder.appendQueryParameter("appversion","caastv_${application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName}")

        application.provideMacAddress()?.let {
            queryBuilder.appendQueryParameter("macId", it)
        }
        val sseUrl = queryBuilder.build().toString()
        loge("PlayerFingerprint url>",sseUrl)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val withHeaders = original.newBuilder()
                    .header("x-api-key", Constants.HEADER_TOKEN)
                    .build()
                chain.proceed(withHeaders)
            }
            .retryOnConnectionFailure(true)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Required for SSE!
            .build()

        val request = Request.Builder()
            .url(sseUrl) // replace with your endpoint URL
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // Log or perform actions on open
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                // Update the global state with new event data.
                loge("SSE >",sseUrl+data.toString())

                try {
                    data.toString()
                        .convertIntoModel(PlayerSSEResponse::class.java)?.let {
                            _playerSSERules.value = it
                        }
                    loge("SSE >", data.toString())

                } catch (e: Exception) {
                    loge("SSE ", "Error parsing JSON: ${e.message}")
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // Optionally handle close events.
                logReport("SSE", "Connection closed")
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                // Handle failures (and consider restarting the connection).
                logReport("PlayerFingerprint SSE", "Connection failed: ${t?.message}")
            }
        }

        // Start the SSE connection.
        playerEventSource = EventSources.createFactory(client).newEventSource(request, listener)
    }


    fun providePrePlayerSSERequest(
        channel: String?=null,//"1003:RAAPCHIK"
    ) {
        prePlayerEventSource?.let {
            prePlayerEventSource?.cancel()
            prePlayerEventSource = null
        }
        val queryBuilder = (UrlManager.getCurrentBaseUrl() +"app/combined-sse?")
            .toUri()
            .buildUpon()
        channel?.takeIf { it.isNotBlank() }?.let {
            queryBuilder.appendQueryParameter("liveChannel", it)
        }

        PreferenceManager.getCMSLoginResponse()?.loginData?.provideUserRegionCode().takeIf { it?.isNotBlank() == true }?.let {
            queryBuilder.appendQueryParameter("region", it)
        }?: run {
            queryBuilder.appendQueryParameter("region", "01")
        }

        queryBuilder.appendQueryParameter("appVersion","panmetro_${application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName}")

        application.provideMacAddress()?.let {
            queryBuilder.appendQueryParameter("macId", it)
        }
        val sseUrl = queryBuilder.build().toString()
        loge("PlayerFingerprint url>",sseUrl)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val withHeaders = original.newBuilder()
                    .header("x-api-key", Constants.HEADER_TOKEN)
                    .build()
                chain.proceed(withHeaders)
            }
            .retryOnConnectionFailure(true)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Required for SSE!
            .build()

        val request = Request.Builder()
            .url(sseUrl) // replace with your endpoint URL
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // Log or perform actions on open
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                // Update the global state with new event data.
                loge("SSE >",sseUrl+data.toString())

                try {
                    data.toString()
                        .convertIntoModel(PlayerSSEResponse::class.java)?.let {
                            _prePlayerSSERules.value = it
                        }
                    loge("SSE >", data.toString())

                } catch (e: Exception) {
                    loge("SSE ", "Error parsing JSON: ${e.message}")
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // Optionally handle close events.
                loge("SSE", "Connection closed")
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                // Handle failures (and consider restarting the connection).
                loge("PlayerFingerprint SSE", "Connection failed: ${t?.message}")
            }
        }

        // Start the SSE connection.
        prePlayerEventSource = EventSources.createFactory(client).newEventSource(request, listener)
    }

    fun stopGlobalSSE() {
        globalEventSource?.cancel()
        globalEventSource = null
    }
    fun stopPlayerSSE() {
        playerEventSource?.cancel()
        playerEventSource = null
    }


    fun updateLastSearchSelectedIndex(idx: Int) {
        _lastSearchSelectedIndex.value = idx
    }

    fun updateLastSelectedChannelIndex(index: Int) {
        _lastSelectedChannelIndex.value = index
    }

    fun updateGenreScreenLastGenreIndex(index: Int) {
        _genreScreenLastGenreIndex.value = index
    }

    fun updateGenreScreenLastChannelIndex(index: Int) {
        _genreScreenLastChannelIndex.value = index
    }

    fun updateChannelScreenLastSelectedChannelIndex(index: Int) {
        ChannelScreenlastSelectedChannelIndex.value = index
    }

    fun updateLastHomeSelection(categoryIndex: Int, channelIndex: Int) {
        _lastHomeCategory.value = categoryIndex
        _lastHomeChannel.value = channelIndex
    }

    fun recordRecentlyWatched(item: EPGDataItem) {
        Log.d("SharedViewModel", "▶ recordRecentlyWatched: ${item.channelId}")
        val current = _recentlyWatched.value.toMutableList()
        current.removeAll { it.channelId == item.channelId }
        current.add(0, item)
        if (current.size > 20) current.removeLast()
        _recentlyWatched.value = current
        val ids = current.map { it.channelId.toString() }
        Log.d("SharedViewModel", "↳ saving recents to prefs: $ids")
        PreferenceManager.recentChannelIds = ids
    }

    fun clearRecentlyWatched() {
        _recentlyWatched.value = emptyList()
        PreferenceManager.clearRecentlyWatched()
    }


    fun stopPrePlayerSSE() {
        prePlayerEventSource?.cancel()
        prePlayerEventSource = null
    }

    override fun onCleared() {
        filterPreferences.clearFilter(viewModelScope)
        stopGlobalSSE()
        stopPlayerSSE()
        stopPrePlayerSSE()
        super.onCleared()
    }
}
