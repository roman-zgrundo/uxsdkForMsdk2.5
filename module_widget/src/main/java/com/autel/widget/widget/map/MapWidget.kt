package com.autel.widget.widget.map

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import com.autel.common.base.widget.ConstraintLayoutWidget
import com.autel.drone.sdk.log.SDKLog
import com.autel.map.MapManager
import com.autel.map.annotation.AutelPointAnnotation
import com.autel.map.annotation.AutelPolyLineAnnotation
import com.autel.map.bean.AutelLatLng
import com.autel.map.bean.AutelMapStyle
import com.autel.map.bean.LayerPriority
import com.autel.map.options.AutelPointAnnotationOptions
import com.autel.map.options.AutelPolylineAnnotationOptions
import com.autel.map.util.MapBoxUtils
import com.autel.widget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapWidget @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayoutWidget(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "MapWidget"
        private const val MAP_ICON_DRONE = "map_icon_drone"
        private const val MAP_ICON_HOME = "map_icon_home"
        private const val MAP_ICON_RC = "map_icon_rc"
        private val DEFAULT_MAP_ZOOM = 16.0
    }

    enum class TrackingTarget {
        DRONE, RC, HOME
    }

    var isAutoFollowEnabled = true
        private set

    var isNorthUpMode = true
        private set

    fun setNorthUpMode(enabled: Boolean) {
        this.isNorthUpMode = enabled

        if (isAutoFollowEnabled) {
            // Если мы в режиме слежения, стандартно обновляем и позицию, и поворот
            updateCameraPosition()
        } else {
            // Багфикс: Если карта сдвинута, мы НЕ возвращаем фокус на дрон,
            // но саму карту на текущем месте ОБЯЗАНЫ повернуть!
            val heading = if (isNorthUpMode) 0.0 else getTargetHeading()
            mapManager.rotate(heading, 0L)

            // И сразу же пересчитываем углы иконок под этот новый поворот карты
            activeMarkers.values.forEach { marker ->
                marker.droneAnnotation?.let { anno ->
                    anno.options.withIconRotate(getRelativeRotation(marker.absoluteHeading, heading))
                    mapManager.updatePoint(anno)
                }
            }
        }
    }

    var currentTrackingTarget: TrackingTarget = TrackingTarget.DRONE
        private set

    var onManualPan: (() -> Unit)? = null

    lateinit var mapManager: MapManager
    private val mapVM: MapWidgetVM by lazy { MapWidgetVM() }
    private val activeMarkers = mutableMapOf<Int, DroneMarker>()

    private var liveDroneLatLng: AutelLatLng? = null
    private var liveRcLatLng: AutelLatLng? = null
    private var liveHomeLatLng: AutelLatLng? = null
    private var liveDroneHeading: Double = 0.0
    private var currentZoom: Double = DEFAULT_MAP_ZOOM

    init { initMap() }

    private fun updateOrAddMarker(info: DroneInfoModel, iconImage: String, isDrone: Boolean = false) {
        if (isDrone) {
            liveDroneLatLng = AutelLatLng(info.latitude, info.longitude)
            liveDroneHeading = info.heading.toDouble()
            if (info.homeLatitude != 0.0 && info.homeLongitude != 0.0) {
                liveHomeLatLng = AutelLatLng(info.homeLatitude, info.homeLongitude)
            }
        } else if (info.id == -1) {
            liveRcLatLng = AutelLatLng(info.latitude, info.longitude)
        }

        var marker = activeMarkers[info.id]

        if (marker == null) {
            val options = AutelPointAnnotationOptions().apply {
                withLatLng(info.longitude, info.latitude)
                withIconImage(iconImage)
                // ИЗМЕНЕНО: Считаем относительный поворот для новой иконки
                withIconRotate(getRelativeRotation(info.heading.toDouble()))
                withLayerPriority(LayerPriority.HIGH)
                withSymbolSortKey(if (isDrone) 10.0 else 8.0)
            }
            val anno = mapManager.addPoint(options)

            marker = DroneMarker().apply {
                id = info.id
                droneAnnotation = anno
                absoluteHeading = info.heading.toDouble() // <-- ИЗМЕНЕНО: Запоминаем курс
                if (isDrone) points.add(AutelLatLng(info.latitude, info.longitude))
            }

            // Блок Home оставляем без изменений...
            if (isDrone && info.homeLatitude != 0.0 && info.homeLongitude != 0.0) {
                val homeOptions = AutelPointAnnotationOptions().apply {
                    withLatLng(info.homeLongitude, info.homeLatitude)
                    withIconImage(MAP_ICON_HOME)
                    withTouchable(false)
                    withLayerPriority(LayerPriority.HIGH)
                    withSymbolSortKey(5.0)
                }
                marker.homeAnnotation = mapManager.addPoint(homeOptions)
            }
            activeMarkers[info.id] = marker
        } else {
            // ИЗМЕНЕНО: Обновляем сохраненный курс у существующего маркера
            marker.absoluteHeading = info.heading.toDouble()

            marker.droneAnnotation?.let {
                it.options.withLatLng(info.longitude, info.latitude)
                // ИЗМЕНЕНО: Считаем относительный поворот при обновлении координат
                it.options.withIconRotate(getRelativeRotation(info.heading.toDouble()))
                mapManager.updatePoint(it)
            }

            if (isDrone && marker.homeAnnotation != null) {
                marker.homeAnnotation?.let { homeAnno ->
                    homeAnno.options.withLatLng(info.homeLongitude, info.homeLatitude)
                    mapManager.updatePoint(homeAnno)
                }
            }

            if (isDrone) {
                val newLatlng = AutelLatLng(info.latitude, info.longitude)
                if (marker.points.isNotEmpty() && MapBoxUtils.getDistance(marker.points.last(), newLatlng) > 1) {
                    marker.points.add(newLatlng)
                    updateDroneLine(marker)
                }
            }
        }

        val isTargetUpdating = when (currentTrackingTarget) {
            TrackingTarget.DRONE -> isDrone
            TrackingTarget.HOME -> isDrone
            TrackingTarget.RC -> !isDrone && info.id == -1
        }
        if (isTargetUpdating) {
            updateCameraPosition()
        }
    }

    private fun updateDroneLine(droneMarker: DroneMarker) {
        try {
            var line = droneMarker.lineAnnotation
            if (line == null) {
                val options = AutelPolylineAnnotationOptions().apply {
                    withPoints(droneMarker.points)
                    withLineColor("#EEEEEE")
                    withLineWidth(5.0)
                    withLayerPriority(LayerPriority.HIGH)
                }
                line = mapManager.addPolyline(options)
                droneMarker.lineAnnotation = line
            } else {
                line.options.withPoints(droneMarker.points)
                mapManager.updatePolyline(line)
            }
        } catch (e: Exception) {
            SDKLog.e(TAG, "Update line error: ${e.message}")
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (isAutoFollowEnabled) {
                isAutoFollowEnabled = false
                Log.d(TAG, "Map manually panned. AutoFollow disabled.")
                onManualPan?.invoke()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun setAutoFollowTarget(target: TrackingTarget) {
        this.isAutoFollowEnabled = true
        this.currentTrackingTarget = target
        this.currentZoom = DEFAULT_MAP_ZOOM
        updateCameraPosition()
    }

    fun resumeAutoFollow() {
        this.isAutoFollowEnabled = true
        this.currentZoom = mapManager.getZoom()
        updateCameraPosition()
    }

    fun initMap() {
        val MAPTILER_KEY = "rgEFGBMZ0ahwpglRNXRL"
        MapManager.setMapToken(MAPTILER_KEY)
        mapManager = MapManager(context)
        mapManager.setDefaultMapSettings()
        addMapView()

        mapManager.loadStyle(context as Activity, AutelMapStyle.MIX, resources.configuration.locale) { isSuc, msg ->
            CoroutineScope(Dispatchers.Main).launch {
                if (isSuc) {
                    loadResources()
                    addDroneObserver()
                }
            }
        }
    }

    private fun addMapView() {
        addView(mapManager.getMapView()!!, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun addDroneObserver() {
        mapVM.setup()
        CoroutineScope(Dispatchers.Main).launch {
            mapVM.droneInfoState.collect { list ->
                list.forEach { updateOrAddMarker(it, MAP_ICON_DRONE, isDrone = true) }
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            mapVM.rcLocationState.collect { rcInfo ->
                rcInfo?.let { updateOrAddMarker(it, MAP_ICON_RC, isDrone = false) }
            }
        }
    }

    private fun updateCameraPosition() {
        if (!isAutoFollowEnabled) return
        val targetLatLng = getTargetLatLng() ?: return

        val heading = if (isNorthUpMode) 0.0 else getTargetHeading()

        // Вращаем и двигаем карту одним атомарным вызовом
        mapManager.moveCameraTo(
            latLng = targetLatLng,
            zoom = currentZoom,
            duration = 0L,
            bearing = heading
        )

        activeMarkers.values.forEach { marker ->
            marker.droneAnnotation?.let { anno ->
                anno.options.withIconRotate(getRelativeRotation(marker.absoluteHeading, heading))
                mapManager.updatePoint(anno)
            }
        }
    }

    private fun getTargetLatLng(): AutelLatLng? {
        return when (currentTrackingTarget) {
            TrackingTarget.DRONE -> liveDroneLatLng
            TrackingTarget.RC -> liveRcLatLng
            TrackingTarget.HOME -> liveHomeLatLng
        }
    }

    fun getTargetHeading(): Double {
        return when (currentTrackingTarget) {
            TrackingTarget.DRONE -> liveDroneHeading
            TrackingTarget.RC -> mapVM.currentCompassHeading.toDouble()
            TrackingTarget.HOME -> 0.0
        }
    }

    private fun loadResources() {
        mapManager.addImageIconToStyle(MAP_ICON_DRONE, BitmapFactory.decodeResource(resources, R.drawable.common_ic_ball_drone))
        mapManager.addImageIconToStyle(MAP_ICON_HOME, BitmapFactory.decodeResource(resources, R.drawable.common_ic_ball_home))
        mapManager.addImageIconToStyle(MAP_ICON_RC, BitmapFactory.decodeResource(resources, R.drawable.common_ic_stance_remote))
    }

    fun switchMapStyle(style: AutelMapStyle) {
        mapManager.loadStyle(context as Activity, style, resources.configuration.locale) { isSuc, msg ->
            if (isSuc) loadResources()
        }
    }

    inner class DroneMarker {
        var id = 0
        var points = mutableListOf<AutelLatLng>()
        var droneAnnotation: AutelPointAnnotation? = null
        var homeAnnotation: AutelPointAnnotation? = null
        var lineAnnotation: AutelPolyLineAnnotation? = null
        var absoluteHeading: Double = 0.0
    }

    private fun getRelativeRotation(absoluteHeading: Double, forcedBearing: Double? = null): Double {
        // Если передали точный угол карты — берем его, иначе запрашиваем у менеджера
        val mapBearing = forcedBearing ?: mapManager.getBearing()

        var relative = absoluteHeading - mapBearing
        relative %= 360.0
        if (relative < 0) relative += 360.0
        return relative
    }
}