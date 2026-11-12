package com.recordly.mobile.capture

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectedMouse(
    val id: Int,
    val name: String,
    val isExternal: Boolean
)

class MouseDeviceMonitor(context: Context) {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val _connectedMice = MutableStateFlow<List<ConnectedMouse>>(emptyList())
    val connectedMice: StateFlow<List<ConnectedMouse>> = _connectedMice.asStateFlow()

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
    }

    init {
        refresh()
        inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
    }

    fun refresh() {
        val mice = InputDevice.getDeviceIds().toList().mapNotNull { id ->
            val device = InputDevice.getDevice(id) ?: return@mapNotNull null
            val isMouse = (device.sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            if (isMouse && !device.isVirtual) {
                ConnectedMouse(
                    id = id,
                    name = device.name ?: "USB/Bluetooth Mouse",
                    isExternal = device.isExternal
                )
            } else null
        }
        _connectedMice.value = mice
    }
}
