package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GeminiModelInfo
import com.example.network.GeminiModelManager
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBorder
import kotlinx.coroutines.launch

// Google Blue color matching the screenshot checkmark circle
private val SelectionBlue = Color(0xFF0D6EFD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiModelSelectionSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentModelId by GeminiModelManager.selectedModelId.collectAsState()
    val availableModels by GeminiModelManager.availableModels.collectAsState()
    val isLoading by GeminiModelManager.isLoading.collectAsState()

    val updatedMsg = stringResource(R.string.model_sheet_updated)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = Color(0xFF151C28), // Sleek slate matching the screenshot aesthetic
        dragHandle = {
            // Drag handle styled exactly like in user's image
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4B5565))
                    .testTag("model_sheet_drag_handle")
            )
        },
        modifier = Modifier.testTag("gemini_model_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header with title and live dynamic refresh from API
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.model_sheet_title),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Dynamic GET https://generativelanguage.googleapis.com/v1beta/models refresh button
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = CyanGlow
                    )
                } else {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val result = GeminiModelManager.fetchAvailableModels(context, forceRefresh = true)
                                result.onSuccess {
                                    Toast.makeText(context, updatedMsg, Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("refresh_models_from_api_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.model_sheet_refresh),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Models list grouped by family (e.g., "Gemini 3", "Gemini 2.5")
            val groupedModels = availableModels.groupBy { it.category }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                groupedModels.forEach { (category, models) ->
                    // Group Header as shown in the screenshot ("Gemini 3")
                    item(key = "header_$category") {
                        Text(
                            text = category,
                            color = Color(0xFF94A3B8), // Soft muted grey matching the screenshot
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }

                    items(
                        items = models,
                        key = { it.id }
                    ) { model ->
                        val isSelected = model.id == currentModelId ||
                                model.apiEndpointId == currentModelId ||
                                (currentModelId.removePrefix("models/") == model.apiEndpointId)

                        GeminiModelItemRow(
                            model = model,
                            isSelected = isSelected,
                            onSelect = {
                                GeminiModelManager.setSelectedModel(context, model.id)
                                coroutineScope.launch {
                                    sheetState.hide()
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                }

                // Dynamic API endpoint indicator note at the bottom
                item(key = "footer_info") {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "GET v1beta/models • Yangi modellar avtomatik yangilanadi",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual model row matching the layout and design of the user's provided screenshot:
 * - Left: Model name (bold), description (muted)
 * - Right: Vibrant blue circle with white checkmark when selected
 */
@Composable
private fun GeminiModelItemRow(
    model: GeminiModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 22.dp, vertical = 13.dp)
            .testTag("model_item_${model.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            // Model title (e.g. "3.6 Flash", "3.6 Думающая", "3.1 Pro")
            Text(
                text = model.displayName,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Model subtitle/description (e.g. "All-around help", "Решает сложные задачи")
            Text(
                text = model.description,
                color = Color(0xFF94A3B8),
                fontSize = 13.5.sp,
                lineHeight = 18.sp
            )
        }

        // Circular Blue Checkmark icon if selected - EXACTLY matching the screenshot
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(SelectionBlue)
                    .testTag("selected_check_icon_${model.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            // Empty placeholder to preserve alignment if needed
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}
