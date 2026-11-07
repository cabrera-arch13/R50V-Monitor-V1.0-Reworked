package com.r50v.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun R50App() {
    var wifi by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color(0xFF080B0D))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { wifi = false }) {
                Text("HDMI / USB", color = if (!wifi) MaterialTheme.colorScheme.primary else Color.LightGray)
            }
            TextButton(onClick = { wifi = true }) {
                Text("Wi‑Fi Canon", color = if (wifi) MaterialTheme.colorScheme.primary else Color.LightGray)
            }
        }
        Box(Modifier.weight(1f)) {
            if (wifi) WifiMonitorScreen() else UsbMonitorApp()
        }
    }
}
