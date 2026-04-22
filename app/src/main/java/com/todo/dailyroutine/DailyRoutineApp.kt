package com.todo.dailyroutine

import android.app.Application
import com.todo.dailyroutine.core.AppContainer

class DailyRoutineApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
