package com.yuhanbae.shizuguard

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class AppEntry(
    val name: String,
    val packageName: String,
    val isEnabled: Boolean,
    val isSystem: Boolean,
    val ramMb: Float = 0f
)

class MainActivity : ComponentActivity() {

    private var guardService: IGuardService? = null
    private var isBound by mutableStateOf(false)
    private var shizukuReady by mutableStateOf(false)

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.yuhanbae.shizuguard", GuardUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("guard_service")
        .debuggable(false)
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            guardService = IGuardService.Stub.asInterface(service)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            guardService = null
            isBound = false
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        checkAndBindShizuku()
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
            bindGuardService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShizuGuardScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (isBound) {
            try {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            } catch (_: Exception) {}
        }
    }

    private fun checkAndBindShizuku() {
        if (!Shizuku.pingBinder()) {
            shizukuReady = false
            return
        }
        shizukuReady = true
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindGuardService()
        } else {
            Shizuku.requestPermission(1001)
        }
    }

    private fun bindGuardService() {
        if (!isBound) {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        }
    }

    private fun loadInstalledApps(showSystem: Boolean): List<AppEntry> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName && it.packageName != "moe.shizuku.privileged.api" }
            .filter { showSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map {
                AppEntry(
                    name = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    isEnabled = it.enabled,
                    isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(compareBy({ it.isEnabled }, { it.name.lowercase() }))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ShizuGuardScreen() {
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var showSystem by remember { mutableStateOf(false) }
        var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
        var panicSet by remember { mutableStateOf(PanicStore.getPanicPackages(this)) }
        var panicFrozen by remember { mutableStateOf(PanicStore.isPanicActive(this)) }
        var telemetryText by remember { mutableStateOf("Tap Telemetry to inspect RAM & Wake Locks") }
        var showTelemetryDialog by remember { mutableStateOf(false) }

        fun refresh() {
            scope.launch(Dispatchers.IO) {
                val list = loadInstalledApps(showSystem)
                withContext(Dispatchers.Main) { apps = list }
            }
        }

        LaunchedEffect(showSystem) { refresh() }

        val filtered = remember(apps, query) {
            if (query.isBlank()) apps
            else apps.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }

        if (showTelemetryDialog) {
            AlertDialog(
                onDismissRequest = { showTelemetryDialog = false },
                title = { Text("Live System Telemetry") },
                text = {
                    Text(
                        text = telemetryText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showTelemetryDialog = false }) { Text("Close") }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("ShizuGuard", fontWeight = FontWeight.Bold)
                            Text(
                                text = when {
                                    isBound -> "Shizuku Shell Bridge: ACTIVE"
                                    shizukuReady -> "Shizuku Ready — Awaiting Permission"
                                    else -> "Shizuku Not Running"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBound) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = isBound,
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val mem = guardService?.memorySummary ?: ""
                                    val wl = guardService?.topWakelocks ?: ""
                                    withContext(Dispatchers.Main) {
                                        telemetryText = "$mem

--- ACTIVE WAKELOCKS ---
$wl"
                                        showTelemetryDialog = true
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = "Telemetry")
                        }
                        IconButton(onClick = {
                            checkAndBindShizuku()
                            refresh()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 12.dp)
                    .fillMaxSize()
            ) {
                // Panic Freeze Banner Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (panicFrozen) MaterialTheme.colorScheme.errorContainer
                                         else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Panic Freeze Profile (${panicSet.size} apps)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Also toggleable from Quick Settings Tile",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Button(
                            enabled = isBound && panicSet.isNotEmpty(),
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val nextFreeze = !panicFrozen
                                    val count = guardService?.batchSetEnabled(panicSet.toList(), !nextFreeze) ?: 0
                                    PanicStore.setPanicActive(this@MainActivity, nextFreeze)
                                    withContext(Dispatchers.Main) {
                                        panicFrozen = nextFreeze
                                        refresh()
                                        Toast.makeText(
                                            this@MainActivity,
                                            if (nextFreeze) "Froze $count Panic apps" else "Thawed $count Panic apps",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text(if (panicFrozen) "Thaw All" else "Freeze All")
                        }
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search package or app name...") },
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show System Packages (${filtered.size})", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isInPanic = panicSet.contains(app.packageName)

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilterChip(
                                            selected = isInPanic,
                                            onClick = {
                                                panicSet = PanicStore.togglePanicPackage(this@MainActivity, app.packageName)
                                            },
                                            label = { Text("Panic") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        )
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(if (app.isEnabled) "ACTIVE" else "FROZEN") }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        enabled = isBound,
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val ok = guardService?.setPackageEnabled(app.packageName, !app.isEnabled) ?: false
                                                withContext(Dispatchers.Main) {
                                                    if (ok) refresh()
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        if (ok) (if (app.isEnabled) "Frozen" else "Unfrozen") else "Failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (app.isEnabled) "Freeze" else "Thaw", style = MaterialTheme.typography.labelSmall)
                                    }

                                    FilledTonalButton(
                                        enabled = isBound,
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val ok = guardService?.clampBackground(app.packageName, true) ?: false
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        if (ok) "Background & Wakelocks Clamped" else "Failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Clamp", style = MaterialTheme.typography.labelSmall)
                                    }

                                    OutlinedButton(
                                        enabled = isBound,
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val ok = guardService?.forceStopPackage(app.packageName) ?: false
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        if (ok) "Killed ${app.name}" else "Failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Kill", modifier = Modifier.size(16.dp))
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
