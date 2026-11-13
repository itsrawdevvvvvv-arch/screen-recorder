package com.recordly.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.recordly.mobile.editor.ContactSheetEngine
import com.recordly.mobile.model.CameraState
import com.recordly.mobile.model.ContactSheetFrame
import com.recordly.mobile.model.RecordingProject
import com.recordly.mobile.model.SegmentAction
import com.recordly.mobile.model.ZoomKeyframe
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioEditorScreen(
    projectId: String,
    viewModel: RecordlyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val isGeneratingSheet by viewModel.isGeneratingSheet.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Contact Sheet (Ref Screen 3), 1: Player & Timeline
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedJsonText by remember { mutableStateOf("") }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val currentProject = project
    if (currentProject == null) {
        Box(Modifier.fillMaxSize().background(StudioBg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WarmSand)
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
        while (isActive) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(40L)
        }
    }

    val camState = AutoZoomGenerator.evaluateCameraAt(
        originalTimeMs = currentPositionMs,
        zoomKeyframes = currentProject.editPlan?.zoomKeyframes ?: emptyList()
    )

    Scaffold(
        containerColor = StudioBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBg),
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        color = StudioSurfaceElevated,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = StudioTextPrimary, modifier = Modifier.size(19.dp))
                        }
                    }
                },
                title = {
                    Column(modifier = Modifier.padding(start = 6.dp)) {
                        Text(
                            text = if (activeTab == 0) "Contact Sheet" else "Video Studio",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = "${currentProject.title} • ${currentProject.durationMs / 1000}s",
                            fontSize = 11.sp,
                            color = StudioTextSecondary
                        )
                    }
                },
                actions = {
                    // Close button from Reference Screen 3
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = StudioTextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mode Switcher Pill: [Contact Sheet] vs [Player & Timeline]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabPillButton(
                            selected = activeTab == 0,
                            icon = Icons.Rounded.GridView,
                            label = "Contact Sheet",
                            onClick = { activeTab = 0 }
                        )
                        TabPillButton(
                            selected = activeTab == 1,
                            icon = Icons.Rounded.PlayArrow,
                            label = "Player & Timeline",
                            onClick = { activeTab = 1 }
                        )
                    }
                }
            }

            if (activeTab == 0) {
                // View 1: Contact Sheet & Claude AI Exporter (Matches Reference Screen 3)
                ContactSheetStoryboardView(
                    project = currentProject,
                    isGenerating = isGeneratingSheet,
                    onRegenerate = { viewModel.regenerateContactSheet(currentProject.id) },
                    onCopyPrompt = {
                        val prompt = ContactSheetEngine.buildAiPrompt(currentProject, currentProject.keyframes)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Claude Prompt", prompt))
                        Toast.makeText(context, "Copied prompt for Claude AI!", Toast.LENGTH_SHORT).show()
                    },
                    onDownloadPdf = { viewModel.exportPdfToDownloads() },
                    onDownloadZip = { viewModel.exportZipToDownloads() },
                    onPasteResponseClick = { showPasteDialog = true }
                )
            } else {
                // View 2: Player & Timeline with Media3 TextureView and Zoom Rendering
                PlayerAndTimelineView(
                    exoPlayer = exoPlayer,
                    currentProject = currentProject,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    camState = camState,
                    exportState = exportState,
                    onExportClick = { viewModel.exportProjectMp4() },
                    onPasteResponseClick = { showPasteDialog = true },
                    onSeek = { exoPlayer.seekTo(it) }
                )
            }
        }
    }

    // Modal Dialog to Paste Claude AI Response
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = StudioSurfaceElevated,
            title = {
                Text(
                    "Apply Claude AI Response",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Paste the JSON or code response generated by Claude AI. Recordly will parse the cuts, trims, and zoom keyframes and apply them to the timeline.",
                        fontSize = 12.sp,
                        color = StudioTextSecondary,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pastedJsonText,
                        onValueChange = { pastedJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("Paste JSON here...", color = StudioTextMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmSand,
                            unfocusedBorderColor = StudioBorder,
                            focusedTextColor = StudioTextPrimary,
                            unfocusedTextColor = StudioTextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = WarmSand,
                    modifier = Modifier.clickable {
                        viewModel.applyClaudeAiResponse(currentProject.id, pastedJsonText)
                        showPasteDialog = false
                    }
                ) {
                    Text(
                        "Apply to Timeline",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16130E)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel", color = StudioTextSecondary)
                }
            }
        )
    }
}

