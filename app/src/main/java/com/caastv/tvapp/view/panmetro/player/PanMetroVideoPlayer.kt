package com.caastv.tvapp.view.panmetro.player

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.rememberAsyncImagePainter
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import com.android.caastv.R
import com.caastv.tvapp.extensions.extractYouTubeId
import com.caastv.tvapp.extensions.hideKeyboard
import com.caastv.tvapp.extensions.loge
import com.caastv.tvapp.extensions.playerErrorHandling
import com.caastv.tvapp.extensions.provideCryptoGuardMediaSource
import com.caastv.tvapp.extensions.toJSONObject
import com.caastv.tvapp.model.data.sseresponse.PlayerFingerprint
import com.caastv.tvapp.model.data.sseresponse.ScrollMessage
import com.caastv.tvapp.utils.uistate.PreferenceManager
import com.caastv.tvapp.view.uicomponent.audio.AnimatedAudio
import com.caastv.tvapp.view.uicomponent.error.CommonDialog
import com.caastv.tvapp.view.uicomponent.fingerprint.ChannelFingerprintOverlay
import com.caastv.tvapp.view.uicomponent.fingerprint.ForceMessageDialog
import com.caastv.tvapp.view.uicomponent.fingerprint.ScrollingMessageOverlay
import com.caastv.tvapp.view.uicomponent.fingerprint.state.ForceMessageDialogState
import com.caastv.tvapp.viewmodels.SharedViewModel
import com.caastv.tvapp.viewmodels.player.PlayerViewModel
import com.techit.youtubelib.interfaces.YouTubePlayer
import com.techit.youtubelib.listeners.AbstractYouTubePlayerListener
import com.techit.youtubelib.options.IFramePlayerOptions
import com.techit.youtubelib.view.YouTubePlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(UnstableApi::class)
@Composable
fun PanMetroVideoPlayer(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    playerViewModel: PlayerViewModel= hiltViewModel()
) {

    val context = LocalContext.current
    val playlist by sharedViewModel.currentPlaylist.collectAsState()
    val selectedChannel by sharedViewModel.selectedChannel.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playerSSERules by sharedViewModel.playerSSERules.collectAsState()
    var visibleForce = remember { mutableStateListOf<ForceMessageDialogState>()}
    val visibleMessages = remember { mutableStateListOf<ScrollMessage>() }
    val visibleFingerprint = remember { mutableStateListOf<PlayerFingerprint>() }
    val playerView = remember {
        mutableStateOf<PlayerView?>(null)
    }
    val epgList = if (playlist.isNotEmpty()) {
        playlist
    } else {
        playerViewModel.provideAvailableEPG()
    }
    val channelRequesters = remember(epgList.size) {
        List(epgList.size) { FocusRequester() }
    }
    val filter by sharedViewModel.filterState.collectAsState()
    val language = filter.language ?: "All Languages"
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorCodeState by remember { mutableStateOf(0) }
    var errorMessageState by remember { mutableStateOf("") }
    var errorTitleState by remember { mutableStateOf("") }
    var isAudio = remember { mutableStateOf(false) }
    var isYoutube = remember { mutableStateOf(false) }
    val youtubeId = remember { mutableStateOf<String?>(null) }

    var watchJob by remember { mutableStateOf<Job?>(null) }



    // whenever the channel changes…
    LaunchedEffect(selectedChannel) {
        watchJob?.cancel()
        watchJob = scope.launch {
            delay(300_000L) // 5 minutes
            selectedChannel?.let { sharedViewModel.recordRecentlyWatched(it) }
        }
    }


    LaunchedEffect(playerSSERules) {
        visibleFingerprint.clear()
        visibleMessages.clear()
        visibleForce.clear()
        playerSSERules?.forceMessages?.forEach { item ->
            val messageId = item._id ?: return@forEach
            val storedTimestamp = PreferenceManager.getForceUpdatedAt(messageId)
            val currentTimestamp = item.updatedAt
            val shouldShow = when {
                currentTimestamp == null -> true
                storedTimestamp == null -> true
                currentTimestamp != storedTimestamp -> true
                else -> false
            }

            if (shouldShow) {
                visibleForce.add(ForceMessageDialogState(item,true))
            }
        }

        //handle it for scroll message
        playerSSERules?.scrollMessages?.forEach { item ->
            val messageId = item._id ?: return@forEach
            val storedTimestamp = PreferenceManager.getScrollUpdatedAt(messageId)
            val currentTimestamp = item.updatedAt
            val shouldShow = when {
                currentTimestamp == null -> true
                storedTimestamp == null -> true
                currentTimestamp != storedTimestamp -> true
                else -> false
            }

            if (shouldShow) {
                visibleMessages.add(item)
            }
        }

        //handle it for fingerprint
        playerSSERules?.fingerprints?.forEach { item ->
            val messageId = item._id ?: return@forEach
            val storedTimestamp = PreferenceManager.getFingerUpdatedAt(messageId)
            val currentTimestamp = item.updatedAt
            val shouldShow = when {
                currentTimestamp == null -> true
                storedTimestamp == null -> true
                currentTimestamp != storedTimestamp -> true
                else -> false
            }

            if (shouldShow) {
                visibleFingerprint.add(item)
            }
        }
    }

    //hide keyboard forcefully
    //
    LaunchedEffect(Unit) {
        context.hideKeyboard()
        //request for user hash
        sharedViewModel.provideUserHash()
        //register scroll message request
        sharedViewModel.provideGlobalSSERequest()
    }
    //Finally return the MutableState
    val selectedChannelIndex = remember {mutableIntStateOf(0) }

    // We’ll need the FocusManager to move focus programmatically
    val focusManager = LocalFocusManager.current
    // Keep track of which index is focused
    val listState = rememberLazyListState()

    // Mutable state for UI updates
    // Set FLAG_SECURE if desired.
    (context as? Activity)?.window?.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )

    val categoryName by sharedViewModel.currentPlaylistName.collectAsState()

    // State management
    var isOverlayVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var overlayHideJob by remember { mutableStateOf<Job?>(null) }


    val stops = selectedChannel.bgGradient
        ?.colors
        ?.sortedBy { it.percentage }
        ?.map { Color(android.graphics.Color.parseColor(it.color)) }
        .orEmpty()

    val brush = if (stops.size >= 2) {
        Brush.horizontalGradient(stops)
    } else {
        Brush.verticalGradient(listOf(Color(0xFF232020), Color(0xFF232020))) // fallback
    }

    // Function to handle overlay visibility
    fun showOverlay() {
        isOverlayVisible = true
        lastInteractionTime = System.currentTimeMillis()

        // Cancel existing hide job if any
        overlayHideJob?.cancel()

        // Start new hide job
        overlayHideJob = scope.launch {
            delay(10000) // 10 seconds
            if (System.currentTimeMillis() - lastInteractionTime >= 10000) {
                isOverlayVisible = false
            }
        }
    }
    LaunchedEffect(Unit) {
        showOverlay()
    }
