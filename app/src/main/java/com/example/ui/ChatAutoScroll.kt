package com.example.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.flow.first

private class ChatScrollIntent {
    var following = true
    var userScrolling = false
    var autoScrolling = false
    var messageId: String? = null
    var captureId: String? = null
}

private fun LazyListState.nearBottom(threshold: Int): Boolean {
    if (!canScrollForward) return true
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return true
    return last.index == layout.totalItemsCount - 1 &&
        last.offset + last.size - layout.viewportEndOffset <= threshold
}

/** Remember user intent across inserts and temporary overlay hiding, not just the new list height. */
@Composable
internal fun rememberChatAutoScroll(
    listState: LazyListState,
    latestMessage: ChatMessage?,
    latestCaptureId: String?,
    targetIndex: Int,
    itemCount: Int,
    expanded: Boolean
): Modifier {
    val intent = remember(listState) { ChatScrollIntent() }
    val threshold = with(LocalDensity.current) { 72.dp.roundToPx() }
    val currentTarget = rememberUpdatedState(targetIndex)
    val currentCount = rememberUpdatedState(itemCount)
    val currentExpanded = rememberUpdatedState(expanded)
    val connection = remember(listState, threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    intent.userScrolling = true
                    // Stop following immediately when the reader starts moving toward old messages.
                    if (available.y > 0f) intent.following = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (intent.userScrolling && !intent.autoScrolling) {
                    intent.following = listState.nearBottom(threshold)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState, threshold) {
        // Observe scroll completion (including flings) without recomposing for every pixel.
        snapshotFlow { Triple(listState.isScrollInProgress, listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset) }.collect { (moving, _, _) ->
            if (intent.userScrolling && !intent.autoScrolling) {
                intent.following = listState.nearBottom(threshold)
                if (!moving) intent.userScrolling = false
            }
        }
    }

    LaunchedEffect(latestMessage?.id, latestCaptureId, expanded) {
        if (!expanded) return@LaunchedEffect
        val newMessage = latestMessage?.id != intent.messageId
        val explicitTurn = (newMessage && latestMessage?.sender == MessageSender.USER) ||
            latestCaptureId != intent.captureId
        val shouldScroll = explicitTurn || intent.following
        intent.messageId = latestMessage?.id
        intent.captureId = latestCaptureId
        if (!shouldScroll || currentCount.value == 0) return@LaunchedEffect
        // A submitted question/capture explicitly opts into following its new turn.
        intent.following = true
        intent.autoScrolling = true
        try {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it == currentCount.value }
            if (!explicitTurn && !intent.following) return@LaunchedEffect
            val target = currentTarget.value
            if (target >= 0) listState.animateScrollToItem(target)
            // Align to the START of an answer. Never scroll to an index after a long reply.
        } finally {
            intent.autoScrolling = false
        }
    }

    LaunchedEffect(listState) {
        var previousHeight = 0
        snapshotFlow { listState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset } }
            .collect { height ->
                val shrunk = previousHeight > height && height > 0
                previousHeight = height
                if (!shrunk || !currentExpanded.value || !intent.following || intent.autoScrolling ||
                    intent.userScrolling || currentTarget.value < 0) return@collect
                val target = currentTarget.value
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                // A short last message must stay above the input after IME/resize. For a long
                // answer retain its reading position instead of jumping to its end or beginning.
                if (item == null || (item.size <= height &&
                        item.offset + item.size > listState.layoutInfo.viewportEndOffset)) {
                    intent.autoScrolling = true
                    try { listState.scrollToItem(target) } finally { intent.autoScrolling = false }
                }
            }
    }
    return Modifier.nestedScroll(connection)
}
