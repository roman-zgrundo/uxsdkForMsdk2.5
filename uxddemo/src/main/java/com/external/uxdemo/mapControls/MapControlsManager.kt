    package com.external.uxdemo.mapControls

    import android.content.Context
    import android.graphics.Color
    import android.view.HapticFeedbackConstants
    import android.view.MotionEvent
    import android.view.View
    import android.widget.ImageButton
    import android.widget.Toast
    import com.autel.map.bean.AutelMapStyle
    import com.autel.widget.widget.map.MapWidget
    import com.external.uxddemo.R

    class MapControlsManager(
        private val context: Context,
        private val rootView: View,
        private val mapWidget: MapWidget?
    ) {

        fun setup() {
            val btnMapLayers = rootView.findViewById<View>(R.id.btn_map_layers)
            val submenuLayers = rootView.findViewById<View>(R.id.ll_submenu_layers)
            val submenuTracking = rootView.findViewById<View>(R.id.ll_submenu_tracking)

            val btnMapNormal = rootView.findViewById<View>(R.id.btn_map_normal)
            val btnMapHybrid = rootView.findViewById<View>(R.id.btn_map_hybrid)
            val btnMapOrientation = rootView.findViewById<ImageButton>(R.id.btn_map_orientation)
            val btnMapTrackingMenu = rootView.findViewById<View>(R.id.btn_map_tracking_menu)

            val btnTrackDrone = rootView.findViewById<View>(R.id.btn_track_drone)
            val btnTrackRc = rootView.findViewById<View>(R.id.btn_track_rc)
            val btnTrackHome = rootView.findViewById<View>(R.id.btn_track_home)
            val btnMapFocusDrone = rootView.findViewById<View>(R.id.btn_map_focus_drone)

            // Подписываемся на сдвиг карты
            mapWidget?.onManualPan = {
                btnMapFocusDrone?.visibility = View.VISIBLE
            }

            // 1. Управление слоями (оставляем как у тебя)
            btnMapLayers?.setOnClickListener {
                submenuLayers?.visibility = if (submenuLayers?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                submenuTracking?.visibility = View.GONE
            }
            btnMapNormal?.setOnClickListener {
                mapWidget?.switchMapStyle(AutelMapStyle.NORMAL)
                submenuLayers?.visibility = View.GONE
            }
            btnMapHybrid?.setOnClickListener {
                mapWidget?.switchMapStyle(AutelMapStyle.MIX)
                submenuLayers?.visibility = View.GONE
            }

            // 2. Компас
            btnMapOrientation?.setOnClickListener { view ->
                // Узнаем текущее состояние из самого виджета и инвертируем его
                val currentMode = mapWidget?.isNorthUpMode ?: true
                val newMode = !currentMode

                mapWidget?.setNorthUpMode(newMode) // Передаем команду в виджет

                val button = view as ImageButton
                if (newMode) {
                    button.setColorFilter(Color.WHITE)
                    Toast.makeText(context, "Ориентация: Север сверху", Toast.LENGTH_SHORT).show()
                } else {
                    button.setColorFilter(Color.parseColor("#FFAA00"))
                    Toast.makeText(context, "Ориентация: По направлению движения", Toast.LENGTH_SHORT).show()
                }
            }

            // 3. Меню выбора слежения
            btnMapTrackingMenu?.setOnClickListener {
                submenuTracking?.visibility = if (submenuTracking?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                submenuLayers?.visibility = View.GONE
            }

            btnTrackDrone?.setOnClickListener {
                mapWidget?.setAutoFollowTarget(MapWidget.TrackingTarget.DRONE)
                btnMapFocusDrone?.visibility = View.GONE
                submenuTracking?.visibility = View.GONE
                Toast.makeText(context, "Слежение за дроном", Toast.LENGTH_SHORT).show()
            }

            btnTrackRc?.setOnClickListener {
                mapWidget?.setAutoFollowTarget(MapWidget.TrackingTarget.RC)
                btnMapFocusDrone?.visibility = View.GONE
                submenuTracking?.visibility = View.GONE
                Toast.makeText(context, "Слежение за пультом", Toast.LENGTH_SHORT).show()
            }

            btnTrackHome?.setOnClickListener {
                mapWidget?.setAutoFollowTarget(MapWidget.TrackingTarget.HOME)
                btnMapFocusDrone?.visibility = View.GONE
                submenuTracking?.visibility = View.GONE
                Toast.makeText(context, "Переход на точку старта", Toast.LENGTH_SHORT).show()
            }

            // 4. Кнопка "Прицел" (Возврат к слежению за текущей выбранной целью)
            btnMapFocusDrone?.setOnClickListener { view ->
                mapWidget?.resumeAutoFollow()
                view.visibility = View.GONE
            }
        }
    }