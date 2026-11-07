package com.r50v.monitor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

@Composable
fun WifiMonitorScreen() {
    var host by remember { mutableStateOf("192.168.0.1") }
    var port by remember { mutableStateOf("8080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Configura CCAPI en la cámara y conecta el teléfono a su Wi‑Fi") }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var streamJob by remember { mutableStateOf<Job?>(null) }

    Column(Modifier.fillMaxSize().background(Color(0xFF080B0D)).padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text("MONITOR INALÁMBRICO", color = Color.White, fontSize = 18.sp)
        Text("Canon CCAPI · Wi‑Fi · Live View JPEG", color = Color(0xFF95A1A3), fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(host, { host = it }, Modifier.weight(1f), label = { Text("IP de cámara") }, singleLine = true)
            OutlinedTextField(port, { port = it }, Modifier.width(110.dp), label = { Text("Puerto HTTP") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(username, { username = it }, Modifier.weight(1f), label = { Text("Usuario (si activaste autenticación)") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.weight(1f), label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !running, onClick = {
                val address = host.trim()
                val p = port.toIntOrNull() ?: 8080
                if (address.isBlank() || p !in 1..65535) { status = "Revisa la IP y el puerto" }
                else {
                    running = true
                    status = "Conectando a CCAPI…"
                    streamJob = scope.launch {
                        try {
                            CcapiLiveView(address, p, username, password).stream { bmp ->
                                frame = bmp
                                status = "Live View conectado · CCAPI"
                            }
                        } catch (e: Exception) {
                            status = "Error: ${e.message ?: "no se pudo conectar"}"
                        } finally { running = false }
                    }
                }
            }) { Text("Conectar y ver") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(enabled = running, onClick = { streamJob?.cancel(); streamJob = null; running = false; status = "Desconectado" }) { Text("Desconectar") }
            Spacer(Modifier.width(12.dp)); Text(status, color = Color(0xFFB8C2C4), fontSize = 10.sp, maxLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF030506), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            val img = frame
            if (img != null) Image(img.asImageBitmap(), contentDescription = "Live View Canon", modifier = Modifier.fillMaxSize())
            else Text("Sin señal Wi‑Fi", color = Color(0xFF879397))
        }
        Spacer(Modifier.height(6.dp))
        Text("La conexión directa usa CCAPI oficial. El Live View por Wi‑Fi suele tener más latencia y menor frecuencia que HDMI.", color = Color(0xFF879397), fontSize = 10.sp)
        Text("En la cámara: activa Camera Control API, crea conexión Wi‑Fi y configura HTTP/puerto. Conecta el móvil a esa red y escribe la IP/puerto mostrados. Si usas autenticación, introduce las credenciales.", color = Color(0xFF879397), fontSize = 10.sp)
    }
}

private class CcapiLiveView(private val host: String, private val port: Int, username: String, password: String) {
    private val auth = if (username.isNotBlank()) "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP) else null
    private fun request(path: String, method: String, body: String? = null): ByteArray = (URL("http://$host:$port$path").openConnection() as HttpURLConnection).run {
        requestMethod = method; connectTimeout = 4500; readTimeout = 4500; useCaches = false
        auth?.let { setRequestProperty("Authorization", it) }
        if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toByteArray()) } }
        val code = responseCode
        if (code !in 200..299) { val detail = runCatching { errorStream?.bufferedReader()?.readText()?.take(180) }.getOrNull().orEmpty(); disconnect(); error("HTTP $code $detail") }
        val bytes = inputStream.use { it.readBytes() }; disconnect(); bytes
    }

    suspend fun stream(onFrame: suspend (Bitmap) -> Unit) = withContext(Dispatchers.IO) {
        var version: String? = null
        for (v in listOf("ver140", "ver130", "ver120", "ver110", "ver100")) {
            val code = runCatching { request("/ccapi/$v", "GET"); v }.getOrNull()
            if (code != null) { version = code; break }
        }
        val v = version ?: error("No responde CCAPI. Comprueba IP, puerto, Wi‑Fi y Camera Control API")
        val base = "/ccapi/$v/shooting/liveview"
        request(base, "POST", """{"cameradisplay":"on","liveviewsize":"medium"}""")
        try {
            while (coroutineContext.isActive) {
                val bytes = request("$base/flipdetail?kind=image", "GET")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("CCAPI no devolvió una imagen JPEG válida")
                withContext(Dispatchers.Main) { onFrame(bitmap) }
                delay(350)
            }
        } finally {
            runCatching { request(base, "POST", """{"cameradisplay":"on","liveviewsize":"off"}""") }
        }
    }
}


