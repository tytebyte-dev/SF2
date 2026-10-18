package com.tytebyte.sf2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tytebyte.sf2.ui.SF2App
import com.tytebyte.sf2.ui.SF2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SF2Theme { SF2App() }
        }
    }
}
