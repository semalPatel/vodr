package com.vodr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vodr.app.navigation.VodrNavHost
import com.vodr.app.ui.theme.VodrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VodrTheme {
                VodrNavHost()
            }
        }
    }
}
