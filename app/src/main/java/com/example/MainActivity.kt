package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.PaymentDetailsDialog
import com.example.ui.components.SmsSimulatorDialog
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.logs.EventLogScreen
import com.example.ui.messages.SmsMessagesScreen
import com.example.ui.payments.PaymentsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.Theme
import kotlinx.coroutines.launch

enum class AppTab(val titleAr: String) {
    DASHBOARD("الرئيسية"),
    PAYMENTS("الحوالات"),
    MESSAGES("الرسائل"),
    LOGS("الأحداث"),
    SETTINGS("الإعدادات")
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GatewayApplication
        viewModel = MainViewModel(app.repository, app.configRepository)

        setContent {
            Theme {
                GatewayMainScreen(viewModel = viewModel, tokenAlreadyConfigured = app.configRepository.hasGatewayToken())
            }
        }
    }
}

@Composable
fun GatewayMainScreen(viewModel: MainViewModel, tokenAlreadyConfigured: Boolean = false) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tokenConfigured by remember { mutableStateOf(tokenAlreadyConfigured) }
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var isSimulatorOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // SMS Permissions Check
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.RECEIVE_SMS] == true
    }

    LaunchedEffect(Unit) {
        val permissionsToAsk = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToAsk.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToAsk.toTypedArray())
    }

    // React to feedback messages
    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    // BackHandler: Return to Dashboard if on another tab
    if (selectedTab != AppTab.DASHBOARD) {
        BackHandler {
            selectedTab = AppTab.DASHBOARD
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.DASHBOARD,
                    onClick = { selectedTab = AppTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text(AppTab.DASHBOARD.titleAr) },
                    modifier = Modifier.testTag("tab_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.PAYMENTS,
                    onClick = { selectedTab = AppTab.PAYMENTS },
                    icon = { Icon(Icons.Default.Payment, contentDescription = "Payments") },
                    label = { Text(AppTab.PAYMENTS.titleAr) },
                    modifier = Modifier.testTag("tab_payments")
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.MESSAGES,
                    onClick = { selectedTab = AppTab.MESSAGES },
                    icon = { Icon(Icons.Default.Sms, contentDescription = "Messages") },
                    label = { Text(AppTab.MESSAGES.titleAr) },
                    modifier = Modifier.testTag("tab_messages")
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.LOGS,
                    onClick = { selectedTab = AppTab.LOGS },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Logs") },
                    label = { Text(AppTab.LOGS.titleAr) },
                    modifier = Modifier.testTag("tab_logs")
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SETTINGS,
                    onClick = { selectedTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(AppTab.SETTINGS.titleAr) },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Banner if SMS permission not granted
            if (!hasSmsPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFB71C1C))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Permission Alert",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مطلوب صلاحية RECEIVE_SMS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "التطبيق يحتاج صلاحية قراءة SMS لاعتراض رسائل المحافظ تلقائياً.",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_SMS
                                    )
                                )
                            }
                        ) {
                            Text("منح الصلاحية", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                AppTab.DASHBOARD -> DashboardScreen(
                    state = uiState,
                    onToggleGateway = { viewModel.toggleGatewayActive(it) },
                    onCheckPosConnection = { viewModel.checkPosConnection() },
                    onSyncNow = { viewModel.syncPendingNow() },
                    onOpenSimulator = { isSimulatorOpen = true },
                    onSelectPayment = { paymentId -> viewModel.selectPaymentForDetails(paymentId) }
                )

                AppTab.PAYMENTS -> PaymentsScreen(
                    state = uiState,
                    onFilterWallet = { viewModel.setWalletFilter(it) },
                    onSelectPayment = { paymentId -> viewModel.selectPaymentForDetails(paymentId) }
                )

                AppTab.MESSAGES -> SmsMessagesScreen(
                    state = uiState,
                    onFilterWallet = { viewModel.setWalletFilter(it) }
                )

                AppTab.LOGS -> EventLogScreen(
                    logs = uiState.eventLogs,
                    onClearLogs = { viewModel.clearLogs() }
                )

                AppTab.SETTINGS -> SettingsScreen(
                    currentConfig = uiState.config,
                    tokenAlreadyConfigured = tokenConfigured,
                    onSaveToken = { value -> viewModel.saveGatewayToken(value); tokenConfigured = true },
                    onSaveConfig = { updated, pin, onSuccess, onError ->
                        viewModel.updateConfig(updated, pin, onSuccess, onError)
                    }
                )
            }
        }
    }

    // Payment Details Dialog Modal
    uiState.selectedPayment?.let { selected ->
        PaymentDetailsDialog(
            payment = selected,
            linkedMessages = uiState.selectedPaymentMessages,
            onDismiss = { viewModel.dismissPaymentDetails() },
            onRetry = { viewModel.retryPayment(selected) }
        )
    }

    // SMS Simulator Dialog Modal
    if (isSimulatorOpen) {
        SmsSimulatorDialog(
            onDismiss = { isSimulatorOpen = false },
            onSimulate = { sender, message ->
                viewModel.simulateSms(sender, message)
            }
        )
    }
}
