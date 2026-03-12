package com.autel.setting.liveStream

interface LiveStreamListener {
    fun onStateChanged(isStreaming: Boolean)
    fun onMessage(msg: String)
    fun onError(error: String)
    fun onStats(fps: Int)
}