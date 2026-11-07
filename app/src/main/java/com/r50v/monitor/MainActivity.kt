package com.r50v.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val Bg = Color(0xFF080B0D)
private val Panel = Color(0xFF11171A)
private val Mint = Color(0xFF66E0B5)

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.CAMERA)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Bg, surface = Panel)) { R50App() } }
    }
}

@Composable
fun UsbMonitorApp() {
    val context = LocalContext.current
    var usbList by remember { mutableStateOf(uvcDevices(context)) }

    var extCameras by remember { mutableStateOf(externalCameraIds(context)) }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) { while (true) { usbList = uvcDevices(context); extCameras = externalCameraIds(context); if (usbList.isEmpty()) selected = null else if (selected !in extCameras) selected = extCameras.firstOrNull(); delay(1500) } }

    var lutName by remember { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val lines = reader.readLines()
                require(lines.any { it.trim().startsWith("LUT_3D_SIZE") }) { "Falta LUT_3D_SIZE" }
                val n = lines.first { it.trim().startsWith("LUT_3D_SIZE") }.trim().substringAfter("LUT_3D_SIZE").trim().toInt()
                require(n in 2..64)
                val entries = lines.count { line -> line.trim().split(Regex("\\s+")).let { it.size == 3 && it.all { v -> v.toFloatOrNull() != null } } }
                require(entries >= n * n * n) { "LUT incompleta: $entries / ${n*n*n}" }
            }
            lutName = uri.lastPathSegment?.substringAfterLast('/') ?: "LUT cargada"
        }.onFailure { lutName = "LUT no válida" }
    }
    var thirds by remember { mutableStateOf(true) }
    var zebra by remember { mutableStateOf(false) }
    var peaking by remember { mutableStateOf(false) }
    var falseColor by remember { mutableStateOf(false) }
    var histogram by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 22.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("R50 V  MONITOR", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text("USB · VÍDEO EXTERNO", color = Color(0xFF879397), fontSize = 10.sp, letterSpacing = 2.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(if (selected != null) Mint else Color(0xFFFFB454), RoundedCornerShape(50))); Spacer(Modifier.width(8.dp)); Text(if (selected != null) "SEÑAL EXTERNA" else "SIN SEÑAL", color = Color.White, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF050708), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    if (selected != null) CameraPreview(selected!!, Modifier.fillMaxSize())
                    if (selected == null) Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("—", color = Mint, fontSize = 34.sp); Spacer(Modifier.height(6.dp)); Text("Conecta una capturadora compatible", color = Color.White, fontSize = 14.sp); Text("USB-C · HDMI → UVC", color = Color(0xFF819093), fontSize = 11.sp) }
                    if (thirds) Canvas(Modifier.fillMaxSize().padding(10.dp)) { val sw = size.width / 3; val sh = size.height / 3; for (i in 1..2) { drawLine(Color.White.copy(alpha=.48f), androidx.compose.ui.geometry.Offset(sw*i,0f), androidx.compose.ui.geometry.Offset(sw*i,size.height), 1f); drawLine(Color.White.copy(alpha=.48f), androidx.compose.ui.geometry.Offset(0f,sh*i), androidx.compose.ui.geometry.Offset(size.width,sh*i), 1f) } }
                    if (zebra || peaking || falseColor) Text("HERRAMIENTAS DE ANÁLISIS: DISPONIBLES EN LA UI; PROCESADO DE IMAGEN PENDIENTE", Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color.Black.copy(alpha=.75f), RoundedCornerShape(5.dp)).padding(6.dp), color=Color(0xFFFFD180), fontSize=8.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("ENTRADA", color = Color(0xFF829093), fontSize = 9.sp, letterSpacing = 1.5.sp); Text("${usbList.size} USB · ${extCameras.size} cámara(s) externas Android", color = Color(0xFFAEB9BB), fontSize = 10.sp) }
                if (usbList.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(usbList) { d -> AssistChip(onClick = { selected = extCameras.firstOrNull() }, label = { Text(d, fontSize = 10.sp) }) } }
            }
            Column(Modifier.width(230.dp).fillMaxHeight().background(Panel, RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("HERRAMIENTAS", color = Color(0xFF9AA7A9), fontSize = 10.sp, letterSpacing = 1.6.sp)
                ToolToggle("Regla de tercios", thirds) { thirds = it }
                ToolToggle("Histograma", histogram) { histogram = it }
                ToolToggle("Zebras", zebra) { zebra = it }
                ToolToggle("Focus peaking", peaking) { peaking = it }
                ToolToggle("False color", falseColor) { falseColor = it }
                HorizontalDivider(color = Color(0xFF293235))
                Text("LUT 3D .CUBE", color = Color(0xFF9AA7A9), fontSize = 10.sp, letterSpacing = 1.4.sp)
                Text(lutName ?: "Ninguna seleccionada", color = Color.White, fontSize = 11.sp, maxLines = 1)
                OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Cargar LUT", fontSize = 11.sp) }
                Spacer(Modifier.weight(1f))
                Text("USB HOST", color = Color(0xFF9AA7A9), fontSize = 10.sp, letterSpacing = 1.4.sp)
                Text(when { usbList.isEmpty() -> "Sin capturadora UVC"; extCameras.isEmpty() -> "UVC detectada; Android no la expone a Camera2"; usbList.isNotEmpty() && extCameras.isNotEmpty() -> "UVC detectada · preview Camera2"; else -> "Dispositivo externo no identificado" }, color = Color.White, fontSize = 10.sp, lineHeight = 13.sp)
                OutlinedButton(onClick = { usbList = uvcDevices(context); extCameras = externalCameraIds(context); selected = if (usbList.isNotEmpty()) extCameras.firstOrNull() else null }, modifier = Modifier.fillMaxWidth()) { Text("Buscar dispositivos", fontSize = 10.sp) }
                Text("La captura UVC solo funciona si Android expone el dispositivo como cámara externa. Wi‑Fi CCAPI está en la pestaña correspondiente.", color = Color(0xFF879397), fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("RESOLUCIÓN · según cámara externa detectada", color = Color(0xFF879397), fontSize = 9.sp); Text("R50 V MONITOR  1.0", color = Color(0xFF586569), fontSize = 9.sp) }
    }
}

