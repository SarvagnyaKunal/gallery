package com.laptop.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var controller: ServerController
    private val hasPermission = mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasPermission.value = granted.values.all { it }
        if (hasPermission.value) loadCount()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = GalleryAppState.controller(applicationContext)

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        hasPermission.value = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (hasPermission.value) loadCount() else requestPermission.launch(perms)

        setContent {
            GalleryTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen(controller, hasPermission.value) { requestPermission.launch(perms) }
                }
            }
        }
    }

    private fun loadCount() {
        lifecycleScope.launch(Dispatchers.IO) { controller.refreshCount() }
    }

}

@Composable
private fun HomeScreen(
    controller: ServerController,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val status = controller.status
    val accent = Color(0xFF3B82F6)

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Laptop Gallery", fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text("Share your photos on this Wi-Fi", fontSize = 14.sp,
            color = Color(0xFF8A8A92))

        Spacer(Modifier.height(36.dp))

        val (label, dot) = when (status) {
            ServerStatus.STOPPED -> "Not sharing" to Color(0xFF555560)
            ServerStatus.WAITING -> "Waiting for laptop…" to Color(0xFFEAB308)
            ServerStatus.CONNECTED -> "Connected" to Color(0xFF22C55E)
        }
        StatusRow(label, dot)

        Spacer(Modifier.height(10.dp))
        Text("${controller.photoCount} photos", fontSize = 14.sp, color = Color(0xFF8A8A92))

        Spacer(Modifier.height(28.dp))

        if (status != ServerStatus.STOPPED) {
            ConnectionCard(
                ip = controller.ip,
                port = controller.port,
                otp = controller.otp
            )
            Spacer(Modifier.height(28.dp))
        }

        if (!hasPermission) {
            Text("Photo access is needed to share.", color = Color(0xFFF87171), fontSize = 13.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text("Grant access")
            }
        } else {
            Button(
                onClick = { controller.toggle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == ServerStatus.STOPPED) accent else Color(0xFF2C2C32)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (status == ServerStatus.STOPPED) "Start Sharing" else "Stop Sharing",
                    fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
            }
            controller.error?.let { msg ->
                Spacer(Modifier.height(14.dp))
                Text(msg, color = Color(0xFFF87171), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(dot) }
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun ConnectionCard(ip: String?, port: Int, otp: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("On your laptop, enter", fontSize = 12.sp, color = Color(0xFF8A8A92))
            Spacer(Modifier.height(8.dp))
            Text(
                if (ip != null) "$ip:$port" else "Wi-Fi not detected",
                fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(18.dp))
            Text("Pairing code", fontSize = 12.sp, color = Color(0xFF8A8A92))
            Spacer(Modifier.height(6.dp))
            Text(
                otp, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, letterSpacing = 8.sp,
                color = Color(0xFF3B82F6)
            )
        }
    }
}
