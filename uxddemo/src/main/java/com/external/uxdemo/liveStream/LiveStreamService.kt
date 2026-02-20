package com.external.uxdemo.liveStream

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
            .setSmallIcon(android.R.drawable.ic_media_play).build())
        return START_STICKY
    }

    fun startStream(url: String, bitrate: Int, port: Int) {
        rtmpManager.stopPublishStream()
        // Небольшая задержка внутри сервиса для инициализации конфига
        Handler(Looper.getMainLooper()).postDelayed({
            rtmpManager.initRtmpConfig(url, bitrate, port, false)
            rtmpManager.setRtmpPublishListener(createPublishListener(bitrate, port))
            rtmpManager.startPublishStream()
        }, 300)
    }

    private fun createPublishListener(bitrate: Int, port: Int) = object : IPublishListener {
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
        override fun onFpsStatistic(fps: Int, p1: String?) { serviceListener?.onStats(fps, bitrate) }
        // ... остальные методы пустые
        override fun onVideoBitrate(p0: Int, p1: String?) {}
        override fun onPublishSuccess() {}
        override fun onReconnect() {}
        override fun onAudioBitrate(p0: Int) {}
        override fun onConnectedFailed(p0: PublishErrorCode?) { onPublishFailed(p0 ?: PublishErrorCode.PUBLISH_TIMEOUT) }
        override fun onPublishFailed(p1: String?, p2: PublishErrorCode?) { onPublishFailed(p2 ?: PublishErrorCode.PUBLISH_TIMEOUT) }
    }

    fun stopForegroundService() {
        isCurrentlyPublishing = false
        rtmpManager.stopPublishStream()
        serviceListener?.onStateChanged(false)
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