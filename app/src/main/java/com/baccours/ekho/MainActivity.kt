package com.baccours.ekho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.baccours.ekho.ui.main.MainScreen
import com.baccours.ekho.ui.theme.EkhoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EkhoTheme {
                MainScreen()
            }
        }
    }
}
