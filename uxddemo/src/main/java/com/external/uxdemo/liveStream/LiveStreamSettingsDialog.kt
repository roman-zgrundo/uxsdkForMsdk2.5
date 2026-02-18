package com.external.uxdemo.liveStream

import android.content.*
import android.content.res.ColorStateList
import android.os.*
import android.view.*
import android.widget.SeekBar
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.autel.widget.databinding.WidgetUxDialogLiveStreamBinding

class LiveStreamSettingsDialog : DialogFragment() {

    private var _binding: WidgetUxDialogLiveStreamBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LiveStreamViewModel
    private var streamService: LiveStreamService? = null
    private val PREFS_NAME = "LiveStreamPrefs"

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LiveStreamService.LocalBinder
            streamService = binder.getService()
            streamService?.let { viewModel.onServiceConnected(it) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { streamService = null }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = WidgetUxDialogLiveStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[LiveStreamViewModel::class.java]

        loadSettings() // Сначала загружаем и выставляем UI
        setupListeners()
        observeViewModel()

        val intent = Intent(requireContext(), LiveStreamService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupListeners() {
        binding.rgQuality.setOnCheckedChangeListener { _, _ -> saveSettings() }
        binding.rgCameraSource.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setCameraPort(checkedId == binding.rbIrCamera.id)
            saveSettings()
        }
        binding.sbBitrate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                binding.tvBitrateLabel.text = "Битрейт: $progress kbps"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) { saveSettings() }
        })

        binding.btnAction.setOnClickListener {
            saveSettings()
            if (viewModel.isStreaming.value == true) {
                viewModel.stopStream()
            } else {
                val url = binding.etRtmpUrl.text.toString()
                if (url.isNotEmpty()) {
                    val intent = Intent(requireContext(), LiveStreamService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(intent)
                    } else {
                        requireContext().startService(intent)
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        viewModel.startStream(url, binding.sbBitrate.progress)
                    }, 400)
                }
            }
        }
    }

    private fun saveSettings() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("rtmp_url", binding.etRtmpUrl.text.toString())
            putInt("bitrate", binding.sbBitrate.progress)
            putInt("camera_id", binding.rgCameraSource.checkedRadioButtonId)
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

        // Восстановление камеры (если 0 или -1, ставим дефолтную)
        var cameraId = prefs.getInt("camera_id", binding.rbMainCamera.id)
        if (cameraId <= 0) cameraId = binding.rbMainCamera.id
        binding.rgCameraSource.check(cameraId)
        // Важно: здесь вызываем просто установку порта БЕЗ старта стрима
        viewModel.setCameraPort(cameraId == binding.rbIrCamera.id)

        // Восстановление качества
        var qualityId = prefs.getInt("quality_id", binding.rbHd.id)
        if (qualityId <= 0) qualityId = binding.rbHd.id
        binding.rgQuality.check(qualityId)
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
        try { requireContext().unbindService(serviceConnection) } catch (e: Exception) {}
        _binding = null
    }

}