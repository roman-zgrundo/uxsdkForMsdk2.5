package com.autel.setting.provider.customFunction

import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.autel.common.delegate.IMainProvider
import com.autel.common.delegate.function.AbsDelegateFunction
import com.autel.common.delegate.function.FunctionType
import com.autel.common.delegate.function.FunctionViewType
import com.autel.setting.R // проверь правильный ли R
import com.autel.setting.liveStream.LiveStreamSettingsDialog

class LivePlayFunctionEntry(mainProvider: IMainProvider) : AbsDelegateFunction(mainProvider) {
    override fun getFunctionType(): FunctionType = FunctionType.LivePlay

    override fun getFunctionName(): String = "Трансляция"

    override fun getFunctionIconRes(): Int = R.drawable.common_shortcuts_function_live_play // пока поставь любую иконку


    override fun onFunctionStart(viewType: FunctionViewType, view: View) {
        super.onFunctionStart(viewType, view)
        // 1. Получаем активити из провайдера
        val activity = mainProvider.getMainContext() as? FragmentActivity

        // 2. Показываем твой диалог
        activity?.let {
            LiveStreamSettingsDialog().apply {
                setStyle(DialogFragment.STYLE_NORMAL, R.style.settingWideDialog)
            }.show(it.supportFragmentManager, "LiveStreamSettings")
        }

        // 3. Важно: отжимаем кнопку обратно, так как она сработала как триггер окна, а не как "вкл/выкл"
        onFunctionStop(viewType, view)
    }
}