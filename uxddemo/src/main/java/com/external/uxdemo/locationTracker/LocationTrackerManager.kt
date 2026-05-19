package com.external.uxdemo.locationTracker

import com.autel.common.utils.DeviceUtils
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.CommonKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.GimbalKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.flight.bean.DroneSystemStateHFNtfyBean
import com.external.uxdemo.locationTracker.dto.TargetPoint
import com.external.uxdemo.locationTracker.dto.TargetResult
import java.util.Locale
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

    // --- Математические методы (Твои старые точные формулы) ---

    private fun calculateTargetMSL(aircraftMsl: Double, range: Double, pitchDeg: Double): Double {
        // MSL цели = MSL дрона - вертикальный катет (RNG * sin(Pitch))
        return aircraftMsl + (range * sin(Math.toRadians(pitchDeg)))
    }

    private fun calculateHorizontalDistance(range: Double, pitchDeg: Double): Double {
        return range * cos(Math.toRadians(abs(pitchDeg)))
    }

    /**
     * Сферическая тригонометрия (Great Circle) - Самый точный вариант
     */
    private fun computeOffset(lat: Double, lon: Double, dist: Double, brng: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val bRad = Math.toRadians(brng)

        val tLatRad = asin(sin(latRad) * cos(dist / EARTH_RADIUS) +
                cos(latRad) * sin(dist / EARTH_RADIUS) * cos(bRad))

        val tLonRad = lonRad + atan2(sin(bRad) * sin(dist / EARTH_RADIUS) * cos(latRad),
            cos(dist / EARTH_RADIUS) - sin(latRad) * sin(tLatRad))

        return Math.toDegrees(tLatRad) to Math.toDegrees(tLonRad)
    }

    // --- Основная логика получения данных ---

    fun setupSubscriptions() {
        val device = DeviceUtils.singleControlDrone()
        val keyManager = device?.getKeyManager() ?: return

        keyManager.setValue(AutelKey.create(GimbalKey.KeyLaserRangingSwitch), true, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() { debugStatus = "LRF Switch: ON" }
            override fun onFailure(code: IAutelCode, msg: String?) { debugStatus = "LRF Err: $msg" }
        })

        keyManager.setValue(AutelKey.create(GimbalKey.KeyLaserSwitch), true, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() { debugStatus += " | Module: ON" }
            override fun onFailure(code: IAutelCode, msg: String?) { debugStatus += " | ModErr: $code" }
        })

        keyManager.listen(AutelKey.create(CommonKey.KeyDroneSystemStatusHFNtfy), object : CommonCallbacks.KeyListener<DroneSystemStateHFNtfyBean> {
            override fun onValueChange(oldValue: DroneSystemStateHFNtfyBean?, newValue: DroneSystemStateHFNtfyBean) {
                val correctedDist = newValue.laserDistance.toDouble() / 100.0
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
                laserDistance = if (isLaserValid) correctedDist else null
            }
        })
    }

    // --- Методы вывода отчетов ---

    private fun resolveTargets(): TargetResult {
        val laser = if (isLaserValid && (laserDistance ?: 0.0) > 0.5) {
            calculatePoint(laserDistance!!)
        } else null

        val height = if (finalPitch < -2.0 && droneAlt > 0.8) {
            val rngH = abs(droneAlt / sin(Math.toRadians(finalPitch)))
            calculatePoint(rngH)
        } else null

        return TargetResult(laser, height)
    }

    fun getTargetReport(): String {
        if (droneLat == 0.0 || droneLon == 0.0) return "TARGET: WAITING FOR GPS\n"

        val targets = resolveTargets()

        val lRep = targets.laser?.let {
            "TGT(L): ${"%.6f".format(it.lat)}, ${"%.6f".format(it.lon)} (${"%.1f".format(it.hDist)}m)"
        } ?: "TGT(L): NO DATA"

        val hRep = targets.height?.let {
            "TGT(H): ${"%.6f".format(it.lat)}, ${"%.6f".format(it.lon)} (${"%.1f".format(it.hDist)}m)"
        } ?: "TGT(H): LOW PITCH"

        return "$lRep\n$hRep"
    }

    fun getFixData(): String {
        if (droneLat == 0.0) return "RNG: 0.0m\nMSL: 0.0m\n0.000000, 0.000000"

        // Просто берем "лучший" доступный вариант через resolveTargets
        return resolveTargets().best?.let {
            String.format(Locale.US, "RNG: %.1fm\nMSL: %.1fm\n%.6f, %.6f",
                it.rng, it.msl, it.lat, it.lon)
        } ?: "RNG: 0.0m\nMSL: 0.0m\n0.000000, 0.000000"
    }

    private fun calculatePoint(range: Double): TargetPoint {
        val targetMSL = calculateTargetMSL(droneAbsAlt, range, finalPitch)
        val hDist = calculateHorizontalDistance(range, finalPitch)
        val coords = computeOffset(droneLat, droneLon, hDist, finalBearing)

        return TargetPoint(coords.first, coords.second, targetMSL, hDist, range)
    }
}