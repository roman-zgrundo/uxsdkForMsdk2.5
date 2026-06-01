package com.autel.widget.widget.map

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import com.autel.common.base.widget.ConstraintLayoutWidget
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.lang.reflect.InvocationHandler
import kotlin.math.abs

class MapWidget @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayoutWidget(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "MapWidget"
        private const val MAP_ICON_DRONE = "map_icon_drone"
        private const val MAP_ICON_HOME = "map_icon_home"
        private const val MAP_ICON_RC = "map_icon_rc"

        // ==========================================================================================
        // НАСТРОЙКИ КОМФОРТА, ПЛАВНОСТИ И ЧУВСТВИТЕЛЬНОСТИ КАРТЫ
        // ==========================================================================================

        /**
         * Дефолтный зум карты при сбросе фокуса на цель или переключении режимов.
         */
        private val DEFAULT_MAP_ZOOM = 16.0

        /**
         * Максимальное количество точек в истории хвоста трека.
         * С нативным рендером через GeoJSON можно смело ставить от 2000 до 10000 точек без просадки FPS.
         */
        private const val MAX_LINE_POINTS = 10000

        /**
         * Порог сдвига камеры (в метрах).
         * Камера сделает микро-шаг за дроном, только если он улетел дальше этой дистанции от центра.
         * Меньше значение (напр. 1.0-2.0) = камера намертво привязана к центру, но может слегка зудеть.
         */
        private const val CAMERA_MOVE_THRESHOLD_METERS = 3.0

        /**
         * ЧУВСТВИТЕЛЬНОСТЬ ПОВОРОТА КАМЕРЫ (в градусах).
         * Камера довернет карту по курсу дрона, если угол изменился сильнее этого значения.
         * Было: 5.0 (камера ждала сильного разворота). Стало: 1.0 (камера реагирует мгновенно и чутко).
         * Можно опустить до 0.5 для максимальной плавности следования за курсом.
         */
        private const val CAMERA_ROTATE_THRESHOLD_DEGREES = 1.0

        /**
         * Ограничение частоты пересчета позиции камеры (в миллисекундах).
         * Задает интервал, не чаще которого вызывается moveCameraTo. 200ms означает обновление 5 раз в секунду.
         * Защищает карту от перегрузки нативными командами движения.
         */
        private const val CAMERA_UPDATE_THROTTLE_MS = 200L

        /**
         * Длительность анимации движения/поворота камеры (в миллисекундах).
         * Время, за которое нативный движок перетекает из старой позиции/угла в новую.
         * 150L-200L дает приятный сглаживающий эффект без эффекта "желейности".
         */
        private const val CAMERA_MOVE_DURATION_MS = 150L

        /**
         * Ограничение частоты обновления линии трека на карте (в миллисекундах).
         * Перерисовывает GeoJSON слой раз в Х мс. 1000L (1 секунда) — идеальный баланс:
         * линия визуально поспевает за дроном, но UI поток не напрягается сборкой строк.
         */
        private const val LINE_RENDER_THROTTLE_MS = 1000L

        /**
         * Минимальный шаг дрона (в метрах) для записи новой точки в массив трека.
         * Исключает ситуацию, когда стоящий на месте или висящий в сильный ветер дрон
         * превращает трек в жирную "кляксу" из сотен микро-точек в одном радиусе.
         */
        private const val TRACK_POINT_MIN_DISTANCE_METERS = 4.0

        /**
         * Интервал сбора данных из Kotlin Flow (телеметрия дрона и пульта) в миллисекундах.
         * 33L обеспечивает частоту отрисовки маркеров ~30 FPS (каждые 33 мс).
         * Дает максимальную плавность хода иконки по экрану.
         */
        private const val FLOW_SAMPLE_PERIOD_MS = 33L

        /**
         * Защитный фильтр "шума" гироскопа дрона (в градусах).
         * Если курс изменился меньше чем на этот порог, мы не обновляем маркер.
         * Защищает стрелку дрона от мелкого дрожания (компенсация погрешности датчиков).
         */
        private const val HEADING_CHANGE_THRESHOLD_DEGREES = 0.2

        /**
         * Защитный порог координат (в градусах, погрешность GPS).
         * Если изменения в координатах меньше этого значения, позиция считается неизменной.
         */
        private const val POSITION_CHANGE_THRESHOLD = 0.000001

        // ==========================================================================================
        // ID НАТИВНЫХ СЛОЕВ ДЛЯ КАРТЫ
        // ==========================================================================================
        private const val TRACK_SOURCE_ID = "drone_track_source"
        private const val TRACK_LAYER_ID = "drone_track_layer"
    }

    private var nativeMapLibreMap: MapLibreMap? = null
    private var lastRawDroneLat: Double = 0.0
    private var lastRawDroneLng: Double = 0.0

    private var viewJob = SupervisorJob()
    private var viewScope = CoroutineScope(Dispatchers.Main + viewJob)
    private var isStyleLoaded = false

    private var lastCameraUpdateTime = 0L
    private var lastCameraLatLng: AutelLatLng? = null
    private var lastCameraBearing: Double = -1.0

    enum class TrackingTarget { DRONE, RC, HOME }

    var isAutoFollowEnabled = true
        private set

    var isNorthUpMode = true
        private set

    fun setNorthUpMode(enabled: Boolean) {
        this.isNorthUpMode = enabled
        if (isAutoFollowEnabled) {
            updateCameraPosition()
        } else {
            val heading = if (isNorthUpMode) 0.0 else getTargetHeading()
            mapManager.rotate(heading, 0L)

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewJob.isCancelled) {
            viewJob = SupervisorJob()
            viewScope = CoroutineScope(Dispatchers.Main + viewJob)
        }
        if (isStyleLoaded) {
            startFlowCollection()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewJob.cancel()
    }

    fun releaseMapResources() {
        mapVM.cleanup()
        try {
            mapManager.onDestroy()
            mapManager.onMapDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying map manager: ${e.message}")
        }
    }

    private fun updateOrAddMarker(info: DroneInfoModel, iconImage: String, isDrone: Boolean = false) {
        if (isDrone) {
            val isSamePosition = abs(lastRawDroneLat - info.latitude) < POSITION_CHANGE_THRESHOLD &&
                    abs(lastRawDroneLng - info.longitude) < POSITION_CHANGE_THRESHOLD

            if (isSamePosition && abs(liveDroneHeading - info.heading) < HEADING_CHANGE_THRESHOLD_DEGREES) {
                return
            }

            lastRawDroneLat = info.latitude
            lastRawDroneLng = info.longitude
            liveDroneLatLng = AutelLatLng(info.latitude, info.longitude)
            liveDroneHeading = info.heading.toDouble()
            if (info.homeLatitude != 0.0 && info.homeLongitude != 0.0) {
                liveHomeLatLng = AutelLatLng(info.homeLatitude, info.homeLongitude)
            }
        } else if (info.id == -1) {
            liveRcLatLng = AutelLatLng(info.latitude, info.longitude)
        }

        var marker = activeMarkers[info.id]
        val isNewMarker = marker == null
        val currentTime = System.currentTimeMillis()

        if (isNewMarker) {
            Log.d(TAG, "New marker created: id=${info.id}, isDrone=$isDrone")

            val options = AutelPointAnnotationOptions().apply {
                withLatLng(info.longitude, info.latitude)
                withIconImage(iconImage)
                withIconRotate(getRelativeRotation(info.heading.toDouble()))
                withLayerPriority(LayerPriority.HIGH)
                withSymbolSortKey(if (isDrone) 10.0 else 8.0)
            }
            val anno = mapManager.addPoint(options)

            marker = DroneMarker().apply {
                id = info.id
                droneAnnotation = anno
                absoluteHeading = info.heading.toDouble()
                if (isDrone) points.add(AutelLatLng(info.latitude, info.longitude))
            }

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
            activeMarkers[info.id] = marker!!
        } else {
            marker!!.absoluteHeading = info.heading.toDouble()

            marker.droneAnnotation?.let {
                it.options.withLatLng(info.longitude, info.latitude)
                it.options.withIconRotate(getRelativeRotation(info.heading.toDouble()))
                mapManager.updatePoint(it)
            }

            if (isDrone) {
                if (marker.homeAnnotation != null) {
                    marker.homeAnnotation?.let { homeAnno ->
                        homeAnno.options.withLatLng(info.homeLongitude, info.homeLatitude)
                        mapManager.updatePoint(homeAnno)
                    }
                } else if (info.homeLatitude != 0.0 && info.homeLongitude != 0.0) {
                    val homeOptions = AutelPointAnnotationOptions().apply {
                        withLatLng(info.homeLongitude, info.homeLatitude)
                        withIconImage(MAP_ICON_HOME)
                        withTouchable(false)
                        withLayerPriority(LayerPriority.HIGH)
                        withSymbolSortKey(5.0)
                    }
                    marker.homeAnnotation = mapManager.addPoint(homeOptions)
                }
            }

            if (isDrone) {
                val newLatlng = AutelLatLng(info.latitude, info.longitude)
                if (marker.points.isNotEmpty() && MapBoxUtils.getDistance(marker.points.last(), newLatlng) > TRACK_POINT_MIN_DISTANCE_METERS) {
                    marker.points.add(newLatlng)

                    if (marker.points.size > MAX_LINE_POINTS) {
                        marker.points.removeAt(0)
                    }

                    if (currentTime - marker.lastLineRenderTime > LINE_RENDER_THROTTLE_MS) {
                        updateDroneLine(marker)
                        marker.lastLineRenderTime = currentTime
                    }
                }
            }
        }

        checkCameraFollow(isDrone, info.id)
    }

    private fun checkCameraFollow(isDrone: Boolean, id: Int) {
        val isTargetUpdating = when (currentTrackingTarget) {
            TrackingTarget.DRONE -> isDrone
            TrackingTarget.HOME -> isDrone
            TrackingTarget.RC -> !isDrone && id == -1
        }
        if (isTargetUpdating) {
            updateCameraPosition()
        }
    }

    private fun updateDroneLine(droneMarker: DroneMarker) {
        val points = droneMarker.points
        if (points.isEmpty()) return

        val map = nativeMapLibreMap ?: return
        val style = map.style ?: return
        val source = style.getSource(TRACK_SOURCE_ID) as? GeoJsonSource ?: return

        try {
            val sb = java.lang.StringBuilder()
            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
            for (i in points.indices) {
                val pt = points[i]
                sb.append("[").append(pt.longitude).append(",").append(pt.latitude).append("]")
                if (i < points.size - 1) sb.append(",")
            }
            sb.append("]}}")

            source.setGeoJson(sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Native update line error: ${e.message}")
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
        lastCameraLatLng = null
        lastCameraBearing = -1.0
        updateCameraPosition()
    }

    fun resumeAutoFollow() {
        this.isAutoFollowEnabled = true
        this.currentZoom = mapManager.getZoom()
        lastCameraLatLng = null
        lastCameraBearing = -1.0
        updateCameraPosition()
    }

    fun initMap() {
        val MAPTILER_KEY = "rgEFGBMZ0ahwpglRNXRL"
        MapManager.setMapToken(MAPTILER_KEY)
        mapManager = MapManager(context)
        mapManager.setDefaultMapSettings()
        addMapView()

        val activity = getActivity(context) ?: return
        mapManager.loadStyle(activity, AutelMapStyle.MIX, resources.configuration.locale) { isSuc, msg ->
            viewScope.launch {
                if (isSuc) {
                    loadResources()
                    mapVM.setup()
                    isStyleLoaded = true
                    startFlowCollection()

                    tryExtractMapLibre()
                }
            }
        }
    }

    private fun addMapView() {
        addView(mapManager.getMapView()!!, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun startFlowCollection() {
        viewScope.launch {
            mapVM.droneInfoState
                .sample(FLOW_SAMPLE_PERIOD_MS)
                .collect { list ->
                    list.forEach { updateOrAddMarker(it, MAP_ICON_DRONE, isDrone = true) }
                }
        }

        viewScope.launch {
            mapVM.rcLocationState
                .sample(FLOW_SAMPLE_PERIOD_MS)
                .collect { rcInfo ->
                    rcInfo?.let { updateOrAddMarker(it, MAP_ICON_RC, isDrone = false) }
                }
        }
    }

    private fun updateCameraPosition() {
        if (!isAutoFollowEnabled) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCameraUpdateTime < CAMERA_UPDATE_THROTTLE_MS) return

        val targetLatLng = getTargetLatLng() ?: return
        val heading = if (isNorthUpMode) 0.0 else getTargetHeading()

        val distanceMoved = lastCameraLatLng?.let { MapBoxUtils.getDistance(it, targetLatLng) } ?: Double.MAX_VALUE
        val bearingChanged = lastCameraBearing?.let { abs(it - heading) } ?: Double.MAX_VALUE

        if (distanceMoved < CAMERA_MOVE_THRESHOLD_METERS && bearingChanged < CAMERA_ROTATE_THRESHOLD_DEGREES) {
            return
        }

        lastCameraUpdateTime = currentTime
        lastCameraLatLng = targetLatLng
        lastCameraBearing = heading

        mapManager.moveCameraTo(
            latLng = targetLatLng,
            zoom = currentZoom,
            duration = CAMERA_MOVE_DURATION_MS,
            bearing = heading
        )
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
        val activity = getActivity(context) ?: return
        mapManager.loadStyle(activity, style, resources.configuration.locale) { isSuc, msg ->
            if (isSuc) loadResources()
        }
    }

    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    inner class DroneMarker {
        var id = 0
        var points = mutableListOf<AutelLatLng>()
        var droneAnnotation: AutelPointAnnotation? = null
        var homeAnnotation: AutelPointAnnotation? = null
        var lineAnnotation: AutelPolyLineAnnotation? = null
        var absoluteHeading: Double = 0.0
        var lastRenderTime = 0L
        var lastLineRenderTime = 0L
    }

    private fun getRelativeRotation(absoluteHeading: Double, forcedBearing: Double? = null): Double {
        val mapBearing = forcedBearing ?: mapManager.getBearing()
        var relative = absoluteHeading - mapBearing
        relative %= 360.0
        if (relative < 0) relative += 360.0
        return relative
    }

    private fun tryExtractMapLibre() {
        try {
            val field = mapManager.javaClass.getDeclaredField("mapTilerView")
            field.isAccessible = true
            val mapView = field.get(mapManager) as? MapView

            if (mapView != null) {
                Log.d(TAG, "MapHack: Нашли скрытый MapView в рантайме!")

                mapView.getMapAsync { mapLibreMap ->
                    Log.d(TAG, "MapHack: Нативный движок MapLibre ПОДКЛЮЧЕН!")
                    nativeMapLibreMap = mapLibreMap

                    mapLibreMap.getStyle { style ->
                        if (style.getSource(TRACK_SOURCE_ID) == null) {
                            val geoJsonSource = GeoJsonSource(TRACK_SOURCE_ID)
                            style.addSource(geoJsonSource)

                            val lineLayer = LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).withProperties(
                                PropertyFactory.lineColor("#EEEEEE"),
                                PropertyFactory.lineWidth(5.0f),
                                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                            )
                            style.addLayer(lineLayer)
                            Log.d(TAG, "MapHack: Нативные слои для линии трека созданы.")
                        }
                    }
                }
            } else {
                Log.e(TAG, "MapHack: mapTilerView сейчас null.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MapHack: Ошибка извлечения карты: ${e.message}")
            e.printStackTrace()
        }
    }
}