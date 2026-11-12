package com.recordly.mobile.capture

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.recordly.mobile.BuildConfig
import com.recordly.mobile.shizuku.IMouseEventCallback
import com.recordly.mobile.shizuku.IMouseEventService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    READY,
    TRACKING
}

object ShizukuMouseBridge {

    private const val REQ_CODE_SHIZUKU = 4091

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val _statusDetail = MutableStateFlow("Shizuku not detected")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private var userService: IMouseEventService? = null
    private var bound = false
    private var permissionListenerRegistered = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQ_CODE_SHIZUKU) {
            refreshStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _statusDetail.value = "Shizuku permission granted"
            }
        }
    }

    private val callback = object : IMouseEventCallback.Stub() {
        override fun onMouseSample(
            elapsedMs: Long,
            xNorm: Float,
            yNorm: Float,
            isDown: Boolean,
            isUp: Boolean,
            button: Int
        ) {
            ActiveSessionTracker.recordMouseSample(xNorm, yNorm, isDown, isUp, button)
        }

        override fun onStatus(message: String?) {
            if (!message.isNullOrBlank()) {
                _statusDetail.value = message
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            userService = IMouseEventService.Stub.asInterface(service)
            bound = true
            _status.value = ShizukuStatus.READY
            _statusDetail.value = "Shizuku mouse service connected"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            bound = false
            refreshStatus()
        }
    }

    fun refreshStatus() {
        val pingOk = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!pingOk) {
            _status.value = ShizukuStatus.NOT_RUNNING
            _statusDetail.value = "Shizuku not running (Visual Cursor Fallback active)"
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            _status.value = ShizukuStatus.PERMISSION_REQUIRED
            _statusDetail.value = "Tap to grant Shizuku permission for 60fps mouse tracking"
            return
        }
        if (userService?.isTracking == true) {
            _status.value = ShizukuStatus.TRACKING
            _statusDetail.value = "60fps hardware mouse tracking active"
        } else {
            _status.value = ShizukuStatus.READY
            _statusDetail.value = "Shizuku ready for 60fps mouse tracking"
        }
    }

    fun requestPermission() {
        if (!permissionListenerRegistered) {
            runCatching {
                Shizuku.addRequestPermissionResultListener(permissionListener)
                permissionListenerRegistered = true
            }
        }
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            runCatching { Shizuku.requestPermission(REQ_CODE_SHIZUKU) }
        }
        refreshStatus()
    }

    fun bindIfReady(context: Context) {
        refreshStatus()
        if (_status.value != ShizukuStatus.READY || bound) return
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, MouseEventUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("mouse_tracker")
            .debuggable(BuildConfig.DEBUG)
            .version(1)

        runCatching {
            Shizuku.bindUserService(args, serviceConnection)
        }
    }

    fun startTracking(context: Context, screenWidth: Int, screenHeight: Int, sensitivity: Float) {
        bindIfReady(context)
        val svc = userService ?: return
        runCatching {
            svc.startTracking(screenWidth, screenHeight, sensitivity, callback)
            _status.value = ShizukuStatus.TRACKING
            _statusDetail.value = "60fps mouse tracking active"
        }
    }

    fun stopTracking() {
        runCatching { userService?.stopTracking() }
        refreshStatus()
    }
}