/**
 * Screen 3 Reference Implementation: Contact Sheet & Claude AI Storyboard View.
 */
@Composable
private fun ContactSheetStoryboardView(
    project: RecordingProject,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    onCopyPrompt: () -> Unit,
    onDownloadPdf: () -> Unit,
    onDownloadZip: () -> Unit,
    onPasteResponseClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Radar Focal Circle Callout (Matches Reference Screen 3 Radar Graphic)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Concentric Radar Graphic with dashed path
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // Outer pulse circles
                            drawCircle(color = WarmSand.copy(alpha = 0.08f), radius = size.minDimension * 0.48f)
                            drawCircle(color = WarmSand.copy(alpha = 0.16f), radius = size.minDimension * 0.36f)
                            drawCircle(color = WarmSand.copy(alpha = 0.28f), radius = size.minDimension * 0.24f)

                            // Dashed trail path
                            val trail = Path().apply {
                                moveTo(center.x - 20f, size.height * 0.88f)
                                quadraticTo(
                                    center.x - 30f, center.y + 20f,
                                    center.x, center.y
                                )
                            }
                            drawPath(
                                path = trail,
                                color = Terracotta,
                                style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                            )
                        }

                        // Center focal circle icon
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = CircleShape,
                            color = WarmSand
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.GridView, contentDescription = null, tint = Color(0xFF16130E), modifier = Modifier.size(26.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Big Metric (Reference Screen 3: "230 m ahead")
                    Text(
                        text = "${project.keyframes.size} Keyframes",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary
                    )
                    Text(
                        text = "Sampled across ${project.durationMs / 1000}s recording • Ready for Claude AI",
                        fontSize = 12.sp,
                        color = StudioTextSecondary
                    )

                    Spacer(Modifier.height(16.dp))

                    // Dual Download Pill Buttons: PDF & ZIP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable(onClick = onDownloadPdf),
                            shape = RoundedCornerShape(999.dp),
                            color = WarmSand
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color(0xFF16130E), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16130E))
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable(onClick = onDownloadZip),
                            shape = RoundedCornerShape(999.dp),
                            color = ForestSage
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download ZIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 2. Primary Action Pill: "Copy Prompt for Claude" (Reference Screen 3 bottom action)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onCopyPrompt),
                shape = RoundedCornerShape(999.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = Terracotta
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    Text(
                        text = "Copy AI Prompt for Claude",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary
                    )

                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = StudioTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 3. "Paste Claude Response" Pill
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onPasteResponseClick),
                shape = RoundedCornerShape(999.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, WarmSand.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = WarmSand, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Paste Claude AI Response to Apply Cuts & Zooms",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmSand
                    )
                }
            }
        }

        // 4. Thumbnails Grid Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storyboard Thumbnails (${project.keyframes.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioTextPrimary
                )
                if (isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = WarmSand)
                        Spacer(Modifier.width(6.dp))
                        Text("Sampling…", fontSize = 11.sp, color = WarmSand)
                    }
                } else {
                    Text(
                        text = "Refresh",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmSand,
                        modifier = Modifier.clickable(onClick = onRegenerate)
                    )
                }
            }
        }

        // 5. Thumbnails List / Horizontal Scroller
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (frame in project.keyframes) {
                    ContactSheetFrameThumbnailCard(frame = frame)
                }
            }
        }
    }
}

/**
 * Thumbnail card showing timestamp badge on bottom right.
 */
