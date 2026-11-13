package com.recordly.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordly.mobile.model.RecordingProject
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
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val elapsedMs by viewModel.recordingElapsedMs.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = StudioBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBg),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "recordly",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = StudioTextPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ForestSage)
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        // Minimalist indicator dashes from reference
                        Box(
                            Modifier
                                .width(18.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(WarmSand)
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier
                                .width(8.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(StudioTextMuted)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Status Pill Banner with Floating Menu Toggle
                item {
                    StatusPillBanner(
                        isRecording = isRecording,
                        elapsedMs = elapsedMs,
                        showFloatingMenu = appSettings.showFloatingMenu,
                        onToggleFloatingMenu = { viewModel.toggleFloatingMenu() }
                    )
                }

                // 2. Large Hero Showcase Card (Reference Screen 1)
                item {
                    HeroShowcaseCard(
                        isRecording = isRecording,
                        elapsedMs = elapsedMs,
                        latestProject = projects.firstOrNull(),
                        onStartRecording = onStartRecordingClick,
                        onStopRecording = onStopRecordingClick
                    )
                }

                // 3. Bento Grid: 2 Cards (Reference Screen 2)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card 1: Warm Sand Light Card
                        BentoWarmSandCard(
                            modifier = Modifier.weight(1f),
                            projectCount = projects.size,
                            onImportClick = onImportVideoClick
                        )

                        // Card 2: Dark AI Storyboard Card
                        BentoDarkAiCard(
                            modifier = Modifier.weight(1f),
                            latestProject = projects.firstOrNull(),
                            onCardClick = {
                                projects.firstOrNull()?.let { onOpenProject(it.id) }
                            }
                        )
                    }
                }

                // 4. Section Header for Recent Recordings
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Recordings",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = StudioSurfaceElevated,
                            modifier = Modifier.clickable(onClick = onImportVideoClick)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = WarmSand, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Import", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = WarmSand)
                            }
                        }
                    }
                }

                // 5. Recordings List
                if (projects.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = StudioSurface,
                            border = BorderStroke(1.dp, StudioBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.GridView, contentDescription = null, tint = StudioTextMuted, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No recordings yet",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioTextPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tap 'Start Recording' above to record your screen & generate a visual contact sheet.",
                                    fontSize = 12.sp,
                                    color = StudioTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(projects, key = { it.id }) { proj ->
                        ProjectListItem(
                            project = proj,
                            onClick = { onOpenProject(proj.id) },
                            onDelete = { viewModel.deleteProject(proj.id) }
                        )
                    }
                }
            }

            // 6. Floating Bottom Navigation Bar (Reference Screen 2)
            FloatingBottomNavBar(
                activeTab = activeTab,
                onTabSelect = { tab ->
                    activeTab = tab
                    when (tab) {
                        0 -> {} // Home
                        1 -> projects.firstOrNull()?.let { onOpenProject(it.id) }
                        2 -> onOpenSettings()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

/**
 * Top Status Pill Banner with Floating Menu Toggle Switch.
 */
@Composable
private fun StatusPillBanner(
    isRecording: Boolean,
    elapsedMs: Long,
    showFloatingMenu: Boolean,
    onToggleFloatingMenu: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Terracotta else ForestSage),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Rounded.FiberManualRecord else Icons.Rounded.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (isRecording) "Recording • ${formatElapsed(elapsedMs)}" else "Ready to record",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary
                    )
                    Text(
                        if (showFloatingMenu) "Floating menu: Visible" else "Floating menu: Hidden",
                        fontSize = 10.sp,
                        color = StudioTextSecondary
                    )
                }
            }

            // Quick Toggle pill for Recording Menu Hide / Show
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (showFloatingMenu) WarmSand.copy(alpha = 0.16f) else StudioSurfaceElevated,
                border = BorderStroke(1.dp, if (showFloatingMenu) WarmSand.copy(alpha = 0.35f) else StudioBorder),
                modifier = Modifier.clickable(onClick = onToggleFloatingMenu)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showFloatingMenu) "MENU: ON" else "MENU: OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (showFloatingMenu) WarmSand else StudioTextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Large Hero Showcase Card copying Screen 1 layout with organic contour lines.
 */
