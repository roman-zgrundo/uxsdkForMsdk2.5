package com.external.uxdemo.view

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.external.uxdemo.soldatServiceConnection.SoldatServiceViewModel
import com.autel.codec.debug.TestSkyLinkFragment
import com.autel.codec.splitscreen.business.ScreenStateManager
import com.autel.common.delegate.function.FunctionBarState
import com.autel.common.delegate.function.FunctionViewType
import com.autel.common.lifecycle.LiveDataBus
import com.autel.common.lifecycle.event.FunctionViewStyleEvent
import com.autel.common.lifecycle.event.SplitScreenEffectEvent
import com.autel.common.manager.AutelStorageManager
import com.autel.common.manager.StorageKey
import com.autel.common.model.splitscreen.AircraftScreenItem
import com.autel.common.utils.AnimateUtil
import com.autel.common.utils.DeviceUtils
import com.autel.drone.sdk.vmodelx.interfaces.IAutelDroneDevice
import com.autel.drone.sdk.vmodelx.manager.SpeedModeManager
import com.autel.drone.sdk.vmodelx.manager.data.ControlMode
import com.autel.drone.sdk.vmodelx.utils.BroadcastUtils
import com.autel.map.MapManager
import com.autel.player.player.AutelPlayerManager
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.widget.widget.map.MapWidget
import com.external.uxddemo.R
import com.external.uxdemo.liveStream.LiveStreamSettingsDialog
import com.external.uxdemo.liveStream.LiveStreamViewModel
import com.external.uxdemo.locationTracker.LocationTrackerManager
import androidx.core.content.edit
import com.external.uxdemo.soldatServiceConnection.ClassifierUIHelper
import com.external.uxdemo.soldatServiceConnection.SoldatManager


class UxsdkDemoActivity : BaseMainActivity() {

    private lateinit var soldatManager: SoldatManager
    private lateinit var classifierUI: ClassifierUIHelper
    private val trackerManager = LocationTrackerManager()

    // Константы и настройки
    private val BURST_TYPE_ID = 99009025
    private val PREFS_NAME = "DroneSettings"
    private var currentProtocolType = 1    // 1 - R181, 2 - IP
    private var currentR181Address = 111111
    private var currentIpAddress = 0

    // ViewModel-и
    private val soldatViewModel: SoldatServiceViewModel by lazy {
        ViewModelProvider(this)[SoldatServiceViewModel::class.java]
    }
    private val liveStreamViewModel: LiveStreamViewModel by lazy {
        ViewModelProvider(this)[LiveStreamViewModel::class.java]
    }

    private var skyLinkFragment: TestSkyLinkFragment? = null
    var mapWidget: MapWidget? = null

