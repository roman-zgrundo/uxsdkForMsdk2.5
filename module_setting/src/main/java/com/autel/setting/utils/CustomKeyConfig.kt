package com.autel.setting.utils

import com.autel.common.bean.CustomRemoteKeyEnum

object CustomKeyConfig {
    // Красивые и понятные ключи для хранилища
    const val KEY_C1_ACTION = "CUSTOM_C1_ACTION"
    const val KEY_C2_ACTION = "CUSTOM_C2_ACTION"

    // Централизованный список доступных действий
    val actions = listOf(
        "Наклон. центр./45°/опустить стабилиз-р" to CustomRemoteKeyEnum.GIMBAL_ANGLE,
        "Нижняя подсветка" to CustomRemoteKeyEnum.DOWN_LIGHT_SWITCH,
//        "Переключить карту" to CustomRemoteKeyEnum.MAP_FPV_SWITCH,
        "Режим НЕВИДИМКА" to CustomRemoteKeyEnum.ARM_LIGHT_SWITCH,
        "Пусто" to CustomRemoteKeyEnum.UNKNOWN
    )

    val displayNames = actions.map { it.first }
}