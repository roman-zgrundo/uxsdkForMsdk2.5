package com.autel.setting.custommenu

import com.autel.setting.R

enum class WidgetControl(
    val title: String,
    val iconRes: Int,
    val viewIds: List<String> // ID вьюхи из uxsdk
) {
    CODEC_TAB("Переключение камер", R.drawable.outline_cameraswitch_24, listOf("codec_tab_view")),

    FL_GIMBAL("наклон камеры", R.drawable.outline_arrow_split_24, listOf("fl_gimbal")),

    FIXED_TARGET("Информация по цели", R.drawable.outline_flag_circle_24, listOf("ll_fixed_target_panel")),

    SET_ADDRESS("Адрес получателя", R.drawable.outline_contact_phone_24, listOf("btn_set_address")),

    TRACKER_DEBUG_PANEL("Отладки дальномера", R.drawable.outline_stylus_laser_pointer_24, listOf("ll_tracker_debug_panel")),

    LOCATION_TRACKER_PANEL("Информация расчетов координат", R.drawable.outline_chat_info_24, listOf("ll_location_tracker_panel")),

    ATTRIBUTE_BALL("Авиагоризонт телеметрия", R.drawable.outline_data_info_alert_24, listOf("asl_view")),

    ATTRIBUTE_BALL_BALL_VIEW("Авиагоризонт (КРУГ)", R.drawable.outline_assistant_navigation_24, listOf("ballView", "iv_ball_visual")),

//    MAP("Карта", R.drawable.outline_map_24, listOf("acv_map")),

}