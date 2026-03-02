package com.external.uxdemo.soldatServiceConnection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import by.agat.soldat.android.service.ServiceAPI

class SoldatServiceConnector(private val context: Context) {
    private var soldatService: ServiceAPI? = null
    private var isBound = false
    private lateinit var handler: Handler
    private lateinit var retryConnectionRunnable: Runnable

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            soldatService = SoldatServiceAPI.Stub.asInterface(service)
            soldatService = ServiceAPI.Stub.asInterface(service)
            isBound = true
            println("onServiceConnected onServiceConnected onServiceConnected")
            println("soldatService = $soldatService")
            handler.removeCallbacks(retryConnectionRunnable) // Остановите попытки подключения
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            soldatService = null
            println("onServiceDisconnected")
            isBound = false
        }
    }

    fun bind() {
        val intent = Intent()
        intent.component = ComponentName("by.agat.soldat", "by.agat.soldat.android.service.MainService")
//        intent.component = ComponentName("by.zgrunapp.testapplication1", "by.zgrunapp.testapplication1.Service.SoldatService")
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        startRetryConnectionHandler()
    }

    fun unbind() {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
            println("unbind isBound")
        }
    }

    private fun startRetryConnectionHandler() {
        handler = Handler(Looper.getMainLooper())
        retryConnectionRunnable = Runnable {
            println("startRetryConnectionHandler")
            bind() // Попробуйте снова подключиться
        }
        handler.postDelayed(retryConnectionRunnable, 5000) // Интервал повторной попытки
    }


    // Метод для вызова методов сервиса
    fun <T> invokeServiceMethod(action: (ServiceAPI) -> T?): T? {
//    fun <T> invokeServiceMethod(action: (SoldatServiceAPI) -> T?): T? {
        return if (isBound && soldatService != null) {
            println("isBound && soldatService != null")
            action(soldatService!!)
        } else {
            println("!!!!!!!!!!!!!isBound && soldatService != null")
            null
        }
    }
}
