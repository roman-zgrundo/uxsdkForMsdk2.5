package com.external.uxdemo.liveStream

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LiveStreamViewModel : ViewModel(), LiveStreamListener {

    val isStreaming = MutableLiveData(false)
    val statsText = MutableLiveData("Статистика: -")
    val streamStatusText = MutableLiveData("Готов")

    private var currentPort = 16010
    private var boundService: LiveStreamService? = null

    // Храним последние параметры здесь, чтобы переключать камеру без участия UI
    private var lastUrl: String = ""
    private var lastBitrate: Int = 3000

    fun onServiceConnected(service: LiveStreamService) {
        boundService = service
        service.serviceListener = this
        isStreaming.postValue(service.isPublishingNow())
    }

    // Главная функция запуска/остановки
    fun toggleStream(url: String, bitrate: Int) {
        lastUrl = url
        lastBitrate = bitrate

        if (isStreaming.value == true) {
            stopStream()
        } else {
            startStream()
        }
    }

    private fun startStream() {
        if (lastUrl.isEmpty()) return
        boundService?.startStream(lastUrl, lastBitrate, currentPort)
    }

    fun stopStream() {
        boundService?.stopForegroundService()
        isStreaming.postValue(false)
    }

    // Вызывается из UxsdkDemoActivity при клике на табы камер
    fun updateCameraSource(isThermal: Boolean) {
        val newPort = if (isThermal) 16015 else 16010
        if (newPort == currentPort) return // Ничего не меняем, если порт тот же

        currentPort = newPort
        Log.d("StreamDebug", "Смена порта на: $currentPort")

        // Если сейчас идет эфир — перезапускаем автоматически
        if (isStreaming.value == true) {
            stopStream()
            // Пауза 500мс дает SDK Autel время закрыть старую сессию
            Handler(Looper.getMainLooper()).postDelayed({
                startStream()
            }, 500)
        }
    }

    // Реализация интерфейса LiveStreamListener
    override fun onStateChanged(streaming: Boolean) { isStreaming.postValue(streaming) }
    override fun onMessage(msg: String) { streamStatusText.postValue(msg) }
    override fun onError(error: String) { streamStatusText.postValue("Ошибка: $error") }
    override fun onStats(fps: Int, bps: Int) {
        val name = if (currentPort == 16015) "ИК" else "Основная"
        statsText.postValue("Камера: $name | FPS: $fps")
    }
}