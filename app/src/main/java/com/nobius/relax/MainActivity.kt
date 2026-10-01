package com.nobius.relax

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.spherical.SphericalGLSurfaceView
import kotlinx.coroutines.delay
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setContent { NobiusScreen() }
    }
}

private data class HapticCue(
    val timeMs: Long,
    val kind: String,
    val amplitude: Int,
    val durationMs: Int
)

@Composable
private fun NobiusScreen() {
    val context = LocalContext.current
    var intensity by remember { mutableFloatStateOf(0.35f) }
    var focusMode by remember { mutableStateOf(false) }
    var showDiscovery by remember { mutableStateOf(false) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }

    val videoId = remember(context) {
        context.resources.getIdentifier("nobius_360", "raw", context.packageName)
    }
    val audioId = remember(context) {
        context.resources.getIdentifier("nobius_ambisonic", "raw", context.packageName)
    }
    val hasMedia = videoId != 0 || audioId != 0
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
                .build()
            setAudioAttributes(audioAttributes, true)
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            volume = intensity
            val sourceId = if (videoId != 0) videoId else audioId
            if (sourceId != 0) {
                setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$sourceId"))
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(focusMode) {
        if (focusMode) {
            delay(30_000)
            focusMode = false
        }
    }

    // The spherical Media3 surface owns head tracking during video playback.
    // This sensor-backed orientation also gently shifts the generated fallback scene.
    DisposableEffect(context, videoId) {
        if (videoId == 0) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val listener = object : SensorEventListener {
                private val matrix = FloatArray(9)
                private val orientation = FloatArray(3)
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, orientation)
                    yaw = orientation[0]
                    pitch = orientation[1]
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { manager.unregisterListener(listener) }
        } else onDispose { }
    }

    val cues = remember(context) {
        runCatching {
            val source = context.assets.open("haptics.json").bufferedReader().use { it.readText() }
            val json = JSONArray(source)
            (0 until json.length()).map { i ->
                json.getJSONObject(i).let {
                    HapticCue(it.getLong("timeMs"), it.getString("kind"), it.getInt("amplitude"), it.getInt("durationMs"))
                }
            }
        }.getOrDefault(emptyList())
    }
    LaunchedEffect(player, intensity, cues) {
        player.volume = intensity
        var previousPosition = 0L
        var fired = mutableSetOf<Long>()
        while (true) {
            delay(100)
            val now = player.currentPosition.coerceAtLeast(0L)
            if (now + 700 < previousPosition) fired.clear()
            cues.firstOrNull { it.timeMs <= now && it.timeMs > previousPosition && it.timeMs !in fired }?.let { cue ->
                fired.add(cue.timeMs)
                if (intensity > 0.08f) pulse(context, cue, intensity)
            }
            previousPosition = now
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF100F28))) {
        if (videoId != 0) {
            AndroidView(
                factory = { viewContext ->
                    SphericalGLSurfaceView(viewContext).also { player.setVideoSurfaceView(it) }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AlienLandscape(
                yaw = yaw,
                pitch = pitch,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            yaw += drag.x / size.width * 2.2f
                            pitch = (pitch + drag.y / size.height).coerceIn(-0.8f, 0.8f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { showDiscovery = !showDiscovery }
                    }
            )
        }

        if (!focusMode) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Explore, null, tint = Color(0xFFB7F3D2), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("NOBIUS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                        Text("A quiet world, waiting", color = Color(0xFFCAD2DC), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = Color(0x55232A42),
                        onClick = { focusMode = true }
                    ) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SelfImprovement, null, tint = Color(0xFFD7E9DF), modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Focus · 30 sec", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasMedia) {
                        IconButton(
                            onClick = { if (player.isPlaying) player.pause() else player.play() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x66343B53))
                        ) {
                            Icon(
                                if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (player.isPlaying) "Pause" else "Play",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    AnimatedVisibility(visible = showDiscovery) {
                        DiscoveryCard()
                    }
                    if (showDiscovery) Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xB91C2032),
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Spa, null, tint = Color(0xFFB7F3D2), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Nobius Grove", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Rounded.Headphones, null, tint = Color(0xFFCCD5E0), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (hasMedia) "Spatial mix" else "Scene preview", color = Color(0xFFCCD5E0), fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    videoId != 0 -> "Turn gently to explore · tap a glow to discover"
                                    audioId != 0 -> "Breathe with the sound · drag to look around"
                                    else -> "Scene preview · add Nobius media for sound and timed haptics"
                                },
                                color = Color(0xFFB9C2D0), fontSize = 12.sp
                            )
                            Spacer(Modifier.height(7.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Quiet", color = Color(0xFFAAB4C3), fontSize = 11.sp)
                                Slider(
                                    value = intensity,
                                    onValueChange = { intensity = it },
                                    modifier = Modifier.weight(1f).height(32.dp).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFB7F3D2),
                                        activeTrackColor = Color(0xFF77C9A2),
                                        inactiveTrackColor = Color(0xFF4A5262)
                                    )
                                )
                                Text("Lively", color = Color(0xFFAAB4C3), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pulse(context: Context, cue: HapticCue, intensity: Float) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (!vibrator.hasVibrator()) return
    val amplitude = (cue.amplitude * intensity).toInt().coerceIn(1, 255)
    val effect = VibrationEffect.createOneShot(cue.durationMs.toLong(), amplitude)
    vibrator.vibrate(effect)
}

@Composable
private fun DiscoveryCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xD9E9F0EB)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFD4EFE1)),
                contentAlignment = Alignment.Center
            ) {
                Text("✧", fontSize = 27.sp, color = Color(0xFF4F9A82))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Luminous Moss", color = Color(0xFF1C2C2A), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "It gathers the last light of the day and releases it slowly, like a breath.",
                    color = Color(0xFF4C5A58), fontSize = 12.sp, lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun AlienLandscape(yaw: Float, pitch: Float, modifier: Modifier = Modifier) {
    val slowTime by androidx.compose.runtime.rememberInfiniteFloat()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF39264E), Color(0xFF8D5260), Color(0xFFEE9A70)), startY = 0f, endY = h))
        val offset = (yaw * 95f).coerceIn(-150f, 150f)
        // Twin moons and a soft atmospheric halo.
        drawCircle(Color(0x55FFB7AE), radius = w * .19f, center = Offset(w * .72f - offset * .18f, h * .32f + pitch * 24f))
        drawCircle(Color(0xFFF1DCC8), radius = w * .105f, center = Offset(w * .71f - offset * .18f, h * .32f + pitch * 24f))
        drawCircle(Color(0xFFDBB8CC), radius = w * .052f, center = Offset(w * .31f - offset * .1f, h * .23f + pitch * 18f))
        drawCircle(Color(0x22D1FFC0), radius = w * .29f, center = Offset(w * .52f, h * .74f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

        val ridge = Path().apply {
            moveTo(0f, h * .62f)
            cubicTo(w * .2f, h * .49f, w * .36f, h * .67f, w * .53f, h * .56f)
            cubicTo(w * .7f, h * .47f, w * .83f, h * .62f, w, h * .52f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(ridge, Color(0xFF49324D))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF25323B), Color(0xFF111C26)), h * .53f, h), topLeft = Offset(0f, h * .53f), size = androidx.compose.ui.geometry.Size(w, h * .47f))

        // Silhouetted bioluminescent stems.
        for (i in 0..12) {
            val x = (i * w / 12f + offset * (0.18f + i % 3 * .05f)).mod(w + 40f) - 20f
            val base = h * (.96f - (i % 3) * .025f)
            val height = h * (.16f + (i % 4) * .035f)
            drawLine(Color(0xFF344B4B), Offset(x, base), Offset(x + sin(i * .7f + slowTime) * 7f, base - height), strokeWidth = 3f + i % 3)
            val glow = if (i % 2 == 0) Color(0xFF91F0C5) else Color(0xFFB79AF5)
            drawCircle(glow.copy(alpha = .72f), radius = 5f + i % 3, center = Offset(x, base - height))
            drawCircle(glow.copy(alpha = .2f), radius = 15f + i % 4, center = Offset(x, base - height))
        }
        // Three quiet, discoverable rings.
        val rings = listOf(Offset(w * .22f - offset * .08f, h * .57f), Offset(w * .77f - offset * .12f, h * .68f), Offset(w * .53f, h * .48f))
        rings.forEachIndexed { index, point ->
            val pulse = 1f + .1f * sin(slowTime + index)
            drawCircle(Color(0x558EF3D1), radius = 12f * pulse, center = point, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            drawCircle(Color(0x338EF3D1), radius = 20f * pulse, center = point, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        }
        // A few stars soften the open sky.
        for (i in 0..28) {
            val x = ((i * 97) % 997) / 997f * w
            val y = ((i * 61) % 499) / 499f * h * .47f
            drawCircle(Color.White.copy(alpha = .28f + .18f * sin(slowTime + i)), radius = 1.2f, center = Offset(x, y))
        }
    }
}

@androidx.compose.runtime.Composable
private fun androidx.compose.runtime.rememberInfiniteFloat(): androidx.compose.runtime.State<Float> {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "landscape")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(10_000, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "ambientMotion"
    )
}
