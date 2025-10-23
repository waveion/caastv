package com.caastv.tvapp.view.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.android.caastv.R
import com.caastv.tvapp.extensions.hideKeyboard
import com.caastv.tvapp.extensions.loge
import com.caastv.tvapp.model.data.banner.Banner
import com.caastv.tvapp.model.data.epgdata.Channel
import com.caastv.tvapp.model.home.WTVHomeCategory
import com.caastv.tvapp.utils.theme.base_color
import com.caastv.tvapp.utils.theme.bg_card_color
import com.caastv.tvapp.utils.uistate.PreferenceManager
import com.caastv.tvapp.view.navigationhelper.Destination
import com.caastv.tvapp.view.navigationhelper.ExpandableNavigationMenu
import com.caastv.tvapp.viewmodels.SharedViewModel
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

@Composable
fun HomeScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val homeCategories = sharedViewModel.homeData.collectAsState()////provideApplicationContext().appHomeLiveData().observeAsState(initial = emptyList())
    val epgChannels by sharedViewModel.wtvEPGList.collectAsState()
    val banners by sharedViewModel.bannerList.collectAsState()
    val context = LocalContext.current
    val recentlyWatched by sharedViewModel.recentlyWatched.collectAsState()
    val menuFocusRequester = remember { FocusRequester() }
    val firstChannelFocusRequester = remember { FocusRequester() }
    val favIds by sharedViewModel.favoriteChannelIds.collectAsState()



    LaunchedEffect(Unit) {
        context.hideKeyboard()
        firstChannelFocusRequester?.let { requester ->
            try {
                requester.requestFocus()
            } catch (e: IllegalStateException) {
                loge("FocusError", "FocusRequester not initialized ${e.message}")
            }
        }
        //register scroll message request
        sharedViewModel.provideGlobalSSERequest()
    }

    val displayCategories = remember(homeCategories, recentlyWatched, favIds) {
        val list = mutableListOf<WTVHomeCategory>()
            if (favIds.isNotEmpty()) {
                list += WTVHomeCategory(
                    id        = "favs",
                    name      = "Favorites",
                    channels  = favIds,
                    order     = Int.MIN_VALUE + 1,
                    createdAt = Instant.now().toString(),
                    updatedAt = Instant.now().toString(),
                    version   = 0
                )
            }
        if (recentlyWatched.isNotEmpty()) {
            list += WTVHomeCategory(
                id        = "recent",
                name      = "Recently Watched",
                channels  = recentlyWatched.asReversed().mapNotNull { it.channelId },
                order     = Int.MIN_VALUE,
                createdAt = Instant.now().toString(),
                updatedAt = Instant.now().toString(),
                version   = 0
            )
        }
        list += homeCategories.value
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ExpandableNavigationMenu(
            modifier        = Modifier.align(Alignment.CenterStart) .focusRequester(menuFocusRequester),
            navController   = navController,
            sharedViewModel = sharedViewModel,
            onNavMenuIntent = { _, _ -> },
            menuFocusRequester = menuFocusRequester,
            onBackPressed = {
                menuFocusRequester.requestFocus()
            }
        )
        Column(modifier = Modifier.fillMaxSize()) {
            HeroCarousel(
                bannerList = banners,
                navController = navController
            )
            LazyColumn(modifier = Modifier.fillMaxSize().padding(start = 70.dp)) {
                itemsIndexed(displayCategories) { categoryIndex, category ->
                    val epgList = epgChannels.filter { it.channelId in category.channels }
                    val channelsForCategory = epgList.mapNotNull { epgItem ->
                        epgItem.tv?.channel?.copy(
                            logoUrl    = epgItem?.thumbnailUrl,
                            videoUrl   = epgItem?.videoUrl,
                            genreId    = epgItem?.genreId ?: "Unknown",
                            bgGradient = epgItem?.bgGradient
                        )
                    }
                    if (channelsForCategory.isNotEmpty()) {
                        CategorySection(
                            title                    = category.name,
                            categoryIndex            = categoryIndex,
                            channels                 = channelsForCategory,
                            navController            = navController,
                            sharedViewModel          = sharedViewModel,
                            categoryChannelIds       = category.channels,
                            firstChannelFocusRequester = if (categoryIndex==0) firstChannelFocusRequester else null,
                            isFirstCategory          = (categoryIndex == 0)
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategorySection(
    title: String,
    channels: List<Channel>,
    categoryIndex: Int,
    categoryChannelIds: List<String>,
    navController: NavController,
    sharedViewModel: SharedViewModel,
    firstChannelFocusRequester: FocusRequester? = null,
    isFirstCategory: Boolean = false
) {
    val lastCat by sharedViewModel.lastHomeCategory.collectAsState()
    val lastChan by sharedViewModel.lastHomeChannel.collectAsState()
    val rowState = rememberLazyListState()
    val channelFocusRequesters = remember(channels) {
        channels.map { FocusRequester() }
    }
    LaunchedEffect(lastCat, channels) {
        if (lastCat == categoryIndex && channels.isNotEmpty()) {
            val idx = lastChan.coerceIn(channels.indices)
            rowState.animateScrollToItem(idx)
            channelFocusRequesters[idx].requestFocus()
        }
    }
    val bringRequester = remember { BringIntoViewRequester() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 16.dp, bottom = 5.dp),
            style = TextStyle(
                fontSize = 21.sp,
                fontFamily = FontFamily(Font(R.font.figtree_light)),
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ⑤ Use itemsIndexed so we know when it's the first channel
            itemsIndexed(channels) { idx, channel ->
                // only the first item of the first category gets our focusRequester
                val baseModifier = if (isFirstCategory && idx == 0 && firstChannelFocusRequester != null) {
                    Modifier.focusRequester(firstChannelFocusRequester)
                } else {
                    Modifier
                }
                val combinedModifier = baseModifier
                    .focusRequester(channelFocusRequesters[idx])
                ChannelBox(
                    channel = channel,
                    modifier = combinedModifier,
                ) { videoUrl ->
                    sharedViewModel.updateLastHomeSelection(categoryIndex, idx)
                    val selectedItem = sharedViewModel.wtvEPGList.value
                        .firstOrNull { it?.videoUrl == videoUrl }
                        ?: return@ChannelBox
                    val categoryEpg = sharedViewModel.wtvEPGList.value
                        .filter { it.channelId in categoryChannelIds }
                    sharedViewModel.setCurrentPlaylist(categoryEpg)
                    sharedViewModel.setCurrentPlaylist(categoryEpg, title)
                    sharedViewModel.updateLanguage(null)
                    sharedViewModel.updateSelectedChannel(selectedItem)
                    if(PreferenceManager.getAppSettings()?.isPlayerAnimationOverlay == true){
                        navController.navigate(Destination.animationPlayer)
                    }else{
                        navController.navigate(Destination.panMetroScreen)
                    }
                }
            }
        }
    }
}
@Composable
fun ChannelBox(
    channel: Channel,
    modifier: Modifier = Modifier,   // ← new
    onChannelClick: (String) -> Unit
) {

    val stops = channel.bgGradient
        ?.colors
        ?.sortedBy { it.percentage }
        ?.map { Color(android.graphics.Color.parseColor(it.color)) }
        .orEmpty()

    val brush = if (stops.size >= 2) {
        Brush.horizontalGradient(stops)
    } else {
        SolidColor(bg_card_color)
    }

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (isFocused) 1.1f else 1f,
        animationSpec = tween(durationMillis = 150)
    )

    Box(
        modifier = modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(brush, shape = RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) base_color else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { channel.videoUrl?.let(onChannelClick) }
    ) {
        AsyncImage(
            model         = channel.logoUrl,
            contentDescription = channel.displayName,
            contentScale  = ContentScale.Fit,
            modifier      = Modifier
                .aspectRatio(16f / 9f)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}


@Composable
fun HeroCarousel(bannerList: List<Banner>, navController: NavController) {
    var selectedIndex by remember { mutableStateOf(0) }
    var isButtonFocused by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition()
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    if (bannerList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(start = 70.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = base_color)
        }
        return
    }
    // Auto-scroll logic.
    LaunchedEffect(bannerList) {
        while (true) {
            delay(5000)
            selectedIndex = (selectedIndex + 1) % bannerList.size
        }
    }
    val playInteraction = remember { MutableInteractionSource() }
    val playFocused by playInteraction.collectIsFocusedAsState()
    val watchNowRequester = remember { FocusRequester() }
    val selectedBanner = bannerList[selectedIndex]
    // Use a default video URL (adjust as needed)
    val videoUrl = selectedBanner.bannerContentLink ?: ""
    val encodedUrl = URLEncoder.encode(videoUrl, StandardCharsets.UTF_8.toString())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(200.dp)
    ) {
        // Load the banner image using AsyncImage.
        AsyncImage(
            model = selectedBanner.bannerUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp) // same as banner height
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = selectedBanner.name,
                style = TextStyle(
                    fontSize = 35.sp,
                    fontFamily = FontFamily(Font(R.font.figtree_medium)),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = "Watch the latest breaking news", // default description
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = FontFamily(Font(R.font.figtree_light)),
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {    if (videoUrl.contains("youtube.com", ignoreCase = true)) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.setPackage("com.google.android.youtube")
                    val context = navController.context
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        // fallback: browser
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)))
                    }
                } else {
                    navController.navigate("${Destination.demoplayer}/$encodedUrl")
                }
                 },
                interactionSource = playInteraction,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (playFocused) Color(0xFF50776E) else Color.White,
                    contentColor   = if (playFocused) Color.White      else Color.Black
                ),
                modifier = Modifier
                    .height(48.dp)
                    .focusRequester(watchNowRequester)
                    .then(
                        if (playFocused)
                            Modifier.border(2.dp, base_color, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .focusable(interactionSource = playInteraction)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Watch Now", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bannerList.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == selectedIndex) 12.dp else 8.dp)
                        .background(
                            color = if (index == selectedIndex) Color.White else Color.Gray.copy(alpha = dotAlpha),
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}
