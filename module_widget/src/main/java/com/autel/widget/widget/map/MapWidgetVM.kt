package com.autel.widget.widget.map

import androidx.lifecycle.Observer
import com.autel.common.base.BaseApp
import com.autel.common.base.widget.BaseWidgetModel
import com.autel.common.feature.phone.AutelPhoneLocationManager
import com.autel.common.utils.DeviceUtils
import com.autel.map.bean.AutelLatLng
import com.autel.map.bean.AutelLatLng.Companion.isValid
import com.autel.map.util.CompassManager
import kotlinx.coroutines.flow.MutableStateFlow

class MapWidgetVM : BaseWidgetModel() {
    private val IS_DEBUG = false

    val droneInfoState = MutableStateFlow<List<DroneInfoModel>>(emptyList())
    val rcLocationState = MutableStateFlow<DroneInfoModel?>(null)
    var currentCompassHeading: Float = 0f

    private var compassDegree: Float = 0f
    private var tick = 0.0

    private val rcObserver: Observer<AutelLatLng> = object : Observer<AutelLatLng> {
        override fun onChanged(value: AutelLatLng) {
            if (!value.isValid()) return
            val rcInfo = DroneInfoModel(
                id = -1,
                latitude = value.latitude,
                longitude = value.longitude,
                height = value.altitude.toFloat(),
                heading = currentCompassHeading,
                homeLatitude = 0.0,
                homeLongitude = 0.0
            )
            rcLocationState.value = rcInfo
        }
    }

    override fun fixedFrequencyRefresh() {
        updateDroneInfo()
    }

    fun updateDroneInfo() {
        val list = mutableListOf<DroneInfoModel>()

        if (IS_DEBUG) {
            list.add(getFakeDrone())
        } else {
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
        droneInfoState.value = list
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
            currentCompassHeading = degree - 90
        }
    }

    private fun getFakeDrone(): DroneInfoModel {
        tick += 0.1

        val baseLat = 53.928611
        val baseLng = 27.623633
        val speedX = 0.0001
        val amplitude = 0.001
        val frequency = 1.0

        val offsetLng = tick * speedX
        val offsetLat = Math.sin(tick * frequency) * amplitude

        val dx = speedX
        val dy = Math.cos(tick * frequency) * amplitude * frequency

        var heading = 90.0 - Math.toDegrees(Math.atan2(dy, dx))
        if (heading < 0) heading += 360.0

        return DroneInfoModel(
            id = 555,
            latitude = baseLat + offsetLat,
            longitude = baseLng + offsetLng,
            height = 20f + (Math.sin(tick) * 5).toFloat(),
            heading = heading.toFloat(),
            // Координаты Дома
            homeLatitude = 53.927611,
            homeLongitude = 27.622633
        )
    }

    private fun testRc() {
        val rcInfo = DroneInfoModel(
            id = -1,
            // ФИКС: Сдвинули пульт в сторону от Дома (было 22.57672)
            latitude = 53.926611,
            longitude = 27.621633,
            height = 0f,
            heading = currentCompassHeading,
            homeLatitude = 0.0,
            homeLongitude = 0.0
        )
        rcLocationState.value = rcInfo
    }

    override fun cleanup() {
        super.cleanup()
        CompassManager.getInstance(BaseApp.getContext()).stopCompass()
        AutelPhoneLocationManager.locationLiveData.removeObserver(rcObserver)
    }
}