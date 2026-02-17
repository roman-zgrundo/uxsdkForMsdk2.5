package com.external.uxdemo.liveStream

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LiveStreamViewModel : ViewModel(), LiveStreamListener {

    val isStreaming = MutableLiveData<Boolean>(false)
    val streamStatusText = MutableLiveData<String>("Готов")
    val statsText = MutableLiveData<String>("Статистика: -")

    private var currentPort = 16010
    @SuppressLint("StaticFieldLeak")
    private var boundService: LiveStreamService? = null

    fun onServiceConnected(service: LiveStreamService) {
        boundService = service
        service.serviceListener = this
        // При подключении сервиса обновляем статус из него
        isStreaming.postValue(service.isPublishingNow())
    }

    fun setCameraPort(isIr: Boolean) {
        val newPort = if (isIr) 16015 else 16010

        // Если порт изменился И мы уже стримим — тогда перезапускаем
        if (newPort != currentPort && isStreaming.value == true) {
            currentPort = newPort
            startStream(boundService?.currentUrl ?: "", boundService?.currentBitrate ?: 3000)
        } else {
            currentPort = newPort
        }
    }

    fun startStream(url: String, bitrate: Int) {
        boundService?.startStream(url, bitrate, currentPort)
    }

    fun stopStream() {
        boundService?.stopForegroundService()
        isStreaming.postValue(false)
    }

    override fun onStateChanged(streaming: Boolean) { isStreaming.postValue(streaming) }
    override fun onMessage(msg: String) { streamStatusText.postValue(msg) }
    override fun onError(error: String) { streamStatusText.postValue("Ошибка: $error") }
    override fun onStats(fps: Int, bps: Int) {
        val name = if (currentPort == 16015) "ИК" else "Основная"
        statsText.postValue("Камера: $name | FPS: $fps")
    }
}