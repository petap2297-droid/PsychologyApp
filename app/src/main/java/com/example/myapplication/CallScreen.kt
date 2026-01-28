package com.example.myapplication.ui.screens
import androidx.compose.material.icons.filled.FlipCameraIos
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.myapplication.webrtc.SimpleWebRTCManager
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer
import androidx.compose.foundation.clickable

@Composable
fun CallScreen(
    callerName: String,
    remoteUserId: String,
    currentUserId: String,
    isIncomingCall: Boolean = false,
    isVideoCall: Boolean = false, // <--- НОВЫЙ ПАРАМЕТР
    onCallFinished: () -> Unit
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var callStatus by remember { mutableStateOf(if (isIncomingCall) (if(isVideoCall) "Входящий видеозвонок..." else "Входящий аудиозвонок...") else "Соединение...") }
    var callDuration by remember { mutableStateOf(0) }
    var isCallActive by remember { mutableStateOf(false) }
    var isMicOn by remember { mutableStateOf(true) }
    var isSpeakerOn by remember { mutableStateOf(isVideoCall) } // Видео - сразу динамик
    var isSwapped by remember { mutableStateOf(false) }
    val webRTCManager = remember {
        SimpleWebRTCManager(context, currentUserId, remoteUserId).apply {
            onCallEstablished = {
                callStatus = "Разговор"
                isCallActive = true
            }
            onCallEnded = {
                callStatus = "Звонок завершен"
                isCallActive = false
                onCallFinished()
            }
        }
    }

    val neededPermissions = if (isVideoCall) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            permissionsGranted = true // <--- СТАВИМ ФЛАГ
            webRTCManager.initialize(isVideoCall) // Передаем тип
            if (isIncomingCall) webRTCManager.playRingtone() else webRTCManager.startCall()
        } else {
            callStatus = "❌ Нет разрешений"
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = neededPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            permissionsGranted = true // <--- СТАВИМ ФЛАГ
            webRTCManager.initialize(isVideoCall) // Передаем тип
            if (isIncomingCall) webRTCManager.playRingtone() else webRTCManager.startCall()
        } else {
            permissionLauncher.launch(neededPermissions)
        }
    }

    DisposableEffect(Unit) {
        onDispose { webRTCManager.cleanup() }
    }

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            val startTime = System.currentTimeMillis()
            while (isCallActive) {
                callDuration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                delay(1000)
            }
        }
    }
    // Кнопка камеры
    CallButton(Icons.Default.SwitchCamera, Color.White, Color.Gray.copy(alpha = 0.7f), "Камера") {
        webRTCManager.switchCamera()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isVideoCall) Color.Black else Color(0xFF202124))
    ) {
        // === ЛОГИКА ОТОБРАЖЕНИЯ (Смена мест) ===
        if (isVideoCall && permissionsGranted) {
            // 1. БОЛЬШОЙ ЭКРАН (Фон)
            AndroidView(
                factory = { ctx -> SurfaceViewRenderer(ctx).apply {
                    // Если swap=true, тут показываем МЕНЯ (local), иначе СОБЕСЕДНИКА (remote)
                    webRTCManager.initSurfaceView(this, isLocal = isSwapped)
                } },
                modifier = Modifier.fillMaxSize(),
                // Важно обновлять при изменении флага
                update = { view -> webRTCManager.initSurfaceView(view, isLocal = isSwapped) }
            )

            // 2. МАЛЕНЬКИЙ ЭКРАН (Карточка)
            if (isCallActive || !isIncomingCall) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 16.dp)
                        .size(110.dp, 160.dp)
                        .clickable { isSwapped = !isSwapped }, // <--- Теперь ошибки не будет
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ){
                    AndroidView(
                        factory = { ctx -> SurfaceViewRenderer(ctx).apply {
                            setZOrderMediaOverlay(true)
                            // Наоборот: если swap=true, тут СОБЕСЕДНИК, иначе Я
                            webRTCManager.initSurfaceView(this, isLocal = !isSwapped)
                        } },
                        modifier = Modifier.fillMaxSize(),
                        update = { view -> webRTCManager.initSurfaceView(view, isLocal = !isSwapped) }
                    )
                }
            }
        } else {
            // --- АУДИО РЕЖИМ (Аватар + Волны) ---
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCallActive || !isIncomingCall) {
                        PulsatingCircle(0, Color(0xFF6A5AE0).copy(0.3f))
                        PulsatingCircle(1000, Color(0xFF6A5AE0).copy(0.3f))
                    }
                    Box(Modifier.size(120.dp).clip(CircleShape).background(Color(0xFF6A5AE0)), Alignment.Center) {
                        Text(callerName.take(1).uppercase(), fontSize = 48.sp, color = Color.White)
                    }
                }
            }
        }

        // === ОБЩИЙ ИНТЕРФЕЙС ===
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Верхняя инфа
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 64.dp)
            ) {
                if (!isCallActive) {
                    Text(callerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(callStatus, color = Color.White.copy(0.8f), fontSize = 18.sp)
                } else if (!isVideoCall) {
                    Spacer(Modifier.height(32.dp)) // Отступ для аудио режима
                    Text(callerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(formatTime(callDuration), color = Color.Green, fontSize = 18.sp)
                }
            }

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically // Выравнивание по центру!
            ) {
                if (isIncomingCall && !isCallActive) {
                    // === ВХОДЯЩИЙ ===
                    CallButton(Icons.Default.CallEnd, Color.White, Color.Red, "Отклонить") {
                        webRTCManager.endCall()
                        onCallFinished()
                    }
                    CallButton(if(isVideoCall) Icons.Default.Videocam else Icons.Default.Call, Color.White, Color.Green, "Принять") {
                        callStatus = "Подключение..."
                        webRTCManager.acceptCall()
                    }
                } else {
                    // === РАЗГОВОР ===

                    // 1. Микрофон
                    CallButton(if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff, Color.White, if (isMicOn) Color.Gray.copy(alpha = 0.5f) else Color.Red, "Микрофон") {
                        isMicOn = !isMicOn
                        webRTCManager.toggleMute(!isMicOn)
                    }

                    // 2. Завершить
                    CallButton(Icons.Default.CallEnd, Color.White, Color.Red, "Завершить", size = 72.dp) {
                        webRTCManager.endCall()
                        onCallFinished()
                    }

                    // 3. Камера ИЛИ Динамик
                    if (isVideoCall) {
                        // Если видео -> кнопка смены камеры
                        // Если FlipCameraIos не работает, используй SwitchCamera
                        CallButton(Icons.Default.FlipCameraIos, Color.White, Color.Gray.copy(alpha = 0.5f), "Камера") {
                            webRTCManager.switchCamera()
                        }
                    } else {
                        // Если аудио -> кнопка динамика
                        CallButton(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff, Color.White, if (isSpeakerOn) Color(0xFF6A5AE0) else Color.Gray.copy(alpha = 0.5f), "Динамик") {
                            isSpeakerOn = !isSpeakerOn
                            webRTCManager.toggleSpeaker(isSpeakerOn)
                        }
                    }
                }
            }
        }
    }
}

// (PulsatingCircle и CallButton оставить как были, они есть в твоем коде)
@Composable
fun PulsatingCircle(delay: Int, color: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = delay), RepeatMode.Restart)
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = delay), RepeatMode.Restart)
    )
    Box(Modifier.size(120.dp).scale(scale).clip(CircleShape).background(color.copy(alpha)))
}

@Composable
fun CallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bgColor: Color, label: String, size: androidx.compose.ui.unit.Dp = 56.dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick, Modifier.size(size).background(bgColor, CircleShape)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

private fun formatTime(seconds: Int) = String.format("%02d:%02d", seconds / 60, seconds % 60)
