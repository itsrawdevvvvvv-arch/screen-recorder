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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordly.mobile.model.KeyboardTrimMode
import com.recordly.mobile.ui.theme.StudioAccent
import com.recordly.mobile.ui.theme.StudioBg
import com.recordly.mobile.ui.theme.StudioBorder
import com.recordly.mobile.ui.theme.StudioPrimary
import com.recordly.mobile.ui.theme.StudioPrimaryBright
import com.recordly.mobile.ui.theme.StudioSurface
import com.recordly.mobile.ui.theme.StudioSurfaceElevated
import com.recordly.mobile.ui.theme.StudioTextPrimary
import com.recordly.mobile.ui.theme.StudioTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RecordlyViewModel,
    onBack: () -> Unit
) {
    val saved by viewModel.groqSettings.collectAsStateWithLifecycle()
    val testResult by viewModel.groqTestResult.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf(saved) }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(saved) { draft = saved }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = StudioPrimary,
        unfocusedBorderColor = StudioTextSecondary.copy(alpha = 0.3f),
        focusedLabelColor = Color(0xFFB8B0FF),
        unfocusedLabelColor = StudioTextSecondary
    )

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
                title = {}
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, StudioBg.copy(alpha = 0.95f))))
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            viewModel.saveGroqSettings(draft)
                            onBack()
                        },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(999.dp),
                    shadowElevation = 12.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(StudioPrimary, StudioPrimaryBright)))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, tint = StudioPrimary, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            "Save Settings",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                brush = Brush.horizontalGradient(listOf(StudioPrimaryBright, StudioAccent)),
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) { append("Groq AI") }
                        withStyle(SpanStyle(color = StudioTextPrimary, fontWeight = FontWeight.ExtraBold)) {
                            append(" & Capture Settings")
                        }
                    },
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            // 1. Groq API Key Card
            item {
                SettingsCard(icon = Icons.Rounded.Key, title = "GROQ API CONFIGURATION") {
                    OutlinedTextField(
                        value = draft.apiKey,
                        onValueChange = { draft = draft.copy(apiKey = it) },
                        label = { Text("Groq API Key (gsk_...)") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle key visibility",
                                    tint = StudioTextSecondary
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { viewModel.testGroqConnection(draft.apiKey) },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.5.dp, StudioPrimary)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Test Key", color = Color(0xFFC4BEFF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        testResult?.let { msg ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(msg, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StudioAccent)
                            }
                        }
                    }
                }
            }

            // 2. AI Models Card
            item {
                SettingsCard(icon = Icons.Rounded.Memory, title = "AI MODELS") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = draft.whisperModel,
                            onValueChange = { draft = draft.copy(whisperModel = it) },
                            label = { Text("Whisper Model (.srt & word timestamps)") },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.chatModel,
                            onValueChange = { draft = draft.copy(chatModel = it) },
                            label = { Text("AI Director Chat Model") },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.visionModel,
                            onValueChange = { draft = draft.copy(visionModel = it) },
                            label = { Text("Vision Keyframe Model") },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Phone Keyboard Auto-Trim Mode Card
            item {
                SettingsCard(icon = Icons.Rounded.Keyboard, title = "PHONE KEYBOARD AUTO-TRIM MODE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KeyboardTrimMode.entries.forEach { mode ->
                            val selected = draft.keyboardTrimMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { draft = draft.copy(keyboardTrimMode = mode) },
                                color = if (selected) StudioPrimary.copy(alpha = 0.24f) else StudioSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (selected) StudioPrimary else StudioTextSecondary.copy(alpha = 0.28f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        when (mode) {
                                            KeyboardTrimMode.REMOVE -> "Remove"
                                            KeyboardTrimMode.SPEED_UP_5X -> "Speed 5x"
                                            KeyboardTrimMode.KEEP -> "Keep"
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else StudioTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (draft.keyboardTrimMode) {
                            KeyboardTrimMode.REMOVE -> "Detected keyboard frames are cut from the final video."
                            KeyboardTrimMode.SPEED_UP_5X -> "Keyboard frames are kept but played back at 5x speed."
                            KeyboardTrimMode.KEEP -> "Keyboard frames are left untouched in the timeline."
                        },
                        fontSize = 11.5.sp,
                        color = StudioTextSecondary
                    )
                }
            }

            // 4. Mouse Tracking Sensitivity Card
            item {
                SettingsCard(icon = Icons.Rounded.Mouse, title = "MOUSE TRACKING") {
                    Text(
                        "Mouse Tracking Sensitivity (${"%.1f".format(draft.mouseSensitivity)}x)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Slider(
                        value = draft.mouseSensitivity,
                        onValueChange = { draft = draft.copy(mouseSensitivity = it) },
                        valueRange = 0.4f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = StudioPrimary,
                            inactiveTrackColor = Color(0xFF2A3144)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.4x", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = StudioTextSecondary)
                        Text("1.0x", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = StudioTextSecondary)
                        Text("2.5x", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = StudioTextSecondary)
                    }
                }
            }

            // 5. Switches Card
            item {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Auto-Zoom on Mouse Clicks", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StudioTextPrimary)
                                Text("Generates smooth zoom keyframes automatically", fontSize = 11.5.sp, color = StudioTextSecondary)
                            }
                            Switch(
                                checked = draft.autoZoomEnabled,
                                onCheckedChange = { draft = draft.copy(autoZoomEnabled = it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StudioPrimary)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(StudioBorder))
                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Record Microphone Audio", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StudioTextPrimary)
                                Text("Used by Groq Whisper to generate .srt & match speed", fontSize = 11.5.sp, color = StudioTextSecondary)
                            }
                            Switch(
                                checked = draft.recordMicrophone,
                                onCheckedChange = { draft = draft.copy(recordMicrophone = it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = StudioPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = StudioPrimaryBright, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StudioTextSecondary
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
