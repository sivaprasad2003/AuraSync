package com.enterprise.syncpro.ui

import android.Manifest
import android.app.Activity
import com.enterprise.syncpro.service.SyncNotificationReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import com.enterprise.syncpro.data.ConnectedDeviceEntity
import com.enterprise.syncpro.data.LocalFileIndexEntity
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.enterprise.syncpro.data.DeviceRole
import com.enterprise.syncpro.data.HubDeduplicationEntity
import com.enterprise.syncpro.media.MediaBucketSummary
import com.enterprise.syncpro.media.QrCodeCameraAnalyzer
import com.enterprise.syncpro.network.NetworkAndSafetyMonitor
import com.enterprise.syncpro.network.RelayQueueTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class PreviewMediaData(
    val title: String,
    val filePath: String? = null,
    val contentUri: String? = null,
    val fileSize: Long = 0L,
    val bucketOrSource: String = "",
    val sha256: String? = null,
    val timestamp: Long = 0L
)

private enum class BottomNavTab(val title: String) {
    DASHBOARD("Dashboard"),
    FOLDERS("Folders"),
    QUEUE("Queue"),
    NETWORK("Network")
}

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.toastFlow.collect { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }

            SyncProTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SyncProRootScreen(
                        viewModel = viewModel,
                        onLaunchBiometricPrompt = { triggerBiometricUnlock() },
                        onRequestIgnoreBatteryOptimizations = { requestBatteryOptimizationExemption() }
                    )
                }
            }
        }
    }

    private fun triggerBiometricUnlock() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(
                this,
                "Biometric hardware unavailable or not enrolled; use your 6-digit PIN.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.unlockVaultViaBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("AuraSync Cryptographic Vault")
            .setSubtitle("Authenticate with Fingerprint or Face Unlock to access pairing tokens")
            .setNegativeButtonText("Use 6-Digit PIN")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun requestBatteryOptimizationExemption() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    "AuraSync is already exempted from Doze & Battery Optimizations",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncProRootScreen(
    viewModel: MainViewModel,
    onLaunchBiometricPrompt: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var selectedTab by rememberSaveable { mutableStateOf(BottomNavTab.DASHBOARD) }
    var showAboutModal by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showConfigBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticLogsDialog by rememberSaveable { mutableStateOf(false) }
    var showSafetySettingsModal by rememberSaveable { mutableStateOf(false) }
    var selectedDeviceForConfig by remember { mutableStateOf<ConnectedDeviceEntity?>(null) }
    var previewMediaItem by remember { mutableStateOf<PreviewMediaData?>(null) }

    val showFirstLaunchOnboarding by viewModel.showFirstLaunchOnboarding.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val deviceRole by viewModel.deviceRole.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val refreshAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshAngle"
    )

    // Complete Application Exit Confirmation Dialog
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = CrimsonAlert,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Exit AuraSync?") },
            text = {
                Text("This will completely stop background syncing, shut down local transfer services, and close the application.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmation = false
                        runCatching {
                            context.sendBroadcast(Intent(context, SyncNotificationReceiver::class.java).apply {
                                action = SyncNotificationReceiver.ACTION_EXIT
                            })
                        }
                        (context as? Activity)?.finishAffinity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Exit App", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Runtime Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshAllState()
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // First-Time Fresh Installation Onboarding (Step 1: Welcome -> Step 2: Role Selection -> Dashboard)
    if (showFirstLaunchOnboarding) {
        FirstLaunchOnboardingModal(
            initialRole = deviceRole,
            isDark = isDarkMode,
            onRoleConfirmed = { chosenRole ->
                viewModel.setDeviceRole(chosenRole)
                viewModel.dismissFirstLaunchOnboarding()
                Toast.makeText(
                    context,
                    "Role set: ${if (chosenRole == DeviceRole.PRIMARY_SENDER) "Primary Sender" else "Secondary Hub"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showAboutModal) {
        AboutSyncProModal(
            onDismiss = { showAboutModal = false }
        )
    }

    if (showConfigBackupDialog) {
        ConfigExportImportModal(
            viewModel = viewModel,
            onDismiss = { showConfigBackupDialog = false }
        )
    }

    if (showDiagnosticLogsDialog) {
        DiagnosticLogsModal(
            viewModel = viewModel,
            onDismiss = { showDiagnosticLogsDialog = false }
        )
    }

    if (showSafetySettingsModal) {
        SafetySettingsModal(
            viewModel = viewModel,
            onRequestBatteryExemption = onRequestIgnoreBatteryOptimizations,
            onDismiss = { showSafetySettingsModal = false }
        )
    }

    selectedDeviceForConfig?.let { device ->
        DeviceConfigModal(
            device = device,
            onSaveConfig = { kbps, sh, sm ->
                viewModel.updateDeviceSettings(device.deviceId, kbps, sh, sm)
                selectedDeviceForConfig = null
            },
            onDismiss = { selectedDeviceForConfig = null }
        )
    }

    previewMediaItem?.let { item ->
        MediaPreviewDialog(
            media = item,
            onDismiss = { previewMediaItem = null }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkMode) SyncProGradients.EmeraldIndigoHero else SyncProGradients.EmeraldHeroLight)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "AuraSync Logo",
                            tint = EmeraldNeon,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AURASYNC",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Unlimited Google Photos P2P Relay",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkMode) EmeraldNeon else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (deviceRole == DeviceRole.SECONDARY_HUB) {
                            "Active Mode: Secondary Hub (Receiver)"
                        } else {
                            "Active Mode: Primary Node (Sender)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "ACTIVE DEVICE ROLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Primary Sender Node",
                            fontWeight = if (deviceRole == DeviceRole.PRIMARY_SENDER) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    selected = deviceRole == DeviceRole.PRIMARY_SENDER,
                    icon = {
                        Icon(
                            imageVector = if (deviceRole == DeviceRole.PRIMARY_SENDER) Icons.Default.CheckCircle else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (deviceRole == DeviceRole.PRIMARY_SENDER) EmeraldNeon else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        viewModel.setDeviceRole(DeviceRole.PRIMARY_SENDER)
                        Toast.makeText(context, "Switched role to Primary Sender", Toast.LENGTH_SHORT).show()
                        scope.launch { drawerState.close() }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Secondary Google Hub",
                            fontWeight = if (deviceRole == DeviceRole.SECONDARY_HUB) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    selected = deviceRole == DeviceRole.SECONDARY_HUB,
                    icon = {
                        Icon(
                            imageVector = if (deviceRole == DeviceRole.SECONDARY_HUB) Icons.Default.CheckCircle else Icons.Default.Hub,
                            contentDescription = null,
                            tint = if (deviceRole == DeviceRole.SECONDARY_HUB) EmeraldNeon else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        viewModel.setDeviceRole(DeviceRole.SECONDARY_HUB)
                        Toast.makeText(context, "Switched role to Secondary Google Hub", Toast.LENGTH_SHORT).show()
                        scope.launch { drawerState.close() }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "SETTINGS & SYSTEM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = {
                        Text("Hardware Safety & Doze Settings", maxLines = 1)
                    },
                    selected = false,
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    onClick = {
                        showSafetySettingsModal = true
                        scope.launch { drawerState.close() }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Config JSON Export & Import") },
                    selected = false,
                    icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null) },
                    onClick = {
                        viewModel.prepareExportConfigJson()
                        showConfigBackupDialog = true
                        scope.launch { drawerState.close() }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Diagnostic & Security Logs") },
                    selected = false,
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    onClick = {
                        showDiagnosticLogsDialog = true
                        scope.launch { drawerState.close() }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Bypass Doze / Standby Bucket") },
                    selected = false,
                    icon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                    onClick = {
                        onRequestIgnoreBatteryOptimizations()
                        scope.launch { drawerState.close() }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                NavigationDrawerItem(
                    label = { Text("GitHub Repository") },
                    selected = false,
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    onClick = {
                        openExternalUrl(context, "https://github.com/sivaprasad2003/AuraSync")
                        scope.launch { drawerState.close() }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Telegram Community") },
                    selected = false,
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    onClick = {
                        openExternalUrl(context, "https://t.me/aurasyncapp")
                        scope.launch { drawerState.close() }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("About AuraSync & Support") },
                    selected = false,
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showAboutModal = true
                        scope.launch { drawerState.close() }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Exit AuraSync",
                            color = CrimsonAlert,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    selected = false,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Exit AuraSync",
                            tint = CrimsonAlert
                        )
                    },
                    onClick = {
                        scope.launch { drawerState.close() }
                        showExitConfirmation = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "AuraSync",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (deviceRole == DeviceRole.SECONDARY_HUB) {
                                    "SECONDARY HUB • UNLIMITED PHOTOS RECEIVER"
                                } else {
                                    "PRIMARY SENDER • SHA-256 DEDUPLICATED RELAY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showExitConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Exit AuraSync",
                                tint = CrimsonAlert
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.refreshAllState()
                            },
                            enabled = !isRefreshing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Telemetry",
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = if (isRefreshing) refreshAngle else 0f
                                },
                                tint = if (isRefreshing) EmeraldNeon else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        val themeRotation by animateFloatAsState(
                            targetValue = if (isDarkMode) 360f else 0f,
                            animationSpec = tween(durationMillis = 350, easing = LinearEasing),
                            label = "ThemeToggleRotation"
                        )
                        IconButton(onClick = {
                            viewModel.toggleDarkMode(!isDarkMode)
                            Toast.makeText(context, if (!isDarkMode) "Dark mode" else "Light mode", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Toggle Dark/Light Mode",
                                tint = if (isDarkMode) EmeraldNeon else Color(0xFFF59E0B),
                                modifier = Modifier.graphicsLayer { rotationZ = themeRotation }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                // Streamlined 3-Tab Bottom Navigation Bar: Dashboard, Folders, Network
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.DASHBOARD,
                        onClick = { selectedTab = BottomNavTab.DASHBOARD },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.FOLDERS,
                        onClick = {
                            selectedTab = BottomNavTab.FOLDERS
                            viewModel.refreshMediaBuckets()
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                        label = { Text("Folders") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.QUEUE,
                        onClick = { selectedTab = BottomNavTab.QUEUE },
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Queue") },
                        label = { Text("Queue") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.NETWORK,
                        onClick = { selectedTab = BottomNavTab.NETWORK },
                        icon = { Icon(Icons.Default.Wifi, contentDescription = "Network") },
                        label = { Text("Network") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        (slideIntoContainer(direction, animationSpec = tween(220)) + fadeIn(animationSpec = tween(200)))
                            .togetherWith(slideOutOfContainer(direction, animationSpec = tween(220)) + fadeOut(animationSpec = tween(180)))
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        BottomNavTab.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            onRequestBatteryExemption = onRequestIgnoreBatteryOptimizations,
                            onPreviewMedia = { previewMediaItem = it },
                            onConfigureDevice = { selectedDeviceForConfig = it }
                        )
                        BottomNavTab.FOLDERS -> FoldersScreen(
                            viewModel = viewModel,
                            onPreviewMedia = { previewMediaItem = it }
                        )
                        BottomNavTab.QUEUE -> UploadQueueScreen(
                            viewModel = viewModel,
                            onPreviewMedia = { previewMediaItem = it }
                        )
                        BottomNavTab.NETWORK -> NetworkAndVaultScreen(
                            viewModel = viewModel,
                            onLaunchBiometricPrompt = onLaunchBiometricPrompt
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncProTimePickerDialog(
    title: String = "Select Time",
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean = false,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = is24Hour
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
                onDismiss()
            }) {
                Text("OK", color = EmeraldNeon, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun DashboardScreen(
    viewModel: MainViewModel,
    onRequestBatteryExemption: () -> Unit,
    onPreviewMedia: (PreviewMediaData) -> Unit,
    onConfigureDevice: (ConnectedDeviceEntity) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val role by viewModel.deviceRole.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val safety by viewModel.safetyStatus.collectAsState()
    val scheduleHour by viewModel.scheduleHour.collectAsState()
    val scheduleMinute by viewModel.scheduleMinute.collectAsState()
    val daytimeKbps by viewModel.daytimeLimitKbps.collectAsState()
    val throttleStartHour by viewModel.throttleStartHour.collectAsState()
    val throttleStartMinute by viewModel.throttleStartMinute.collectAsState()
    val throttleEndHour by viewModel.throttleEndHour.collectAsState()
    val throttleEndMinute by viewModel.throttleEndMinute.collectAsState()
    val pairingToken by viewModel.pairingToken.collectAsState()
    val priorityWeight by viewModel.devicePriorityWeight.collectAsState()
    val instantCapture by viewModel.instantCaptureSync.collectAsState()
    val syncedLocalCount by viewModel.syncedLocalCount.collectAsState()
    val syncedLocalBytes by viewModel.syncedLocalBytes.collectAsState()
    val hubReceivedCount by viewModel.hubReceivedCount.collectAsState()
    val hubReceivedBytes by viewModel.hubReceivedBytes.collectAsState()
    val recentHubFiles by viewModel.recentHubFiles.collectAsState()
    val recentUploadedFiles by viewModel.recentUploadedFiles.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val uploadQueue by viewModel.uploadQueue.collectAsState()
    val storageCutoffGb by viewModel.storageCutoffGb.collectAsState()

    var showStorageSafetyModal by rememberSaveable { mutableStateOf(false) }
    var showHubVaultDetailsModal by rememberSaveable { mutableStateOf(false) }

    if (showStorageSafetyModal) {
        StorageSafetyModal(
            viewModel = viewModel,
            onDismiss = { showStorageSafetyModal = false }
        )
    }

    if (showHubVaultDetailsModal) {
        HubVaultDetailsModal(
            viewModel = viewModel,
            onDismiss = { showHubVaultDetailsModal = false }
        )
    }

    var showScheduleTimePicker by rememberSaveable { mutableStateOf(false) }
    var showThrottleStartTimePicker by rememberSaveable { mutableStateOf(false) }
    var showThrottleEndTimePicker by rememberSaveable { mutableStateOf(false) }

    if (showScheduleTimePicker) {
        SyncProTimePickerDialog(
            title = "Set Daily Sync Schedule",
            initialHour = scheduleHour,
            initialMinute = scheduleMinute,
            onConfirm = { h, m -> viewModel.updateDailySchedule(h, m) },
            onDismiss = { showScheduleTimePicker = false }
        )
    }
    if (showThrottleStartTimePicker) {
        SyncProTimePickerDialog(
            title = "Set Throttle Window Start",
            initialHour = throttleStartHour,
            initialMinute = throttleStartMinute,
            onConfirm = { h, m -> viewModel.updateBandwidthThrottleWindow(h, m, throttleEndHour, throttleEndMinute) },
            onDismiss = { showThrottleStartTimePicker = false }
        )
    }
    if (showThrottleEndTimePicker) {
        SyncProTimePickerDialog(
            title = "Set Throttle Window End",
            initialHour = throttleEndHour,
            initialMinute = throttleEndMinute,
            onConfirm = { h, m -> viewModel.updateBandwidthThrottleWindow(throttleStartHour, throttleStartMinute, h, m) },
            onDismiss = { showThrottleEndTimePicker = false }
        )
    }

    val isDark by viewModel.isDarkMode.collectAsState()

    // Realtime Overall Batch Progress
    val totalBatchFiles = telemetry.completedSessionFiles + telemetry.skippedDuplicateFiles + telemetry.queuedFileCount
    val completedBatchFiles = telemetry.completedSessionFiles + telemetry.skippedDuplicateFiles
    val overallProgress = if (totalBatchFiles > 0) {
        ((completedBatchFiles.toFloat() + telemetry.activeFileProgress) / totalBatchFiles.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val isSyncActive = telemetry.isRunning || telemetry.queuedFileCount > 0

    val animatedProgress by animateFloatAsState(
        targetValue = if (telemetry.isRunning || telemetry.queuedFileCount > 0) overallProgress else if (completedBatchFiles > 0 && telemetry.queuedFileCount == 0) 1f else 0f,
        label = "transferProgress"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Live Circular Ring & Transfer Telemetry Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isDark) SyncProGradients.EmeraldIndigoHero else SyncProGradients.EmeraldHeroLight)
                    .border(
                        width = 1.dp,
                        color = EmeraldNeon.copy(alpha = if (isDark) 0.4f else 0.5f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = telemetry.statusBanner,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Route: ${telemetry.activeEndpointRoute} • 64KB Direct I/O",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) EmeraldNeon else EmeraldDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Live Circular Progress Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(84.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = EmeraldNeon,
                                trackColor = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                strokeWidth = 8.dp
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(animatedProgress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (totalBatchFiles > 1 && (telemetry.isRunning || telemetry.queuedFileCount > 0)) {
                                    Text(
                                        text = "$completedBatchFiles/$totalBatchFiles",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldNeon,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Metrics Row with equal 1/3 weight and ellipsis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatPill(
                            label = "Throughput",
                            value = String.format("%.2f MB/s", telemetry.currentSpeedMbps),
                            modifier = Modifier.weight(1f),
                            isDark = isDark
                        )
                        TelemetryStatPill(
                            label = "Priority Queue",
                            value = "${telemetry.queuedFileCount} items",
                            modifier = Modifier.weight(1f),
                            isDark = isDark
                        )
                        TelemetryStatPill(
                            label = "Deduplicated",
                            value = "${telemetry.skippedDuplicateFiles} skipped",
                            modifier = Modifier.weight(1f),
                            isDark = isDark
                        )
                    }

                    // Live Active File Streaming Progress Indicator
                    if (telemetry.isRunning && telemetry.activeFileName != "Idle") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = telemetry.activeFileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${(telemetry.activeFileProgress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { telemetry.activeFileProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = EmeraldNeon,
                                trackColor = EmeraldNeon.copy(alpha = 0.2f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            if (telemetry.isRunning || isSyncActive || telemetry.isHubBusyWithSync) {
                                val currentPeerText = if (role == DeviceRole.SECONDARY_HUB) {
                                    val sender = if (telemetry.sourceDeviceName.isNotBlank()) telemetry.sourceDeviceName else "Primary Sender Node"
                                    "Receiving from: $sender"
                                } else {
                                    val hubName = pairingToken?.hubDeviceName?.ifBlank { "Secondary Hub" } ?: "Secondary Hub"
                                    "Syncing to: $hubName"
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentPeerText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    if (role != DeviceRole.SECONDARY_HUB) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Intelligent State-Driven Animated Action Controls (Idle: Sync Now | Active: Pause & Stop | Paused: Resume & Stop)
                        val isSyncPaused = telemetry.isPaused

                        AnimatedContent(
                            targetState = when {
                                isSyncPaused -> "PAUSED"
                                isSyncActive -> "ACTIVE"
                                else -> "IDLE"
                            },
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + expandVertically()) togetherWith
                                        (fadeOut(animationSpec = tween(180)) + shrinkVertically())
                            },
                            label = "SyncActionButtonsTransition",
                            modifier = Modifier.fillMaxWidth()
                        ) { syncState ->
                            when (syncState) {
                                "ACTIVE" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.togglePauseResume()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (isDark) Color(0xFF2C2517) else Color(0xFFFEF3C7),
                                                contentColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Pause,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Pause",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.stopSync()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Stop",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                                "PAUSED" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.togglePauseResume()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Resume",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.stopSync()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                                            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.8f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = null,
                                                tint = CrimsonAlert,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Stop",
                                                color = CrimsonAlert,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                                else -> { // "IDLE"
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.startFullBatchSyncNow()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sync Now",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Two-Tier Vault Ledger Metrics (Symmetric matched height & balanced content)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { showHubVaultDetailsModal = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val queuedBytes = remember(uploadQueue) { uploadQueue.sumOf { it.fileSize } }
                        Column {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldNeon)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (role == DeviceRole.SECONDARY_HUB) {
                                    "$hubReceivedCount Files"
                                } else {
                                    if (syncedLocalCount > 0) "$syncedLocalCount Files"
                                    else if (uploadQueue.isNotEmpty()) "${uploadQueue.size} Files"
                                    else "0 Files"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Column {
                            Text(
                                text = if (role == DeviceRole.SECONDARY_HUB) {
                                    "Hub Vault Storage"
                                } else {
                                    if (syncedLocalCount > 0) "Tier-1 Index Verified"
                                    else if (uploadQueue.isNotEmpty()) "Pending Relay Queue"
                                    else "Tier-1 Index Verified"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (role == DeviceRole.SECONDARY_HUB) {
                                    "${formatBytes(hubReceivedBytes)} total"
                                } else {
                                    if (syncedLocalBytes > 0) "${formatBytes(syncedLocalBytes)} total"
                                    else if (queuedBytes > 0) "${formatBytes(queuedBytes)} queued"
                                    else "0 MB total"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { showStorageSafetyModal = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = EmeraldNeon)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = formatBytes(safety.availableStorageBytes),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (safety.isLowStorageTripped) CrimsonAlert else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Column {
                            Text(
                                text = if (safety.isLowStorageTripped) "Low Storage Alert" else "Available Storage",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (safety.isLowStorageTripped) CrimsonAlert else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (safety.isLowStorageTripped) "< ${String.format(Locale.US, "%.1f", storageCutoffGb)}GB guard tripped" else "> ${String.format(Locale.US, "%.1f", storageCutoffGb)}GB guard active",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (safety.isLowStorageTripped) CrimsonAlert else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Automation, 12-Hour TimePickerDialog Scheduler & Bandwidth Throttling (Hidden on Secondary Hub)
        if (role != DeviceRole.SECONDARY_HUB) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily Automated Sync Schedule",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Daily automated sync at ${viewModel.format12HourTime(scheduleHour, scheduleMinute)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    showScheduleTimePicker = true
                                },
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(viewModel.format12HourTime(scheduleHour, scheduleMinute))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Daytime Bandwidth Throttle Slider (Discrete Presets: 1MB/s, 2.5MB/s, 5MB/s, 10MB/s, 20MB/s, Unlimited)
                        val bandwidthOptions = remember { listOf(1024, 2560, 5120, 10240, 20480, 0) }
                        val currentSliderIndex = remember(daytimeKbps) {
                            val idx = bandwidthOptions.indexOf(daytimeKbps)
                            if (idx >= 0) idx.toFloat() else (bandwidthOptions.size - 1).toFloat()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Daytime Bandwidth Throttle", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (daytimeKbps <= 0) "Unlimited (Max)" else "${String.format(Locale.US, "%.1f", daytimeKbps / 1024f)} MB/s (${daytimeKbps} KB/s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = currentSliderIndex,
                            onValueChange = { pos ->
                                val chosen = bandwidthOptions[pos.roundToInt().coerceIn(0, bandwidthOptions.size - 1)]
                                viewModel.updateDaytimeBandwidthLimit(chosen)
                            },
                            valueRange = 0f..(bandwidthOptions.size - 1).toFloat(),
                            steps = bandwidthOptions.size - 2
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Throttle Active Window (Timer)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${viewModel.format12HourTime(throttleStartHour, throttleStartMinute)} - ${viewModel.format12HourTime(throttleEndHour, throttleEndMinute)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (daytimeKbps <= 0) "Transfers run uncapped at maximum speed."
                            else "Transfers throttled to ${String.format(Locale.US, "%.1f", daytimeKbps / 1024f)} MB/s during this window. Full speed outside.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showThrottleStartTimePicker = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp), tint = EmeraldNeon)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "From: ${viewModel.format12HourTime(throttleStartHour, throttleStartMinute)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    showThrottleEndTimePicker = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp), tint = EmeraldNeon)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "To: ${viewModel.format12HourTime(throttleEndHour, throttleEndMinute)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Priority Weight Selector (1..5)
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Device PriorityQueue Weight",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Weight: $priorityWeight",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (1..5).forEach { weight ->
                                    val isSelected = priorityWeight == weight
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clickable {
                                                viewModel.updateDevicePriorityWeight(weight)
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) EmeraldNeon else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) EmeraldNeon else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (weight == 5) "5 (Max)" else "$weight",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        // Instant Capture MediaStore Trigger Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Instant Camera Capture Trigger", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Instantly relays new camera photos to hub",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = instantCapture,
                                onCheckedChange = { viewModel.toggleInstantCapture(it) },
                                colors = syncProSwitchColors()
                            )
                        }
                    }
                }
            }
        }

        // Connected Network Devices Section (Visible on both Primary and Secondary Dashboard)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val myDeviceId by viewModel.myDeviceId.collectAsState()
                    val myFriendlyName = viewModel.myFriendlyName
                    val displayDevices = remember(connectedDevices, role, myDeviceId, myFriendlyName) {
                        val filtered = if (role == DeviceRole.PRIMARY_SENDER) {
                            connectedDevices.filterNot { dev ->
                                (myDeviceId.isNotEmpty() && dev.deviceId == myDeviceId) ||
                                dev.deviceName.equals(myFriendlyName, ignoreCase = true) ||
                                dev.deviceName.equals(android.os.Build.MODEL, ignoreCase = true) ||
                                (dev.role == "PRIMARY_SENDER" && (dev.deviceName.contains(android.os.Build.MODEL, ignoreCase = true) || dev.deviceId.contains(android.os.Build.MODEL, ignoreCase = true)))
                            }
                        } else {
                            connectedDevices
                        }
                        filtered.groupBy { dev ->
                            if (dev.ipAddress.isNotBlank() && dev.ipAddress != "Wi-Fi LAN" && dev.ipAddress != "0.0.0.0") {
                                dev.ipAddress
                            } else {
                                dev.deviceId
                            }
                        }.values.map { list ->
                            list.firstOrNull { it.syncStatus.startsWith("Receiving", ignoreCase = true) || it.syncStatus.startsWith("Sending", ignoreCase = true) }
                                ?: list.maxByOrNull { it.lastSeen } ?: list.first()
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = EmeraldNeon,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Connected Devices",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = EmeraldNeon.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${displayDevices.size} ${if (displayDevices.size == 1) "Client" else "Clients"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (role == DeviceRole.SECONDARY_HUB)
                            "Real-time connected primary senders, schedules & data traffic"
                        else
                            "Hub mesh connection, peer devices & allocated bandwidth",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (displayDevices.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (role == DeviceRole.SECONDARY_HUB) "No Peer Devices Connected Yet" else "Searching for Hub Peers...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (role == DeviceRole.SECONDARY_HUB)
                                        "When Primary sender nodes pair and sync to this Hub, they will appear here with individual schedule, bandwidth, and traffic statistics."
                                    else
                                        "When connected to a Secondary Hub, connection status, allocated throughput, and relayed statistics will appear here.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        displayDevices.forEach { dev ->
                            ConnectedDeviceRow(
                                device = dev,
                                onConfigure = {
                                    onConfigureDevice(dev)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // Secondary Hub Recent Committed Files Ledger (Self-Contained Scrolling Box)
        if (recentHubFiles.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Atomic Commits to Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldNeon.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${recentHubFiles.size} Files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentHubFiles.forEach { entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onPreviewMedia(
                                                PreviewMediaData(
                                                    title = entry.finalFileName,
                                                    contentUri = entry.savedUri,
                                                    fileSize = entry.fileSize,
                                                    bucketOrSource = entry.sourceDevice,
                                                    sha256 = entry.sha256,
                                                    timestamp = entry.receivedAt
                                                )
                                            )
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(EmeraldNeon.copy(alpha = 0.15f))
                                        ) {
                                            Icon(
                                                imageVector = if (entry.finalFileName.substringAfterLast('.', "").lowercase() in setOf("mp4", "mov", "mkv", "webm", "3gp")) {
                                                    Icons.Default.Movie
                                                } else {
                                                    Icons.Default.Image
                                                },
                                                contentDescription = "Preview",
                                                tint = EmeraldNeon,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.finalFileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "SHA-256: ${entry.sha256.take(16)}... • From ${entry.sourceDevice}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = formatBytes(entry.fileSize),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldNeon
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Tap to view",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Primary Phone Recent Uploaded Files Ledger (Self-Contained Scrolling Box)
        if (role == DeviceRole.PRIMARY_SENDER && recentUploadedFiles.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Uploaded Files Relayed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldNeon.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${recentUploadedFiles.size} Files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentUploadedFiles.forEach { entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            Toast.makeText(context, "Viewing: ${entry.fileName}", Toast.LENGTH_SHORT).show()
                                            onPreviewMedia(
                                                PreviewMediaData(
                                                    title = entry.fileName,
                                                    filePath = entry.filePath,
                                                    fileSize = entry.fileSize,
                                                    bucketOrSource = entry.bucketName ?: "DCIM",
                                                    sha256 = entry.sha256,
                                                    timestamp = entry.lastModified
                                                )
                                            )
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(EmeraldNeon.copy(alpha = 0.15f))
                                        ) {
                                            Icon(
                                                imageVector = if (entry.fileName.substringAfterLast('.', "").lowercase() in setOf("mp4", "mov", "mkv", "webm", "3gp")) {
                                                    Icons.Default.Movie
                                                } else {
                                                    Icons.Default.Image
                                                },
                                                contentDescription = "Preview",
                                                tint = EmeraldNeon,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.fileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = entry.filePath,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "SHA-256: ${entry.sha256.take(16)}... • ${entry.bucketName ?: "DCIM"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = formatBytes(entry.fileSize),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldNeon
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Uploaded",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldNeon.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersScreen(
    viewModel: MainViewModel,
    onPreviewMedia: (PreviewMediaData) -> Unit
) {
    val context = LocalContext.current
    val buckets by viewModel.mediaBuckets.collectAsState()
    val enabledBuckets by viewModel.enabledBuckets.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Granular Source Folders & Urgent Queue-Jumping",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Check folders for automated background syncing, or tap 'Upload' on a folder to relay only files in that folder. Tap any file to preview.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (buckets.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Media Folders Discovered",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ensure storage and media permissions are granted so AuraSync can index photos and videos on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        items(buckets, key = { it.bucketName }) { summary ->
            val isChecked = enabledBuckets.any { it.equals(summary.bucketName, ignoreCase = true) }
            FolderBucketCard(
                summary = summary,
                isChecked = isChecked,
                onCheckedChange = { checked ->
                    viewModel.toggleBucketSelection(summary.bucketName, checked)
                    Toast.makeText(
                        context,
                        if (checked) "Included folder: ${summary.bucketName}" else "Excluded folder: ${summary.bucketName}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onInstantUploadFolder = {
                    viewModel.triggerUploadOnlyThisFolder(summary.sampleFiles, summary.bucketName)
                    Toast.makeText(
                        context,
                        "Uploading only folder: ${summary.bucketName} (${summary.sampleFiles.size} items)",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onInstantUploadSingleFile = { singleTask ->
                    viewModel.triggerUrgentInstantUpload(listOf(singleTask), singleTask.fileName)
                    Toast.makeText(
                        context,
                        "Express uploading: ${singleTask.fileName}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onPreviewMedia = onPreviewMedia
            )
        }
    }
}

@Composable
private fun FolderBucketCard(
    summary: MediaBucketSummary,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInstantUploadFolder: () -> Unit,
    onInstantUploadSingleFile: (com.enterprise.syncpro.network.RelayQueueTask) -> Unit,
    onPreviewMedia: (PreviewMediaData) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Text(
                        text = summary.bucketName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${summary.fileCount} files • ${formatBytes(summary.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onInstantUploadFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp
                    )
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Upload",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            if (expanded && summary.sampleFiles.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Tap any file for Urgent Priority-5 Queue-Jump (or preview):",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldNeon
                )
                Spacer(modifier = Modifier.height(6.dp))
                summary.sampleFiles.take(6).forEach { fileTask ->
                    val isVid = fileTask.fileName.substringAfterLast('.', "").lowercase() in
                            setOf("mp4", "mov", "mkv", "webm", "3gp")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPreviewMedia(
                                    PreviewMediaData(
                                        title = fileTask.fileName,
                                        filePath = fileTask.filePath,
                                        fileSize = fileTask.fileSize,
                                        bucketOrSource = fileTask.bucketName,
                                        timestamp = fileTask.lastModified
                                    )
                                )
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isVid) Icons.Default.Movie else Icons.Default.Image,
                                contentDescription = "Preview",
                                tint = EmeraldNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${fileTask.fileName} (${formatBytes(fileTask.fileSize)})",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { onInstantUploadSingleFile(fileTask) }) {
                            Text("Upload Immediately", color = EmeraldNeon)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkAndVaultScreen(
    viewModel: MainViewModel,
    onLaunchBiometricPrompt: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val role by viewModel.deviceRole.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val isPinConfigured by viewModel.isPinConfigured.collectAsState()
    val pairingToken by viewModel.pairingToken.collectAsState()
    val hubQrBitmap by viewModel.hubQrBitmap.collectAsState()
    val discoveredHubs by viewModel.discoveredHubs.collectAsState()
    val localLanIp by viewModel.localLanIp.collectAsState()
    val tailscaleIp by viewModel.tailscaleIp.collectAsState()
    val currentSsid by viewModel.currentSsid.collectAsState()
    val isOnWifi by viewModel.isOnWifi.collectAsState()
    val trustedSsids by viewModel.trustedSsids.collectAsState()
    val restrictWifi by viewModel.restrictToTrustedWifi.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()

    var pinInput by rememberSaveable { mutableStateOf("") }
    var confirmPinInput by rememberSaveable { mutableStateOf("") }
    var customWanInput by rememberSaveable { mutableStateOf(tailscaleIp ?: "") }
    var newSsidInput by rememberSaveable { mutableStateOf("") }
    var showCameraScanner by rememberSaveable { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Biometric & 6-Digit PIN Cryptographic Vault Lock
        if (!isVaultUnlocked) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isPinConfigured) {
                                "Cryptographic Vault Locked"
                            } else {
                                "Initialize 6-Digit Cryptographic Vault PIN"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Protected by AES-256-GCM EncryptedSharedPreferences & Android BiometricPrompt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val lockoutRemainingMs = remember(pinInput) { viewModel.getPinLockoutRemainingMs() }
                        if (lockoutRemainingMs > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CrimsonAlert.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, CrimsonAlert),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = "⚠️ Vault locked due to failed attempts. Try again in ${lockoutRemainingMs / 1000}s or unlock via Biometric.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CrimsonAlert,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                            label = { Text(if (isPinConfigured) "Enter 6-Digit PIN" else "Enter 6-Digit PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            enabled = lockoutRemainingMs <= 0,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!isPinConfigured) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = confirmPinInput,
                                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPinInput = it },
                                label = { Text("Confirm 6-Digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isPinConfigured) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.verifyVaultPin(pinInput)
                                        pinInput = ""
                                    },
                                    enabled = lockoutRemainingMs <= 0 && pinInput.length == 6,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unlock PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onLaunchBiometricPrompt,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Biometric")
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.initializeVaultPin(pinInput, confirmPinInput)
                                    if (pinInput == confirmPinInput && pinInput.length == 6) {
                                        pinInput = ""
                                        confirmPinInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set 6-Digit Vault PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Unlocked Vault: Role-Aware Persistent QR Pairing & CameraX Scanner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Role Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (role == DeviceRole.SECONDARY_HUB) "Secondary Hub Server" else "Primary Sender Node",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldNeon
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (role == DeviceRole.SECONDARY_HUB) "Role: Receives & Stores Media from Sender" else "Role: Uploads & Backs Up Media to Hub",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.lockVault() },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lock", style = MaterialTheme.typography.labelSmall)
                                }
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = if (role == DeviceRole.SECONDARY_HUB) "Receiver" else "Sender",
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "LAN IP: $localLanIp • Mesh/Tailscale: ${tailscaleIp ?: "Not Detected"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Clear step-by-step pairing guidance
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = EmeraldNeon,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (role == DeviceRole.SECONDARY_HUB) "How to Pair with Primary Phone:" else "How to Connect to Secondary Hub:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                if (role == DeviceRole.SECONDARY_HUB) {
                                    Text(
                                        text = "1. Keep this Hub screen open.\n" +
                                                "2. Open SyncPro on your Primary Sender phone.\n" +
                                                "3. Go to Network tab on Primary phone & tap 'Scan Secondary Hub QR Code'.\n" +
                                                "4. Aim Primary phone's camera at this QR code below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "1. Open SyncPro on your Secondary Hub phone (set to 'Secondary Google Hub').\n" +
                                                "2. Go to Network tab on your Hub to display its Hub QR code.\n" +
                                                "3. Tap the 'Scan Secondary Hub QR Code' button below.\n" +
                                                "4. Aim this phone's camera at the Hub screen to pair instantly.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (role == DeviceRole.SECONDARY_HUB) {
                            // Secondary Hub displays the QR code prominently
                            hubQrBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Hub Pairing QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scan this QR code with your Primary Sender phone",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldNeon
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Listening on Port 49210 • SHA-256 E2EE Vault Ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Remote / WAN input for Secondary Hub
                            OutlinedTextField(
                                value = customWanInput,
                                onValueChange = { customWanInput = it },
                                label = { Text("Tailscale / DDNS / Remote WAN IP") },
                                placeholder = { Text("e.g. 100.x.x.x or sync.duckdns.org") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.regenerateHubPairingQr(customWanInput) }) {
                                        Icon(
                                            imageVector = Icons.Default.QrCode2,
                                            contentDescription = "Update Pairing QR",
                                            tint = EmeraldNeon
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Primary Sender highlights camera scanning as primary action
                            Button(
                                onClick = { showCameraScanner = !showCameraScanner },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (showCameraScanner) Icons.Default.Close else Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (showCameraScanner) "Close Camera QR Scanner" else "Scan Secondary Hub QR Code",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (showCameraScanner) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LiveCameraQrScannerBox(
                                    onQrDetected = { payload ->
                                        showCameraScanner = false
                                        viewModel.onQrPayloadScanned(payload)
                                    }
                                )
                            }

                            // Paired Hub info or Not Paired status
                            Spacer(modifier = Modifier.height(12.dp))
                            if (pairingToken != null) {
                                val token = pairingToken!!
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = EmeraldNeon.copy(alpha = 0.1f)
                                    ),
                                    border = BorderStroke(1.dp, EmeraldNeon.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldNeon,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Paired with: ${token.hubDeviceName}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "Local Wi-Fi Route: ${token.localIp}:${token.port}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldNeon
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Fallback Route: ${token.tailscaleOrWanHost}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Not Paired Yet — Tap button above to scan Hub QR code or connect via Wi-Fi below.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Collapsible Advanced Settings (Manual IP / Reverse QR)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showAdvancedSettings = !showAdvancedSettings }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (showAdvancedSettings) "Hide Advanced WAN / QR Settings ▲" else "Manual WAN IP / Show Device QR ▼",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            if (showAdvancedSettings) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customWanInput,
                                    onValueChange = { customWanInput = it },
                                    label = { Text("Secondary Hub IP / Tailscale / WAN") },
                                    placeholder = { Text("e.g. 192.168.1.50 or 100.x.x.x") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { viewModel.updatePairedHubHost(customWanInput) }) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Save Hub Route",
                                                tint = EmeraldNeon
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                hubQrBitmap?.let { bmp ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Device QR Code",
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .padding(6.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Sender Device QR Code (For reverse scanner pairing)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Zero-Config mDNS (_syncvault._tcp.) Local Discovery
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Wi-Fi Discovery",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Zero-Config Protocol: _syncvault._tcp.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text("mDNS", maxLines = 1) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // User-friendly explanation of why mDNS is there
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "What is this? SyncPro uses local network discovery (mDNS) so your phones can automatically find each other on the same Wi-Fi router without manual IP configuration or rescanning QR codes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (role == DeviceRole.SECONDARY_HUB) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldNeon,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Broadcasting Hub presence on local Wi-Fi. Primary Sender nodes will discover this device automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            if (connectedDevices.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = EmeraldNeon
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Listening for Primary Sender nodes on local Wi-Fi (Port ${NetworkAndSafetyMonitor.DEFAULT_HUB_PORT})...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "Connected Sender Devices on Local Wi-Fi (${connectedDevices.size}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldNeon
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                for (dev in connectedDevices) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dev.deviceName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${dev.ipAddress} • ${dev.syncStatus}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                color = EmeraldNeon.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (dev.bandwidthLimitKbps <= 0) "Uncapped" else "${dev.bandwidthLimitKbps / 1024f} MB/s",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldNeon,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (discoveredHubs.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = EmeraldNeon
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Scanning local Wi-Fi for active Secondary Hubs...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Note: If your Wi-Fi has 'Client Isolation' or devices are on different networks, discovery is blocked. Please use the QR Code Scanner above.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Discovered Hubs on Local Wi-Fi (Tap to Connect):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldNeon
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                discoveredHubs.forEach { endpoint ->
                                    val isThisHubConnected = pairingToken != null && (
                                        pairingToken!!.localIp == endpoint.hostAddress ||
                                        pairingToken!!.hubDeviceName.equals(endpoint.serviceName, ignoreCase = true) ||
                                        pairingToken!!.hubDeviceId.equals(endpoint.serviceName, ignoreCase = true)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = endpoint.serviceName,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${endpoint.hostAddress}:${endpoint.port}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (isThisHubConnected) {
                                            FilledTonalButton(
                                                onClick = {
                                                    viewModel.pairWithDiscoveredMdnsHub(
                                                        endpoint = endpoint,
                                                        sharedSecretHex = pairingToken?.e2eeSharedSecretHex ?: "",
                                                        pin = pinInput
                                                    )
                                                },
                                                modifier = Modifier.height(36.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = EmeraldNeon.copy(alpha = 0.2f),
                                                    contentColor = EmeraldNeon
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Connected", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    viewModel.pairWithDiscoveredMdnsHub(
                                                        endpoint = endpoint,
                                                        sharedSecretHex = pairingToken?.e2eeSharedSecretHex ?: "",
                                                        pin = pinInput
                                                    )
                                                },
                                                modifier = Modifier.height(36.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("Connect", style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Trusted Wi-Fi SSID Whitelisting
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trusted Wi-Fi SSID Whitelisting",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val ssidStatus = when {
                                !currentSsid.isNullOrBlank() -> "Current SSID: $currentSsid"
                                isOnWifi -> "Connected to Wi-Fi (Enable GPS Location to read SSID name)"
                                else -> "Network: Mobile Data / Disconnected"
                            }
                            Text(
                                text = ssidStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = restrictWifi,
                            onCheckedChange = {
                                viewModel.setRestrictToTrustedWifi(it)
                                Toast.makeText(context, if (it) "Wi-Fi whitelist enabled" else "Wi-Fi whitelist disabled", Toast.LENGTH_SHORT).show()
                            },
                            colors = syncProSwitchColors()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSsidInput,
                            onValueChange = { newSsidInput = it },
                            placeholder = { Text("Home Wi-Fi SSID") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        )
                        Button(
                            onClick = {
                                val toAdd = when {
                                    newSsidInput.isNotBlank() -> newSsidInput.trim().also { newSsidInput = "" }
                                    !currentSsid.isNullOrBlank() -> currentSsid!!
                                    isOnWifi -> "Wi-Fi"
                                    else -> ""
                                }
                                if (toAdd.isNotBlank()) {
                                    viewModel.addTrustedSsid(toAdd)
                                    Toast.makeText(context, "Added $toAdd", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                        ) {
                            Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (trustedSsids.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            trustedSsids.forEach { ssid ->
                                AssistChip(
                                    onClick = {
                                        viewModel.removeTrustedSsid(ssid)
                                        Toast.makeText(context, "Removed trusted SSID: $ssid", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("$ssid ✕", maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadQueueScreen(
    viewModel: MainViewModel,
    onPreviewMedia: (PreviewMediaData) -> Unit
) {
    val context = LocalContext.current
    val queue by viewModel.uploadQueue.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) SyncProGradients.EmeraldIndigoHero else SyncProGradients.EmeraldHeroLight)
                    .border(1.dp, EmeraldNeon.copy(alpha = if (isDark) 0.35f else 0.5f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Upload Queue",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${queue.size} item${if (queue.size == 1) "" else "s"} pending relay",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) EmeraldNeon else EmeraldDark
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = "${queue.size}",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    if (telemetry.isRunning && !telemetry.isPaused) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "▶  ${telemetry.statusBanner}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) EmeraldNeon else EmeraldDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { telemetry.activeFileProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldNeon,
                            trackColor = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }
        }

        // Empty state
        if (queue.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Queue is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "All files have been relayed. Go to Folders tab to trigger new uploads, or tap Sync Now on Dashboard.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Legend row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "File (tap to preview)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Priority / Actions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Queue items
            items(queue, key = { it.filePath }) { task ->
                QueueItemCard(
                    task = task,
                    onPreview = {
                        Toast.makeText(context, "Preview: ${task.fileName}", Toast.LENGTH_SHORT).show()
                        onPreviewMedia(
                            PreviewMediaData(
                                title = task.fileName,
                                filePath = task.filePath,
                                fileSize = task.fileSize,
                                bucketOrSource = task.bucketName,
                                timestamp = task.lastModified
                            )
                        )
                    },
                    onRemove = {
                        viewModel.removeFromQueue(task.filePath)
                        Toast.makeText(context, "Removed ${task.fileName} from queue", Toast.LENGTH_SHORT).show()
                    },
                    onRaisePriority = {
                        if (task.priorityWeight < 5) {
                            viewModel.reprioritizeInQueue(task.filePath, task.priorityWeight + 1)
                            Toast.makeText(context, "Raised priority of ${task.fileName} to P${task.priorityWeight + 1}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLowerPriority = {
                        if (task.priorityWeight > 1) {
                            viewModel.reprioritizeInQueue(task.filePath, task.priorityWeight - 1)
                            Toast.makeText(context, "Lowered priority of ${task.fileName} to P${task.priorityWeight - 1}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    task: RelayQueueTask,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
    onRaisePriority: () -> Unit,
    onLowerPriority: () -> Unit
) {
    val priorityColor = when (task.priorityWeight) {
        5 -> CrimsonAlert
        4 -> AmberWarning
        3 -> EmeraldNeon
        else -> IndigoNeon
    }
    val isVid = task.fileName.substringAfterLast('.', "").lowercase() in
            setOf("mp4", "mov", "mkv", "webm", "3gp")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPreview() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Priority badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(priorityColor.copy(alpha = 0.15f))
                    .border(1.dp, priorityColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            ) {
                Text(
                    text = "P${task.priorityWeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = priorityColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isVid) Icons.Default.Movie else Icons.Default.Image,
                        contentDescription = "Preview",
                        tint = EmeraldNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${task.bucketName} \u2022 ${formatBytes(task.fileSize)} \u2022 Tap to preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onRaisePriority,
                    modifier = Modifier.size(32.dp),
                    enabled = task.priorityWeight < 5
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Raise Priority",
                        modifier = Modifier.size(18.dp),
                        tint = if (task.priorityWeight < 5) EmeraldNeon else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                IconButton(
                    onClick = onLowerPriority,
                    modifier = Modifier.size(32.dp),
                    enabled = task.priorityWeight > 1
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Lower Priority",
                        modifier = Modifier.size(18.dp),
                        tint = if (task.priorityWeight > 1) EmeraldNeon else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove from Queue",
                        modifier = Modifier.size(18.dp),
                        tint = CrimsonAlert
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCameraQrScannerBox(
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, EmeraldNeon, RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, QrCodeCameraAnalyzer(onQrDetected))
                            }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FirstLaunchOnboardingModal(
    initialRole: DeviceRole,
    isDark: Boolean = isSystemInDarkTheme(),
    onRoleConfirmed: (DeviceRole) -> Unit
) {
    val context = LocalContext.current
    var currentStep by rememberSaveable { mutableIntStateOf(1) } // Step 1: Initial Welcome Page -> Step 2: Choose Device Role
    var selectedRole by rememberSaveable { mutableStateOf(initialRole) }

    AlertDialog(
        onDismissRequest = { /* Modal requires user to complete onboarding */ },
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (currentStep == 1) Icons.Default.Hub else Icons.Default.Tune,
                    contentDescription = null,
                    tint = EmeraldNeon
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (currentStep == 1) "Welcome to AuraSync" else "Choose Device Role",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep == 1) {
                    // STEP 1: INITIAL WELCOME PAGE (NO DEVICE ROLE EMBEDDED)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) SyncProGradients.EmeraldIndigoHero else SyncProGradients.EmeraldHeroLight)
                            .border(1.dp, EmeraldNeon.copy(alpha = if (isDark) 0.35f else 0.5f), RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Enterprise P2P Unlimited Google Photos Relay",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Author / Architect: Siva Prasad\nVersion: 1.0.0-ENTERPRISE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) EmeraldNeon else EmeraldDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "AuraSync establishes a direct, encrypted, high-throughput peer-to-peer relay between your primary phone and your secondary Google Photos hub. No third-party cloud servers, zero subscription fees, and 100% cryptographic integrity.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Community & Support Channels:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) EmeraldNeon else EmeraldDark
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openExternalUrl(context, "https://github.com/sivaprasad2003/AuraSync") },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldNeon)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GitHub", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { openExternalUrl(context, "https://t.me/aurasyncapp") },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldNeon)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Telegram", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // STEP 2: CHOOSE DEVICE ROLE PAGE (AFTER WELCOME)
                    Text(
                        text = "Select how this device will function in your AuraSync setup:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Primary Sender Option Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRole = DeviceRole.PRIMARY_SENDER },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedRole == DeviceRole.PRIMARY_SENDER) {
                                EmeraldNeon.copy(alpha = if (isDark) 0.12f else 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = BorderStroke(
                            width = if (selectedRole == DeviceRole.PRIMARY_SENDER) 2.dp else 1.dp,
                            color = if (selectedRole == DeviceRole.PRIMARY_SENDER) EmeraldNeon else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRole == DeviceRole.PRIMARY_SENDER,
                                onClick = { selectedRole = DeviceRole.PRIMARY_SENDER },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldNeon)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = EmeraldNeon,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Primary Sender Phone",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Your daily driver phone. Scans local photos and videos, deduplicates with SHA-256, and uploads to Hub.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Secondary Google Hub Option Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRole = DeviceRole.SECONDARY_HUB },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedRole == DeviceRole.SECONDARY_HUB) {
                                EmeraldNeon.copy(alpha = if (isDark) 0.12f else 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = BorderStroke(
                            width = if (selectedRole == DeviceRole.SECONDARY_HUB) 2.dp else 1.dp,
                            color = if (selectedRole == DeviceRole.SECONDARY_HUB) EmeraldNeon else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRole == DeviceRole.SECONDARY_HUB,
                                onClick = { selectedRole = DeviceRole.SECONDARY_HUB },
                                colors = RadioButtonDefaults.colors(selectedColor = EmeraldNeon)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = null,
                                        tint = EmeraldNeon,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Secondary Google Hub Phone",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Home receiver phone (e.g. Pixel). Receives media files and automatically backs them up to unlimited Google Photos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Tip: You can change this role anytime later from the side menu (hamburger icon).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (currentStep == 1) {
                Button(
                    onClick = { currentStep = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Next", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onRoleConfirmed(selectedRole) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Get Started", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (currentStep == 2) {
                TextButton(
                    onClick = { currentStep = 1 },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Back")
                }
            }
        }
    )
}

@Composable
private fun AboutSyncProModal(
    onDismiss: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldNeon)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "About AuraSync Enterprise",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) SyncProGradients.EmeraldIndigoHero else SyncProGradients.EmeraldHeroLight)
                        .border(1.dp, EmeraldNeon.copy(alpha = if (isDark) 0.35f else 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Enterprise P2P Unlimited Google Photos Relay",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Author / Architect: Siva Prasad\nVersion: 1.0.0-ENTERPRISE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) EmeraldNeon else EmeraldDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "• Dual-Role Engine: Primary Sender & Secondary Unlimited Google Photos Hub in a single APK.\n" +
                            "• Two-Tier SHA-256 Deduplication & 64KB Chunked Socket I/O.\n" +
                            "• Zero-Config mDNS + Seamless Tailscale / WAN Handover.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = "Official Community Channels:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldNeon
                )

                OutlinedButton(
                    onClick = { openExternalUrl(context, "https://github.com/sivaprasad2003/AuraSync") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmeraldNeon)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Official GitHub Repository", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { openExternalUrl(context, "https://t.me/aurasyncapp") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmeraldNeon)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Official Telegram Channel", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { openExternalUrl(context, "https://github.com/sivaprasad2003/AuraSync") },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Star on GitHub & Support Project", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ConfigExportImportModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exportedJson by viewModel.exportedConfigJson.collectAsState()
    var importField by rememberSaveable(exportedJson) { mutableStateOf(exportedJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = EmeraldNeon)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Config Export & Import",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Backup or restore paired Hub tokens, schedules, and folder filters as JSON:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CrimsonAlert.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Security Notice: Exported JSON contains end-to-end pairing tokens. Store it securely and never share it publicly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonAlert,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                OutlinedTextField(
                    value = importField,
                    onValueChange = { importField = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.importConfigJson(importField)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text("Import JSON", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SyncProConfig", importField))
                        Toast.makeText(context, "Configuration copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(42.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text("Copy")
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun DiagnosticLogsModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val logs by viewModel.diagnosticLogs.collectAsState()
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = EmeraldNeon)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Diagnostic & Audit Logs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Text(
                                text = "No diagnostic events recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(logs, key = { it.id }) { log ->
                            Text(
                                text = "[${formatter.format(Date(log.timestamp))}] [${log.level}/${log.tag}] ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.clearDiagnosticLogs() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text("Clear Logs", color = CrimsonAlert)
            }
        }
    )
}

@Composable
private fun TelemetryStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun format12HourTime(hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format(Locale.US, "%02d:%02d %s", h, minute, amPm)
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

@Composable
private fun MediaPreviewDialog(
    media: PreviewMediaData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var previewBitmap by remember(media) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(media) { mutableStateOf(true) }

    val isVideo = media.title.substringAfterLast('.', "").lowercase() in
            setOf("mp4", "mov", "mkv", "webm", "3gp", "avi")

    LaunchedEffect(media) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val bmp = runCatching {
                if (!media.contentUri.isNullOrBlank()) {
                    val uri = Uri.parse(media.contentUri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            context.contentResolver.loadThumbnail(uri, Size(800, 800), null)
                        } catch (_: Exception) {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val options = BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }
                                BitmapFactory.decodeStream(stream, null, options)
                                options.inSampleSize = calculateInSampleSize(options, 800, 800)
                                options.inJustDecodeBounds = false
                                context.contentResolver.openInputStream(uri)?.use { secondStream ->
                                    BitmapFactory.decodeStream(secondStream, null, options)
                                }
                            }
                        }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeStream(stream, null, options)
                        }
                    }
                } else if (!media.filePath.isNullOrBlank()) {
                    val file = File(media.filePath)
                    if (file.exists() && file.canRead()) {
                        if (isVideo) {
                            @Suppress("DEPRECATION")
                            ThumbnailUtils.createVideoThumbnail(media.filePath, MediaStore.Images.Thumbnails.MINI_KIND)
                        } else {
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeFile(media.filePath, options)
                            options.inSampleSize = calculateInSampleSize(options, 800, 800)
                            options.inJustDecodeBounds = false
                            BitmapFactory.decodeFile(media.filePath, options)
                        }
                    } else null
                } else null
            }.getOrNull()
            previewBitmap = bmp
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.Image,
                    contentDescription = null,
                    tint = EmeraldNeon
                )
                Text(
                    text = media.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(color = EmeraldNeon)
                        }
                        previewBitmap != null -> {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = media.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                            if (isVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = if (isVideo) "Video File Preview" else "Preview Unavailable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Size: ${formatBytes(media.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (media.bucketOrSource.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Folder / Node: ${media.bucketOrSource}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (media.timestamp > 0) {
                            val formattedDate = remember(media.timestamp) {
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(media.timestamp))
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Timestamp: $formattedDate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!media.sha256.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SHA-256: ${media.sha256}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = EmeraldNeon
                            )
                        }
                        if (!media.filePath.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Path: ${media.filePath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    runCatching {
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            val mime = if (isVideo) "video/*" else "image/*"
                            if (!media.contentUri.isNullOrBlank()) {
                                setDataAndType(Uri.parse(media.contentUri), mime)
                            } else if (!media.filePath.isNullOrBlank()) {
                                val file = File(media.filePath)
                                val fileUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                setDataAndType(fileUri, mime)
                            }
                        }
                        context.startActivity(Intent.createChooser(viewIntent, "Open media with"))
                    }.onFailure {
                        Toast.makeText(context, "Could not open viewer: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open In Viewer", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ConnectedDeviceRow(
    device: ConnectedDeviceEntity,
    onConfigure: () -> Unit
) {
    val (statusBadge, activeFile) = remember(device.syncStatus) {
        val raw = device.syncStatus.trim()
        when {
            raw.startsWith("Receiving ", ignoreCase = true) -> "Receiving" to raw.removePrefix("Receiving ").trim()
            raw.startsWith("Sending ", ignoreCase = true) -> "Sending" to raw.removePrefix("Sending ").trim()
            raw.startsWith("Syncing", ignoreCase = true) -> {
                val remainder = raw.removePrefix("Syncing").trim().removePrefix(":").trim()
                "Syncing" to remainder.ifEmpty { null }
            }
            raw.equals("Connected (Hub)", ignoreCase = true) -> "Hub Master" to null
            else -> raw to null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmeraldNeon.copy(alpha = 0.15f))
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = if (device.role == "SECONDARY_HUB") Icons.Default.Hub else Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = EmeraldNeon,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = when (statusBadge.lowercase()) {
                            "syncing", "receiving", "sending" -> EmeraldNeon.copy(alpha = 0.2f)
                            "active", "hub master" -> EmeraldNeon.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (statusBadge.lowercase()) {
                                "syncing", "receiving", "sending" -> EmeraldNeon
                                "active", "hub master" -> EmeraldNeon
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                if (!activeFile.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldNeon.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = activeFile,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldNeon,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val lastSyncText = remember(device.lastSyncedAt) {
                    if (device.lastSyncedAt <= 0L) {
                        "Last synced: Never"
                    } else {
                        val diffMs = System.currentTimeMillis() - device.lastSyncedAt
                        when {
                            diffMs < 60_000L -> "Synced just now"
                            diffMs < 3600_000L -> "Synced ${diffMs / 60_000L}m ago"
                            diffMs < 86400_000L -> "Synced ${diffMs / 3600_000L}h ago"
                            else -> {
                                val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a", Locale.US)
                                "Synced " + sdf.format(java.util.Date(device.lastSyncedAt))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.ipAddress,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.lastSyncedAt > 0L) EmeraldNeon else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (device.lastSyncedAt > 0L) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val capLabel = if (device.bandwidthLimitKbps <= 0) "Cap: Uncapped" else "Cap: ${device.bandwidthLimitKbps / 1024f} MB/s"
                    Text(
                        text = capLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldNeon,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Daily Sync: ${device.scheduledSync}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formatBytes(device.trafficBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                onClick = onConfigure,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Configure Device",
                        tint = EmeraldNeon,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetySettingsModal(
    viewModel: MainViewModel,
    onRequestBatteryExemption: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val safety by viewModel.safetyStatus.collectAsState()
    val tempCutoff by viewModel.tempCutoffCelsius.collectAsState()
    val batteryCutoff by viewModel.batteryCutoffPercent.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = EmeraldNeon,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Hardware Safety & Doze",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Status Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Live Hardware Telemetry",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldNeon
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Battery: ${safety.batteryPercent}% (${if (safety.isCharging) "Charging" else "Unplugged"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Temperature: ${String.format(Locale.US, "%.1f°C", safety.temperatureCelsius)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Status: ${if (safety.isSafeToSync) "All hardware guards normal" else "Safety guard tripped (sync paused)"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (safety.isSafeToSync) EmeraldNeon else CrimsonAlert,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Battery Optimization Exemption
                FilledTonalButton(
                    onClick = {
                        onRequestBatteryExemption()
                        Toast.makeText(context, "Opening battery settings...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmeraldNeon)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exempt from Battery Optimization", fontWeight = FontWeight.SemiBold)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Temperature Cutoff Slider (Manual)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Thermal Cutoff Threshold", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format(Locale.US, "%.0f°C", tempCutoff),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonAlert
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Transfers automatically pause when device battery temperature reaches or exceeds this limit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = tempCutoff,
                        onValueChange = {
                            viewModel.setTempCutoff(it)
                        },
                        valueRange = 35f..55f,
                        steps = 0
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Battery Cutoff Slider (Manual)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Low-Battery Cutoff Threshold", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "$batteryCutoff%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldNeon
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Transfers automatically pause when unplugged and battery drops below this percentage.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = batteryCutoff.toFloat(),
                        onValueChange = {
                            viewModel.setBatteryCutoff(it.roundToInt())
                        },
                        valueRange = 5f..35f,
                        steps = 0
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Toast.makeText(context, "Safety thresholds saved", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DeviceConfigModal(
    device: ConnectedDeviceEntity,
    onSaveConfig: (bandwidthLimitKbps: Int, scheduleHour: Int, scheduleMinute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedLimit by rememberSaveable { mutableIntStateOf(device.bandwidthLimitKbps) }
    var scheduleHour by rememberSaveable { mutableIntStateOf(device.scheduleHour) }
    var scheduleMinute by rememberSaveable { mutableIntStateOf(device.scheduleMinute) }
    var showDeviceTimePicker by rememberSaveable { mutableStateOf(false) }

    if (showDeviceTimePicker) {
        SyncProTimePickerDialog(
            title = "Set Automated Sync Timer",
            initialHour = scheduleHour,
            initialMinute = scheduleMinute,
            onConfirm = { h, m ->
                scheduleHour = h
                scheduleMinute = m
            },
            onDismiss = { showDeviceTimePicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = EmeraldNeon)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Device Network & Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Device Summary Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "IP Address: ${device.ipAddress} • Role: ${device.role}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Synced Traffic: ${formatBytes(device.trafficBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldNeon,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Section 1: Maximum Relay Bandwidth (Matches Daily Automated Discrete Presets Slider)
                val bandwidthOptions = remember { listOf(1024, 2560, 5120, 10240, 20480, 0) }
                val currentSliderIndex = remember(selectedLimit) {
                    val idx = bandwidthOptions.indexOf(selectedLimit)
                    if (idx >= 0) idx.toFloat() else (bandwidthOptions.size - 1).toFloat()
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Relay Bandwidth Limit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = if (selectedLimit <= 0) EmeraldNeon.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (selectedLimit <= 0) "Unlimited (Max)" else "${String.format(Locale.US, "%.1f", selectedLimit / 1024f)} MB/s (${selectedLimit} KB/s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedLimit <= 0) EmeraldNeon else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (selectedLimit <= 0)
                            "Uncapped: Maximum local Wi-Fi / WAN network throughput."
                        else
                            "Throttled to ${selectedLimit} KB/s to preserve device network resources.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = currentSliderIndex,
                        onValueChange = { pos ->
                            val chosen = bandwidthOptions[pos.roundToInt().coerceIn(0, bandwidthOptions.size - 1)]
                            selectedLimit = chosen
                        },
                        valueRange = 0f..(bandwidthOptions.size - 1).toFloat(),
                        steps = bandwidthOptions.size - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldNeon,
                            activeTrackColor = EmeraldNeon,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Section 2: Automated Sync Schedule Timer (Matches Daily Automated Sync Schedule)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automated Sync Schedule Timer",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Daily automated sync at ${format12HourTime(scheduleHour, scheduleMinute)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showDeviceTimePicker = true
                        },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(format12HourTime(scheduleHour, scheduleMinute))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveConfig(selectedLimit, scheduleHour, scheduleMinute)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Save & Sync to Device", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}


@Composable
private fun StorageSafetyModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val safety by viewModel.safetyStatus.collectAsState()
    val storageCutoffGb by viewModel.storageCutoffGb.collectAsState()
    var selectedCutoff by remember(storageCutoffGb) { mutableStateOf(storageCutoffGb) }
    val context = LocalContext.current

    val totalBytes = safety.totalStorageBytes
    val availBytes = safety.availableStorageBytes
    val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)
    val usedRatio = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldNeon.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = EmeraldNeon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text("Storage & Safety Guard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Manage storage threshold for automated transfers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Storage Bar Breakdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Used: ${formatBytes(usedBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Free: ${formatBytes(availBytes)}", style = MaterialTheme.typography.labelSmall, color = EmeraldNeon, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { usedRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (safety.isLowStorageTripped) CrimsonAlert else EmeraldNeon,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBytes(totalBytes), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Editable Storage Cutoff Threshold Slider & Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Minimum Free Storage Cutoff", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${String.format(Locale.US, "%.1f", selectedCutoff)} GB",
                            style = MaterialTheme.typography.titleSmall,
                            color = EmeraldNeon,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Transfers will pause/reject when available storage falls below this threshold to safeguard host filesystem stability.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = selectedCutoff,
                        onValueChange = { selectedCutoff = (kotlin.math.round(it * 2f) / 2f) },
                        valueRange = 0.5f..10.0f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldNeon,
                            activeTrackColor = EmeraldNeon,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Preset Chips (0.5GB, 1GB, 2GB, 3GB, 5GB)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5f, 1.0f, 2.0f, 3.0f, 5.0f).forEach { preset ->
                            val isChosen = kotlin.math.abs(selectedCutoff - preset) < 0.05f
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCutoff = preset },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) EmeraldNeon else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isChosen) EmeraldNeon else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = if (preset == 0.5f) "0.5G" else "${preset.toInt()}G",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isChosen) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.setStorageCutoffGb(selectedCutoff)
                    Toast.makeText(context, "Storage cutoff updated to ${String.format(Locale.US, "%.1f", selectedCutoff)} GB", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Save Cutoff Limit", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun HubVaultDetailsModal(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val role by viewModel.deviceRole.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val hubReceivedCount by viewModel.hubReceivedCount.collectAsState()
    val hubReceivedBytes by viewModel.hubReceivedBytes.collectAsState()
    val syncedLocalCount by viewModel.syncedLocalCount.collectAsState()
    val syncedLocalBytes by viewModel.syncedLocalBytes.collectAsState()
    val recentHubFiles by viewModel.recentHubFiles.collectAsState()
    val recentUploadedFiles by viewModel.recentUploadedFiles.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()

    val totalFiles = if (role == DeviceRole.SECONDARY_HUB) hubReceivedCount else syncedLocalCount
    val totalTraffic = if (role == DeviceRole.SECONDARY_HUB) hubReceivedBytes else syncedLocalBytes

    val latestTimestamp = remember(recentHubFiles, recentUploadedFiles, connectedDevices) {
        val fromRecentHub = recentHubFiles.firstOrNull()?.receivedAt ?: 0L
        val fromRecentUpload = recentUploadedFiles.firstOrNull()?.lastModified ?: 0L
        val fromDevices = connectedDevices.maxOfOrNull { it.lastSyncedAt } ?: 0L
        maxOf(fromRecentHub, maxOf(fromRecentUpload, fromDevices))
    }

    val lastSyncFormatted = remember(latestTimestamp) {
        if (latestTimestamp <= 0L) {
            "Never synced"
        } else {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.US)
            sdf.format(java.util.Date(latestTimestamp))
        }
    }

    val isSyncActive = telemetry.isRunning || telemetry.queuedFileCount > 0 || telemetry.isHubBusyWithSync
    val currentStatusText = if (isSyncActive) "Syncing" else "Standby (Free)"
    val currentDeviceSending = if (isSyncActive) {
        if (telemetry.sourceDeviceName.isNotBlank()) telemetry.sourceDeviceName else "Active Node"
    } else {
        "None (Idle)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldNeon.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = EmeraldNeon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text("Hub Vault Storage Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Realtime vault statistics & peer relay status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Key Metrics Grid: Total Files Received & Total Traffic
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Files Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$totalFiles Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldNeon)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Traffic", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(formatBytes(totalTraffic), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldNeon)
                        }
                    }
                }

                // Current Live Status & Sending Device
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isSyncActive) EmeraldNeon else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Hub Status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSyncActive) EmeraldNeon.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = currentStatusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSyncActive) EmeraldNeon else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Device Sending / Sync From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = currentDeviceSending,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSyncActive) EmeraldNeon else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isSyncActive) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Relay Speed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", telemetry.currentSpeedMbps)} Mbps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Last Sync Timestamp Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Last Synced Time", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(lastSyncFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = EmeraldNeon, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}
