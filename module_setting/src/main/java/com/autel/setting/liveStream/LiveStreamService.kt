package com.autel.setting.liveStream

import android.R
import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autel.drone.sdk.vmodelx.manager.RtmpServiceManager
import com.autel.publisher.IPublishListener
import com.autel.publisher.PublishErrorCode
import com.autel.video.VideoSource

class LiveStreamService : Service() {
    private val rtmpManager = RtmpServiceManager.getInstance()
    private val binder = LocalBinder()
    var serviceListener: LiveStreamListener? = null
    private var isCurrentlyPublishing = false

    inner class LocalBinder : Binder() {
        fun getService(): LiveStreamService = this@LiveStreamService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun isPublishingNow(): Boolean = isCurrentlyPublishing

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, NotificationCompat.Builder(this, "STREAM_CHANNEL")
            .setContentTitle("Autel Live Streaming")
            .setSmallIcon(R.drawable.ic_media_play).build())
        return START_STICKY
    }

    fun startStream(url: String, port: Int) {
        // ВАЖНО: Принудительный сброс нативки перед любым новым действием
        try {
            rtmpManager.stopPublishStream()
            rtmpManager.releasePublishStream()
        } catch (e: Exception) { }

        val internalBitrate = 2500

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                rtmpManager.initRtmpConfig(url, internalBitrate, port, false)
                rtmpManager.setRtmpPublishListener(createPublishListener(port))
                rtmpManager.startPublishStream()
            } catch (e: Exception) {
                Log.e("StreamDebug", "Ошибка: ${e.message}")
            }
        }, 600) // Даем нативке время "отдуплиться"
    }

    fun stopOnlyStream() {
        isCurrentlyPublishing = false
        rtmpManager.stopPublishStream()
        serviceListener?.onStateChanged(false)
    }

    private fun createPublishListener(port: Int) = object : IPublishListener {
        override fun onConnecting() { serviceListener?.onMessage("Подключение...") }
        override fun onConnected() { serviceListener?.onMessage("Соединение") }
        override fun onStartPublish() {
            isCurrentlyPublishing = true
            serviceListener?.onStateChanged(true)
            VideoSource.RequestKeyFrame(port, 0L, 7)
        }
        override fun onPublishFailed(code: PublishErrorCode) {
            isCurrentlyPublishing = false
            serviceListener?.onStateChanged(false)
        }
        override fun onStopPublish() { isCurrentlyPublishing = false }
        override fun onFpsStatistic(fps: Int, p1: String?) {
            // Передаем 0 вместо битрейта в UI, так как он нам не интересен
            serviceListener?.onStats(fps)
        }
        // ... остальные методы пустые
        override fun onVideoBitrate(p0: Int, p1: String?) {}
        override fun onPublishSuccess() {}
        override fun onReconnect() {}
        override fun onAudioBitrate(p0: Int) {}
        override fun onConnectedFailed(p0: PublishErrorCode?) { onPublishFailed(p0 ?: PublishErrorCode.PUBLISH_TIMEOUT) }
        override fun onPublishFailed(p1: String?, p2: PublishErrorCode?) { onPublishFailed(p2 ?: PublishErrorCode.PUBLISH_TIMEOUT) }
    }

    fun stopForegroundService() {
        stopOnlyStream()
        stopForeground(true)
        stopSelf()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("STREAM_CHANNEL", "Live Stream", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}