// Remember the player and recreate it when the DRM type changes
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                // optional: any static setup
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val analyticsListener = object : AnalyticsListener {
            override fun onEvents(player: Player, events: AnalyticsListener.Events) {
                if(!isYoutube.value) {
                    if (events.contains(AnalyticsListener.EVENT_DRM_KEYS_LOADED)) {
                        loge("DRM", "Keys loaded successfully")
                    }
                    if (events.contains(AnalyticsListener.EVENT_DRM_SESSION_MANAGER_ERROR)) {
                        loge("DRM", "Session manager error")
                    }
                }
            }
        }
        // Error listener
        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if(!isYoutube.value) {
                    val httpCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)
                        ?.responseCode
                    val rawCode = httpCode ?: error.errorCode
                    val (code, title, message) = playerErrorHandling(rawCode)
                    errorCodeState = code
                    errorTitleState = title
                    errorMessageState = message
                    showErrorDialog = true

                    // 2) Schedule a retry between 0ms and 120 000ms (i.e. 0–2 minutes)
                    scope.launch {
                        val retryDelay = Random.nextLong(0L, 120_000L)
                        Log.d("Retry", "Retrying live stream in ${retryDelay / 1000}s…")
                        delay(retryDelay)

                        // re‐prepare the same live source
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if(!isYoutube.value) {
                    if (playbackState == Player.STATE_READY) {
                        // Video started playing successfully
                        showErrorDialog = false
                    }
                }
            }
        }
        if(!isYoutube.value) {

            // Attach them
            exoPlayer.addAnalyticsListener(analyticsListener)
            exoPlayer.addListener(errorListener)

            // Kick off the first playback
            exoPlayer.prepare()
        }

        onDispose {
            if(!isYoutube.value) {
                exoPlayer.removeAnalyticsListener(analyticsListener)
                exoPlayer.removeListener(errorListener)
            }
            exoPlayer.release()
            watchJob?.cancel()
        }
    }

    // Whenever the selected channel changes, load its media
    LaunchedEffect(selectedChannel) {
        selectedChannelIndex.intValue = epgList.indexOfFirst {
            it.videoUrl == (selectedChannel?.videoUrl ?: "")
        }
        selectedChannel.videoUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            if(selectedChannel?.contentType.equals("audio",true)){
                isAudio.value = true
            }else if(selectedChannel?.contentType.equals("youtube",true)){
                isYoutube.value = true
                youtubeId.value = selectedChannel?.videoUrl?.extractYouTubeId()
            }else{
                isAudio.value = false
                isYoutube.value = false
            }
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            showErrorDialog = false

            if(!isYoutube.value) {
                val drmData = HashMap<String, String>()
                drmData.put("DRMType", selectedChannel?.drmType ?: "")
                drmData.put("contentId", selectedChannel?.assetId ?: "")
                drmData.put("contentUrl", selectedChannel?.videoUrl ?: "" ?: "")
                val mediaItem = if (selectedChannel?.drmType.equals(
                        "cryptoguard",
                        ignoreCase = true
                    )
                ) {
                    context.provideCryptoGuardMediaSource(
                        contentUrl = selectedChannel?.videoUrl,
                        contentId = selectedChannel?.assetId,
                        logData = drmData
                    )
                } else {
                    MediaItem.fromUri(url)
                }
                loge("Requested Data>", drmData.toJSONObject().toString())
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true  //  Ensure playback starts automatically
            }
            //make fingerprint request
            sharedViewModel.providePlayerSSERequest(channel = "${selectedChannel?.channelNo}:${selectedChannel?.title}")

        }
    }

