package com.autel.setting.provider.function

import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.autel.common.delegate.IMainProvider
import com.autel.common.delegate.function.AbsDelegateFunction
import com.autel.common.delegate.function.FunctionType
import com.autel.common.delegate.function.FunctionViewType
import com.autel.common.lifecycle.LiveDataBus
import com.autel.common.sdk.business.DroneLightVM
import com.autel.setting.R

class NavigationLightFunctionEntry(mainProvider: IMainProvider) : AbsDelegateFunction(mainProvider) {

    private val viewModel = ViewModelProvider(mainProvider.getMainContext() as ComponentActivity)[DroneLightVM::class.java]

    override fun getFunctionType(): FunctionType {
        return FunctionType.NavigationLight
    }

    override fun getFunctionName(): String {
        return mainProvider.getMainContext().getString(R.string.common_text_light_night_title)
    }

    override fun getFunctionIconRes(): Int {
        return R.drawable.common_selector_shortcuts_function_navigation_light
    }

    override fun functionEnableDependsOnAircraft(): Boolean {
        return true
    }

    override fun onFunctionCreate() {
        super.onFunctionCreate()
        viewModel.queryAllLedLight()

        // Подписка на событие пульта для режима тишины
        LiveDataBus.of<Boolean>("EVENT_TOGGLE_STEALTH_MODE").observe(mainProvider.getMainLifecycleOwner()) {
            // Получаем текущее состояние из ViewModel
            val isSilent = viewModel.silenceModeStatusLD.value ?: false
            // Переключаем на противоположное
            viewModel.switchSilenceModeStatus()
            // Примечание: Метод setLedSilentMode должен быть в DroneLightVM
            // Если его там нет, используй привычный тебе способ через KeyManager,
            // но здесь, внутри Entry, это заставит UI обновиться.
        }

        viewModel.navigationLightLD.observe(mainProvider.getMainLifecycleOwner()) {
            refreshNavLightOn()
        }

        viewModel.silenceModeStatusLD.observe(mainProvider.getMainLifecycleOwner()) {
            refreshNavLightOn()
        }
        viewModel.addObserver()
    }
    override fun functionEnableCondition(): Boolean {
        return super.functionEnableCondition() && viewModel.silenceModeStatusLD.value != true
    }
    private fun refreshNavLightOn() {
        val silenceModeStatus = viewModel.silenceModeStatusLD.value
        functionModel.isOn = viewModel.navigationLightLD.value == true
        if (silenceModeStatus == true) {
            functionModel.isOn = false
            functionModel.isEnabled = functionEnableCondition()
        } else {
            functionModel.isEnabled = functionEnableCondition()
        }
        mainProvider.getMainHandler().updateFunctionState(functionModel)
    }

    override fun onFunctionStart(viewType: FunctionViewType, view: View) {
        super.onFunctionStart(viewType, view)
        viewModel.setNavigationLight(true, {}, {})
    }

    override fun onFunctionStop(viewType: FunctionViewType, view: View) {
        super.onFunctionStop(viewType, view)
        functionStop()
    }

    override fun onFunctionDestroy() {
        super.onFunctionDestroy()
        viewModel.removeObserver()
    }

    fun functionStop() {
        viewModel.setNavigationLight(false, {}, {})
    }
}