package com.recordly.mobile.ui

import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.recordly.mobile.editor.AutoZoomGenerator
import com.recordly.mobile.editor.CameraMath
import com.recordly.mobile.model.CameraState
import com.recordly.mobile.model.MouseSample
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.ZoomKeyframe
import com.recordly.mobile.model.ZoomSource
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
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioEditorScreen(
    projectId: String,
    viewModel: RecordlyViewModel,
    onBack: () -> Unit,
    onImportSrt: () -> Unit,
    onImportAudio: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val aiProgress by viewModel.aiProgress.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val groqSettings by viewModel.groqSettings.collectAsStateWithLifecycle()

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val currentProject = project
    if (currentProject == null) {
        Box(Modifier.fillMaxSize().background(StudioBg), contentAlignment = Alignment.Center) {
            Text("Loading Studio…", color = StudioTextSecondary)
        }
        return
    }

    val exoPlayer = remember(currentProject.videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(currentProject.videoPath))))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.setVideoTextureView(null)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(40L)
        }
    }

    val camState = AutoZoomGenerator.evaluateCameraAt(
        originalTimeMs = currentPositionMs,
        zoomKeyframes = currentProject.editPlan?.zoomKeyframes ?: emptyList(),
        mouseSamples = currentProject.mouseSamples
    )
    val activeCue = currentProject.srtCues.firstOrNull { currentPositionMs in it.startMs..it.endMs }
    val aspect = (currentProject.width.toFloat() / currentProject.height.coerceAtLeast(1).toFloat())
        .coerceIn(0.45f, 1.85f)

    Scaffold(
        containerColor = StudioBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBg),
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        color = StudioSurfaceElevated,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = StudioTextPrimary)
                        }
                    }
                },
                title = {
                    Column(modifier = Modifier.padding(start = 6.dp)) {
                        Text(
                            currentProject.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StudioTextPrimary
                        )
                        Text(
                            "${formatMs(currentProject.durationMs)} • ${currentProject.editPlan?.segments?.size ?: 1} segments",
                            fontSize = 11.sp,
                            color = StudioTextSecondary
                        )
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = !exportState.exporting) { viewModel.exportProjectMp4() },
                        color = StudioAccent,
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Upload,
                                contentDescription = null,
                                tint = Color(0xFF032A21),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (exportState.exporting) "Exporting…" else "Export",
                                color = Color(0xFF032A21),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live Studio Preview Canvas with HUD pills
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF43359F), Color(0xFF1D1946), Color(0xFF10131F))
                            )
                        )
                        .padding(14.dp)
                ) {
                    // Top-left zoom scale HUD pill
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart),
                        color = StudioBg.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "${"%.1f".format(camState.scale)}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }

                    // Top-right Auto-Zoom HUD pill
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        color = StudioAccent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Auto-Zoom", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = StudioAccent)
                        }
                    }

                    // Centered Phone Video Frame
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight(0.92f)
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        val t = CameraMath.ndcTransform(camState.scale, camState.focusX, camState.focusY)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin.Center
                                    scaleX = t.scale
                                    scaleY = t.scale
                                    translationX = t.pixelTranslationX(size.width)
                                    translationY = t.pixelTranslationY(size.height)
                                }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    TextureView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { textureView ->
                                    exoPlayer.setVideoTextureView(textureView)
                                }
                            )

                            if (currentProject.styleConfig.showCursorRing && camState.cursorX != null && camState.cursorY != null) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val pt = Offset(camState.cursorX * size.width, camState.cursorY * size.height)
                                    if (camState.clickRippleProgress in 0f..1f) {
                                        drawCircle(
                                            color = StudioPrimary.copy(alpha = 1f - camState.clickRippleProgress),
                                            radius = 12f + camState.clickRippleProgress * 36f,
                                            center = pt,
                                            style = Stroke(width = 4f)
                                        )
                                    }
                                    drawCircle(color = Color.White, radius = 8f, center = pt)
                                    drawCircle(color = StudioPrimary, radius = 8f, center = pt, style = Stroke(width = 3f))
                                }
                            }
                        }

                        if (currentProject.styleConfig.burnSubtitles && activeCue != null) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp),
                                color = StudioBg.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = activeCue.text,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 2. Playback Scrubber & Quick Chips
            item {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(StudioPrimary)
                                    .clickable { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                formatMs(currentPositionMs),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = StudioTextPrimary
                            )
                            Slider(
                                value = currentPositionMs.toFloat(),
                                onValueChange = { exoPlayer.seekTo(it.toLong()) },
                                valueRange = 0f..currentProject.durationMs.coerceAtLeast(1L).toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = StudioAccent,
                                    inactiveTrackColor = Color(0xFF2A3144)
                                )
                            )
                            Text(
                                formatMs(currentProject.durationMs),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = StudioTextSecondary
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioToolChip(Icons.Rounded.Add, "Zoom Here", active = false, isToggle = false) {
                                viewModel.addManualZoomAt(currentPositionMs)
                            }
                            StudioToolChip(Icons.Rounded.Download, "Import .srt", active = false, isToggle = false, onClick = onImportSrt)
                            StudioToolChip(Icons.Rounded.Mic, "Voiceover Audio", active = false, isToggle = false, onClick = onImportAudio)
                            StudioToolChip(Icons.Rounded.Save, "Save .srt", active = false, isToggle = false) {
                                viewModel.exportSrtFile()
                            }
                            StudioToolChip(
                                Icons.Rounded.Check,
                                "Captions",
                                active = currentProject.styleConfig.burnSubtitles,
                                isToggle = true
                            ) { viewModel.toggleBurnSubtitles() }
                            StudioToolChip(
                                Icons.Rounded.Check,
                                "Cursor",
                                active = currentProject.styleConfig.showCursorRing,
                                isToggle = true
                            ) { viewModel.toggleShowCursor() }
                        }
                    }
                }
            }

            // 3. Groq AI Magic Sync CTA & Progress
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = !aiProgress.running) { viewModel.runGroqMagicSync(currentProject.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .background(Brush.horizontalGradient(listOf(StudioPrimary, StudioPrimaryBright, StudioPrimary)))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (aiProgress.running) aiProgress.stepTitle else "Groq AI Magic Sync (.srt + Trim + Speed + Zoom)",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 2
                        )
                    }
                }

                if (aiProgress.running || aiProgress.progress > 0f) {
                    Spacer(Modifier.height(10.dp))
                    GradientProgressBar(fraction = aiProgress.progress)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            aiProgress.stepTitle.ifBlank { "AI Sync Complete" },
                            fontSize = 11.5.sp,
                            color = StudioTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(aiProgress.progress * 100).toInt()}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = StudioAccent
                        )
                    }
                }

                if (exportState.exporting || exportState.message != null || exportState.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                exportState.error ?: exportState.message ?: "Exporting MP4…",
                                color = if (exportState.error != null) StudioDanger else StudioAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (exportState.exporting) {
                                GradientProgressBar(fraction = exportState.progress)
                            }
                        }
                    }
                }
            }

            // 4. Groq AI Director Card
            item {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, StudioAccent.copy(alpha = 0.22f))
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(listOf(StudioAccent.copy(alpha = 0.06f), Color.Transparent))
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Groq AI Director", color = StudioAccent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            Surface(color = StudioPrimary.copy(alpha = 0.22f), shape = RoundedCornerShape(999.dp)) {
                                Text(
                                    groqSettings.chatModel.substringBefore("-versatile"),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFB8B0FF)
                                )
                            }
                        }

                        Text(
                            currentProject.editPlan?.summary?.takeIf { it.isNotBlank() }
                                ?: "Run Magic Sync or prompt Groq AI below to auto-trim keyboards, adjust speed to audio, and add mouse zooms.",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFCBD5E1)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = aiPrompt,
                                onValueChange = { aiPrompt = it },
                                placeholder = {
                                    Text(
                                        "e.g. Cut keyboard parts, speed up loading 3x, zoom 2.2x on clicks",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = StudioSurfaceElevated,
                                    unfocusedContainerColor = StudioSurfaceElevated,
                                    focusedBorderColor = StudioAccent,
                                    unfocusedBorderColor = StudioBorder
                                ),
                                maxLines = 2
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (!aiProgress.running && aiPrompt.isNotBlank()) StudioPrimary else StudioSurfaceElevated)
                                    .clickable(enabled = !aiProgress.running && aiPrompt.isNotBlank()) {
                                        viewModel.refineWithPrompt(currentProject.id, aiPrompt)
                                        aiPrompt = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // 5. Auto-Zoom Keyframes Strip
            val zooms = currentProject.editPlan?.zoomKeyframes ?: emptyList()
            if (zooms.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto-Zoom Keyframes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                        Text("${zooms.size} keyframes", fontSize = 11.5.sp, color = StudioTextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        zooms.forEach { z ->
                            val dotColor = when (z.source) {
                                ZoomSource.MOUSE -> StudioPrimaryBright
                                ZoomSource.AI -> StudioAccent
                                else -> Color(0xFFCBD5E1)
                            }
                            Surface(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { exoPlayer.seekTo(z.startMs) },
                                color = StudioSurfaceElevated,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, dotColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        "${formatMs(z.startMs)} • ${"%.1f".format(z.zoomScale)}x (${z.source.name})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Timeline Segments Proportional Bar + Legend + Rows
            val segments = currentProject.editPlan?.segments ?: emptyList()
            if (segments.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Timeline Segments", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                        Text("Tap speed · toggle keep / trim", fontSize = 11.5.sp, color = StudioTextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        segments.forEach { seg ->
                            val w = (seg.endMs - seg.startMs).coerceAtLeast(100L).toFloat()
                            val barColor = when {
                                seg.action == SegmentAction.TRIM -> StudioDanger
                                seg.speed > 1.05f -> StudioAccent
                                else -> StudioPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(w)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(StudioPrimary, "Keep")
                        LegendDot(StudioAccent, "Sped up")
                        LegendDot(StudioDanger, "Trimmed")
                    }
                }

                itemsIndexed(segments) { index, seg ->
                    val isTrim = seg.action == SegmentAction.TRIM
                    val isFast = seg.speed > 1.05f
                    val reasonColor = if (isTrim) StudioWarning else StudioTextSecondary

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { exoPlayer.seekTo(seg.startMs) },
                        color = if (isTrim) StudioDanger.copy(alpha = 0.12f) else StudioSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isTrim) StudioDanger.copy(alpha = 0.5f) else Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${formatMs(seg.startMs)} → ${formatMs(seg.endMs)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = StudioTextPrimary
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(reasonColor))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        seg.reason.ifBlank { if (isTrim) "Trimmed" else "Main content" },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = reasonColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { viewModel.cycleSegmentSpeed(index) },
                                color = if (isFast) StudioAccent.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.07f),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, if (isFast) StudioAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isFast) {
                                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    Text(
                                        "${seg.speed}x",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp,
                                        color = if (isFast) StudioAccent else StudioTextPrimary
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            Surface(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { viewModel.toggleSegmentTrim(index) },
                                color = if (isTrim) StudioDanger.copy(alpha = 0.2f) else StudioPrimary,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, if (isTrim) StudioDanger else StudioPrimary)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isTrim) {
                                        Icon(Icons.Rounded.ContentCut, contentDescription = null, tint = Color(0xFFFF8A96), modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        if (isTrim) "TRIM" else "KEEP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp,
                                        color = if (isTrim) Color(0xFFFF8A96) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Synced .SRT Subtitles Grouped Card
            val displayCues = currentProject.editPlan?.remappedSrtCues?.takeIf { it.isNotEmpty() }
                ?: currentProject.srtCues
            if (displayCues.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Synced .SRT Subtitles", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StudioTextPrimary)
                        Surface(color = StudioAccent.copy(alpha = 0.15f), shape = RoundedCornerShape(999.dp)) {
                            Text(
                                "${displayCues.size} CUES",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = StudioAccent
                            )
                        }
                    }
                }

                item {
                    Surface(
                        color = StudioSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Column(Modifier.padding(6.dp)) {
                            displayCues.forEachIndexed { i, cue ->
                                val isActive = currentPositionMs in cue.startMs..cue.endMs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isActive) StudioPrimary.copy(alpha = 0.18f) else Color.Transparent)
                                        .clickable { exoPlayer.seekTo(cue.startMs) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        formatMs(cue.startMs),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = StudioAccent,
                                        modifier = Modifier.width(50.dp)
                                    )
                                    Text(
                                        cue.text,
                                        fontSize = 12.5.sp,
                                        color = StudioTextPrimary,
                                        lineHeight = 17.sp
                                    )
                                }
                                if (i < displayCues.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(StudioBorder)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioToolChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    isToggle: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) StudioPrimary.copy(alpha = 0.24f) else StudioSurfaceElevated
    val border = if (active) StudioPrimary else StudioTextSecondary.copy(alpha = 0.28f)
    Surface(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isToggle || active) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) Color.White else StudioPrimaryBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Color.White else StudioTextPrimary
            )
        }
    }
}

@Composable
private fun GradientProgressBar(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF242B3D))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(listOf(StudioPrimary, StudioAccent)))
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = StudioTextSecondary)
    }
}

