package com.caastv.tvapp.view.channels

import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.caastv.tvapp.extensions.hideKeyboard
import com.caastv.tvapp.model.data.banner.Banner
import com.caastv.tvapp.model.data.epgdata.Channel
import com.caastv.tvapp.utils.theme.base_color
import com.caastv.tvapp.utils.theme.bg_card_color
import com.caastv.tvapp.utils.theme.screen_bg_color
import com.caastv.tvapp.utils.uistate.PreferenceManager
import com.caastv.tvapp.view.navigationhelper.CategoryMenu
import com.caastv.tvapp.view.navigationhelper.Destination
import com.caastv.tvapp.view.navigationhelper.ExpandableNavigationMenu
import com.caastv.tvapp.view.navigationhelper.LanguageMenu
import com.caastv.tvapp.viewmodels.SharedViewModel

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
@Composable
fun ChannelScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    showBanner: Boolean = true,
    bannerList: List<Banner> = emptyList()
) {
    val context = LocalContext.current
    val appManifestData = sharedViewModel.manifestData.collectAsState()
    val categories = appManifestData.value?.genre ?: arrayListOf()
    val languages  = appManifestData.value?.language ?: arrayListOf()
    val filteredContent by sharedViewModel.filteredEPGList.collectAsState(emptyList())
    val filterState by sharedViewModel.filterState.collectAsState()
    val categorySelectedIndex = remember { mutableStateOf(0) }
    val languageSelectedIndex = remember { mutableStateOf(0) }
    val channelList = filteredContent.mapNotNull { epgItem ->
        epgItem.tv?.channel?.copy(
            videoUrl   = epgItem?.videoUrl,
            logoUrl    = epgItem?.thumbnailUrl,
            genreId    = epgItem?.genreId ?: "",
            channelNo  = epgItem?.channelNo,
            bgGradient  = epgItem?.bgGradient
        )
    }
    val channelFocusRequesters = remember(channelList.size) {
        List(channelList.size) { FocusRequester() }
    }

    val isBannerVisible = showBanner && bannerList.isNotEmpty()
    var backPressCount by remember { mutableStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }
    //val gridState = rememberLazyGridState()
    // Create one FocusRequester per item for categories and languages.
    val categoryFocusRequesters = remember(categories) { List(categories.size) { FocusRequester() } }
    val languageFocusRequesters = remember(languages) { List(languages.size) { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    val genre = sharedViewModel.filterState.value.genre ?: "All Channels"
    val lang  = sharedViewModel.filterState.value.language ?: "All Languages"
    val menuFocusRequester = remember { FocusRequester() }


    LaunchedEffect(Unit) {
        context.hideKeyboard()
    }

    LaunchedEffect(filterState, categories, languages) {
        if (categories.isNotEmpty()) {
            categorySelectedIndex.value =
                categories.indexOfFirst { it.name == filterState.genre }
                    .takeIf { it >= 0 } ?: 0
        } else {
            categorySelectedIndex.value = -1
        }
        if (languages.isNotEmpty()) {
            languageSelectedIndex.value =
                languages.indexOfFirst { it.name == filterState.language }
                    .takeIf { it >= 0 } ?: 0
        } else {
            languageSelectedIndex.value = -1
        }
    }
    var hasRestoredFocus by remember { mutableStateOf(false) }
    LaunchedEffect(channelList) {
        if (channelList.isNotEmpty() && !hasRestoredFocus) {
            // clamp into bounds
            val idx = sharedViewModel.ChannelScreenlastSelectedChannelIndex.value
                .coerceIn(0, channelList.lastIndex)

            channelFocusRequesters.getOrNull(idx)?.let { requester ->
                try {
                    requester.requestFocus()
                } catch (t: Throwable) {
                    Log.e("ChannelScreen", "couldn't restore focus to channel #$idx", t)
                }
            }
            hasRestoredFocus = true
        }
    }
//    BackHandler {
//        navController.navigate(Destination.epgScreen) {
//            popUpTo(0) { inclusive = true }
//            launchSingleTop = true
//        }
//    }
    BackHandler {
        backPressCount++

        if (backPressCount >= 2) {
            // Show exit confirmation if pressed back twice
            showExitDialog = true
        } else {
            // First back: just clear focus and move left as before
            focusManager.clearFocus(force = true)
            focusManager.moveFocus(FocusDirection.Left)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screen_bg_color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161D25))
                .padding(start = 70.dp)
                .zIndex(1f)
        ) {
            // Render navigation menus only when data exists.
            if (categories.isNotEmpty() && languages.isNotEmpty()) {
                    CategoryMenu(
                        sharedViewModel = sharedViewModel,
                        selectedIndex = categorySelectedIndex,
                        categoryFocusRequesters = categoryFocusRequesters,
                        languageFocusRequesters = languageFocusRequesters,
                        languageSelectedIndex = languageSelectedIndex
                    )
                    LanguageMenu(
                        sharedViewModel = sharedViewModel,
                        selectedIndex = languageSelectedIndex,
                        firstChannelFocusRequester  = channelFocusRequesters.firstOrNull() ?: FocusRequester(),
                        languageFocusRequesters = languageFocusRequesters,
                        categoryFocusRequesters = categoryFocusRequesters,
                        categorySelectedIndex = categorySelectedIndex
                    )
                }
                // Render channel list only when non-empty; otherwise, show a loading placeholder.
                if (channelList.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color = screen_bg_color)
                    ) {
                        itemsIndexed(channelList) { index, channel ->
                            val isFirstChannel = (index == 0)
                            val isLastChannel = (index == channelList.size - 1)
                            if (index == 0) {
                                ChannelList(
                                    sharedViewModel = sharedViewModel,
                                    channel = channel,
                                    focusRequester = channelFocusRequesters[index],
                                    isFirstChannel = isFirstChannel,
                                    isLastChannel = isLastChannel,
                                    onClick = { clickedChannel ->
                                        sharedViewModel.updateChannelScreenLastSelectedChannelIndex(index)
                                        sharedViewModel.wtvEPGList.value?.find { it?.videoUrl == channel.videoUrl }
                                            ?.let { channelItem ->
                                                sharedViewModel.updateSelectedChannel(channelItem)
                                                if (genre != "All Channels" && lang != "All Languages") {
                                                    sharedViewModel.setCurrentPlaylist(filteredContent, genre)
                                                    sharedViewModel.updateLanguage(lang)
                                                }
                                                if(PreferenceManager.getAppSettings()?.isPlayerAnimationOverlay == true){
                                                    navController.navigate(Destination.animationPlayer)
                                                }else{
                                                    navController.navigate(Destination.panMetroScreen)
                                                }
                                            }
                                    },
                                    languageFocusRequesters = languageFocusRequesters,
                                    languageSelectedIndex = languageSelectedIndex,
                                    categoryFocusRequesters = categoryFocusRequesters,
                                    categorySelectedIndex = categorySelectedIndex
                                )
                            } else {
                                ChannelList(
                                    sharedViewModel = sharedViewModel,
                                    channel = channel,
                                    focusRequester = channelFocusRequesters[index],
                                    onClick = { clickedChannel ->
                                        sharedViewModel.updateChannelScreenLastSelectedChannelIndex(index)
                                        sharedViewModel.wtvEPGList.value?.find { it?.videoUrl == channel.videoUrl }
                                            ?.let { channelItem ->
                                                sharedViewModel.updateSelectedChannel(channelItem)
                                                if (genre != "All Channels" && lang != "All Languages") {
                                                    sharedViewModel.setCurrentPlaylist(filteredContent, genre)
                                                    sharedViewModel.updateLanguage(lang)
                                                }
                                                if(PreferenceManager.getAppSettings()?.isPlayerAnimationOverlay == true){
                                                    navController.navigate(Destination.animationPlayer)
                                                }else{
                                                    navController.navigate(Destination.panMetroScreen)
                                                }
                                            }
                                    },
                                    isFirstChannel = isFirstChannel,
                                    isLastChannel = isLastChannel,
                                    languageFocusRequesters = languageFocusRequesters,
                                    languageSelectedIndex = languageSelectedIndex,
                                    categoryFocusRequesters = categoryFocusRequesters,
                                    categorySelectedIndex = categorySelectedIndex
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Placeholder when channels are loading
                        androidx.tv.material3.Text("No channels available", color = Color.White, fontSize = 25.sp)
                    }
                }

        }
        ExpandableNavigationMenu(
            navController   = navController,
            sharedViewModel = sharedViewModel,
            onNavMenuIntent = { tabInfo, _ ->
                // no-op or update categories if needed
            },
            modifier  = Modifier.align(Alignment.CenterStart),
            menuFocusRequester = menuFocusRequester,
            onBackPressed = {
                menuFocusRequester.requestFocus()
            }
        )

    }
}

@Composable
fun ChannelList(
    sharedViewModel: SharedViewModel,
    channel: Channel,
    focusRequester: FocusRequester? = null,
    onClick: (Channel) -> Unit = {},
    isFirstChannel: Boolean,
    isLastChannel: Boolean,
    languageSelectedIndex: MutableState<Int>,
    categoryFocusRequesters: List<FocusRequester>,
    languageFocusRequesters: List<FocusRequester>,
    categorySelectedIndex: MutableState<Int>
) {

    val stops = channel.bgGradient
        ?.colors
        ?.sortedBy { it.percentage }
        ?.map { Color(android.graphics.Color.parseColor(it.color)) }
        .orEmpty()

    val brush = when {
        stops.size >= 2 && channel.bgGradient?.angle == 90 ->
            Brush.horizontalGradient(stops)
        stops.size >= 2 ->
            Brush.verticalGradient(stops)
        else ->
            SolidColor(bg_card_color)   // your old fallback
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = tween(durationMillis = 150)
    )

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER -> {
                            onClick(channel)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (isFirstChannel) {
                                // Safely request focus on the language list (if available)
                                if (languageFocusRequesters.isNotEmpty() && languageSelectedIndex.value >= 0) {
                                    languageFocusRequesters.getOrNull(languageSelectedIndex.value)?.requestFocus()
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> isLastChannel
                        else -> false
                    }
                } else false
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .background(brush = brush, shape = RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) base_color else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Box {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                channel.channelNo?.let { no ->
                    Text(
                        text = no.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}
