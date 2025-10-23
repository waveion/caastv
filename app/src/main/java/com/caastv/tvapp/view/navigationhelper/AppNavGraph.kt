package com.caastv.tvapp.view.navigationhelper

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caastv.tvapp.model.data.login.LoginResponseData
import com.caastv.tvapp.model.data.sseresponse.Fingerprint
import com.caastv.tvapp.model.data.sseresponse.ScrollMessage
import com.caastv.tvapp.utils.network.error.GlobalErrorHandler
import com.caastv.tvapp.utils.uistate.PreferenceManager
import com.caastv.tvapp.view.appscreen.AppsScreen
import com.caastv.tvapp.view.channels.ChannelScreen
import com.caastv.tvapp.view.epg.EPGScreen
import com.caastv.tvapp.view.home.DemoHomeScreen
import com.caastv.tvapp.view.home.DemoPlayerScreen
import com.caastv.tvapp.view.home.HomePlayerScreen
import com.caastv.tvapp.view.home.HomeScreen
import com.caastv.tvapp.view.notificationbanner.NotificationBanner
import com.caastv.tvapp.view.otp.OtpScreen1
import com.caastv.tvapp.view.panmetro.genre.PanmetroGenreScreen
import com.caastv.tvapp.view.panmetro.login.PanmetroLoginScreen
import com.caastv.tvapp.view.panmetro.player.CaastvVideoPlayer
import com.caastv.tvapp.view.panmetro.player.PanMetroVideoPlayer
import com.caastv.tvapp.view.panmetro.settings.NewPanMetroSettingsScreen
import com.caastv.tvapp.view.profile.ProfileScreen
import com.caastv.tvapp.view.search.SearchScreen
import com.caastv.tvapp.view.splash.SplashScreen
import com.caastv.tvapp.view.uicomponent.fingerprint.ForceMessageDialog
import com.caastv.tvapp.view.uicomponent.fingerprint.GlobalFingerprintOverlay
import com.caastv.tvapp.view.uicomponent.fingerprint.ScrollingMessageOverlay
import com.caastv.tvapp.view.uicomponent.fingerprint.state.ForceMessageDialogState
import com.caastv.tvapp.viewmodels.SharedViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@SuppressLint("UnrememberedMutableState")
@Composable
fun WTVPlayerApp(sharedViewModel: SharedViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController() // This is the one you'll use everywhere.
    val bannerMsg by sharedViewModel.bannerMessage.collectAsState()
    val globalSSERules by sharedViewModel.globalSSERules.collectAsState()
    var userInfo = remember { mutableStateOf<LoginResponseData?>(null)}
    //In your composable function or ViewModel
    var visibleForce = remember { mutableStateListOf<ForceMessageDialogState>()}
    val visibleMessages = remember { mutableStateListOf<ScrollMessage>() }
    val visibleFingerprint = remember { mutableStateListOf<Fingerprint>() }
    val isOfflineEnable by sharedViewModel.isOfflineEnable.collectAsState()
    var pulse = rememberInfiniteTransition(label = "pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue   = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "alpha"
        )



    LaunchedEffect(globalSSERules) {
        userInfo.value = PreferenceManager.getCMSLoginResponse()
        visibleFingerprint.clear()
        visibleMessages.clear()
        visibleForce.clear()
        globalSSERules?.forceMessages?.forEach { item ->
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
        globalSSERules?.scrollMessages?.forEach { item ->
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
        globalSSERules?.fingerprints?.forEach { item ->
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

    LaunchedEffect(isOfflineEnable) {
        if(isOfflineEnable) {
            sharedViewModel.syncOfflineData().join() // Wait for completion
            sharedViewModel.requiredOfflineDataInitialization()
        }else{
            sharedViewModel.initializeAppRequiredData()
        }
    }

    Box(Modifier.fillMaxSize()) {
        WTVPlayerNavHost(
            navController = navController,
            sharedViewModel = sharedViewModel
        )
        bannerMsg?.let { msg ->
            NotificationBanner(
                message = msg,
                visible = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                // you can add paddingIfNeeded here
            )
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

        // Display only visible fingerprint
        visibleFingerprint.forEach { fingerprint ->
            key(fingerprint._id) { // Important for proper recomposition
                GlobalFingerprintOverlay(fingerprint,
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
        globalSSERules?.packageUpdates?.distinct()?.forEach {
            if(it.packageUpdate == 1){
                it.packageID?.let {pkgId->
                   // sharedViewModel.customerChannelUpdates(arrayListOf(pkgId))
                }
            }
        }

        globalSSERules?.userUpdates?.forEach {
            if(userInfo?.value?.loginData?.userId.toString()?.equals(it.userId,true) == true){
                sharedViewModel.validateCMSUserLogin(
                    userName = PreferenceManager.getUsername()?:"",
                    userPassword = PreferenceManager.getUsername()?:"",
                    onLoginResponse = { response, errorMsg ->
                        if (response != null) {
                            PreferenceManager.saveCMSUserInfo(response)
                            sharedViewModel.provideGlobalSSERequest()
                        }

                    })
                //sharedViewModel.userPackageUpdate(customerNumber = it, isPkgUpdateOnly = true)
               // sharedViewModel.provideGlobalSSERequest()
            }
        }

        // Global error handler (will show on top when needed)
        GlobalErrorHandler()
        if (isOfflineEnable) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, end = 2.dp)
                    .background(Color.Transparent, RoundedCornerShape(10.dp))
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
            ) {
                // Red dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = pulse.value }
                        .background(Color.Red, CircleShape)
                )
            }
        }
    }
}

@SuppressLint("ContextCastToActivity")
@Composable
fun WTVPlayerNavHost(navController: NavHostController, sharedViewModel: SharedViewModel) {
    NavHost(navController = navController, startDestination = Destination.splashScreen) {
        composable(route = Destination.splashScreen) {
            SplashScreen(sharedViewModel = sharedViewModel, navController)
        }
        composable(Destination.loginScreen) {
            PanmetroLoginScreen(sharedViewModel = sharedViewModel,navController = navController)
        }
        composable(Destination.epgScreen) {
            EPGScreen(navController, sharedViewModel)
        }
        composable(Destination.profile) {
            ProfileScreen(navController, sharedViewModel)
        }
        composable(Destination.genreScreen) {
            PanmetroGenreScreen(navController,sharedViewModel)
        }
        composable(Destination.settings) {
            NewPanMetroSettingsScreen(navController,sharedViewModel)
        }
        composable(Destination.panMetroScreen) {backStackEntry ->
            CaastvVideoPlayer(navController,sharedViewModel)
        }
        composable(Destination.animationPlayer) {backStackEntry ->
            PanMetroVideoPlayer(navController,sharedViewModel)
        }
        composable(Destination.demoHome) {
            DemoHomeScreen(
                navController   = navController,
                sharedViewModel = sharedViewModel
            )
        }
        composable(
            route = Destination.demoplayer + "/{videoUrl}",
            arguments = listOf(
                navArgument("videoUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
            DemoPlayerScreen(
                url = videoUrl,
                onBack = { navController.popBackStack() }
            )
        }

        /*
        composable(
             route = Destination.panMetroScreen+"{fromEpg}",
             arguments = listOf(
                 navArgument("fromEpg") {
                     type = NavType.StringType
                     defaultValue = "false"
                 }
             )
         ) { backStackEntry ->
             val fromEpg = backStackEntry.arguments?.getString("fromEpg")?.toBoolean() ?: false
             PanMetroVideoPlayer(fromEpg,navController,sharedViewModel)

         }
         */
        composable(Destination.otpScreen) {
            OtpScreen1(navController)
        }
        composable(Destination.searchScreen) {
            SearchScreen(navController, sharedViewModel)
        }
        // This route is reused across your app.
        // It expects a videoUrl (path parameter) and a categoryIds (query parameter).
        composable(
            route = Destination.playerScreen + "/{videoUrl}?categoryIds={categoryIds}",
            arguments = listOf(
                navArgument("videoUrl") { type = NavType.StringType },
                navArgument("categoryIds") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: ""
            val categoryIds = backStackEntry.arguments?.getString("categoryIds") ?: ""

            val filteredEPGList = sharedViewModel.filteredEPGList.collectAsState().value

            val categoryEPGItems = if (categoryIds.isBlank()) {
                filteredEPGList // Show all channels if no category filter applied
            } else {
                filteredEPGList.filter { categoryIds.contains(it.channelId ?: "") }
            }

            HomePlayerScreen(
                initialVideoUrl = videoUrl,
                allChannels = categoryEPGItems,
                onBack = { navController.popBackStack() },
                onVideoChange = { newUrl ->
                    val newEncodedUrl = URLEncoder.encode(newUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("homeplayer/$newEncodedUrl?categoryIds=$categoryIds") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Destination.homeScreen) {
            HomeScreen(navController, sharedViewModel)
        }
        composable(Destination.channel) {
            ChannelScreen(navController = navController, sharedViewModel = sharedViewModel)
        }
        composable(Destination.appsScreen) {
            AppsScreen(navController, sharedViewModel)
        }
    }

}