@Composable
private fun ContactSheetFrameThumbnailCard(frame: ContactSheetFrame) {
    val bmp = remember(frame.imagePath) {
        runCatching { BitmapFactory.decodeFile(frame.imagePath) }.getOrNull()
    }

    Surface(
        modifier = Modifier
            .width(140.dp)
            .height(220.dp),
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(StudioSurfaceElevated), contentAlignment = Alignment.Center) {
                    Text("#${frame.index}", color = StudioTextMuted)
                }
            }

            // Timestamp Badge overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = StudioBg.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, WarmSand.copy(alpha = 0.3f))
            ) {
                Text(
                    text = frame.formattedTime,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarmSand
                )
            }
        }
    }
}

/**
 * View 2: Video Player & Timeline with Media3 TextureView and Zoom Centering.
 */
@Composable
private fun PlayerAndTimelineView(
    exoPlayer: ExoPlayer,
    currentProject: RecordingProject,
    currentPositionMs: Long,
    isPlaying: Boolean,
    camState: CameraState,
    exportState: ExportUiState,
    onExportClick: () -> Unit,
    onPasteResponseClick: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val aspect = (currentProject.width.toFloat() / currentProject.height.coerceAtLeast(1).toFloat())
        .coerceIn(0.45f, 1.85f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Video Preview Container
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(StudioSurface)
                    .border(1.dp, StudioBorder, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Video Surface with Zoom Centering via CameraMath
                Box(
                    modifier = Modifier
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
                    }
                }

                // Top zoom indicator pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = StudioBg.copy(alpha = 0.75f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = null, tint = WarmSand, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${"%.1f".format(camState.scale)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarmSand)
                    }
                }
            }
        }

        // Play / Scrubbing Controls
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTimeSec(currentPositionMs)} / ${formatTimeSec(currentProject.durationMs)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )

                        IconButton(
                            onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = WarmSand,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    val dur = currentProject.durationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = currentPositionMs.toFloat().coerceIn(0f, dur),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..dur,
                        colors = SliderDefaults.colors(
                            thumbColor = WarmSand,
                            activeTrackColor = WarmSand,
                            inactiveTrackColor = StudioSurfaceElevated
                        )
                    )
                }
            }
        }

        // Active Segments & Keyframes Summary
        item {
            val plan = currentProject.editPlan
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "AI Timeline EDL",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Text(
                            "Paste Response",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmSand,
                            modifier = Modifier.clickable(onClick = onPasteResponseClick)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (plan == null || (plan.segments.isEmpty() && plan.zoomKeyframes.isEmpty())) {
                        Text(
                            "No AI plan applied yet. Go to the Contact Sheet tab, copy the prompt for Claude AI, and paste Claude's response here.",
                            fontSize = 12.sp,
                            color = StudioTextSecondary,
                            lineHeight = 17.sp
                        )
                    } else {
                        Text(
                            plan.summary.ifBlank { "Timeline synchronized according to AI response." },
                            fontSize = 12.sp,
                            color = StudioTextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(6.dp), color = ForestSage.copy(alpha = 0.2f)) {
                                Text("${plan.segments.size} segments", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = ForestSage)
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = WarmSand.copy(alpha = 0.2f)) {
                                Text("${plan.zoomKeyframes.size} zoom keyframes", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = WarmSand)
                            }
                        }
                    }
                }
            }
        }

        // Export Final Video Button
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable(enabled = !exportState.exporting, onClick = onExportClick),
                shape = RoundedCornerShape(999.dp),
                color = WarmSand
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (exportState.exporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = Color(0xFF16130E))
                        Spacer(Modifier.width(10.dp))
                        Text("Exporting ${(exportState.progress * 100).toInt()}%…", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16130E))
                    } else {
                        Icon(Icons.Rounded.Upload, contentDescription = null, tint = Color(0xFF16130E), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export Final MP4 Video", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16130E))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPillButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) WarmSand else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF16130E) else StudioTextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color(0xFF16130E) else StudioTextSecondary
            )
        }
    }
}

private fun formatTimeSec(ms: Long): String {
    val s = (ms / 1000L).toInt()
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}
