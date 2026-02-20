package com.external.uxdemo.locationTracker

import android.util.Log
import com.autel.common.utils.DeviceUtils
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.GimbalKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateHFNtfyBean
import com.autel.log.AutelLog
import kotlin.math.*

class LocationTrackerManager {
    private val TAG = "LocationTracker"
    private val EARTH_RADIUS = 6378137.0

    var droneLat: Double = 0.0
    var droneLon: Double = 0.0
    var droneAlt: Double = 0.0
    var droneAbsAlt: Double = 0.0
    var finalPitch: Double = 0.0
    var finalBearing: Double = 0.0
    var laserDistance: Double? = null
    var isLaserValid: Boolean = false
    var debugStatus: String = "Initializing..."
    var rawLaserData: String = "Valid: false, Dist: 0"

    fun setupSubscriptions() {
        val device = DeviceUtils.singleControlDrone()
        val keyManager = device?.getKeyManager() as? IKeyManager ?: return

        // 1. Пытаемся включить Дальномер (Ranging)
        val laserRangingKey = AutelKey.create(GimbalKey.KeyLaserRangingSwitch)
        keyManager.setValue(laserRangingKey, true, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                debugStatus = "LRF Switch: ON"
                AutelLog.i("LocationTracker", "Laser Ranging Switch -> ON")
            }
            override fun onFailure(code: IAutelCode, msg: String?) {
                debugStatus = "LRF Err: $msg"
            }
        })

        // 2. Пытаемся включить основной Модуль лазера (Switch)
        val laserSwitchKey = AutelKey.create(GimbalKey.KeyLaserSwitch)
        keyManager.setValue(laserSwitchKey, true, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                debugStatus += " | Module: ON"
            }
            override fun onFailure(code: IAutelCode, msg: String?) {
                debugStatus += " | ModErr: $code"
            }
        })

        // 3. Подписка на данные
        val gpsKey = AutelKey.create(CommonKey.KeyDroneSystemStatusHFNtfy)
        keyManager.listen(gpsKey, object : CommonCallbacks.KeyListener<DroneSystemStateHFNtfyBean> {
            override fun onValueChange(oldValue: DroneSystemStateHFNtfyBean?, newValue: DroneSystemStateHFNtfyBean) {
                // ПРИМЕНЯЕМ ДЕЛИТЕЛЬ 100 (см -> м)
                val correctedDist = newValue.laserDistance.toDouble() / 100.0

                // В дебаг выводим уже метры для наглядности
                rawLaserData = "Valid: ${newValue.laserDistanceIsValid}, Dist: ${"%.1f".format(correctedDist)}m"

                droneLat = newValue.droneLatitude
                droneLon = newValue.droneLongitude
                droneAlt = newValue.altitude.toDouble()

                droneAbsAlt = newValue.altitudeMSL.toDouble()

                newValue.gimbalAttitude?.let {
                    finalPitch = it.getPitchDegree().toDouble()
                    finalBearing = it.getYawDegree().toDouble()
                }

                isLaserValid = newValue.laserDistanceIsValid
                // Присваиваем скорректированную дистанцию
                laserDistance = if (isLaserValid) correctedDist else null

                if (!isLaserValid && correctedDist == 0.0) {
//                    Log.d("LRF_TEST", "Valid: false, Dist: 0")
                }
            }
        })
    }

    fun getTargetReport(): String {
        if (droneLat == 0.0 || droneLon == 0.0) return "TARGET: WAITING FOR GPS\n"

        val pitchInRad = Math.toRadians(abs(finalPitch))

        // 1. Считаем по лазеру (если он валиден)
        var laserReport = "TGT(L): NO DATA"
        if (isLaserValid && (laserDistance ?: 0.0) > 0.5) {
            val hDistL = laserDistance!! * cos(pitchInRad)
            val targetL = computeOffset(droneLat, droneLon, hDistL, finalBearing)
            laserReport = "TGT(L): ${"%.6f".format(targetL.first)}, ${"%.6f".format(targetL.second)} (${"%.1f".format(hDistL)}m)"
        }

        // 2. Считаем по высоте (всегда, если Pitch > 3°)
        var heightReport = "TGT(H): LOW PITCH"
        if (droneAlt > 0.8 && abs(finalPitch) > 3.0) {
            val hDistH = droneAlt / tan(pitchInRad)
            val targetH = computeOffset(droneLat, droneLon, hDistH, finalBearing)
            heightReport = "TGT(H): ${"%.6f".format(targetH.first)}, ${"%.6f".format(targetH.second)} (${"%.1f".format(hDistH)}m)"
        }

        return "$laserReport\n$heightReport"
    }

    private fun computeOffset(lat: Double, lon: Double, dist: Double, brng: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val bRad = Math.toRadians(brng)

        val tLatRad = asin(sin(latRad) * cos(dist / EARTH_RADIUS) + cos(latRad) * sin(dist / EARTH_RADIUS) * cos(bRad))
        val tLonRad = lonRad + atan2(sin(bRad) * sin(dist / EARTH_RADIUS) * cos(latRad), cos(dist / EARTH_RADIUS) - sin(latRad) * sin(tLatRad))

        return Math.toDegrees(tLatRad) to Math.toDegrees(tLonRad)
    }

    fun getFixData(): String {
        val pitchInRad = Math.toRadians(abs(finalPitch))
        val rng = laserDistance ?: (if(abs(finalPitch) > 3.0) droneAlt / sin(pitchInRad) else 0.0)

        // MSL цели = MSL дрона - вертикальный катет (RNG * sin(Pitch))
        val targetMSL = droneAbsAlt - (rng * sin(pitchInRad))

        // Считаем координаты (берем приоритет лазера, если нет - высоту)
        val hDist = if (isLaserValid) rng * cos(pitchInRad) else (droneAlt / tan(pitchInRad))
        val target = computeOffset(droneLat, droneLon, hDist, finalBearing)

        return "RNG: ${"%.1f".format(rng)}m\n" +
                "MSL: ${"%.1f".format(targetMSL)}m\n" +
                "${"%.6f".format(target.first)}, ${"%.6f".format(target.second)}"
    }
}