/**
 * Standalone live preview composable matching Claude's integration spec.
 * Uses the exact same NdcTransform mathematics as the Media3 export.
 */
@Composable
fun StudioEditorPreview(
    exoPlayer: ExoPlayer,
    zoomKeyframes: List<ZoomKeyframe>,
    mouseSamples: List<MouseSample>,
    modifier: Modifier = Modifier,
    outputToOriginalMs: (Long) -> Long = { it }
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { }
            positionMs = exoPlayer.currentPosition
        }
    }

    val camState = remember(zoomKeyframes, mouseSamples, outputToOriginalMs) {
        derivedStateOf {
            AutoZoomGenerator.evaluateCameraAt(outputToOriginalMs(positionMs), zoomKeyframes, mouseSamples)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        ZoomedVideoSurface(exoPlayer = exoPlayer, camProvider = { camState.value })
    }
}

/**
 * The camera operates on the VIDEO rectangle (not the letterboxed container), exactly like the export:
 *  - outer box = video aspect ratio + clip (what leaves the frame is cropped, same as Transformer output)
 *  - inner layer = scale about the center + translation that moves the focus point to the center.
 *
 * TextureView (not SurfaceView) ensures that graphicsLayer transforms apply directly to the video pixels.
 */
@Composable
fun ZoomedVideoSurface(
    exoPlayer: ExoPlayer,
    camProvider: () -> CameraState,
    modifier: Modifier = Modifier
) {
    var aspect by remember { mutableFloatStateOf(9f / 16f) }
    DisposableEffect(exoPlayer) {
        fun apply(v: VideoSize) {
            if (v.width > 0 && v.height > 0) {
                aspect = v.width * v.pixelWidthHeightRatio / v.height
            }
        }
        apply(exoPlayer.videoSize)
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) = apply(videoSize)
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.setVideoTextureView(null)
        }
    }

    Box(modifier = modifier.aspectRatio(aspect).clipToBounds()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val cam = camProvider()
                    val t = CameraMath.ndcTransform(cam.scale, cam.focusX, cam.focusY)
                    transformOrigin = TransformOrigin.Center
                    scaleX = t.scale
                    scaleY = t.scale
                    translationX = t.pixelTranslationX(size.width)
                    translationY = t.pixelTranslationY(size.height)
                }
        ) {
            AndroidView(
                factory = { ctx -> TextureView(ctx) },
                update = { exoPlayer.setVideoTextureView(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
