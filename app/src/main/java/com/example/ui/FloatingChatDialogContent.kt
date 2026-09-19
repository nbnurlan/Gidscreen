package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import com.example.MainActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.network.GeminiModelManager
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PurpleNeon
import com.example.util.LocaleHelper

@Composable
fun FloatingChatDialogContent(
    analysisState: AnalysisState,
    chatMessages: List<ChatMessage>,
    onSendFollowUp: (String) -> Unit,
    onNewSelectionRequested: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onResizeDelta: (Float, Float) -> Unit,
    onClearChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLang by LocaleHelper.currentLanguage.collectAsState()

    var isMinimized by remember { mutableStateOf(false) }
    var followUpInput by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val isAnalyzing = analysisState is AnalysisState.Analyzing
    val listState = rememberLazyListState()

    val visibleMessages = remember(chatMessages.size, chatMessages.count { it.isVisible }) {
        chatMessages.filter { isVisibleChatMessage(it) }
    }

    // Auto scroll on new message
    LaunchedEffect(visibleMessages.size, analysisState) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "loadingRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    if (isMinimized) {
        // Minimized floating pill state
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 12.dp,
            modifier = modifier
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(CyanGlow, PurpleNeon)),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .testTag("floating_chat_minimized_pill")
        ) {
            Row(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (analysisState is AnalysisState.Analyzing) {
                        stringResource(R.string.chat_minimized_analyzing)
                    } else {
                        stringResource(R.string.chat_minimized_title)
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { isMinimized = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.btn_expand),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        return
    }

    // Full Resizable & Draggable Floating Chat Window
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 16.dp,
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(CyanGlow.copy(alpha = 0.8f), PurpleNeon.copy(alpha = 0.8f))),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .testTag("floating_chat_dialog")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Draggable Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
                        )
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag Handle & Title
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.btn_drag_window),
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_title),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    CompactModelSelector()
                }

                // In-Overlay Compact Flag Language Selector Dropdown
                CompactLanguageDropdown()

                Spacer(modifier = Modifier.width(2.dp))

                // Clear Chat Session Button (if messages exist)
                if (visibleMessages.isNotEmpty() && onClearChat != null) {
                    IconButton(
                        onClick = onClearChat,
                        enabled = !isAnalyzing,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.btn_clear_chat),
                            tint = if (!isAnalyzing) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // New Selection Action
                IconButton(
                    onClick = onNewSelectionRequested,
                    enabled = !isAnalyzing,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = stringResource(R.string.btn_new_selection),
                        tint = if (!isAnalyzing) CyanGlow else CyanGlow.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Minimize Button
                IconButton(
                    onClick = { isMinimized = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = stringResource(R.string.btn_minimize),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Body Area (Messages & Screen Thumbnail)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF090D16))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 1: Captured Screen Thumbnail (shown if not already in chat items)
                    val thumb = when (analysisState) {
                        is AnalysisState.Analyzing -> analysisState.thumbnail
                        is AnalysisState.Success -> analysisState.thumbnail
                        is AnalysisState.Error -> analysisState.thumbnail
                        else -> null
                    }

                    if (thumb != null && visibleMessages.none { it.image != null }) {
                        item {
                            ScreenThumbnailCard(
                                bitmap = thumb,
                                onImageClick = { previewBitmap = thumb }
                            )
                        }
                    }

                    // Item 2: Loading State
                    if (analysisState is AnalysisState.Analyzing) {
                        item {
                            AnalyzingStatusCard(rotation = rotation)
                        }
                    }

                    // Item 3: Error State
                    if (analysisState is AnalysisState.Error) {
                        item {
                            ErrorCard(
                                message = analysisState.message,
                                onRetry = onRetry
                            )
                        }
                    }

                    // Chat messages (Initial AI answer + multi-turn history)
                    items(visibleMessages, key = { it.id }) { message ->
                        val copiedToastText = stringResource(R.string.toast_copied)
                        ChatBubbleItem(
                            message = message,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AI Insight", message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                            },
                            onImageClick = { img ->
                                previewBitmap = img
                            }
                        )
                    }

                    // Quick suggestion prompts if initial analysis succeeded and conversation has no user follow-up yet
                    if (analysisState is AnalysisState.Success && visibleMessages.count { it.sender == MessageSender.USER } == 0) {
                        item {
                            QuickPromptsRow(
                                isEnabled = !isAnalyzing,
                                onPromptSelected = { prompt ->
                                    if (!isAnalyzing) {
                                        onSendFollowUp(prompt)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Follow-up Input Bar
            Surface(
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = followUpInput,
                        onValueChange = { followUpInput = it },
                        enabled = !isAnalyzing,
                        placeholder = {
                            Text(
                                text = if (isAnalyzing) stringResource(R.string.input_placeholder_waiting) else stringResource(R.string.input_placeholder_follow_up),
                                color = Color.White.copy(alpha = if (isAnalyzing) 0.25f else 0.4f),
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White.copy(alpha = 0.4f),
                            disabledBorderColor = DarkBorder.copy(alpha = 0.5f),
                            cursorColor = CyanGlow
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_follow_up_input")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val canSend = followUpInput.isNotBlank() && !isAnalyzing
                    IconButton(
                        onClick = {
                            if (canSend) {
                                val text = followUpInput.trim()
                                followUpInput = ""
                                onSendFollowUp(text)
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) CyanGlow else Color.White.copy(alpha = 0.1f)
                            )
                            .testTag("chat_send_button")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = CyanGlow
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.btn_send),
                                tint = if (canSend) Color.Black else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Resize Handle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.footer_status_active),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )

                // Drag grip to resize window
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onResizeDelta(dragAmount.x, dragAmount.y)
                            }
                        }
                        .testTag("chat_window_resize_handle"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◢",
                        color = CyanGlow.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Full screen Image Zoom & Pan dialog
    previewBitmap?.let { bmp ->
        ImageZoomDialog(
            bitmap = bmp,
            onDismiss = { previewBitmap = null }
        )
    }
}

@Composable
private fun ScreenThumbnailCard(
    bitmap: Bitmap,
    onImageClick: (() -> Unit)? = null
) {
    Surface(
        color = Color(0xFF131B2E),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.captured_screen_area),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onImageClick != null) {
                        Text(
                            text = stringResource(R.string.image_zoom_hint),
                            color = CyanGlow.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = "${bitmap.width} × ${bitmap.height} px",
                        color = CyanGlow,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
                    .then(
                        if (onImageClick != null) {
                            Modifier.clickable { onImageClick() }
                        } else Modifier
                    )
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.captured_screen_area),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                if (onImageClick != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.btn_zoom),
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingStatusCard(rotation: Float) {
    Surface(
        color = Color(0xFF131B2E),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanGlow.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = CyanGlow
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(rotation)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.analyzing_heading),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.analyzing_subheading),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        color = Color(0xFF2C1517),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(R.string.error_heading),
                color = Color(0xFFEF4444),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.btn_retry_analysis),
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_retry_analysis),
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val isCapturePermissionError = message.contains("capture permission", ignoreCase = true) ||
                    message.contains("MediaProjection", ignoreCase = true) ||
                    message.contains("Screen capture", ignoreCase = true) ||
                    message.contains("ruxsat berish", ignoreCase = true) ||
                    message.contains("предоставить", ignoreCase = true)

                val context = LocalContext.current
                if (isCapturePermissionError) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyanGlow.copy(alpha = 0.2f))
                            .clickable {
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra(MainActivity.EXTRA_REQUEST_CAPTURE, true)
                                }
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.btn_grant),
                            tint = CyanGlow,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_grant),
                            color = CyanGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                val isApiKeyError = message.contains("leaked", ignoreCase = true) ||
                    message.contains("kaliti", ignoreCase = true) ||
                    message.contains("ключ", ignoreCase = true) ||
                    message.contains("GEMINI_API_KEY", ignoreCase = true)

                if (isApiKeyError) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                            .clickable {
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Secrets • Key",
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    onCopy: () -> Unit,
    onImageClick: ((Bitmap) -> Unit)? = null
) {
    val isUser = message.sender == MessageSender.USER
    val isAI = message.sender == MessageSender.AI

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF1E293B) else Color(0xFF111827),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 12.dp
            ),
            border = if (isAI) androidx.compose.foundation.BorderStroke(1.dp, DarkBorder) else null,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 1f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) stringResource(R.string.sender_you) else stringResource(R.string.sender_gemini),
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else CyanGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isAI) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.btn_copy_text),
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (message.image != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .then(
                                if (onImageClick != null) {
                                    Modifier.clickable { onImageClick(message.image) }
                                } else Modifier
                            )
                    ) {
                        Image(
                            bitmap = message.image.asImageBitmap(),
                            contentDescription = stringResource(R.string.captured_screen_area),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPromptsRow(
    isEnabled: Boolean = true,
    onPromptSelected: (String) -> Unit
) {
    val prompt1 = stringResource(R.string.prompt_explain_simply)
    val prompt2 = stringResource(R.string.prompt_extract_text)
    val prompt3 = stringResource(R.string.prompt_solve_code)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val prompts = listOf(prompt1, prompt2, prompt3)
        for (prompt in prompts) {
            Surface(
                color = if (isEnabled) Color(0xFF1E1B4B) else Color(0xFF1E1B4B).copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isEnabled) PurpleNeon.copy(alpha = 0.5f) else PurpleNeon.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (isEnabled) {
                            Modifier.clickable { onPromptSelected(prompt) }
                        } else Modifier
                    )
            ) {
                Text(
                    text = prompt,
                    color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactModelSelector() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val currentModelId by GeminiModelManager.selectedModelId.collectAsState()
    val availableModels by GeminiModelManager.availableModels.collectAsState()
    val activeModel = GeminiModelManager.getSelectedModel()

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(vertical = 1.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeModel.displayName,
                color = CyanGlow,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = CyanGlow.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF151C28))
        ) {
            availableModels.forEach { model ->
                val isSelected = model.id == currentModelId || model.apiEndpointId == currentModelId
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = model.displayName,
                                    color = if (isSelected) CyanGlow else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = model.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0D6EFD)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        GeminiModelManager.setSelectedModel(context, model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Filter out background/system prompts from the chat UI so they only execute silently
 * with Gemini API and do not clutter the conversation bubbles.
 */
private fun isVisibleChatMessage(message: ChatMessage): Boolean {
    if (!message.isVisible) return false
    val text = message.text.trim()
    if (message.sender == MessageSender.USER) {
        if (text.startsWith("Belgilangan ekran qismini batafsil tahlil qiling") ||
            text.startsWith("Подробно проанализируйте выделенный фрагмент") ||
            text.startsWith("Analyze the selected screen content in detail") ||
            text.matches(Regex("^(Capture|Фрагмент|№)\\s*#?\\d+.*tahlil qiling.*", RegexOption.IGNORE_CASE))
        ) {
            return false
        }
    }
    return true
}
