package com.external.uxdemo.liveStream

import android.content.*
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import android.widget.SeekBar
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

        binding.sbBitrate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                binding.tvBitrateLabel.text = "Битрейт: $progress kbps"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) { saveSettings() }
        })

        binding.btnAction.setOnClickListener {
            saveSettings()
            val url = binding.etRtmpUrl.text.toString()
            val bitrate = binding.sbBitrate.progress
            viewModel.toggleStream(url, bitrate)
        }
    }

    private fun saveSettings() {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("rtmp_url", binding.etRtmpUrl.text.toString())
            putInt("bitrate", binding.sbBitrate.progress)
            putInt("quality_id", binding.rgQuality.checkedRadioButtonId)
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.etRtmpUrl.setText(prefs.getString("rtmp_url", ""))
        val bitrate = prefs.getInt("bitrate", 3000)
        binding.sbBitrate.progress = bitrate
        binding.tvBitrateLabel.text = "Битрейт: $bitrate kbps"
        binding.rgQuality.check(prefs.getInt("quality_id", binding.rbHd.id))
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