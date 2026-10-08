package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSimulatorDialog(
    onDismiss: () -> Unit,
    onSimulate: (sender: String, message: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var senderInput by remember { mutableStateOf("JEEB") }
    var messageInput by remember {
        mutableStateOf("تم استلام حوالة بمبلغ 5,000 ريال من العميل محمد أحمد. رقم العملية: JB-78912")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "محاكي استقبال رسائل SMS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "اختبر تحليل الرسائل واكتشاف التكرار والمزامنة محلياً بدون شريحة حقيقية",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "نماذج اختبار جاهزة:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        senderInput = "JEEB"
                        messageInput = "تم استلام حوالة بمبلغ 5,000 ريال من العميل محمد أحمد. رقم العملية: JB-78912"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("جيب (5,000)", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        senderInput = "JEEB"
                        messageInput = "إشعار إيداع: تم إضافة 5,000 ريال إلى محفظتك. المرجع: JB-78912"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("جيب 2 (تكرار نفس Trx)", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        senderInput = "FLOOSAK"
                        messageInput = "تم تحويل مبلغ 12,500 ريال بنجاح إلى حسابك عبر فلوسك. رقم عملية: FL-9981"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("فلوسك (12,500)", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        senderInput = "HAWALY"
                        messageInput = "حوالتي: استلمت دفعة بقيمة 20,000 ريال. رقم الحوالة: HW-3312"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("حوالتي (20,000)", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        senderInput = "JEEB"
                        messageInput = "تم استلام مبلغ 5,000 ريال من مجهول بدون رقم مرجعي"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("بدون Trx (مراجعة)", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        senderInput = "KURAMI"
                        messageInput = "رمز التحقق OTP الخاص بك هو 948123 لا تشاركه مع أحد"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("OTP (غير مالية)", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = senderInput,
                onValueChange = { senderInput = it },
                label = { Text("مرسل الرسالة (Sender Address / Number)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                label = { Text("نص الرسالة (SMS Body)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (senderInput.isNotBlank() && messageInput.isNotBlank()) {
                        onSimulate(senderInput, messageInput)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulate_send_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                Spacer(modifier = Modifier.height(4.dp))
                Text("إرسال إلى Gateway فوراً", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
