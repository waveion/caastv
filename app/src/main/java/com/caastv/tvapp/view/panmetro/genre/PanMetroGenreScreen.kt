package com.caastv.tvapp.view.panmetro.genre

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.android.caastv.R
import com.caastv.tvapp.extensions.loge
import com.caastv.tvapp.model.data.epgdata.EPGDataItem
import com.caastv.tvapp.model.data.manifest.TabInfo
import com.caastv.tvapp.utils.uistate.PreferenceManager
import com.caastv.tvapp.view.navigationhelper.Destination
import com.caastv.tvapp.viewmodels.SharedViewModel

@SuppressLint("StateFlowValueCalledInComposition", "UnrememberedMutableState")
@Composable
fun PanmetroGenreScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val appManifestData = sharedViewModel.manifestData.collectAsState()
    val tabItems by remember { mutableStateOf<List<TabInfo>>(appManifestData.value?.tab ?: emptyList()) }

    val epgList = sharedViewModel.wtvEPGList.value

    val availableGenre = sharedViewModel.manifestData.value?.genre?: arrayListOf()

    val filteredChannels by sharedViewModel.filteredPanMetroChannels.collectAsState()

    var channelToGenreFocus = remember { mutableStateOf(false) }
    val selectedVideoUrl by remember(sharedViewModel.selectedChannel.value) {
        mutableStateOf(sharedViewModel.selectedChannel)
    }


    val selectedGenreIndex: MutableState<Int> = remember { mutableStateOf( 0) }
    val selectedChannelIndex: MutableState<Int> = remember { mutableStateOf( 0) }

    var genreListFocusRequester = remember { FocusRequester() }
    var channelListFocusRequester = remember { FocusRequester() }

    val genreFocusRequesters = remember(availableGenre) { List(availableGenre.size) { FocusRequester() } }

    val onVideoChange: (EPGDataItem,Int) -> Unit = { channel,channelIndex ->
        selectedChannelIndex.value = channelIndex
        sharedViewModel.updateSelectedChannel(channel)  // This method should update selectedVideoUrl.
    }
    val lastGenreIndex by sharedViewModel.genreScreenLastGenreIndex.collectAsState()
    val lastChannelIndex by sharedViewModel.genreScreenLastChannelIndex.collectAsState()


    BackHandler {
        navController.navigate(Destination.homeScreen) {
//            popUpTo(Destination.genreScreen) { inclusive = true }
        }
    }

    LaunchedEffect(Unit) {
        //register scroll message request
        sharedViewModel.provideGlobalSSERequest()
        //request for user hash
        sharedViewModel?.provideUserHash()

        val genreName = availableGenre.getOrNull(lastGenreIndex)?.name ?: "All"
        selectedGenreIndex.value = lastGenreIndex.coerceAtLeast(0)
        sharedViewModel.filterPanMetroChannelsByGenre(genreName)
    }
    LaunchedEffect(filteredChannels) {
        if (filteredChannels.isNotEmpty()) {
            // on splash → defaultChannel
            val defaultChannel = epgList
                ?.find { it?.channelId == appManifestData.value?.landingChannel?.channelId }
            if (sharedViewModel.isFromSplash.value && defaultChannel != null) {
                selectedChannelIndex.value = epgList?.indexOf(defaultChannel) ?: 0
                sharedViewModel.updateSelectedChannel(defaultChannel)
                sharedViewModel.isFromSplash.value = false

                // safe focus
                try {
                    channelListFocusRequester.requestFocus()
                } catch (t: Throwable) {
                    Log.e("PanmetroGenre", "couldn't focus default splash channel", t)
                }
            }

            channelToGenreFocus.value = false
            selectedGenreIndex.value  = lastGenreIndex.coerceAtLeast(0)
            selectedChannelIndex.value = lastChannelIndex.coerceAtLeast(0)

            // safe focus again
            try {
                channelListFocusRequester.requestFocus()
            } catch (t: Throwable) {
                Log.e("PanmetroGenre", "couldn't focus restored channel #${selectedChannelIndex.value}", t)
            }
        } else {
            // no channels → keep focus on genre
            channelToGenreFocus.value = true
            genreFocusRequesters
                .getOrNull(selectedGenreIndex.value)
                ?.let { req ->
                    try {
                        req.requestFocus()
                    } catch (t: Throwable) {
                        Log.e("PanmetroGenre", "couldn't focus genre #${selectedGenreIndex.value}", t)
                    }
                }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F3A6B)) // Example dark blue background
    ){
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black)
        ) {
            // 1) Top bar with brand logo on left and date/time on right
//            PermettoTopBar()
//            GradientBackground(content = {
            // 2) Main content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp)
            ) {
                Box(modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    Row(modifier = Modifier) {
                        // Left: Categories
                        GenreListMenu(
                            genres = availableGenre,
                            genreSelectedIndex = selectedGenreIndex,
                            channelToGenreFocus = channelToGenreFocus,
                            focusRequesters = genreFocusRequesters,
                            // On selection, update the index, filter channels, and move focus to the channel list.
                            onCategoryForward = { index, selectedGenre ->
                                selectedGenreIndex.value = index
                                selectedChannelIndex.value = 0
                                sharedViewModel.updateGenreScreenLastGenreIndex(index)
                                val genreName = selectedGenre.name ?: "All"
                                sharedViewModel.filterPanMetroChannelsByGenre(genreName)
                                // Request focus back to the channel list so its first item is focused.
//                                channelListFocusRequester.requestFocus()
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Middle: Channel List
                        ChannelListMenuScreen(
                            sharedViewModel= sharedViewModel,
                            selectedChannelIndex= selectedChannelIndex,
                            channelListFocusRequester = channelListFocusRequester,
                            channelToGenreFocus = channelToGenreFocus,
                            onNavigateToGenre = {
                                // Request focus on the genre item that was last selected.
                                genreFocusRequesters.getOrNull(selectedGenreIndex.value)?.let { requester ->
                                    try {
                                        channelToGenreFocus.value = true
                                        requester.requestFocus()
                                    } catch (e: IllegalStateException) {
                                        loge("FocusError", "FocusRequester not initialized ${e.message}")
                                    }
                                }
                            },
                            onVideoChange =  onVideoChange,
                            onPlayerScreenIntent = { channelInfo ->
                                epgList?.find { it?.videoUrl == channelInfo?.videoUrl }
                                    ?.let { channelItem ->
                                        val channelsInThisGenre = sharedViewModel.filteredPanMetroChannels.value
                                        val genreName = availableGenre
                                            .getOrNull(selectedGenreIndex.value)
                                            ?.name
                                            ?: "All"
                                        sharedViewModel.updateGenreScreenLastChannelIndex(selectedChannelIndex.value)
                                        sharedViewModel.setCurrentPlaylist(channelsInThisGenre, genreName)
                                        sharedViewModel.updateLanguage(null)
                                        sharedViewModel.updateSelectedChannel(channelItem)
                                        if(PreferenceManager.getAppSettings()?.isPlayerAnimationOverlay == true){
                                            navController.navigate(Destination.animationPlayer) {
                                                popUpTo(Destination.animationPlayer) { inclusive = true }
                                            }
                                        }else{
                                            navController.navigate(Destination.panMetroScreen) {
                                                popUpTo(Destination.panMetroScreen) { inclusive = true }
                                            }
                                        }
                                    }

                            }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .background(Color.Transparent)
                    ) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(.6f)) {
                            GenreMultiDRMPlayer (
                                selectedChannelIndex= selectedChannelIndex,
                                sharedViewModel = sharedViewModel
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(1f)
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize() // Force the inner Box to fill the outer Box.
                                    .background(Color.Transparent, shape = RoundedCornerShape(10.dp))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.banner3),
                                    contentDescription = "Panmetro Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize() // Stretch the image to fill the inner Box.
                                        .clip(RoundedCornerShape(16.dp)) // Adjust the corner radius as needed.
                                )
                            }
                        }

                    }
                }
            }
            //powered by footer
