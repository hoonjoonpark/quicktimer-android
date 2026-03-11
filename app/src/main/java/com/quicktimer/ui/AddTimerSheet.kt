package com.quicktimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quicktimer.R
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun AddTimerBottomSheet(
    title: String,
    initialDurationSeconds: Int,
    initialLabel: String,
    bottomOffset: Dp,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    var hours by remember(initialDurationSeconds) { mutableIntStateOf((initialDurationSeconds / 3600).coerceIn(0, 23)) }
    var minutes by remember(initialDurationSeconds) { mutableIntStateOf(((initialDurationSeconds % 3600) / 60).coerceIn(0, 59)) }
    var seconds by remember(initialDurationSeconds) { mutableIntStateOf((initialDurationSeconds % 60).coerceIn(0, 59)) }
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val baseContentBottomPadding = 12.dp + (bottomOffset * 0.28f)
    val contentBottomPadding = (baseContentBottomPadding - (imeBottomDp * 0.12f)).coerceAtLeast(8.dp)
    val hideDurationMs = 220L
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(true) }
    var isClosing by remember { mutableStateOf(false) }
    val sheetVisibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.55f else 0f,
        label = "addTimerScrimAlpha"
    )

    fun dismissWithAnimation(action: () -> Unit) {
        if (isClosing) return
        isClosing = true
        isVisible = false
        sheetVisibleState.targetState = false
        scope.launch {
            delay(hideDurationMs)
            action()
        }
    }

    BackHandler(enabled = true) {
        dismissWithAnimation(onDismiss)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { dismissWithAnimation(onDismiss) }
            )
    ) {
        AnimatedVisibility(
            visibleState = sheetVisibleState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = contentBottomPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        WheelPicker(
                            modifier = Modifier.weight(1f),
                            maxValue = 23,
                            label = stringResource(R.string.hours),
                            initialValue = hours
                        ) { hours = it }
                        WheelPicker(
                            modifier = Modifier.weight(1f),
                            maxValue = 59,
                            label = stringResource(R.string.minutes),
                            initialValue = minutes
                        ) { minutes = it }
                        WheelPicker(
                            modifier = Modifier.weight(1f),
                            maxValue = 59,
                            label = stringResource(R.string.seconds),
                            initialValue = seconds
                        ) { seconds = it }
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.timer_label)) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { dismissWithAnimation(onDismiss) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                val total = hours * 3600 + minutes * 60 + seconds
                                if (total > 0) {
                                    dismissWithAnimation { onSave(total, label) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun WheelPicker(
    modifier: Modifier = Modifier,
    maxValue: Int,
    label: String,
    initialValue: Int,
    onValueChanged: (Int) -> Unit
) {
    val visibleRows = 5
    val boundedInitial = initialValue.coerceIn(0, maxValue)
    val rangeSize = maxValue + 1
    val cycleCount = 21
    val totalCount = rangeSize * cycleCount
    val baseCycleIndex = (cycleCount / 2) * rangeSize
    val startIndex = remember(maxValue, boundedInitial) { baseCycleIndex + boundedInitial }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeight = 34.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.roundToPx() }
    val centerIndex by remember(listState, itemHeightPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
        }
    }

    LaunchedEffect(listState, itemHeightPx, rangeSize) {
        snapshotFlow {
            listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
        }
            .distinctUntilChanged()
            .collect { index ->
                val normalized = ((index % rangeSize) + rangeSize) % rangeSize
                onValueChanged(normalized)
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * visibleRows)
                .padding(horizontal = 6.dp)
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = itemHeight * (visibleRows / 2)
                )
            ) {
                items(totalCount) { index ->
                    val value = index % rangeSize
                    val distance = abs(index - centerIndex)
                    val alpha = when {
                        distance == 0 -> 1f
                        distance == 1 -> 0.32f
                        else -> 0.10f
                    }
                    Text(
                        text = value.toString().padStart(2, '0'),
                        fontSize = 24.sp,
                        fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        modifier = Modifier.height(itemHeight)
                    )
                }
            }
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
