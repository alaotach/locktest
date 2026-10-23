package com.example.locktest

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.locktest.ui.theme.LockTestTheme

class MainActivity : ComponentActivity() {

    private var overlayGranted       by mutableStateOf(false)
    private var notifListenerOk      by mutableStateOf(false)
    private var notifPostGranted     by mutableStateOf(false)
    private var batteryOptimized     by mutableStateOf(false) // true = STILL optimized (bad)
    private var isXiaomi             by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LockScreenOverlay", "MainActivity.onCreate called - App has opened!")
        isXiaomi = Build.MANUFACTURER.lowercase().contains("xiaomi")
        enableEdgeToEdge()

        setContent {
            LockTestTheme {
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> notifPostGranted = granted }

                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Column(
                        modifier = Modifier
                            .padding(inner)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text("🔒", fontSize = 52.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Lock Screen Overlay", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Complete every step below. All are required on Xiaomi.",
                            fontSize = 13.sp, textAlign = TextAlign.Center,
                            color = Color.Gray, lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(20.dp))

                        // ── Step 1: Display over other apps ──────────────────────
                        PermCard(
                            step = "Step 1  ${if (overlayGranted) "✅" else "⚠️  Required"}",
                            title = "Display Over Other Apps",
                            desc = "Allows the overlay to launch over the lock screen.\n" +
                                    "Settings → Apps → LockTest → Display over other apps → ON",
                            btn = if (overlayGranted) null else "Open Settings",
                            urgent = !overlayGranted,
                            onClick = {
                                startActivity(Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                ))
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // ── Step 2: Battery optimisation ─────────────────────────
                        PermCard(
                            step = "Step 2  ${if (!batteryOptimized) "✅" else "⚠️  Required"}",
                            title = "Disable Battery Optimisation",
                            desc = "MIUI/HyperOS kills background services aggressively.\n" +
                                    "This WILL prevent the overlay from working if not disabled.",
                            btn = if (!batteryOptimized) null else "Open Settings",
                            urgent = batteryOptimized,
                            onClick = {
                                try {
                                    startActivity(Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:$packageName")
                                    ))
                                } catch (_: Exception) {
                                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // ── Step 3: Notification Listener ────────────────────────
                        PermCard(
                            step = "Step 3  ${if (notifListenerOk) "✅" else "✨  Recommended"}",
                            title = "Notification Access",
                            desc = "Keeps the app alive in the background via a system binding — " +
                                    "more reliable than the foreground service on MIUI.\n" +
                                    "Settings → Notification Access → LockTest → ON",
                            btn = if (notifListenerOk) null else "Open Settings",
                            urgent = false,
                            onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        )

                        if (isXiaomi) {
                            Spacer(Modifier.height(12.dp))
                            XiaomiCard(
                                onClick = {
                                    try {
                                        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                                        intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                                        intent.putExtra("extra_pkgname", packageName)
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:$packageName")
                                        })
                                    }
                                }
                            )
                        }

                        // ── POST_NOTIFICATIONS ────────────────────────────────────
                        if (!notifPostGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Spacer(Modifier.height(12.dp))
                            PermCard(
                                step = "Step ${if (isXiaomi) "5" else "4"}",
                                title = "Allow Notifications",
                                desc = "Needed for the fallback full-screen intent trigger.",
                                btn = "Grant",
                                urgent = false,
                                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── Status summary ────────────────────────────────────────
                        val coreReady = overlayGranted && !batteryOptimized
                        val allGood   = coreReady && notifListenerOk
                        Text(
                            text = when {
                                allGood   -> "✅  Fully optimised! Lock your phone and press\nthe power button to test."
                                coreReady -> "⚡  Core permissions granted. Grant Notification\nAccess for extra reliability."
                                else      -> "⚠️  Complete the required steps above."
                            },
                            fontSize = 14.sp, textAlign = TextAlign.Center,
                            color = when {
                                allGood   -> Color(0xFF2E7D32)
                                coreReady -> Color(0xFF1565C0)
                                else      -> Color(0xFFB71C1C)
                            },
                            lineHeight = 20.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                startActivity(Intent(this@MainActivity, LockScreenActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Preview overlay now") }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        manageServices()
    }

    private fun refreshState() {
        overlayGranted = Settings.canDrawOverlays(this)

        notifListenerOk = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )?.split(":")?.mapNotNull { ComponentName.unflattenFromString(it) }
            ?.any { it.packageName == packageName } ?: false

        notifPostGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        val pm = getSystemService(PowerManager::class.java)
        batteryOptimized = !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun manageServices() {
        Log.d("LockScreenOverlay", "manageServices() called. Forcing LockScreenService to start as a fallback against HyperOS restrictions.")
        val si = Intent(this, LockScreenService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(si)
        } else {
            startService(si)
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
fun PermCard(
    step: String, title: String, desc: String,
    btn: String?, urgent: Boolean, onClick: () -> Unit
) {
    val bg     = if (urgent) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)
    val accent = if (urgent) Color(0xFFE65100) else Color(0xFF455A64)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(step, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(desc, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
        if (btn != null) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) { Text(btn) }
        }
    }
}

@Composable
fun XiaomiCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("Step 4  📱  Xiaomi / HyperOS Extra", fontSize = 11.sp,
            color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Xiaomi Required Settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        val steps = listOf(
            "Autostart → Toggle ON",
            "\"Display pop-up windows while running in background\" → Allow",
            "\"Show on Lock screen\" → Allow  (if visible)",
        )
        steps.forEachIndexed { i, text ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("${i + 1}. ", fontSize = 13.sp, color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.Bold)
                Text(text, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
            }
        }
        
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) { 
            Text("Open Xiaomi Permissions") 
        }
    }
}