//            PoweredBy()
        }
    }


    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                //context.showToastS("ON_STOP>${selectedGenreIndex.value} and ${selectedChannelIndex.value}")
                sharedViewModel.persistToGenrePrefs(prefs = PreferenceManager,selectedGenreIndex=selectedGenreIndex.value, selectedChannelIndex = selectedChannelIndex.value)
            }else if (event == Lifecycle.Event.ON_START) {
                if(PreferenceManager.selectedChannelIndex >0) {
                   // context.showToastS("ON_RESUME>${PreferenceManager.selectedGenreIndex} and ${PreferenceManager.selectedChannelIndex}")
                    selectedGenreIndex.value = PreferenceManager.selectedGenreIndex
                    selectedChannelIndex.value = PreferenceManager.selectedChannelIndex
                    availableGenre.getOrNull(selectedGenreIndex.value)?.let {
                        sharedViewModel.filterPanMetroChannelsByGenre(it.name)
                        /*genreViewModel.filteredPanMetroChannels.value.getOrNull(sharedViewModel.selectedChannelIndex.value)
                            ?.let { it1 -> sharedViewModel.updateSelectedChannel(it1) }*/
                    }
                    PreferenceManager.selectedGenreIndex = 0
                    PreferenceManager.selectedChannelIndex = 0
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}