@Composable
private fun HeroShowcaseCard(
    isRecording: Boolean,
    elapsedMs: Long,
    latestProject: RecordingProject?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Contour line curves background (Reference Screen 1)
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
            ) {
                val strokeColor = Color(0x0EFFFFFF)
                val w = size.width
                val h = size.height

                for (i in 0..4) {
                    val offsetY = h * 0.15f * i
                    val path = Path().apply {
                        moveTo(0f, offsetY + 40f)
                        cubicTo(
                            w * 0.35f, offsetY - 20f,
                            w * 0.65f, offsetY + 90f,
                            w, offsetY + 20f
                        )
                    }
                    drawPath(path, color = strokeColor, style = Stroke(width = 1.5f))
                }
            }

            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                // Top Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ForestSage.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, ForestSage.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Terracotta else ForestSage)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isRecording) "RECORDING IN PROGRESS" else "ZERO SETUP • PURE PRIVACY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) Terracotta else ForestSage
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.GridView, contentDescription = null, tint = WarmSand, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Bold Punchy Headline
                Text(
                    text = buildAnnotatedString {
                        append("Know exactly\n")
                        withStyle(SpanStyle(color = WarmSand)) {
                            append("what to trim, cut & zoom.")
                        }
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 31.sp,
                    color = StudioTextPrimary
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Record clean screen video, get a visual contact sheet, and export pre-built prompts for Claude AI.",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = StudioTextSecondary
                )

                Spacer(Modifier.height(22.dp))

                // Floating Action Capsule Pill (Reference Screen 1 bottom button)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            if (isRecording) onStopRecording() else onStartRecording()
                        },
                    shape = RoundedCornerShape(999.dp),
                    color = StudioBg,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Circular Action Button
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = if (isRecording) Terracotta else WarmSand
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isRecording) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isRecording) Color.White else Color(0xFF16130E),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Center Title
                        Text(
                            text = if (isRecording) "Stop & Build Contact Sheet" else "Start Screen Recording",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )

                        // Right Chevron Pill
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = StudioSurfaceElevated,
                            border = BorderStroke(1.dp, StudioBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bento Grid Card 1: Warm Sand Card (Reference Screen 2 Left Card).
 */
@Composable
private fun BentoWarmSandCard(
    modifier: Modifier = Modifier,
    projectCount: Int,
    onImportClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(148.dp)
            .clickable(onClick = onImportClick),
        shape = RoundedCornerShape(24.dp),
        color = WarmSand
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.GridView, contentDescription = null, tint = Color(0xFF1B1710), modifier = Modifier.size(20.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0x24000000)
                ) {
                    Text(
                        text = "PDF & ZIP",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1B1710)
                    )
                }
            }

            Column {
                Text(
                    text = "Contact Sheet",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A4132)
                )
                Text(
                    text = if (projectCount > 0) "$projectCount Saved" else "Storyboard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1B1710)
                )
                Text(
                    text = "Auto timestamp grid",
                    fontSize = 10.5.sp,
                    color = Color(0xFF5A503F)
                )
            }
        }
    }
}

/**
 * Bento Grid Card 2: Dark Slate Card with Terracotta Accents (Reference Screen 2 Right Card).
 */
@Composable
private fun BentoDarkAiCard(
    modifier: Modifier = Modifier,
    latestProject: RecordingProject?,
    onCardClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(148.dp)
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(24.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Terracotta, modifier = Modifier.size(20.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Terracotta.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Terracotta)
                    )
                }
            }

            Column {
                Text(
                    text = "AI Direction",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioTextSecondary
                )
                Text(
                    text = "Claude AI",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StudioTextPrimary
                )
                Text(
                    text = "Copy prompt & apply EDL",
                    fontSize = 10.5.sp,
                    color = StudioTextSecondary
                )
            }
        }
    }
}

/**
 * Project item card.
 */
@Composable
private fun ProjectListItem(
    project: RecordingProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = WarmSand, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = project.title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ForestSage.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "${project.durationMs / 1000}s",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestSage
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${project.keyframes.size} keyframes • PDF ready",
                            fontSize = 11.sp,
                            color = StudioTextSecondary
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = StudioTextMuted, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/**
 * Floating bottom navigation bar matching Screen 2.
 */
@Composable
private fun FloatingBottomNavBar(
    activeTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(999.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(
                selected = activeTab == 0,
                icon = Icons.Rounded.Home,
                label = "Studio",
                onClick = { onTabSelect(0) }
            )
            NavBarItem(
                selected = activeTab == 1,
                icon = Icons.Rounded.GridView,
                label = "Contact Sheet",
                onClick = { onTabSelect(1) }
            )
            NavBarItem(
                selected = activeTab == 2,
                icon = Icons.Rounded.Settings,
                label = "Settings",
                onClick = { onTabSelect(2) }
            )
        }
    }
}

@Composable
private fun NavBarItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) WarmSand else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) Color(0xFF16130E) else StudioTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16130E)
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val s = (ms / 1000L).toInt()
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}
