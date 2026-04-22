package com.todo.dailyroutine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.todo.dailyroutine.ui.DailyRoutineAppContent
import com.todo.dailyroutine.ui.theme.FlowOSTheme
import com.todo.dailyroutine.util.AppLockManager

class MainActivity : FragmentActivity() {
    private var isUnlocked by mutableStateOf(false)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        
        setContent {
            FlowOSTheme {
                if (isUnlocked) {
                    DailyRoutineAppContent()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0B0B0F))
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAppLock()
    }

    private fun checkAppLock() {
        val app = application as DailyRoutineApp
        val sessionManager = app.container.sessionManager
        
        if (sessionManager.isAppLockEnabled() && sessionManager.isLoggedIn()) {
            val lockManager = AppLockManager(this)
            lockManager.showAppLock(
                onSuccess = { isUnlocked = true },
                onError = {
                    // Locked until success
                }
            )
        } else {
            isUnlocked = true
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
