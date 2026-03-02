package com.external.uxdemo.soldatServiceConnection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import by.agat.soldat.android.service.ServiceAPI

class SoldatServiceViewModel(application: Application) : AndroidViewModel(application) {

    private val serviceConnector = SoldatServiceConnector(application)

    init {
        serviceConnector.bind()
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnector.unbind()
    }

    // Метод для вызова методов сервиса
    fun <T> invokeServiceMethod(action: (ServiceAPI) -> T?): T? {
        return serviceConnector.invokeServiceMethod(action)
    }
}
