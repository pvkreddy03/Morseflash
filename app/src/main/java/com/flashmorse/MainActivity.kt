
package com.flashmorse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.flashmorse.core.LightDecoder
import com.flashmorse.core.Morse
import com.flashmorse.hardware.CameraMorseAnalyzer
import com.flashmorse.hardware.TorchSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MainScreen() } }
    }
}

@Composable
fun MainScreen() {
    var page by remember { mutableStateOf("Send") }
    Scaffold(
        topBar = { Text("Morseflash · Offline", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall) },
        bottomBar = {
            NavigationBar {
                listOf("Send", "Receive", "Learn", "SOS").forEach { destination ->
                    NavigationBarItem(
                        selected = destination == page,
                        onClick = { page = destination },
                        label = { Text(destination) },
                        icon = { Text(when (destination) { "Send" -> "↑"; "Receive" -> "◎"; "Learn" -> "?"; else -> "!" }) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                "Send" -> SendPage(false)
                "SOS" -> SendPage(true)
                "Receive" -> ReceivePage()
                else -> LearnPage()
            }
        }
    }
}

@Composable
fun SendPage(sos: Boolean) {
    val context = LocalContext.current
    val torch = remember(context) { TorchSender(context) }
    val scope = rememberCoroutineScope()
    var message by remember(sos) { mutableStateOf(if (sos) "SOS" else "HELLO") }
    var speed by remember { mutableFloatStateOf(5f) }
    var running by remember { mutableStateOf(false) }
    var lit by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(torch) {
        onDispose { job?.cancel(); torch.off() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(if (sos) "Emergency SOS light" else "Text to flashlight", style = MaterialTheme.typography.headlineSmall)
        if (sos) Text("Supplementary signal only; not a substitute for emergency services.")
        OutlinedTextField(
            value = message,
            onValueChange = { if (!sos && !running) message = it.take(160) },
            enabled = !sos && !running,
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Morse code: " + Morse.encode(message))
        Text("Speed: " + speed.toInt() + " WPM")
        Slider(value = speed, onValueChange = { speed = it },
            valueRange = 3f..15f, enabled = !running)
        Text("Estimated transmission: " + (Morse.durationMs(message, speed.toInt()) / 1000) + " seconds")
        Text(if (lit) "●  LIGHT ON" else "○  LIGHT OFF", style = MaterialTheme.typography.headlineMedium)
        if (!torch.available) Text("No compatible rear flashlight was detected on this device.")
        Button(
            enabled = torch.available && (running || Morse.encode(message).isNotBlank()),
            onClick = {
                if (running) {
                    job?.cancel()
                    torch.off()
                    lit = false
                    running = false
                } else {
                    status = ""
                    running = true
                    job = scope.launch {
                        try { torch.send(message, speed.toInt()) { pulse -> lit = pulse } }
                        catch (error: Exception) {
                            if (error !is CancellationException) status = error.message ?: "Torch error"
                        } finally { torch.off(); lit = false; running = false }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (running) "STOP FLASHING" else "START FLASHING") }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
        Text("Aim your rear flashlight toward a receiving phone. Start at 2–5 metres in a dark environment.")
    }
}

@Composable
fun ReceivePage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val decoder = remember { LightDecoder() }
    val worker = remember { Executors.newSingleThreadExecutor() }
    var active by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(false) }
    var brightness by remember { mutableIntStateOf(0) }
    var received by remember { mutableStateOf("") }
    var symbols by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var speed by remember { mutableFloatStateOf(5f) }
    DisposableEffect(Unit) { onDispose { worker.shutdown() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Camera Morse receiver", style = MaterialTheme.typography.headlineSmall)
        Text("Experimental receiver. Place the transmitting light in the centre of the camera frame.")
        if (!allowed) {
            Button(onClick = { request.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
        } else {
            if (active) {
                val view = remember(context) { PreviewView(context) }
                DisposableEffect(view, lifecycleOwner) {
                    val future = ProcessCameraProvider.getInstance(context)
                    var provider: ProcessCameraProvider? = null
                    var disposed = false
                    val listener = Runnable {
                        if (!disposed) {
                            try {
                                provider = future.get()
                                val preview = Preview.Builder().build()
                                preview.surfaceProvider = view.surfaceProvider
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(worker, CameraMorseAnalyzer(decoder) { on, text, partial, level ->
                                    // Compose snapshot state updates must be dispatched on main thread.
                                    ContextCompat.getMainExecutor(context).execute {
                                        detected = on
                                        brightness = level
                                        received = text
                                        symbols = partial
                                    }
                                })
                                provider?.unbindAll()
                                provider?.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                                )
                            } catch (e: Exception) { error = e.message ?: "Camera open failed" }
                        }
                    }
                    future.addListener(listener, ContextCompat.getMainExecutor(context))
                    onDispose { disposed = true; provider?.unbindAll() }
                }
                AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(250.dp))
                Text("Centre camera on the flashing light source")
            }
            Button(onClick = {
                if (active) {
                    active = false
                    decoder.finish()
                    received = decoder.text
                    detected = false
                } else {
                    decoder.reset()
                    decoder.dotMs = 1200L / speed.toInt()
                    received = ""
                    symbols = ""
                    error = ""
                    active = true
                }
            }) { Text(if (active) "STOP RECEIVING" else "START RECEIVING") }
            OutlinedButton(onClick = {
                decoder.reset()
                received = ""
                symbols = ""
            }) { Text("Clear received message") }
            Text("Expected speed: " + speed.toInt() + " WPM")
            Slider(speed, onValueChange = { speed = it }, valueRange = 3f..15f, enabled = !active)
            Text("Light: " + (if (detected) "ON" else "OFF") + " · intensity: " + brightness)
            Text("Incoming dots/dashes: " + symbols)
            Text("Decoded text: " + received.ifBlank { "Waiting for signals..." },
                style = MaterialTheme.typography.titleLarge)
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Text("Actual reception accuracy and range are not yet verified on physical phones.")
    }
}

@Composable
fun LearnPage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Learn International Morse", style = MaterialTheme.typography.headlineSmall)
        Text("Dots last one unit; dashes last three. Letter gaps last three units; word gaps last seven.")
        Morse.alphabet.forEach { (letter, pattern) ->
            Text(letter.toString() + "    " + pattern, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
