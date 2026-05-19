package com.autel.widget.widget.map

import androidx.lifecycle.Observer
import com.autel.common.base.BaseApp
import com.autel.common.base.widget.BaseWidgetModel
import com.autel.common.feature.phone.AutelPhoneLocationManager
import com.autel.common.utils.DeviceUtils
import com.autel.map.bean.AutelLatLng
import com.autel.map.bean.AutelLatLng.Companion.isValid
import com.autel.map.util.CompassManager
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 *@Author autel
 *@Date 2025/5/30
 *
 */
class MapWidgetVM : BaseWidgetModel() {
    // Автоматически эмулируем пульт, если включен дебаг
    private val IS_DEBUG = false
    private var droneOffsetLat = 0.0
    private var droneOffsetLng = 0.0
    var droneInfoChanged: MutableSharedFlow<List<DroneInfoModel>> = MutableSharedFlow(replay = 1, extraBufferCapacity = 1)
    var rcLocationChanged: MutableSharedFlow<DroneInfoModel> = MutableSharedFlow(replay = 1, extraBufferCapacity = 1)

    private var compassDegree: Float = 0f

    private val rcObserver: Observer<AutelLatLng> = object : Observer<AutelLatLng> {
        override fun onChanged(value: AutelLatLng) {
            if (!value.isValid()) return
            val rcInfo = DroneInfoModel(
                id = -1, // RC ID
                latitude = value.latitude,
                longitude = value.longitude,
                height = value.altitude.toFloat(),
                heading = compassDegree,
                homeLatitude = 0.0, // Not applicable for RC
                homeLongitude = 0.0 // Not applicable for RC
            )
            rcLocationChanged.tryEmit(rcInfo)
        }
    }
    override fun fixedFrequencyRefresh() {
        updateDroneInfo()
    }

    fun updateDroneInfo() {
        val list = mutableListOf<DroneInfoModel>()

        if (IS_DEBUG) {
            // Эмуляция дрона
            list.add(getFakeDrone())
        } else {
            // Работа с реальным железом
            val devices = DeviceUtils.allDrones()
            devices.forEach {
                it.getDeviceStateData().flightControlData.let { data ->
                    if (AutelLatLng(data.droneLatitude, data.droneLongitude).isValid()) {
                        list.add(DroneInfoModel(
                            id = it.deviceNumber(),
                            latitude = data.droneLatitude,
                            longitude = data.droneLongitude,
                            height = data.altitude,
                            heading = data.droneAttitudeYaw,
                            homeLatitude = data.homeLatitude,
                            homeLongitude = data.homeLongitude
                        ))
                    }
                }
            }
        }
        droneInfoChanged.tryEmit(list)

        // Автоматически эмулируем пульт, если включен дебаг
        if (IS_DEBUG) testRc()
    }

    private fun addRCObserver() {
        AutelPhoneLocationManager.locationLiveData.observeForever(rcObserver)
    }

    override fun setup() {
        super.setup()
        if (AutelPhoneLocationManager.hasLocationPermission()) {
            AutelPhoneLocationManager.initRequest()
        }
        addRCObserver()
        CompassManager.getInstance(BaseApp.getContext()).startCompass()
        CompassManager.getInstance(BaseApp.getContext()).setCompassListener { degree ->
            compassDegree = degree - 90
        }
    }

    private fun getFakeDrone(): DroneInfoModel {
        // Увеличиваем смещение при каждом вызове
        droneOffsetLat += 0.0001
        droneOffsetLng += 0.0001

        return DroneInfoModel(
            id = 555,
            latitude = 22.57672069857987 + 0.002 + droneOffsetLat,
            longitude = 114.03794480921324 + 0.002 + droneOffsetLng,
            height = 20f,
            heading = 45f, // Можно менять, чтобы он поворачивался
            homeLatitude = 22.57672069857987,
            homeLongitude = 114.03794480921324
        )
    }
    private fun testRc() {
        val rcInfo = DroneInfoModel(
            id = -1, // RC ID
            latitude = 22.57672069857987,
            longitude = 114.03794480921324,
            height = 0f,
            heading = compassDegree,
            homeLatitude = 0.0, // Not applicable for RC
            homeLongitude = 0.0 // Not applicable for RC
        )
        rcLocationChanged.tryEmit(rcInfo)
    }

    override fun cleanup() {
        super.cleanup()
        CompassManager.getInstance(BaseApp.getContext()).stopCompass()
        AutelPhoneLocationManager.locationLiveData.removeObserver(rcObserver)
    }

}