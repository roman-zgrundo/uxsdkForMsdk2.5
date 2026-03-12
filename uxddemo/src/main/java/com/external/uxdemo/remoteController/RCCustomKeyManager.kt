package com.external.uxdemo.remoteController

import android.util.Log
import com.autel.common.bean.CustomRemoteKeyEnum
import com.autel.common.manager.AutelStorageManager
import com.autel.drone.sdk.vmodelx.manager.DeviceManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.base.AutelKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.RemoteControllerKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.GimbalKey
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.bean.HardwareButtonInfoBean
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.remotecontrol.enums.RCButtonTypeEnum
import com.autel.drone.sdk.vmodelx.interfaces.IKeyManager
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks.KeyListener
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks.CompletionCallbackWithParam
import com.autel.drone.sdk.libbase.error.IAutelCode
import com.autel.common.lifecycle.LiveDataBus
import com.autel.drone.sdk.vmodelx.manager.keyvalue.callback.CommonCallbacks
import com.autel.drone.sdk.vmodelx.manager.keyvalue.key.FlightPropertyKey
import com.autel.setting.utils.CustomKeyConfig // Импортируем наш конфиг

object RCCustomKeyManager {
    private const val TAG = "RCCustomKeyManager"
    private var activeKeyManager: IKeyManager? = null
    private var infoListener: KeyListener<HardwareButtonInfoBean>? = null
    private var gimbalStep = 0

    fun init() {
        val rcDevice = try { DeviceManager.getDeviceManager().getFirstRemoteDevice() } catch (e: Exception) { null }
        activeKeyManager = rcDevice?.getKeyManager() ?: return

        val infoKey = AutelKey.create(RemoteControllerKey.KeyRCHardwareInfo)
        infoListener = object : KeyListener<HardwareButtonInfoBean> {
            override fun onValueChange(oldValue: HardwareButtonInfoBean?, newValue: HardwareButtonInfoBean) {
                if (newValue.clickType.name.contains("CLICK", ignoreCase = true)) {
                    processButton(newValue.buttonType)
                }
            }
        }
        activeKeyManager?.listen(infoKey, infoListener!!)
    }

    private fun processButton(type: RCButtonTypeEnum) {
        val storageKey = when (type) {
            RCButtonTypeEnum.LEFT_CUSTOM -> CustomKeyConfig.KEY_C1_ACTION
            RCButtonTypeEnum.RIGHT_CUSTOM -> CustomKeyConfig.KEY_C2_ACTION
            else -> return
        }

        val savedOrdinal = AutelStorageManager.getPlainStorage().getIntValue(storageKey, -1)
        val action = CustomRemoteKeyEnum.values().getOrNull(savedOrdinal) ?: CustomRemoteKeyEnum.UNKNOWN

        Log.i(TAG, "Кнопка: ${type.name}, Действие: ${action.name}")

        when (action) {
            CustomRemoteKeyEnum.GIMBAL_ANGLE -> cycleGimbalAngle()
//            CustomRemoteKeyEnum.MAP_FPV_SWITCH -> LiveDataBus.of<Boolean>("switch_map_fpv").post(true)
            CustomRemoteKeyEnum.DOWN_LIGHT_SWITCH -> toggleDownFillLight()
            CustomRemoteKeyEnum.ARM_LIGHT_SWITCH -> toggleStealthMode()
            CustomRemoteKeyEnum.UNKNOWN -> LiveDataBus.of<Boolean>("switch_unknown").post(true)
            else -> {}
        }
    }

    private fun toggleDownFillLight() {
        Log.i(TAG, "Нажата настраиваемая кнопка: переключение нижней подсветки через шину событий")
        LiveDataBus.of<Boolean>("EVENT_TOGGLE_DOWN_LIGHT").post(true)
    }
    private fun cycleGimbalAngle() {
        val angles = floatArrayOf(0f, 45f, 90f)
        gimbalStep = (gimbalStep + 1) % angles.size
        val targetAngle = angles[gimbalStep]

        val droneKM = DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager() ?: return
        droneKM.performAction(AutelKey.create(GimbalKey.KeyAngleDegreeControl), targetAngle, null)
    }

//    private fun toggleStealthMode() {
//        val droneKM = DeviceManager.getDeviceManager().getFirstDroneDevice()?.getKeyManager() ?: return
//        val silentKey = AutelKey.create(FlightPropertyKey.KeySilentModeStatus)
//
//        droneKM.getValue(silentKey, object : CompletionCallbackWithParam<Boolean> {
//            override fun onSuccess(currentValue: Boolean?) {
//                droneKM.setValue(silentKey, !(currentValue ?: false), null)
//            }
//            override fun onFailure(error: IAutelCode, msg: String?) {}
//        })
//    }

    private fun toggleStealthMode() {
        Log.i(TAG, "Нажата кнопка: переключение режима тишины (Stealth Mode)")
        // Просто уведомляем систему, а Entry сам разберется с логикой
        LiveDataBus.of<Boolean>("EVENT_TOGGLE_STEALTH_MODE").post(true)
    }


    fun release() {
        activeKeyManager?.let { it.cancelListen(infoListener ?: return) }
        activeKeyManager = null
        infoListener = null
    }
}