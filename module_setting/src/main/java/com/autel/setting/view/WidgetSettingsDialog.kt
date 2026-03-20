package com.autel.setting.view

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.autel.setting.R
import com.autel.setting.custommenu.WidgetControl
import com.autel.widget.databinding.WidgetDialogWidgetsSettingsBinding
import com.google.android.material.button.MaterialButton
import androidx.core.view.isVisible

class WidgetSettingsDialog : DialogFragment() {

    private var _binding: WidgetDialogWidgetsSettingsBinding? = null
    private val binding get() = _binding!!

    private val colorSelected = 0xFFFF9800.toInt()
    private val colorNormal = 0xFF2C2C2C.toInt()
    private val colorTextSelected = Color.BLACK
    private val colorTextNormal = Color.WHITE

    companion object {
        private const val PREFS_NAME = "widget_prefs"

        /**
         * Метод для применения всех сохраненных настроек.
         * Вызывай его в UxsdkDemoActivity после инициализации всех View.
         */
        fun applyAllSavedSettings(activity: Activity) {
            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            WidgetControl.entries.forEach { widget ->
                val isVisible = prefs.getBoolean(widget.name, true)
                widget.viewIds.forEach { idName ->
                    val resId = activity.resources.getIdentifier(idName, "id", activity.packageName)
                    if (resId != 0) {
                        // Ищем View. Если это карта (acv_map), она может быть внутри контейнера.
                        val view = activity.findViewById<View>(resId)
                        if (view != null) {
                            enforceVisibility(view, isVisible, idName)
                        }
                    }
                }
            }
        }

        // Внутри WidgetSettingsDialog.Companion

        private fun enforceVisibility(view: View, shouldBeVisible: Boolean, idName: String) {
            val oldListener = view.tag as? View.OnLayoutChangeListener
            if (oldListener != null) {
                view.removeOnLayoutChangeListener(oldListener)
                view.tag = null
            }

            if (shouldBeVisible) {
                // Если виджет должен быть виден, мы НЕ СТАВИМ СТОРОЖА.
                // Просто даем команду на показ.
                if (view.visibility != View.VISIBLE) {
                    view.visibility = View.VISIBLE
                }
            } else {
                // Если виджет скрыт - ставим жесткий контроль
                val hiddenState = if (idName == "acv_map") View.INVISIBLE else View.GONE

                if (view.visibility != hiddenState) {
                    view.visibility = hiddenState
                }

                val lockListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    if (v.visibility == View.VISIBLE) {
                        // SDK попытался включить - мы блокируем
                        v.visibility = hiddenState
                    }
                }
                view.addOnLayoutChangeListener(lockListener)
                view.tag = lockListener
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = WidgetDialogWidgetsSettingsBinding.inflate(inflater, container, false)
        return binding.root as View
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        setupWidgetButtons()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            val metrics = resources.displayMetrics
            val dialogHeight = (metrics.heightPixels * 0.85).toInt()
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, dialogHeight)
            window.setGravity(Gravity.CENTER)
        }
    }

    private fun setupWidgetButtons() {
        val context = requireContext()
        val activity = activity ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        WidgetControl.entries.forEach { widget ->
            val button = MaterialButton(context, null, R.attr.materialButtonStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 12, 0, 12) }

                text = widget.title
                icon = context.getDrawable(widget.iconRes)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                cornerRadius = 12

                // 1. Читаем статус из памяти (а не из View)
                val isVisible = prefs.getBoolean(widget.name, true)
                updateButtonState(this, isVisible)

                setOnClickListener {
                    // 2. Инвертируем состояние
                    val newState = !prefs.getBoolean(widget.name, true)

                    // 3. Сохраняем в память
                    prefs.edit().putBoolean(widget.name, newState).apply()

                    // 4. Применяем к экрану и обновляем кнопку
                    toggleWidgetViews(activity, widget.viewIds, newState)
                    updateButtonState(this, newState)
                }
            }
            binding.llWidgetContainer.addView(button)
        }
    }

    private fun updateButtonState(button: MaterialButton, isSelected: Boolean) {
        button.isSelected = isSelected
        val bgColor = if (isSelected) colorSelected else colorNormal
        val textColor = if (isSelected) colorTextSelected else colorTextNormal

        button.backgroundTintList = ColorStateList.valueOf(bgColor)
        button.setTextColor(textColor)
        button.iconTint = ColorStateList.valueOf(textColor)
    }

    private fun toggleWidgetViews(activity: Activity, viewIdNames: List<String>, isVisible: Boolean) {
        viewIdNames.forEach { idName ->
            val resId = resources.getIdentifier(idName, "id", activity.packageName)
            if (resId != 0) {
                val view = activity.findViewById<View>(resId) ?: return@forEach

                // Используем наш Companion метод, который умеет правильно ставить/снимать сторожа
                WidgetSettingsDialog.enforceVisibility(view, isVisible, idName)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}