package com.external.uxdemo.soldatServiceConnection

import android.util.Log

class SoldatManager(private val viewModel: SoldatServiceViewModel) {

    // Текущие настройки (обновляются из Activity)
    var protocolType = 1
    var r181Address = 111111
    var ipAddress = 0

    /**
     * Ставит маркер цели в сервисе
     */
    fun sendMarker(lat: Double, lon: Double) {
        viewModel.invokeServiceMethod { service ->
            try {
                service.setMapClickPosWGS84(lat, lon, true)
            } catch (e: Exception) {
                Log.e("SoldatManager", "Marker error: ${e.message}")
            }
        }
    }

    /**
     * Отправляет любой объект (Танк, Разрыв и т.д.)
     */
    fun sendObject(lat: Double, lon: Double, typeId: Int, name: String) {
        val activeAddress = if (protocolType == 1) r181Address else ipAddress

        Log.e("SoldatManager", "TX: $name (ID: $typeId) -> $lat, $lon | Addr: $activeAddress")

        viewModel.invokeServiceMethod { service ->
            try {
                service.createObjectFromDrone(lat, lon, typeId, activeAddress, protocolType)
                Log.e("SoldatManager", "Success: $name sent")
            } catch (e: Exception) {
                Log.e("SoldatManager", "Error sending $name: ${e.message}")
            }
        }
    }
}