//    BackHandler {
//        navController.navigate(Destination.epgScreen) {
//            popUpTo(Destination.panMetroScreen) { inclusive = true }
//        }
//    }

    BackHandler {
        //sharedViewModel.updateLanguage(null)                         // clear language filter
        //sharedViewModel.updateGenre(null)                            // clear genre filter
        sharedViewModel.setCurrentPlaylist(emptyList(), "All Channels")
        navController.popBackStack()
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                showOverlay()
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            PreferenceManager.selectedGenreIndex  = 0
                            PreferenceManager.selectedChannelIndex = 0
                            PreferenceManager.lastEpgDataItem       = null
                            navController.popBackStack()
                            true
                        }

//                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
//                            navController.navigate(Destination.genreScreen) {
//                                PreferenceManager.selectedGenreIndex = 0
//                                PreferenceManager.selectedChannelIndex = 0
//                                PreferenceManager.lastEpgDataItem = null
//                                popUpTo(Destination.panMetroScreen) { inclusive = true }
//                            }
//                            true
//                        }
//                        KeyEvent.KEYCODE_DPAD_LEFT -> {
//                            navController.navigate(Destination.epgScreen) {
//                                PreferenceManager.lastEpgDataItem = null
//                                popUpTo(Destination.panMetroScreen) { inclusive = true }
//                            }
//                            true
//                        }
                        KeyEvent.KEYCODE_DPAD_UP-> {
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN-> {
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER -> {
                            if (selectedChannelIndex.intValue>=0 && selectedChannelIndex.intValue < (epgList.size)) {
                                sharedViewModel.updateSelectedChannel(epgList[selectedChannelIndex.intValue])
                            }
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT-> {
                            if (selectedChannelIndex.intValue > 0) {
                                focusManager.moveFocus(FocusDirection.Down)
                                selectedChannelIndex.intValue--
                                scope.launch {
                                    delay(200)
                                    // Scroll into view
                                    listState.animateScrollToItem(selectedChannelIndex.intValue)
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT-> {
                            if (selectedChannelIndex.intValue >= 0 && selectedChannelIndex.intValue < (epgList.size-1)) {
                                focusManager.moveFocus(FocusDirection.Up)
                                selectedChannelIndex.intValue++
                                scope.launch {
                                    delay(200)
                                    // Scroll into view
                                    listState.animateScrollToItem(selectedChannelIndex.intValue)
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {

        if(isYoutube.value){
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    YouTubePlayerView(ctx).apply {
                        enableAutomaticInitialization = false           // 🔑
                        keepScreenOn = true
                        val opts = IFramePlayerOptions.Builder()
                            .controls(0)
                            .fullscreen(0)
                            .autoplay(1)
                            .rel(0)
                            .build()

                        initialize(object : AbstractYouTubePlayerListener() {
                            override fun onReady(player: YouTubePlayer) {
                                youtubeId.value?.let {
                                    Log.e("loadYoutubeVideo","$youtubeId")
                                    player.loadVideo(it, 0f)
                                   // youtubeId.value = it
                                }
                            }
                        }, opts)
                    }
                },
                update = { view ->
                    // If the composable is still alive but the videoId changed, load the new video
                    /*if (lastVideoId.value != videoId) {
                        view.getYouTubePlayerWhenReady { youTubePlayer ->
                            youTubePlayer.loadVideo(videoId, 0f)
                            lastVideoId.value = videoId
                        }
                    }*/
                },
                onRelease = { view -> view.release() }    // called when the composable leaves the tree
            )
        }else{
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.exoplayer_view, null)
                    playerView.value = view.findViewById<PlayerView>(R.id.player_view)

                    playerView.value?.apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        /*PreferenceManager.provideUserHash()?.let {
                            addWatermarkToPlayer(this,it)
                        }*/
                    }

                    view

                }
            )
        }


        if(isAudio.value){
            val gifPainter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/tiled_bar_anim.gif")
                    .decoderFactory(GifDecoder.Factory())
                    .build(),
                contentScale = ContentScale.FillWidth,
            )


            Box(
                modifier = Modifier.fillMaxSize()
                    .background(brush, shape = RoundedCornerShape(0.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.8f)
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.7f)
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedAudio(
                        isSongPlaying = true,
                        channel = selectedChannel
                    )

                    /*Image(
                        painter = gifPainter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop
                    )*/
                }
            }
        }


        if (showErrorDialog) {
            val borderColor = remember(errorCodeState) {
                if (errorCodeState in 606..700) Color(0xFF6B2828) else Color(0xFF49FEDD)
            }

            CommonDialog(
                painter = painterResource(id = R.drawable.media_error),
                showDialog         = true,
                title              = errorTitleState,
                message            = null,
                errorCode          = errorCodeState,
                errorMessage       = errorMessageState,
                borderColor        = borderColor,
                confirmButtonText  = null,
                onConfirm          = null,
                dismissButtonText  = null,
                onDismiss          = null,
            )
        }


        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE-> {
                        exoPlayer.pause()
                    }
                    Lifecycle.Event.ON_STOP-> {
                        //context.showToastS("ON_STOP>${selectedChannel.displayName}")
                        sharedViewModel.persistToPlayerPrefs(prefs = PreferenceManager,selectedChannel = selectedChannel)
                    }
                    Lifecycle.Event.ON_START-> {
                        if(PreferenceManager.lastEpgDataItem != null) {
                            //context.showToastS("ON_START>${PreferenceManager.lastEpgDataItem?.displayName}")
                            sharedViewModel.updateSelectedChannel(PreferenceManager.lastEpgDataItem!!)
                            PreferenceManager.lastEpgDataItem = null
                        }

                        exoPlayer.play()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                exoPlayer.release()
                overlayHideJob?.cancel()
                lifecycleOwner.lifecycle.removeObserver(observer)
                watchJob?.cancel()
            }
        }
        if (isOverlayVisible && selectedChannelIndex.intValue >=0) {
            FullScreenPlayerOverlay(
                selectedIndex         = selectedChannelIndex,
                lazyListState         = listState,
                sharedViewModel       = sharedViewModel,
                playerViewModel       = playerViewModel,
                epgList               = epgList,
                categoryName          = categoryName,
                languageName          = language,
                channelFocusRequesters = channelRequesters,
                onChannelFocused      = { sharedViewModel.updateSelectedChannel(it) }
            )
        }

    }

   //Show dialogs
    visibleForce.forEachIndexed { index, dialogState ->
        if(visibleForce[index].show) {
            if (visibleForce[index].message.forcePush == true){
                ForceMessageDialog(
                    showDialog = true,
                    forceMessage = dialogState.message,
                    onConfirm = {
                    }
                )
            }else{
                if (visibleForce[index].message.updatedAt?.equals(PreferenceManager.getForceUpdatedAt(visibleForce[index].message._id?:""), true) != true){
                    ForceMessageDialog(
                        showDialog = true,
                        forceMessage = dialogState.message,
                        onConfirm = {
                            // Remove this message from the visible list
                            visibleMessages.removeIf { it.updatedAt == visibleForce[index].message.updatedAt }
                            // Mark this dialog as dismissed
                            visibleForce[index] = dialogState.copy(show = false)
                            visibleForce[index].message?.let {
                                PreferenceManager.saveForceUpdatedAt((it._id?:""),(it.updatedAt?:""))
                            }
                        }
                    )
                }
            }
        }
    }


    // Display only visible messages
    visibleMessages.forEach { message ->
        key(message._id) { // Important for proper recomposition
            ScrollingMessageOverlay(
                scrollMessageInfo = message,
                onFinish = { updatedAt ->
                    // Remove this message from the visible list
                    visibleMessages.removeIf { it.updatedAt == updatedAt }

                    // Also save to preferences
                    message._id?.let { id ->
                        PreferenceManager.saveScrollUpdatedAt(id, updatedAt)
                    }
                }
            )
        }
    }


    // Display only visible messages
    visibleFingerprint.forEach { fingerprint ->
        key(fingerprint._id) { // Important for proper recomposition
            ChannelFingerprintOverlay(fingerprintRule = fingerprint,
                onFinish = { updatedAt ->
                    // Remove this message from the visible list
                    visibleMessages.removeIf { it.updatedAt == updatedAt }

                    // Also save to preferences
                    fingerprint._id?.let { id ->
                        PreferenceManager.saveFingerUpdatedAt(id, updatedAt)
                    }
                })
        }
    }
}
