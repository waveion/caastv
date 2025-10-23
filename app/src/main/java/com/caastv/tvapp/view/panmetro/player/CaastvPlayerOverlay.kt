package com.caastv.tvapp.view.panmetro.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import com.caastv.tvapp.model.data.epgdata.Programme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Precision
import com.android.caastv.R
import com.caastv.tvapp.model.data.epgdata.EPGDataItem
import com.caastv.tvapp.utils.theme.base_color
import com.caastv.tvapp.viewmodels.SharedViewModel
import com.caastv.tvapp.viewmodels.player.PlayerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.compareTo
import kotlin.div
import kotlin.text.compareTo
import kotlin.text.getOrNull
import kotlin.text.indexOfLast
import kotlin.text.isNotEmpty
import kotlin.text.lastIndex
import kotlin.text.orEmpty
import kotlin.text.toFloat

@Composable
fun CaastvPlayerOverlay(
    channel: EPGDataItem,
    playerViewModel: PlayerViewModel,
    programmeIndex: Int
) {

    val nowMs   by playerViewModel.timestampFlow().collectAsState(initial = System.currentTimeMillis())
    val programmes = remember(channel) {
        channel.tv
            ?.programme
            ?.let { playerViewModel.provideAvailablePrograms(it) }
            .orEmpty()
    }

    // Create a default empty program for safety
    val defaultProgram = remember {
        Programme(
            title = "No Information",
            imageUrl = emptyList()
        )
    }

    // Safe index calculations
    val baseIndex = if (programmes.isNotEmpty()) {
        programmes.indexOfLast { it.startTime?.let { start -> start <= nowMs } ?: false }.coerceAtLeast(0)
    } else {
        0
    }

    val maxIndex = if (programmes.isNotEmpty()) programmes.lastIndex else 0
    val targetIndex = if (programmes.isNotEmpty()) {
        (baseIndex + programmeIndex).coerceIn(0, maxIndex)
    } else {
        0
    }

    val nowProg = programmes.getOrNull(targetIndex) ?: defaultProgram
    val nextProg = if (programmes.isNotEmpty() && targetIndex < maxIndex) {
        programmes.getOrNull(targetIndex + 1)
    } else {
        null
    }

    val progFraction = remember(nowProg, nowMs) {
        nowProg.startTime?.let { startTime ->
            nowProg.endTime?.let { endTime ->
                if (startTime > 0 && endTime > startTime) {
                    val duration = (endTime - startTime).toFloat().coerceAtLeast(1f)
                    ((nowMs - startTime) / duration).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        } ?: 0f
    }

    val stops = channel
        ?.bgGradient
        ?.colors
        ?.sortedBy { it.percentage }
        ?.mapNotNull {
            runCatching { Color(android.graphics.Color.parseColor(it.color)) }
                .getOrNull()
        }
        .orEmpty()

    val logoBrush = if (stops.size >= 2) {
        Brush.horizontalGradient(stops)
    } else {
        SolidColor(Color(0xFF2A3139))  // fallback background
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        // start fully transparent, then a mid‐tone, then your full blackout
                        colors = listOf(
                            Color.Black.copy(alpha = 0f), // super light at top
                            Color.Black.copy(alpha = 0.9f),  // mid‐fade
                            Color.Black.copy(alpha = 0.9f)   // nearly opaque at bottom
                        ),
                        startY = 0f,
                        endY   = Float.POSITIVE_INFINITY
                    )
                )
                .padding(horizontal = 30.dp, vertical = 16.dp)
        ) {
            // —— Main info row (logo ⋅ details ⋅ icons ⋅ banner) ——
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 35.dp)
            ) {

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(logoBrush)
                ) {
                    // Channel thumbnail
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.thumbnailUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .precision(Precision.INEXACT) // Use lower precision for memory efficiency
                            .allowHardware(true) // Use hardware bitmaps when possible
                            .bitmapConfig(Bitmap.Config.RGB_565) // 16-bit color (50% memory reduction)
                            .build(),
                        contentDescription = "Image",
                        modifier = Modifier
                            .width(80.dp)
                            .height(90.dp)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }


                Spacer(Modifier.width(12.dp))

                // 2) Info column (number, title, time + bar), same height as the icon
                Column(
                    Modifier
                        .weight(1f)
                        .height(80.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text     = "${channel?.channelNo ?: "--"} : ${channel?.title?.orEmpty()?:"No Information"}",
                        fontSize = 15.sp,
                        color    = Color.White,
                        fontStyle = FontStyle(R.font.figtree_light),

                        )
                    Spacer(Modifier.height(11.dp))
                    Text(
                        text     = nowProg?.title ?: "No more upcoming programmes",
                        fontSize = 22.sp,
                        color    = Color.White,
                        fontStyle = FontStyle(R.font.figtree_medium),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(11.dp))
                    nowProg?.startTime?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${nowProg?.startTime?.formatTime()} – ${nowProg?.endTime?.formatTime()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                fontStyle = FontStyle(R.font.figtree_light),
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color.White.copy(alpha = 0.7f))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${nowProg?.minutesLeft(nowMs)}m left",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Spacer(Modifier.width(12.dp))
                            LinearProgressIndicator(
                                progress = progFraction,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = base_color,
                                backgroundColor = Color(0xFF333333)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Press Up",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color.White.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Options :",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Spacer(Modifier.width(3.dp))
                    // Favourite
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { /* onFavoriteClick() */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector     = Icons.Default.Favorite,
                            contentDescription = "Favourite",
                            tint            = Color.White,
                            modifier        = Modifier.size(16.dp)
                        )
                    }

                    Spacer(Modifier.width(3.dp))

                    // Audio
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { /* onAudioClick() */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector     = Icons.Default.Menu,
                            contentDescription = "Audio",
                            tint            = Color.White,
                            modifier        = Modifier.size(16.dp)
                        )
                    }

                    Spacer(Modifier.width(3.dp))
                }

                Spacer(Modifier.width(16.dp))

                //Breaking-News banner

                nowProg?.imageUrl?.getOrNull(0)?.let {
                    AsyncImage(
                        model           = nowProg?.imageUrl?.getOrNull(0)?.name,
                        contentDescription = null,
                        contentScale    = ContentScale.FillBounds,
                        modifier         = Modifier
                            .width(200.dp)
                            .height(130.dp)
                            .padding(top = 25.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }?:run {
                    Image(
                        painter         = painterResource(id = R.drawable.banner1),
                        contentDescription = null,
                        contentScale    = ContentScale.Crop,
                        modifier         = Modifier
                            .width(200.dp)
                            .height(130.dp)
                            .padding(top = 25.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
            }

            // —— Next: … ——
//            Spacer(Modifier.height(6.dp))
            nextProg?.let { np ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // indent under the “info” column
                    modifier = Modifier.padding(start = 80.dp ) .offset(y = (-20).dp)
                ) {
                    Icon(
                        imageVector   = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint          = Color.White,
                        modifier      = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text  = "Next : ${np.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}


// — Helpers ——
private fun Long.formatTime(): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(this))

private fun Programme.minutesLeft(nowMs: Long): Int =
    ((endTime ?: nowMs) - nowMs)
        .coerceAtLeast(0L)
        .let { (it / 60_000).toInt().coerceAtLeast(1) }
