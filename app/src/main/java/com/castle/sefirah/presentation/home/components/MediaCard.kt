package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sefirah.common.R
import sefirah.domain.model.PlaybackSession
import sefirah.presentation.components.WavySlider
import sefirah.common.util.base64ToBitmap
import java.util.Locale
import kotlin.math.abs


@Composable
fun MediaCard(
    sessions: List<PlaybackSession>,
    onPlayPauseClick: (PlaybackSession) -> Unit,
    onSkipNextClick: (PlaybackSession) -> Unit,
    onSkipPreviousClick: (PlaybackSession) -> Unit,
    onSeekChange: (PlaybackSession, Double) -> Unit
) {
    if (sessions.isEmpty()) return

    val activeSessionIndex = sessions.indexOfFirst { it.isPlaying }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = activeSessionIndex) { sessions.size }
    val scope = rememberCoroutineScope()

    // When playing session changes, scroll to it
    LaunchedEffect(sessions) {
        val newActiveIndex = sessions.indexOfFirst { it.isPlaying }
        // Only scroll if there's a playing session and current page isn't already showing it
        if (newActiveIndex >= 0) {
            val currentPageIsPlaying = pagerState.currentPage < sessions.size &&
                                      sessions[pagerState.currentPage].isPlaying
            if (newActiveIndex != pagerState.currentPage && !currentPageIsPlaying) {
                scope.launch {
                    pagerState.animateScrollToPage(newActiveIndex)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp
        ) { page ->
            val session = sessions[page]
            PlaybackSession(
                session = session,
                onPlayPauseClick = { onPlayPauseClick(session) },
                onSkipNextClick = { onSkipNextClick(session) },
                onSkipPreviousClick = { onSkipPreviousClick(session) },
                onSeekChange = { position -> onSeekChange(session, position) }
            )
        }

        if (sessions.size > 1) {
            FlowingPageIndicator(
                pageCount = sessions.size,
                currentPage = pagerState.currentPage,
                currentPageOffset = pagerState.currentPageOffsetFraction,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackSession(
    session: PlaybackSession,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeekChange: (Double) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
    ) {
        session.trackTitle?.let {
            Box(Modifier.fillMaxWidth()) {

                session.thumbnail?.let { thumbnail ->
                    // Remember the bitmap conversion result
                    val bitmap = remember(thumbnail) {
                        base64ToBitmap(thumbnail)
                    }

                    val painter = rememberAsyncImagePainter(model = bitmap)

                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop
                    )
                }

                // Overlay content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // App name
                    session.source?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Title and artist info
                    Text(
                        text = session.trackTitle ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = session.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )


                    // Seek bar and position tracking
                    var displayPosition by remember { mutableDoubleStateOf(session.position) }

                    LaunchedEffect(session.position) {
                        // Only update if the change is significant
                        if (abs(session.position - displayPosition) > 1000) {
                            displayPosition = session.position
                        }
                    }

                    // Track if user is currently dragging the slider
                    var isDragging by remember { mutableStateOf(false) }

                    // Create a reference point for time-based updates
                    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

                    // Update position continuously during playback
                    LaunchedEffect(session.isPlaying, isDragging) {
                        if (session.isPlaying && !isDragging) {
                            lastUpdateTime = System.currentTimeMillis()

                            while (true) {
                                delay(100)

                                if (isDragging) break

                                // Calculate elapsed time since last update
                                val now = System.currentTimeMillis()
                                val elapsedTime = now - lastUpdateTime
                                lastUpdateTime = now

                                val newPosition = displayPosition + elapsedTime
                                val boundedPosition = minOf(newPosition, session.maxSeekTime)

                                displayPosition = boundedPosition

                                if (boundedPosition >= session.maxSeekTime) break
                            }
                        }
                    }

                    val sliderPosition = if (session.maxSeekTime > 0) {
                        (displayPosition / session.maxSeekTime).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    // Interaction source for tracking thumb interaction state
                    val thumbInteractionSource = remember { MutableInteractionSource() }

                    WavySlider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            isDragging = true
                            displayPosition = newValue.toDouble() * session.maxSeekTime
                        },
                        onValueChangeFinished = {
                            onSeekChange(displayPosition)
                            lastUpdateTime = System.currentTimeMillis()
                            isDragging = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = thumbInteractionSource,
                        isPlaying = session.isPlaying
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(displayPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            text = formatTime(session.maxSeekTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    // Control buttons
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = onSkipPreviousClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_previous),
                                contentDescription = "Skip Previous",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        val playPauseInteractionSource = remember { MutableInteractionSource() }

                        // Play/Pause button
                        IconToggleButton (
                            checked = session.isPlaying,
                            onCheckedChange = { onPlayPauseClick() },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = contentColorFor(MaterialTheme.colorScheme.primary),
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = contentColorFor(MaterialTheme.colorScheme.primary)
                            ),
                            interactionSource = playPauseInteractionSource,
                            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
                        ) {
                            Icon(
                                painter = if (session.isPlaying) painterResource(R.drawable.ic_pause) else painterResource(R.drawable.ic_play_arrow),
                                contentDescription = if (session.isPlaying) "Pause" else "Play"
                            )
                        }

                        IconButton(onClick = onSkipNextClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_next),
                                contentDescription = "Skip Next",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } ?: EmptyPlaybackHolder()
    }
}

@Composable
fun EmptyPlaybackHolder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(R.string.no_media_playback))
    }
}

private fun formatTime(timeInSeconds: Double): String {
    val totalSeconds = timeInSeconds.toInt() / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}