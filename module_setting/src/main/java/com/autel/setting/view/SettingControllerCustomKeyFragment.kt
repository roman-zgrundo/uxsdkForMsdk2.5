package com.autel.setting.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.autel.common.base.BaseAircraftFragment
import com.autel.common.manager.AutelStorageManager
import com.autel.common.manager.SameResourceHelper
import com.autel.setting.R
import com.autel.setting.databinding.SettingControllerCustomKeyFragmentBinding
import com.autel.setting.utils.CustomKeyConfig

class SettingControllerCustomKeyFragment : BaseAircraftFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = SettingControllerCustomKeyFragmentBinding.inflate(inflater, container, false)
        setupSpinners(binding)
        setupUI(binding)
        return binding.root
    }

    private fun setupSpinners(binding: SettingControllerCustomKeyFragmentBinding) {
        val storage = AutelStorageManager.getPlainStorage()

        // Конфигурация для C1
        binding.tvSelectC1Spinner.apply {
            dataList = CustomKeyConfig.displayNames
            val saved = storage.getIntValue(CustomKeyConfig.KEY_C1_ACTION, -1)
            val index = CustomKeyConfig.actions.indexOfFirst { it.second.ordinal == saved }
            setDefaultText(if (index != -1) index else 0)

            setSpinnerViewListener { position ->
                storage.setIntValue(CustomKeyConfig.KEY_C1_ACTION, CustomKeyConfig.actions[position].second.ordinal)
            }
        }

        // Конфигурация для C2
        binding.tvSelectC2Spinner.apply {
            dataList = CustomKeyConfig.displayNames
            val saved = storage.getIntValue(CustomKeyConfig.KEY_C2_ACTION, -1)
            val index = CustomKeyConfig.actions.indexOfFirst { it.second.ordinal == saved }
            setDefaultText(if (index != -1) index else 0)

            setSpinnerViewListener { position ->
                storage.setIntValue(CustomKeyConfig.KEY_C2_ACTION, CustomKeyConfig.actions[position].second.ordinal)
            }
        }
    }

    private fun setupUI(binding: SettingControllerCustomKeyFragmentBinding) {
        binding.tvKeyDefine.text = getString(R.string.common_text_c1_c2_key_define)
        binding.llCustomRight.isVisible = true
        binding.tvCustomLeft.isVisible = true
        binding.ivKeyDefine.setImageResource(SameResourceHelper.getRemoteCustomDefineRes())
    }

    override fun getData() {}
    override fun addListen() {}
}