@Composable private fun ToolToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { FilterChip(selected = checked, onClick = { onChange(!checked) }, label = { Text(label, fontSize = 10.sp, maxLines = 1) }, modifier = Modifier.height(32.dp)) }

private fun uvcDevices(context: Context): List<String> = runCatching {
    val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    manager.deviceList.values.filter { device ->
        device.deviceClass == android.hardware.usb.UsbConstants.USB_CLASS_VIDEO ||
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_VIDEO }
    }.map { device ->
        val label = device.productName?.takeIf { it.isNotBlank() } ?: "Capturadora UVC"
        "$label (${device.vendorId.toString(16)}:${device.productId.toString(16)})"
    }
}.getOrDefault(emptyList())
private fun externalCameraIds(context: Context): List<String> = runCatching {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    manager.cameraIdList.filter { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL }
}.getOrDefault(emptyList())

@Composable private fun CameraPreview(cameraId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(modifier = modifier, factory = { ctx ->
        SurfaceView(ctx).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) { openExternalCamera(context, cameraId, holder.surface) }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }
                override fun surfaceDestroyed(holder: SurfaceHolder) { }
            })
        }
    })
}

private fun openExternalCamera(context: Context, id: String, surface: Surface) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    runCatching { manager.openCamera(id, object : android.hardware.camera2.CameraDevice.StateCallback() {
        override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
            val request = camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }.build()
            camera.createCaptureSession(listOf(surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) { runCatching { session.setRepeatingRequest(request, null, null) } }
                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) { camera.close() }
            }, null)
        }
        override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) { camera.close() }
        override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) { camera.close() }
    }, null) }
}








