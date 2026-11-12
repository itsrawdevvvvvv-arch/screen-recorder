package com.recordly.mobile.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordly.mobile.capture.ShizukuStatus
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.ui.theme.StudioAccent
import com.recordly.mobile.ui.theme.StudioBg
import com.recordly.mobile.ui.theme.StudioBorder
import com.recordly.mobile.ui.theme.StudioDanger
import com.recordly.mobile.ui.theme.StudioPrimary
import com.recordly.mobile.ui.theme.StudioPrimaryBright
import com.recordly.mobile.ui.theme.StudioSurface
import com.recordly.mobile.ui.theme.StudioSurfaceElevated
import com.recordly.mobile.ui.theme.StudioTextPrimary
import com.recordly.mobile.ui.theme.StudioTextSecondary
import com.recordly.mobile.ui.theme.StudioWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: RecordlyViewModel,
    onStartRecordingClick: () -> Unit,
    onStopRecordingClick: () -> Unit,
    onImportVideoClick: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val mice by viewModel.connectedMice.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val shizukuDetail by viewModel.shizukuDetail.collectAsStateWithLifecycle()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val groqSettings by viewModel.groqSettings.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val elapsedMs by viewModel.recordingElapsedMs.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val shizukuReady = shizukuStatus == ShizukuStatus.READY || shizukuStatus == ShizukuStatus.TRACKING
    val groqReady = groqSettings.apiKey.isNotBlank()
    val readyCount = listOf(mice.isNotEmpty(), shizukuReady, accessibilityEnabled, groqReady).count { it }

    Scaffold(
        containerColor = StudioBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBg),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RecordlyEmblem(size = 40)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Recordly Mobile",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = StudioTextPrimary
                            )
                            Text(
                                "Groq AI + Mouse Auto-Zoom Studio",
                                fontSize = 11.sp,
                                color = StudioTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettings),
                        color = StudioSurfaceElevated,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = StudioTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, StudioBg.copy(alpha = 0.95f)))
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    color = StudioSurface,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DockNavIcon(Icons.Rounded.Home, selected = selectedTab == 0) { selectedTab = 0 }
                        DockNavIcon(Icons.Rounded.Videocam, selected = selectedTab == 1) {
                            selectedTab = 1
                            projects.firstOrNull()?.let { onOpenProject(it.id) }
                        }
                        DockNavIcon(Icons.Rounded.AutoAwesome, selected = selectedTab == 2) {
                            selectedTab = 2
                            projects.firstOrNull()?.let { onOpenProject(it.id) } ?: onOpenSettings()
                        }
                        DockNavIcon(Icons.Rounded.Settings, selected = false) { onOpenSettings() }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Capture Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioSurface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Subtle radial glow & contour ellipses
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(StudioPrimary.copy(alpha = 0.34f), Color.Transparent),
                                    center = Offset(size.width * 0.9f, size.height * 0.1f),
                                    radius = size.width * 0.6f
                                ),
                                center = Offset(size.width * 0.9f, size.height * 0.1f),
                                radius = size.width * 0.6f
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(StudioAccent.copy(alpha = 0.14f), Color.Transparent),
                                    center = Offset(size.width * 0.1f, size.height * 1.0f),
                                    radius = size.width * 0.45f
                                ),
                                center = Offset(size.width * 0.1f, size.height * 1.0f),
                                radius = size.width * 0.45f
                            )
                            val cx = size.width * 0.84f
                            val cy = size.height * 0.22f
                            for (i in 1..4) {
                                drawOval(
                                    color = StudioPrimaryBright.copy(alpha = 0.14f),
                                    topLeft = Offset(cx - i * 34f, cy - i * 20f),
                                    size = Size(i * 68f, i * 40f),
                                    style = Stroke(width = 1.2f)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = StudioAccent.copy(alpha = 0.11f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(StudioAccent)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        if (isRecording) "RECORDING LIVE" else "AI CREATOR STUDIO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp,
                                        color = StudioAccent
                                    )
                                }
                            }

                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = StudioTextPrimary, fontWeight = FontWeight.ExtraBold)) {
                                        append(if (isRecording) "Recording\n" else "Desktop-Grade\n")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            brush = Brush.horizontalGradient(listOf(StudioPrimaryBright, StudioAccent)),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    ) {
                                        append(if (isRecording) "Screen & Mouse" else "Mobile Capture")
                                    }
                                },
                                fontSize = 26.sp,
                                lineHeight = 29.sp
                            )

                            Text(
                                "Tracks connected mouse for smooth auto-zoom, cuts out phone keyboard frames, and uses Groq AI to sync speed & .srt captions to audio.",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = StudioTextSecondary
                            )

                            Spacer(Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Knob-style primary pill button
                                val btnBg = if (isRecording) StudioDanger else StudioPrimary
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(onClick = if (isRecording) onStopRecordingClick else onStartRecordingClick),
                                    color = btnBg,
                                    shape = RoundedCornerShape(999.dp),
                                    shadowElevation = 10.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 7.dp, end = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                if (isRecording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                                                contentDescription = null,
                                                tint = btnBg,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            if (isRecording) "Stop & Edit" else "Start Recording",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            maxLines = 1
                                        )
                                        if (isRecording) {
                                            Spacer(Modifier.weight(1f))
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    formatMs(elapsedMs),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(onClick = onImportVideoClick),
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.5.dp, StudioTextSecondary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = StudioTextPrimary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Import",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = StudioTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Capture & AI Readiness Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Capture & AI Readiness",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary
                    )
                    Text(
                        "$readyCount / 4 ready",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StudioTextSecondary
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadinessTile(
                        icon = Icons.Rounded.Mouse,
                        title = if (mice.isNotEmpty()) "Mouse Connected: ${mice.first().name}" else "No Physical Mouse Connected",
                        subtitle = if (mice.isNotEmpty()) "Pointer is followed for auto-zoom" else "Plug in USB-C OTG or Bluetooth mouse for cursor tracking",
                        badgeText = if (mice.isNotEmpty()) "CONNECTED" else "CONNECT",
                        active = mice.isNotEmpty(),
                        violetIcon = false,
                        onClick = { viewModel.refreshHardwareAndPermissions() }
                    )

                    ReadinessTile(
                        icon = Icons.Rounded.Bolt,
                        title = "60fps Mouse Hook (Shizuku): ${shizukuStatus.name}",
                        subtitle = shizukuDetail,
                        badgeText = if (shizukuReady) "READY" else "SETUP",
                        active = shizukuReady,
                        violetIcon = true,
                        onClick = {
                            if (shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED) {
                                viewModel.requestShizukuPermission()
                            } else {
                                viewModel.refreshHardwareAndPermissions()
                            }
                        }
                    )

                    ReadinessTile(
                        icon = Icons.Rounded.Keyboard,
                        title = if (accessibilityEnabled) "Keyboard Auto-Trim Service Active" else "Enable Keyboard Auto-Detector",
                        subtitle = if (accessibilityEnabled) {
                            "Phone keyboard frames are detected & cut"
                        } else {
                            "Optional: tap to enable Accessibility, or Groq Vision detects keyboards"
                        },
                        badgeText = if (accessibilityEnabled) "ACTIVE" else "ENABLE",
                        active = accessibilityEnabled,
                        violetIcon = false,
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )

                    ReadinessTile(
                        icon = Icons.Rounded.AutoAwesome,
                        title = if (groqReady) "Groq API Ready (${groqSettings.chatModel})" else "Add Groq API Key",
                        subtitle = if (groqReady) {
                            "Whisper .srt + Vision + AI Director enabled"
                        } else {
                            "Tap to configure Groq API key for transcription & smart editing"
                        },
                        badgeText = if (groqReady) "READY" else "ADD KEY",
                        active = groqReady,
                        violetIcon = true,
                        onClick = onOpenSettings
                    )
                }
            }

            // Studio Projects Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Studio Projects (${projects.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary
                    )
                    Text(
                        "Newest first",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StudioTextSecondary
                    )
                }
            }

            if (projects.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, StudioTextSecondary.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No projects yet — tap Start Recording or Import a screen video.",
                                color = StudioTextSecondary,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(projects, key = { _, p -> p.id }) { idx, project ->
                    ProjectCard(
                        project = project,
                        gradientIndex = idx,
                        onOpen = { onOpenProject(project.id) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecordlyEmblem(size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(listOf(StudioPrimary, StudioAccent, StudioPrimaryBright, StudioPrimary))
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size((size - 10).dp)
                .clip(CircleShape)
                .background(StudioBg),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size((size * 0.28f).dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(StudioPrimaryBright, StudioAccent)))
            )
        }
    }
}

