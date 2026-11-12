package com.recordly.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recordly.mobile.capture.FloatingControlService
import com.recordly.mobile.capture.ScreenRecordService
import com.recordly.mobile.ui.HomeScreen
import com.recordly.mobile.ui.RecordlyViewModel
import com.recordly.mobile.ui.SettingsScreen
import com.recordly.mobile.ui.StudioEditorScreen
import com.recordly.mobile.ui.theme.RecordlyMobileTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RecordlyViewModel by viewModels()
    private var activeEditorProjectId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        launchScreenCapturePrompt()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val recordIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, recordIntent)

            if (Settings.canDrawOverlays(this)) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, FloatingControlService::class.java)
                )
            }
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private var navigateToEditorCallback: ((String) -> Unit)? = null

    private val importVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.importVideo(uri) { newId ->
            activeEditorProjectId = newId
            navigateToEditorCallback?.invoke(newId)
        }
    }

    private val importSrtLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val pid = activeEditorProjectId ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        viewModel.importSrt(pid, uri)
    }

    private val importAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val pid = activeEditorProjectId ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        viewModel.importExternalAudio(pid, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RecordlyMobileTheme {
                val navController = rememberNavController()
                navigateToEditorCallback = { pid ->
                    navController.navigate("editor/$pid")
                }
                val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
                val lastSavedProjectId by viewModel.lastSavedProjectId.collectAsStateWithLifecycle()

                LaunchedEffect(toastMessage) {
                    toastMessage?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearToast()
                    }
                }

                LaunchedEffect(lastSavedProjectId) {
                    lastSavedProjectId?.let { pid ->
                        activeEditorProjectId = pid
                        viewModel.consumeLastSavedProject()
                        navController.navigate("editor/$pid")
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onStartRecordingClick = { requestRecordPermissionsAndStart() },
                            onStopRecordingClick = {
                                startService(
                                    Intent(this@MainActivity, ScreenRecordService::class.java)
                                        .setAction(ScreenRecordService.ACTION_STOP)
                                )
                                stopService(Intent(this@MainActivity, FloatingControlService::class.java))
                            },
                            onImportVideoClick = {
                                importVideoLauncher.launch(arrayOf("video/*"))
                            },
                            onOpenProject = { id ->
                                activeEditorProjectId = id
                                navController.navigate("editor/$id")
                            },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }

                    composable(
                        route = "editor/{projectId}",
                        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pid = backStackEntry.arguments?.getString("projectId") ?: return@composable
                        activeEditorProjectId = pid
                        StudioEditorScreen(
                            projectId = pid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onImportSrt = { importSrtLauncher.launch(arrayOf("*/*")) },
                            onImportAudio = { importAudioLauncher.launch(arrayOf("audio/*")) }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHardwareAndPermissions()
    }

    private fun requestRecordPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Grant 'Display over other apps' for the floating studio bubble",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            launchScreenCapturePrompt()
        }
    }

    private fun launchScreenCapturePrompt() {
        val projMgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projMgr.createScreenCaptureIntent())
    }
}
