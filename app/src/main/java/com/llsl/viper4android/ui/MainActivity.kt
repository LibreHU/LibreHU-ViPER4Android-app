package com.llsl.viper4android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.llsl.viper4android.service.ViperService
import com.llsl.viper4android.ui.navigation.ViperNavigation
import com.llsl.viper4android.ui.theme.ViperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViperService.startService(this)
        // LibreHU: LibreHU Launcher's style (Modularer Infotainment-Baukasten, LHU-Link) and key clicks.
        com.llsl.viper4android.librehu.LauncherLook.start(this)
        enableEdgeToEdge()
        setContent {
            ViperTheme {
                ViperNavigation()
            }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        com.llsl.viper4android.librehu.KeyClick.onTouch(this, ev, com.llsl.viper4android.librehu.LauncherLook.clickSound)
        return super.dispatchTouchEvent(ev)
    }
}