@Composable
private fun DockNavIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) StudioPrimary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else StudioTextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ReadinessTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    active: Boolean,
    violetIcon: Boolean,
    onClick: () -> Unit
) {
    val iconTint = when {
        violetIcon && active -> StudioPrimaryBright
        active -> StudioAccent
        else -> StudioWarning
    }
    val iconBg = iconTint.copy(alpha = 0.16f)
    val badgeColor = if (active) StudioAccent else StudioWarning

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.5.sp, color = StudioTextSecondary, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    badgeText,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    color = badgeColor
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: RecordingProject,
    gradientIndex: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(project.createdAtMs))
    val clickCount = project.mouseSamples.count { it.isDown }
    val kbCount = project.keyboardIntervals.size

    val gradColors = when (gradientIndex % 3) {
        0 -> listOf(StudioPrimary, Color(0xFF1D1946))
        1 -> listOf(Color(0xFF00B48E), Color(0xFF0B2A3A))
        else -> listOf(Color(0xFFFF6B7A), Color(0xFF2A1633))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        color = StudioSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(gradColors)),
                contentAlignment = Alignment.Center
            ) {
                // Stylized tilted phone silhouette like mockup
                Box(
                    modifier = Modifier
                        .size(28.dp, 48.dp)
                        .graphicsLayer { rotationZ = -8f }
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.17f))
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = StudioBg,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    project.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$dateStr • ${formatMs(project.durationMs)} • $clickCount clicks • $kbCount KB spans",
                    fontSize = 11.sp,
                    color = StudioTextSecondary
                )
                if (project.srtCues.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = StudioAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            ".SRT Ready (${project.srtCues.size} cues)",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StudioAccent
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(StudioDanger.copy(alpha = 0.1f))
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFFFF7C89),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSecs = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSecs / 60L
    val secs = totalSecs % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
