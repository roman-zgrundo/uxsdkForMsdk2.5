package com.autel.setting.liveStream

import android.content.*
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.autel.widget.databinding.WidgetUxDialogLiveStreamBinding

class LiveStreamSettingsDialog : DialogFragment() {

    private var _binding: WidgetUxDialogLiveStreamBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LiveStreamViewModel
    private val PREFS_NAME = "LiveStreamPrefs"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = WidgetUxDialogLiveStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Важно: берем ViewModel от Activity!
        viewModel = ViewModelProvider(requireActivity())[LiveStreamViewModel::class.java]

        loadSettings()
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnAction.setOnClickListener {
            saveSettings()
            val url = binding.etRtmpUrl.text.toString()
            if (url.isNotEmpty()) {
                viewModel.toggleStream(url)
            }
        }
    }

    private fun saveSettings() {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("rtmp_url", binding.etRtmpUrl.text.toString())
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.etRtmpUrl.setText(prefs.getString("rtmp_url", ""))
    }

    private fun observeViewModel() {
        viewModel.isStreaming.observe(viewLifecycleOwner) { isStreaming ->
            binding.btnAction.text = if (isStreaming) "ОСТАНОВИТЬ" else "ЗАПУСТИТЬ ТРАНСЛЯЦИЮ"
            binding.btnAction.backgroundTintList = ColorStateList.valueOf(
                if (isStreaming) 0xFFFF4444.toInt() else 0xFFFF9800.toInt()
            )
        }
        viewModel.statsText.observe(viewLifecycleOwner) { binding.tvStreamStats.text = it }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}