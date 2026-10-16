package com.enterprise.syncpro.ui

import android.Manifest
import android.app.TimePickerDialog
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.enterprise.syncpro.data.DeviceRole
import com.enterprise.syncpro.media.MediaBucketSummary
import com.enterprise.syncpro.media.QrCodeCameraAnalyzer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private enum class BottomNavTab(val title: String) {
    DASHBOARD("Dashboard"),
    FOLDERS("Folders"),
    NETWORK("Network")
}

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val toastMessage by viewModel.uiToastMessage.collectAsState()

            LaunchedEffect(toastMessage) {
                toastMessage?.let { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToastMessage()
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
            .setTitle("Sync Pro Cryptographic Vault")
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
                    "Sync Pro is already exempted from Doze & Battery Optimizations",
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
    var showConfigBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticLogsDialog by rememberSaveable { mutableStateOf(false) }

    val showFirstLaunchOnboarding by viewModel.showFirstLaunchOnboarding.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val deviceRole by viewModel.deviceRole.collectAsState()

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

    // First-Time Fresh Installation Onboarding / Greeting Box Modal
    if (showFirstLaunchOnboarding || showAboutModal) {
        WelcomeOnboardingAndAboutModal(
            isFirstInstall = showFirstLaunchOnboarding,
            onDismiss = {
                if (showFirstLaunchOnboarding) {
                    viewModel.dismissFirstLaunchOnboarding()
                }
                showAboutModal = false
            }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SyncProGradients.EmeraldIndigoHero)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Sync Pro Logo",
                            tint = EmeraldNeon,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SYNC PRO",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Unlimited Google Photos P2P Relay",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
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
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Master Dark / Light Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = EmeraldNeon)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Master Obsidian Dark Mode", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text("Switch Role (Primary / Hub)") },
                    selected = false,
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    onClick = {
                        val nextRole = if (deviceRole == DeviceRole.PRIMARY_SENDER) {
                            DeviceRole.SECONDARY_HUB
                        } else {
                            DeviceRole.PRIMARY_SENDER
                        }
                        viewModel.setDeviceRole(nextRole)
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text("GitHub Repository") },
                    selected = false,
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    onClick = {
                        openExternalUrl(context, "https://github.com/your-org/sync-pro-android")
                        scope.launch { drawerState.close() }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("About Sync Pro & Support") },
                    selected = false,
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showAboutModal = true
                        scope.launch { drawerState.close() }
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
                                text = "Sync Pro",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (deviceRole == DeviceRole.SECONDARY_HUB) {
                                    "SECONDARY HUB • UNLIMITED PHOTOS RECEIVER"
                                } else {
                                    "PRIMARY SENDER • SHA-256 DEDUPLICATED RELAY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refreshAllState() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Telemetry")
                        }
                        IconButton(onClick = { showAboutModal = true }) {
                            Icon(Icons.Default.Info, contentDescription = "About Sync Pro")
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
                when (selectedTab) {
                    BottomNavTab.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onRequestBatteryExemption = onRequestIgnoreBatteryOptimizations
                    )
                    BottomNavTab.FOLDERS -> FoldersScreen(viewModel = viewModel)
                    BottomNavTab.NETWORK -> NetworkAndVaultScreen(
                        viewModel = viewModel,
                        onLaunchBiometricPrompt = onLaunchBiometricPrompt
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    viewModel: MainViewModel,
    onRequestBatteryExemption: () -> Unit
) {
    val context = LocalContext.current
    val role by viewModel.deviceRole.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val safety by viewModel.safetyStatus.collectAsState()
    val scheduleHour by viewModel.scheduleHour.collectAsState()
    val scheduleMinute by viewModel.scheduleMinute.collectAsState()
    val daytimeKbps by viewModel.daytimeLimitKbps.collectAsState()
    val priorityWeight by viewModel.devicePriorityWeight.collectAsState()
    val instantCapture by viewModel.instantCaptureSync.collectAsState()
    val syncedLocalCount by viewModel.syncedLocalCount.collectAsState()
    val syncedLocalBytes by viewModel.syncedLocalBytes.collectAsState()
    val hubReceivedCount by viewModel.hubReceivedCount.collectAsState()
    val hubReceivedBytes by viewModel.hubReceivedBytes.collectAsState()
    val recentHubFiles by viewModel.recentHubFiles.collectAsState()

    val animatedProgress by animateFloatAsState(
        targetValue = telemetry.activeFileProgress.coerceIn(0f, 1f),
        label = "transferProgress"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dual-Role Runtime Switcher Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dual-Role Engine Selector",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = role == DeviceRole.PRIMARY_SENDER,
                            onClick = { viewModel.setDeviceRole(DeviceRole.PRIMARY_SENDER) },
                            label = { Text("Primary Sender Node") },
                            leadingIcon = {
                                if (role == DeviceRole.PRIMARY_SENDER) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = role == DeviceRole.SECONDARY_HUB,
                            onClick = { viewModel.setDeviceRole(DeviceRole.SECONDARY_HUB) },
                            label = { Text("Secondary Google Hub") },
                            leadingIcon = {
                                if (role == DeviceRole.SECONDARY_HUB) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Hero Live Circular Ring & Transfer Telemetry Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SyncProGradients.EmeraldIndigoHero)
                    .border(
                        width = 1.dp,
                        color = EmeraldNeon.copy(alpha = 0.4f),
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
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Route: ${telemetry.activeEndpointRoute} • 64KB Direct I/O",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
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
                                trackColor = Color.White.copy(alpha = 0.15f),
                                strokeWidth = 8.dp
                            )
                            Text(
                                text = "${(animatedProgress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStatPill(
                            label = "Throughput",
                            value = String.format("%.2f MB/s", telemetry.currentSpeedMbps)
                        )
                        TelemetryStatPill(
                            label = "Priority Queue",
                            value = "${telemetry.queuedFileCount} items"
                        )
                        TelemetryStatPill(
                            label = "Deduplicated",
                            value = "${telemetry.skippedDuplicateFiles} skipped"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startFullBatchSyncNow() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.togglePauseResume() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (telemetry.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (telemetry.isPaused) "Resume" else "Pause",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Two-Tier Vault Ledger Metrics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldNeon)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (role == DeviceRole.SECONDARY_HUB) "$hubReceivedCount Files" else "$syncedLocalCount Files",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (role == DeviceRole.SECONDARY_HUB) {
                                "Committed to Hub DCIM/Camera (${formatBytes(hubReceivedBytes)})"
                            } else {
                                "Verified in Tier-1 Index (${formatBytes(syncedLocalBytes)})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = IndigoNeon)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatBytes(safety.availableStorageBytes),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (safety.isLowStorageTripped) CrimsonAlert else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (safety.isLowStorageTripped) {
                                "ALERT: Below 1GB Guard!"
                            } else {
                                "Free Storage (>1GB Safe)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (safety.isLowStorageTripped) CrimsonAlert else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Thermal, Battery & Standby Safety Switches
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thermal, Battery & Doze Safety Guards",
                            style = MaterialTheme.typography.titleMedium
                        )
                        AssistChip(
                            onClick = onRequestBatteryExemption,
                            label = { Text("Exempt Doze") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Battery: ${safety.batteryPercent}% (${if (safety.isCharging) "Charging" else "Unplugged"}) • " +
                                "Temp: ${String.format("%.1f°C", safety.temperatureCelsius)} (Cutoff: 45.0°C)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!safety.isSafeToSync) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Safety Switch Active: Automated transfers paused until hardware returns to safe operating bounds.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonAlert
                        )
                    }
                }
            }
        }

        // Automation, Daily TimePickerDialog Scheduler & Bandwidth Throttling
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Automated Sync Schedule",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Scheduled Daily at %02d:%02d (Uncapped 02:00-06:00 AM)",
                                    scheduleHour,
                                    scheduleMinute
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        viewModel.updateDailySchedule(selectedHour, selectedMinute)
                                    },
                                    scheduleHour,
                                    scheduleMinute,
                                    true
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(String.format(Locale.US, "%02d:%02d", scheduleHour, scheduleMinute))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Daytime Bandwidth Throttle Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Daytime Bandwidth Throttle", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${String.format("%.1f", daytimeKbps / 1024f)} MB/s (${daytimeKbps} KB/s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldNeon
                        )
                    }
                    Slider(
                        value = daytimeKbps.toFloat(),
                        onValueChange = { viewModel.updateDaytimeBandwidthLimit(it.roundToInt()) },
                        valueRange = 512f..20480f,
                        steps = 19
                    )

                    // Priority Weight Selector (1..5)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device PriorityQueue Weight (1-5)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { weight ->
                                FilterChip(
                                    selected = priorityWeight == weight,
                                    onClick = { viewModel.updateDevicePriorityWeight(weight) },
                                    label = { Text("$weight") }
                                )
                            }
                        }
                    }

                    // Instant Capture MediaStore Trigger Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Instant Camera Capture Trigger", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Watches MediaStore URI and immediately relays new photos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = instantCapture,
                            onCheckedChange = { viewModel.toggleInstantCapture(it) }
                        )
                    }
                }
            }
        }

        // Secondary Hub Recent Committed Files Ledger
        if (recentHubFiles.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Atomic Commits to Hub DCIM/Camera",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(recentHubFiles.take(10), key = { it.sha256 }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.finalFileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "SHA-256: ${entry.sha256.take(16)}... • From ${entry.sourceDevice}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatBytes(entry.fileSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldNeon
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersScreen(viewModel: MainViewModel) {
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Granular Source Folders & Urgent Queue-Jumping",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Check folders for automated background syncing, or tap 'Upload Immediately' to dispatch Express Priority-5 packets that jump ahead of the batch queue.",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                },
                onInstantUploadFolder = {
                    viewModel.triggerUrgentInstantUpload(summary.sampleFiles, summary.bucketName)
                },
                onInstantUploadSingleFile = { singleTask ->
                    viewModel.triggerUrgentInstantUpload(listOf(singleTask), singleTask.fileName)
                }
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
    onInstantUploadSingleFile: (com.enterprise.syncpro.network.RelayQueueTask) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Text(
                        text = summary.bucketName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${summary.relativePathHint} • ${summary.fileCount} files (${formatBytes(summary.totalBytes)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onInstantUploadFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoNeon)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload Now", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (expanded && summary.sampleFiles.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Tap any file for Urgent Priority-5 Queue-Jump:",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldNeon
                )
                Spacer(modifier = Modifier.height(6.dp))
                summary.sampleFiles.take(6).forEach { fileTask ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${fileTask.fileName} (${formatBytes(fileTask.fileSize)})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val isPinConfigured by viewModel.isPinConfigured.collectAsState()
    val pairingToken by viewModel.pairingToken.collectAsState()
    val hubQrBitmap by viewModel.hubQrBitmap.collectAsState()
    val discoveredHubs by viewModel.discoveredHubs.collectAsState()
    val localLanIp by viewModel.localLanIp.collectAsState()
    val tailscaleIp by viewModel.tailscaleIp.collectAsState()
    val currentSsid by viewModel.currentSsid.collectAsState()
    val trustedSsids by viewModel.trustedSsids.collectAsState()
    val restrictWifi by viewModel.restrictToTrustedWifi.collectAsState()

    var pinInput by rememberSaveable { mutableStateOf("") }
    var customWanInput by rememberSaveable { mutableStateOf(tailscaleIp ?: "") }
    var newSsidInput by rememberSaveable { mutableStateOf("") }
    var showCameraScanner by rememberSaveable { mutableStateOf(false) }

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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                        Text(
                            text = "Protected by AES-256-GCM EncryptedSharedPreferences & Android BiometricPrompt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                            label = { Text("Enter 6-Digit PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.verifyOrSetVaultPin(pinInput)
                                    pinInput = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPinConfigured) "Unlock PIN" else "Save PIN")
                            }

                            OutlinedButton(
                                onClick = onLaunchBiometricPrompt,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Biometric")
                            }
                        }
                    }
                }
            }
        } else {
            // Unlocked Vault: Persistent QR Pairing Generator & CameraX Scanner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Zero-Re-Entry Persistent QR Pairing",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldNeon
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "LAN IP: $localLanIp • Tailscale/Mesh IP: ${tailscaleIp ?: "Not Detected"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customWanInput,
                                onValueChange = { customWanInput = it },
                                label = { Text("Tailscale (100.x.x.x) / DDNS / WAN IP") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { viewModel.regenerateHubPairingQr(customWanInput) }) {
                                Icon(Icons.Default.QrCode2, contentDescription = null)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        hubQrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Hub Pairing QR Code",
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showCameraScanner = !showCameraScanner },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoNeon),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (showCameraScanner) "Close Camera QR Scanner" else "Scan Hub QR Code (Primary Node)")
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

                        pairingToken?.let { token ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Paired Hub: ${token.hubDeviceName} (${token.localIp}:${token.port} | Fallback: ${token.tailscaleOrWanHost})",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
                            )
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "mDNS Zero-Config Discovery (_syncvault._tcp.)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (discoveredHubs.isEmpty()) {
                        Text(
                            text = "Scanning local Wi-Fi multicast for active Secondary Hubs...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        discoveredHubs.forEach { endpoint ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(endpoint.serviceName, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${endpoint.hostAddress}:${endpoint.port}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                AssistChip(
                                    onClick = {
                                        viewModel.pairWithDiscoveredMdnsHub(
                                            endpoint = endpoint,
                                            sharedSecretHex = pairingToken?.e2eeSharedSecretHex ?: "",
                                            pin = pinInput
                                        )
                                    },
                                    label = { Text("Use Endpoint") }
                                )
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                            Text(
                                text = "Current SSID: ${currentSsid ?: "Mobile Data / Unknown"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldNeon
                            )
                        }
                        Switch(
                            checked = restrictWifi,
                            onCheckedChange = { viewModel.setRestrictToTrustedWifi(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSsidInput,
                            onValueChange = { newSsidInput = it },
                            label = { Text("Home Wi-Fi SSID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newSsidInput.isNotBlank()) {
                                    viewModel.addTrustedSsid(newSsidInput)
                                    newSsidInput = ""
                                } else if (!currentSsid.isNullOrBlank()) {
                                    viewModel.addTrustedSsid(currentSsid!!)
                                }
                            }
                        ) {
                            Text("Add SSID")
                        }
                    }

                    if (trustedSsids.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            trustedSsids.forEach { ssid ->
                                AssistChip(
                                    onClick = { viewModel.removeTrustedSsid(ssid) },
                                    label = { Text("$ssid ✕") }
                                )
                            }
                        }
                    }
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
private fun WelcomeOnboardingAndAboutModal(
    isFirstInstall: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hub, contentDescription = null, tint = EmeraldNeon)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFirstInstall) "Welcome to Sync Pro" else "About Sync Pro Enterprise",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SyncProGradients.EmeraldIndigoHero)
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Enterprise P2P Unlimited Google Photos Relay",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Author / Architect: [Developer Name Placeholder]\nVersion: 1.0.0-ENTERPRISE (Zero-Compromise Build)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldNeon
                        )
                    }
                }

                Text(
                    text = "• Dual-Role Engine: Primary Sender & Secondary Unlimited Google Photos Hub in a single APK.\n" +
                            "• Two-Tier SHA-256 Deduplication & 64KB Chunked Socket I/O.\n" +
                            "• Zero-Config mDNS + Seamless Tailscale / WAN Handover.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedButton(
                    onClick = { openExternalUrl(context, "https://github.com/your-org/sync-pro-android") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Official GitHub Repository")
                }

                OutlinedButton(
                    onClick = { openExternalUrl(context, "https://t.me/SyncProOfficialGroup") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Official Telegram Group")
                }

                Button(
                    onClick = { openExternalUrl(context, "https://github.com/sponsors/your-org") },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Support & Donate to Project", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isFirstInstall) "Launch Sync Pro" else "Close")
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
        title = { Text("Enterprise Config Export & Import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Backup or restore paired Hub tokens, schedules, and folder filters as JSON:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = importField,
                    onValueChange = { importField = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.importConfigJson(importField)
                    onDismiss()
                }
            ) {
                Text("Import JSON")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SyncProConfig", importField))
                        Toast.makeText(context, "Configuration copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) {
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
        title = { Text("Diagnostic & Security Audit Logs") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text("No diagnostic events recorded yet.", style = MaterialTheme.typography.bodyMedium)
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.clearDiagnosticLogs() }) {
                Text("Clear Logs")
            }
        }
    )
}

@Composable
private fun TelemetryStatPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
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

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}