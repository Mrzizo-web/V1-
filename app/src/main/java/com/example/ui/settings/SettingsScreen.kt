package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.GatewayConfigEntity

@Composable
fun SettingsScreen(
    currentConfig: GatewayConfigEntity,
    onSaveConfig: (GatewayConfigEntity, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var isUnlocked by remember { mutableStateOf(false) }
    var pinDialogVisible by remember { mutableStateOf(!isUnlocked) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var successNotice by remember { mutableStateOf<String?>(null) }

    // Form states
    var deviceId by remember(currentConfig) { mutableStateOf(currentConfig.deviceId) }
    var posIp by remember(currentConfig) { mutableStateOf(currentConfig.posIpAddress) }
    var posPort by remember(currentConfig) { mutableStateOf(currentConfig.posPort.toString()) }
    var connectionMode by remember(currentConfig) { mutableStateOf(currentConfig.connectionMode) }
    var autoSync by remember(currentConfig) { mutableStateOf(currentConfig.isAutoSyncEnabled) }
    var maxRetries by remember(currentConfig) { mutableStateOf(currentConfig.maxRetryCount.toString()) }
    var retryDelay by remember(currentConfig) { mutableStateOf(currentConfig.retryDelaySeconds.toString()) }
    var jeebSender by remember(currentConfig) { mutableStateOf(currentConfig.jeebSenderKeyword) }
    var floosakSender by remember(currentConfig) { mutableStateOf(currentConfig.floosakSenderKeyword) }
    var jawaliSender by remember(currentConfig) { mutableStateOf(currentConfig.jawaliSenderKeyword) }
    var gatewayToken by remember(currentConfig) { mutableStateOf("") }
    var newPin by remember(currentConfig) { mutableStateOf("") }

    // PIN Authentication Dialog
    if (pinDialogVisible) {
        AlertDialog(
            onDismissRequest = { /* Require PIN to enter */ },
            icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked") },
            title = { Text(text = "حماية الإعدادات برمز PIN") },
            text = {
                Column {
                    Text(text = "إذا كان هذا أول تشغيل، أنشئ رمز PIN من 4 إلى 6 أرقام. وإلا أدخل رمز PIN الحالي للمدير.")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            enteredPin = it
                            pinError = null
                        },
                        label = { Text("رمز PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("pin_input_field")
                    )
                    if (pinError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if ((currentConfig.adminPin.isBlank() && enteredPin.isBlank()) || (!currentConfig.adminPin.isBlank() && enteredPin == currentConfig.adminPin)) {
                            isUnlocked = true
                            pinDialogVisible = false
                            pinError = null
                        } else {
                            pinError = "رمز PIN غير صحيح!"
                        }
                    },
                    modifier = Modifier.testTag("pin_confirm_button")
                ) {
                    Text("تأكيد")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "إعدادات الربط والشبكة",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تكوين عنوان كاشير POWER FEUL POS ومعلمات المزامنة",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = {
                isUnlocked = false
                pinDialogVisible = true
                enteredPin = ""
            }) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("قفل", fontSize = 12.sp)
            }
        }

        if (successNotice != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = successNotice!!,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Connection Card (POS IP & Port)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "عنوان جهاز الكاشير (POWER FEUL POS)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = posIp,
                    onValueChange = { posIp = it },
                    label = { Text("POS IP Address (Local Wi-Fi)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = posPort,
                    onValueChange = { posPort = it },
                    label = { Text("POS Port (الافتراضي 8080)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("معرف بوابة الهاتف (Gateway Device ID)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )
            }
        }

        // Sync and Retry Config Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "سلوك المزامنة وطابور Offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "المزامنة التلقائية (Auto Sync)", fontWeight = FontWeight.SemiBold)
                        Text(text = "إرسال الحوالات فور وصولها والمحاولة بالخلفية", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = { autoSync = it },
                        enabled = isUnlocked
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = maxRetries,
                        onValueChange = { maxRetries = it },
                        label = { Text("عدد المحاولات") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        enabled = isUnlocked
                    )
                    OutlinedTextField(
                        value = retryDelay,
                        onValueChange = { retryDelay = it },
                        label = { Text("فاصل الإعادة (ثواني)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        enabled = isUnlocked
                    )
                }
            }
        }

        // Wallet Keywords Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "كلمات تمييز مرسلي المحافظ (قابل للتعديل)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = jeebSender,
                    onValueChange = { jeebSender = it },
                    label = { Text("كلمة مرسل محفظة جيب") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = floosakSender,
                    onValueChange = { floosakSender = it },
                    label = { Text("كلمة مرسل محفظة فلوسك") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = jawaliSender,
                    onValueChange = { jawaliSender = it },
                    label = { Text("كلمة مرسل محفظة جوالي") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = gatewayToken,
                    onValueChange = { gatewayToken = it },
                    label = { Text("Gateway Token (اتركه فارغًا للإبقاء على الحالي)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )
            }
        }

        // Security PIN Change Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "رمز حماية الإعدادات (Admin PIN)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = { Text("رمز PIN الجديد") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isUnlocked
                )
            }
        }

        Button(
            onClick = {
                if (newPin.length !in 4..6 || !newPin.all { it.isDigit() }) {
                    successNotice = "يجب أن يكون PIN الجديد من 4 إلى 6 أرقام."
                    return@Button
                }
                val updated = currentConfig.copy(
                    deviceId = deviceId,
                    posIpAddress = posIp,
                    posPort = posPort.toIntOrNull() ?: 8080,
                    connectionMode = connectionMode,
                    isAutoSyncEnabled = autoSync,
                    maxRetryCount = maxRetries.toIntOrNull() ?: 5,
                    retryDelaySeconds = retryDelay.toIntOrNull() ?: 15,
                    jeebSenderKeyword = jeebSender,
                    floosakSenderKeyword = floosakSender,
                    jawaliSenderKeyword = jawaliSender,
                    gatewayToken = gatewayToken,
                    adminPin = newPin
                )
                onSaveConfig(
                    updated,
                    enteredPin,
                    {
                        successNotice = "تم حفظ الإعدادات بنجاح"
                    },
                    { errorMsg ->
                        successNotice = null
                    }
                )
            },
            enabled = isUnlocked,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_settings_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
            Spacer(modifier = Modifier.width(6.dp))
            Text("حفظ التعديلات")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