    private val streamServiceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as com.external.uxdemo.liveStream.LiveStreamService.LocalBinder
            liveStreamViewModel.onServiceConnected(binder.getService())
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val start = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)

        // 1. Инициализация менеджеров
        soldatManager = SoldatManager(soldatViewModel)
        val classifierContainer = uiBinding.root.findViewById<LinearLayout>(R.id.classifier_items_container)
        classifierUI = ClassifierUIHelper(this, classifierContainer, soldatManager) {
            uiBinding.root.findViewById<View>(R.id.side_panel_classifier)?.visibility = View.GONE
        }

        // 2. Настройка данных цели
        val savedTarget = getSharedPreferences("TargetPrefs", Context.MODE_PRIVATE)
            .getString("last_target", "RNG: 0.0m\nMSL: 0.0m\nNo Data")
        uiBinding.root.findViewById<TextView>(R.id.tv_fixed_target_data)?.text = savedTarget

        // Ручной ввод для теста
        val manualTarget = "RNG: 222.1m\nMSL: 202.0m\n53.934154, 27.635051"
        uiBinding.root.findViewById<TextView>(R.id.tv_fixed_target_data)?.text = manualTarget

        BroadcastUtils.sendKillBroadcast(this)
        checkAndRequestAllFilesPermission()
        initView()

        if (DeviceUtils.isSingleControlDroneConnected()) {
            trackerManager.setupSubscriptions()
        }

        val intent = Intent(this, com.external.uxdemo.liveStream.LiveStreamService::class.java)
        bindService(intent, streamServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unbindService(streamServiceConnection) } catch (e: Exception) {}
    }

    private fun initView() {
        // --- AUTEL SDK UI SETUP ---
        mapWidget = MapWidget(this)
        uiBinding.autelSplitScreenContainer.getMapContainer().addView(mapWidget, ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
        if (MapManager.getMapToken().isNullOrEmpty()) showMapKeyDialog()

        uiBinding.codecTabView.setCodecTabSwitchListener { item ->
            ScreenStateManager.getInstance().updateCodecTabSwitch(ScreenStateManager.getInstance().getPageLocationState().fullScreenType, item)
            liveStreamViewModel.updateCameraSource(item.toString().contains("Thermal", ignoreCase = true))
        }

        uiBinding.statusBar.visibility = if (DeviceUtils.isMainRC()) View.VISIBLE else View.GONE
        uiBinding.rsStatus.visibility = if (DeviceUtils.isMainRC()) View.GONE else View.VISIBLE
        uiBinding.statusBar.multipleClicks.observe(this) {
            if (skyLinkFragment == null) addTestSkyLinkFragment() else removeTestSkyLinkFragment()
        }

        uiBinding.ivBarCollapse.setOnClickListener {
            val state = if (functionBarVm.functionBarLD.value == FunctionBarState.Unfolded) FunctionBarState.Folding else FunctionBarState.Unfolding
            functionBarVm.updateFunctionBarLD(state)
        }

        uiBinding.functionSuspensionView.setOnClickListener {
            val locationArray = IntArray(2)
            it.getLocationOnScreen(locationArray)
            uiBinding.functionFloatWindowView.show(locationArray)
            uiBinding.functionSuspensionView.visibility = View.GONE
            getMainHandler().hiddenFunctionPanel()
        }
        uiBinding.functionFloatWindowView.setDismissListener { uiBinding.functionSuspensionView.visibility = View.VISIBLE }

        refreshFunctionViewType(FunctionViewType.find(AutelStorageManager.getPlainStorage().getIntValue(StorageKey.PlainKey.KEY_FUNCTION_QUICK_ACTION)))
        uiBinding.attitudeBall.isVisible = DeviceUtils.isSingleControl()
        uiBinding.codecToolRight.setMainProvider(this)

        uiBinding.root.findViewById<View>(R.id.ll_custom_live_stream)?.setOnClickListener {
            LiveStreamSettingsDialog().apply { setStyle(DialogFragment.STYLE_NORMAL, R.style.WideDialog) }.show(supportFragmentManager, "LiveStreamSettings")
        }

        startTrackerUpdateLoop()

        // --- SOLDAT SERVICE UI SETUP ---
        loadSettings()

        uiBinding.root.findViewById<Button>(R.id.btn_set_address)?.setOnClickListener { showTargetAddressDialog() }

        uiBinding.root.findViewById<View>(R.id.btn_fix_target)?.setOnClickListener {
            saveTargetFix()
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }

        uiBinding.root.findViewById<View>(R.id.btn_open_classifier)?.setOnClickListener {
            getCoordsFromReport()?.let { (lat, lon) ->
                soldatManager.sendMarker(lat, lon)
                uiBinding.root.findViewById<View>(R.id.side_panel_classifier).apply {
                    bringToFront()
                    visibility = View.VISIBLE
                }
                classifierUI.fillPanel(lat, lon)
            } ?: Toast.makeText(this, "Сначала нажмите ФИКС ЦЕЛЬ", Toast.LENGTH_SHORT).show()
        }

        uiBinding.root.findViewById<View>(R.id.btn_burst_now)?.setOnClickListener {
            getCoordsFromReport()?.let { (lat, lon) ->
                soldatManager.sendObject(lat, lon, BURST_TYPE_ID, "РАЗРЫВ")
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }

        uiBinding.root.findViewById<Button>(R.id.btn_close_classifier)?.setOnClickListener {
            uiBinding.root.findViewById<View>(R.id.side_panel_classifier)?.visibility = View.GONE
        }
    }

    private fun saveTargetFix() {
        val data = trackerManager.getFixData()
        uiBinding.root.findViewById<TextView>(R.id.tv_fixed_target_data)?.text = data
        getSharedPreferences("TargetPrefs", Context.MODE_PRIVATE).edit { putString("last_target", data) }
    }
    private fun addTestSkyLinkFragment() {
        val fragment = TestSkyLinkFragment()
        fragment.mAutelPlayer =
            AutelPlayer(16010)
        skyLinkFragment = fragment
        supportFragmentManager.beginTransaction().replace(R.id.fl_test_sky_link, fragment).commit()
        AutelPlayerManager.getInstance().openVideoInfoShow(true)
    }

    private fun removeTestSkyLinkFragment() {
        skyLinkFragment?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
            skyLinkFragment = null
        }
    }

    override fun initObserver() {
        super.initObserver()

        @Suppress("ObjectLiteralToLambda")
        LiveDataBus.of(SplitScreenEffectEvent::class.java).isUniformScale().observe(
            this,
            object : Observer<Boolean> {
                override fun onChanged(t: Boolean) {
                    t?.let { ScreenStateManager.getInstance().updateUniform(t) }
                }
            })

        LiveDataBus.of(SplitScreenEffectEvent::class.java).updateFullScreenStyle().observe(
            this,
            object : Observer<Int> {
                override fun onChanged(t: Int) {
                    t?.let { ScreenStateManager.getInstance().updateFullScreenStyle(t) }
                }
            })

        LiveDataBus.of(FunctionViewStyleEvent::class.java).switchViewStyle().observe(
            this,
            object : Observer<FunctionViewType> {
                override fun onChanged(t: FunctionViewType) {
                    refreshFunctionViewType(t)
                }
            })

        ScreenStateManager.getInstance().observerScreenState(this, Observer {
            refreshCodecToolLeft()
            refreshCodecToolRight()
            refreshCodecTab()
            refreshAttitudeBall()
        })

        ScreenStateManager.getInstance().observerFullScreen(this, Observer { full ->

            val barHeight = uiBinding.statusBar.height * 1.0f
            val barTranslateY = if (full) {
                -barHeight
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.statusBar, "translationY", barTranslateY)

            val functionHeight = uiBinding.functionView.height
            val functionTranslateY = if (full) {
                -(barHeight + functionHeight)
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.functionView, "translationY", functionTranslateY)

            val barCollapseHeight = uiBinding.ivBarCollapse.height
            val barCollapseTranslateY = if (full) {
                -(barHeight + functionHeight + barCollapseHeight)
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.ivBarCollapse, "translationY", barCollapseTranslateY)

            val rcStatusTranslateY = if (full) {
                -(uiBinding.rsStatus.height + barHeight)
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.rsStatus, "translationY", rcStatusTranslateY)

            val acvStanceBallTranslateY = if (full) {
                uiBinding.attitudeBall.height + uiBinding.attitudeBall.marginBottom + 0.0f
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.attitudeBall, "translationY", acvStanceBallTranslateY)
            val codecTabSwitchTranslateX = if (full) {
                -(uiBinding.codecTabView.width + uiBinding.codecTabView.marginStart) * 1.0f
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.codecTabView, "translationX", codecTabSwitchTranslateX)

            val codecLeftTranslateX = if (full) {
                -(uiBinding.flGimbal.width + uiBinding.flGimbal.marginStart) * 1.0f
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.flGimbal, "translationX", codecLeftTranslateX)

            val codecRightTranslateX = if (full) {
                (uiBinding.codecToolRight.width + uiBinding.codecToolRight.marginEnd) * 1.0f
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.codecToolRight, "translationX", codecRightTranslateX)

            val screenShortcutTranslateX = if (full) {
                (uiBinding.screenShortcutView.width + uiBinding.screenShortcutView.marginEnd) * 1.0f
            } else {
                0f
            }
            AnimateUtil.animateProperty(uiBinding.screenShortcutView, "translationX", screenShortcutTranslateX)

            AnimateUtil.animateAlphaProperty(uiBinding.functionSuspensionView, full)
        })

    }

    private fun refreshFunctionViewType(functionType: FunctionViewType) {
        if (functionType == FunctionViewType.Bar) { //工具栏样式
            functionBarVm.updateFunctionBarLD(FunctionBarState.Unfolded)
            uiBinding.ivBarCollapse.isVisible = true
            uiBinding.functionSuspensionView.isVisible = false
            uiBinding.codecTabView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = resources.getDimensionPixelOffset(R.dimen.common_15dp)
            }
        } else { //悬浮球样式
            functionBarVm.updateFunctionBarLD(FunctionBarState.Folded)
            uiBinding.ivBarCollapse.isVisible = false
            uiBinding.functionSuspensionView.isVisible = true
            uiBinding.codecTabView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = resources.getDimensionPixelOffset(R.dimen.common_102dp)
            }
        }
    }

    private fun refreshAttitudeBall() {
        val state = ScreenStateManager.getInstance().getPageLocationState()
        uiBinding.attitudeBall.isVisible = DeviceUtils.isSingleControl()
        if (state.deviceListShow) {
            uiBinding.attitudeBall.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = resources.getDimensionPixelOffset(R.dimen.common_variety_device_list_height)
            }
        } else {
            uiBinding.attitudeBall.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = 0
            }
        }
    }

    private fun refreshCodecToolLeft() {
        val state = ScreenStateManager.getInstance().getPageLocationState()
        if (!state.isMapWidgetFullScreen() &&
            DeviceUtils.isSingleControl() &&
            DeviceUtils.isMainRC()
        ) {
            uiBinding.flGimbal.visibility = View.VISIBLE
        } else {
            uiBinding.flGimbal.visibility = View.INVISIBLE
        }
    }

    private fun refreshCodecToolRight() {
        val state = ScreenStateManager.getInstance().getPageLocationState()
        if (!state.isMapWidgetFullScreen() && DeviceUtils.isMainRC()) {
            uiBinding.codecToolRight.updateLensInfo(state.getAllDroneSet().firstOrNull(), state.isShowLinkZoom())
            uiBinding.codecToolRight.visibility = View.VISIBLE
        } else {
            uiBinding.codecToolRight.visibility = View.GONE
        }
    }

    private fun refreshCodecTab() {
        val state = ScreenStateManager.getInstance().getPageLocationState()
        if (state.isCodecWidgetFullScreen()) {
            uiBinding.codecTabView.visibility = View.VISIBLE
            uiBinding.codecTabView.updateDronePageWidgetList(
                ScreenStateManager.getInstance().getCodecScreenItems(this, state.fullScreenType)
            )
        } else {
            uiBinding.codecTabView.visibility = View.INVISIBLE
            uiBinding.codecTabView.updateDronePageWidgetList(
                ScreenStateManager.getInstance().getCodecScreenItems(this, state.getFirstCodecWidget() ?: AircraftScreenItem.Empty)
            )
        }
    }

    override fun onDroneChangedListener(connected: Boolean, drone: IAutelDroneDevice) {
        super.onDroneChangedListener(connected, drone)
        refreshCodecToolLeft()

        if (connected) {
            Log.e("LocationTracker", "Дрон подключен, запускаем подписки")
            trackerManager.setupSubscriptions()
        } else {
            Log.e("LocationTracker", "Дрон отключен")
        }
    }

    override fun onControlChange(mode: ControlMode, droneList: List<IAutelDroneDevice>) {
        super.onControlChange(mode, droneList)
        refreshCodecToolLeft()
        refreshAttitudeBall()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ScreenStateManager.getInstance().checkInAircraftScreenAnimTime()) {
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        //回到图传界面，切回正常速度模式
        DeviceUtils.singleControlDrone()?.let { device ->
            SpeedModeManager.changeToNormalSpeed(device, 3) {
                Log.e("MainActivity", "MainActivity onResume change speed normal:$it")
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun checkAndRequestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + packageName)
                startActivity(intent)
            }
        }
    }

    private fun showMapKeyDialog() {
        val messageCN = "地图加载失败，请在 MapWidget 的 fun initMap() { val MAPTILER_KEY = \"\" } 处，替换为你自己申请의 MapTiler API Key，否则地图无法显示。"
        val messageEN = "Map loading failed. Please go to MapWidget's fun initMap() { val MAPTILER_KEY = \"\" } and replace it with your own MapTiler API Key, otherwise the map cannot be displayed."
        val isChinese = java.util.Locale.getDefault().language == "zh"
        val message = if (isChinese) messageCN else messageEN
        val title = if (isChinese) "提示" else "Notice"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (isChinese) "确定" else "OK", null)
            .show()
    }

    private fun updateTrackerUI() {
        // 1. Координаты дрона
        val dronePos = if (trackerManager.droneLat != 0.0) {
            "${"%.6f".format(trackerManager.droneLat)}, ${"%.6f".format(trackerManager.droneLon)}"
        } else "SEARCHING..."

        uiBinding.root.findViewById<TextView>(R.id.tv_tracker_drone_pos)?.text = "Drone: $dronePos"

        // ИСПРАВЛЕНО: Добавили форматирование %.1f для лазера, чтобы не было длинного хвоста из цифр
        val laserDistFormatted = trackerManager.laserDistance?.let { "${"%.1f".format(it)}m" } ?: "0.0m"
        val laserInfo = if (trackerManager.isLaserValid) "LRF: $laserDistFormatted" else "LRF: OFF"

        val sensorDetails = "Pitch: ${"%.1f".format(trackerManager.finalPitch)}° | Bear: ${"%.1f".format(trackerManager.finalBearing)}°\n" +
                "Alt: ${"%.1f".format(trackerManager.droneAlt)}m | $laserInfo"

        uiBinding.root.findViewById<TextView>(R.id.tv_tracker_details)?.text = sensorDetails
        uiBinding.root.findViewById<TextView>(R.id.tv_tracker_target_pos)?.text = trackerManager.getTargetReport()

        // 2. Дебаг панель
        uiBinding.root.findViewById<TextView>(R.id.tv_debug_laser_status)?.text = "Keys: ${trackerManager.debugStatus}"

        // Здесь уже будет отформатированная строка из менеджера (где мы написали "%.1f" выше)
        uiBinding.root.findViewById<TextView>(R.id.tv_debug_raw_data)?.text = trackerManager.rawLaserData
    }

    private fun startTrackerUpdateLoop() {
        val updateRunnable = object : Runnable {
            override fun run() {
                updateTrackerUI()
                uiBinding.root.postDelayed(this, 500)
            }
        }
        uiBinding.root.postDelayed(updateRunnable, 1000)
    }

    // логика для солдата
    private fun saveSettings(protocol: Int, r181Addr: Int, ipAddr: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt("protocol_type", protocol)
            putInt("target_address", r181Addr)
            putInt("ip_address", ipAddr)
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentProtocolType = prefs.getInt("protocol_type", 1)
        currentR181Address = prefs.getInt("target_address", 111111)
        currentIpAddress = prefs.getInt("ip_address", 0)

        // Синхронизация с менеджером
        soldatManager.protocolType = currentProtocolType
        soldatManager.r181Address = currentR181Address
        soldatManager.ipAddress = currentIpAddress

        updateAddressButtonUI()
    }

    private fun updateAddressButtonUI() {
        val btnSetAddress = uiBinding.root.findViewById<Button>(R.id.btn_set_address)
        val prefix = if (currentProtocolType == 1) "R: " else "IP: "
        val activeAddr = if (currentProtocolType == 1) currentR181Address else currentIpAddress
        btnSetAddress?.text = "$prefix$activeAddr"
    }

    private fun showTargetAddressDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Настройка протокола и адреса")

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        // 1. Протокол
        val rgProtocol = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val rbRadio = RadioButton(this).apply {
            text = "Радио (R181)"; id = View.generateViewId()
        }
        val rbNet =RadioButton(this).apply {
            text = "Сеть (IP)"; id = View.generateViewId()
        }

        rgProtocol.addView(rbRadio)
        rgProtocol.addView(rbNet)
        mainLayout.addView(rgProtocol)

        if (currentProtocolType == 1) rbRadio.isChecked = true else rbNet.isChecked = true

        // 2. Поля ввода
        val inputRadio = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentR181Address.toString())
            hint = "Radio ID"
        }
        val inputIp = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentIpAddress.toString())
            hint = "Network ID / IP"
        }

        mainLayout.addView(TextView(this).apply { text = "Radio ID:"; setPadding(0, 20, 0, 0) })
        mainLayout.addView(inputRadio)
        mainLayout.addView(TextView(this).apply { text = "Network ID / IP:"; setPadding(0, 20, 0, 0) })
        mainLayout.addView(inputIp)

        builder.setView(mainLayout)
        builder.setPositiveButton("ОК") { _, _ ->
            try {
                currentProtocolType = if (rbRadio.isChecked) 1 else 2
                currentR181Address = inputRadio.text.toString().toInt()
                currentIpAddress = inputIp.text.toString().toInt()

                soldatManager.protocolType = currentProtocolType
                soldatManager.r181Address = currentR181Address
                soldatManager.ipAddress = currentIpAddress

                saveSettings(currentProtocolType, currentR181Address, currentIpAddress)
                updateAddressButtonUI()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка ввода", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun getCoordsFromReport(): Pair<Double, Double>? {
        val text = uiBinding.root.findViewById<TextView>(R.id.tv_fixed_target_data)?.text?.toString() ?: ""
        return try {
            val coordLine = text.split("\n").findLast { it.contains(",") } ?: return null
            val parts = coordLine.replace(" ", "").split(",")
            if (parts.size >= 2) {
                val lat = parts[0].replace(Regex("[^0-9.\\-]"), "").toDouble()
                val lon = parts[1].replace(Regex("[^0-9.\\-]"), "").toDouble()
                Pair(lat, lon)
            } else null
        } catch (e: Exception) { null }
    }
}