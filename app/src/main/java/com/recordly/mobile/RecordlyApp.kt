package com.recordly.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.recordly.mobile.data.ProjectRepository
import com.recordly.mobile.data.SettingsRepository

class RecordlyApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var projectRepository: ProjectRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        projectRepository = ProjectRepository(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val recordChannel = NotificationChannel(
                CHANNEL_RECORDING,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active screen recording & floating studio controls"
            }
            val exportChannel = NotificationChannel(
                CHANNEL_EXPORT,
                "Video Export & Contact Sheet",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Contact sheet generation and MP4 export progress"
            }
            manager.createNotificationChannel(recordChannel)
            manager.createNotificationChannel(exportChannel)
        }
    }

    companion object {
        const val CHANNEL_RECORDING = "recordly_recording"
        const val CHANNEL_EXPORT = "recordly_export"

        lateinit var instance: RecordlyApp
            private set
    }
}
