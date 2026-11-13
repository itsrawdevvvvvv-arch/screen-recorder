package com.recordly.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordly.mobile.model.AppSettings
import com.recordly.mobile.ui.theme.ForestSage
import com.recordly.mobile.ui.theme.StudioBg
import com.recordly.mobile.ui.theme.StudioBorder
import com.recordly.mobile.ui.theme.StudioSurface
import com.recordly.mobile.ui.theme.StudioSurfaceElevated
import com.recordly.mobile.ui.theme.StudioTextMuted
import com.recordly.mobile.ui.theme.StudioTextPrimary
import com.recordly.mobile.ui.theme.StudioTextSecondary
import com.recordly.mobile.ui.theme.Terracotta
import com.recordly.mobile.ui.theme.WarmSand
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RecordlyViewModel,
    onBack: () -> Unit
) {
    val savedSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    var showFloatingMenu by remember { mutableStateOf(savedSettings.showFloatingMenu) }
    var recordMicrophone by remember { mutableStateOf(savedSettings.recordMicrophone) }
    var intervalSec by remember { mutableFloatStateOf(savedSettings.contactSheetIntervalSec) }
    var maxKeyframes by remember { mutableIntStateOf(savedSettings.maxKeyframes) }

    LaunchedEffect(savedSettings) {
        showFloatingMenu = savedSettings.showFloatingMenu
        recordMicrophone = savedSettings.recordMicrophone
        intervalSec = savedSettings.contactSheetIntervalSec
        maxKeyframes = savedSettings.maxKeyframes
    }

    Scaffold(
        containerColor = StudioBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBg),
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        color = StudioSurfaceElevated,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = StudioTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            "SETTINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = ForestSage
                        )
                        Text(
                            "Studio Preferences",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, StudioBg.copy(alpha = 0.95f), StudioBg)
                        )
                    )
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            viewModel.updateSettings(
                                AppSettings(
                                    showFloatingMenu = showFloatingMenu,
                                    contactSheetIntervalSec = intervalSec,
                                    maxKeyframes = maxKeyframes,
                                    recordMicrophone = recordMicrophone
                                )
                            )
                            onBack()
                        },
                    color = Terracotta,
                    shape = RoundedCornerShape(999.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            "Save Studio Settings",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bento Header 01: Recording Menu & Capture
            item {
                SettingsSectionCard(
                    number = "01",
                    title = "RECORDING & OVERLAY",
                    badge = if (showFloatingMenu) "MENU: VISIBLE" else "MENU: HIDDEN",
                    badgeColor = if (showFloatingMenu) ForestSage else Terracotta
                ) {
                    // Floating Menu Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioSurfaceElevated)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (showFloatingMenu) WarmSand else StudioBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (showFloatingMenu) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (showFloatingMenu) StudioBg else StudioTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Floating Recording Menu",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioTextPrimary
                                )
                                Text(
                                    if (showFloatingMenu)
                                        "Draggable control pill appears over apps"
                                    else
                                        "Record quietly; stop via notification bar",
                                    fontSize = 12.sp,
                                    color = StudioTextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = showFloatingMenu,
                            onCheckedChange = { showFloatingMenu = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = StudioBg,
                                checkedTrackColor = WarmSand,
                                uncheckedThumbColor = StudioTextMuted,
                                uncheckedTrackColor = StudioBorder
                            )
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Microphone audio toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioSurfaceElevated)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (recordMicrophone) Terracotta else StudioBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Record Microphone",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioTextPrimary
                                )
                                Text(
                                    "Capture voiceover while screen recording",
                                    fontSize = 12.sp,
                                    color = StudioTextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = recordMicrophone,
                            onCheckedChange = { recordMicrophone = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Terracotta,
                                uncheckedThumbColor = StudioTextMuted,
                                uncheckedTrackColor = StudioBorder
                            )
                        )
                    }
                }
            }

            // Bento Header 02: Contact Sheet Extraction
            item {
                SettingsSectionCard(
                    number = "02",
                    title = "CONTACT SHEET & STORYBOARD",
                    badge = "AI READY",
                    badgeColor = WarmSand
                ) {
                    // Frame Interval Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioSurfaceElevated)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Sample Frame Interval",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioTextPrimary
                                )
                                Text(
                                    "Capture 1 frame every ${String.format(Locale.US, "%.1f", intervalSec)}s",
                                    fontSize = 12.sp,
                                    color = WarmSand
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WarmSand.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, WarmSand.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    "${String.format(Locale.US, "%.1f", intervalSec)}s",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarmSand
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Slider(
                            value = intervalSec,
                            onValueChange = { intervalSec = (it * 10).toInt() / 10f },
                            valueRange = 0.5f..3.0f,
                            steps = 24,
                            colors = SliderDefaults.colors(
                                thumbColor = WarmSand,
                                activeTrackColor = WarmSand,
                                inactiveTrackColor = StudioBorder
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.5s (Dense)", fontSize = 10.sp, color = StudioTextMuted)
                            Text("1.5s (Recommended)", fontSize = 10.sp, color = StudioTextMuted)
                            Text("3.0s (Sparse)", fontSize = 10.sp, color = StudioTextMuted)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Max Keyframes limit selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioSurfaceElevated)
                            .padding(14.dp)
                    ) {
                        Text(
                            "Max Storyboard Keyframes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Text(
                            "Maximum thumbnails generated for PDF & ZIP",
                            fontSize = 12.sp,
                            color = StudioTextSecondary
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(12, 18, 24, 36).forEach { count ->
                                val selected = maxKeyframes == count
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { maxKeyframes = count },
                                    color = if (selected) ForestSage else StudioBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) ForestSage else StudioBorder
                                    )
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            "$count",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (selected) Color.White else StudioTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bento Header 03: Offline Claude AI Protocol Guide
            item {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "03",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ForestSage
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "ZERO-API CLAUDE WORKFLOW",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = StudioTextSecondary
                                )
                            }
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = WarmSand,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        WorkflowStep(
                            step = "1",
                            title = "Capture Screen",
                            description = "Record any mobile workflow, demo, or tutorial."
                        )
                        WorkflowStep(
                            step = "2",
                            title = "Export Contact Sheet & Prompt",
                            description = "Tap 'Copy AI Prompt' and share the ZIP or PDF with Claude AI."
                        )
                        WorkflowStep(
                            step = "3",
                            title = "Paste Claude's Response",
                            description = "Paste the JSON response into Recordly to instantly execute trims and dynamic zoom keyframes."
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Recordly Mobile v" + com.recordly.mobile.BuildConfig.VERSION_NAME +
                        " (build " + com.recordly.mobile.BuildConfig.VERSION_CODE + ")",
                    fontSize = 11.sp,
                    color = StudioTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    number: String,
    title: String,
    badge: String,
    badgeColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        number,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = StudioTextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun WorkflowStep(
    step: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ForestSage.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ForestSage
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = StudioTextPrimary
            )
            Text(
                description,
                fontSize = 11.5.sp,
                color = StudioTextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
