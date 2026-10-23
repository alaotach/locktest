package com.example.locktest

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.locktest.ui.theme.LockTestTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class LockScreenActivity : ComponentActivity() {

    companion object {
        var isShowing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bypass lock screen and turn on display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setInheritShowWhenLocked(true)
        }

        window.setBackgroundDrawableResource(android.R.color.transparent)

        getSystemService(android.app.NotificationManager::class.java)
            .cancel(ScreenOnReceiver.OVERLAY_NOTIFICATION_ID)

        lifecycleScope.launch {
            LockscreenLifecycleTracker.finishSignal.collect {
                if (!isFinishing) finish()
            }
        }

        setContent {
            LockTestTheme {
                LockScreenOverlay(onDismiss = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isShowing = true
    }

    override fun onStop() {
        super.onStop()
        isShowing = false
    }
}

class DrawPath(val path: Path, val color: Color = Color.White, val width: Float = 12f)

@Composable
fun LockScreenOverlay(onDismiss: () -> Unit) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    val dismissThreshold = 300f

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDismissing) 2000f else dragOffsetY,
        animationSpec = tween(durationMillis = if (isDismissing) 350 else 0),
        finishedListener = { if (isDismissing) onDismiss() },
        label = "dismissOffset"
    )

    val alpha = if (isDismissing) 0f
    else (1f - (dragOffsetY / (dismissThreshold * 2f))).coerceIn(0.3f, 1f)

    // Drawing state
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPath by remember { mutableStateOf<DrawPath?>(null) }
    var pathUpdateTrigger by remember { mutableIntStateOf(0) } // forces recomposition during drag

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .offset { IntOffset(0, animatedOffset.roundToInt()) }
    ) {
        // Subtle bottom gradient so text is legible over any wallpaper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color(0x55000000))
                    )
                )
        )

        // ── Drawing Canvas ────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val newPath = Path().apply { moveTo(offset.x, offset.y) }
                            currentPath = DrawPath(newPath)
                            pathUpdateTrigger++
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPath?.path?.lineTo(change.position.x, change.position.y)
                            pathUpdateTrigger++
                        },
                        onDragEnd = {
                            currentPath?.let { paths.add(it) }
                            currentPath = null
                        },
                        onDragCancel = {
                            currentPath = null
                        }
                    )
                }
        ) {
            // Read pathUpdateTrigger to ensure recomposition when path changes
            val trigger = pathUpdateTrigger

            paths.forEach { drawPath ->
                drawPath(
                    path = drawPath.path,
                    color = drawPath.color,
                    style = Stroke(
                        width = drawPath.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            
            currentPath?.let { drawPath ->
                drawPath(
                    path = drawPath.path,
                    color = drawPath.color,
                    style = Stroke(
                        width = drawPath.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // ── ✕ close button — top right ────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 16.dp)
                .size(44.dp)
                .background(Color(0x55000000), CircleShape)
                .clickable { if (!isDismissing) isDismissing = true }
        ) {
            Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // ── Main content (make it ignore touches so we can draw over it) ──────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
        ) {
            Text(text = "🔒", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "You're on the lock screen",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Draw anywhere on the screen!",
                color = Color(0xCCFFFFFF),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        // ── Bottom swipe hint (Restricted area for swipe-down to dismiss) ─────
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp) // Only the bottom 120dp intercepts the swipe-down
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > dismissThreshold) isDismissing = true
                            else dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (!isDismissing) {
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            }
                        }
                    )
                }
        ) {
            Text(
                text = "↓  swipe down from here to dismiss",
                color = Color(0xAAFFFFFF),
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 60.dp)
            )
        }
    }
}
