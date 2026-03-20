package com.autel.setting.provider.customFunction

import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.autel.common.delegate.IMainProvider
import com.autel.common.delegate.function.AbsDelegateFunction
import com.autel.common.delegate.function.FunctionType
import com.autel.common.delegate.function.FunctionViewType
import com.autel.setting.R
import com.autel.setting.view.WidgetSettingsDialog

class WidgetsMenuFunctionEntry(mainProvider: IMainProvider) : AbsDelegateFunction(mainProvider) {

    override fun getFunctionType(): FunctionType = FunctionType.Personal

    override fun getFunctionName(): String = "Виджеты"

    override fun getFunctionIconRes(): Int = R.drawable.outline_display_settings_24

    override fun onFunctionStart(viewType: FunctionViewType, view: View) {
        super.onFunctionStart(viewType, view)

        val activity = mainProvider.getMainContext() as? FragmentActivity
        activity?.let {
            WidgetSettingsDialog().apply {
                setStyle(DialogFragment.STYLE_NORMAL, R.style.settingWideDialog)
            }.show(it.supportFragmentManager, "WidgetSettings")
        }

        onFunctionStop(viewType, view)
    }
}