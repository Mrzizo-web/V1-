package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.EventLogEntity
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.local.entity.PaymentAttemptEntity
import com.example.data.local.entity.PaymentEntity
import com.example.data.local.entity.SmsMessageEntity
import com.example.data.repository.GatewayConfigRepository
import com.example.data.repository.GatewayRepository
import com.example.data.repository.ProcessSmsResult
import com.example.domain.model.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

enum class PosConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CHECKING
}

data class GatewayUiState(
    val isGatewayActive: Boolean = true,
    val isSimReady: Boolean = true,
    val isSmsReceiverActive: Boolean = true,
    val posConnectionStatus: PosConnectionStatus = PosConnectionStatus.DISCONNECTED,
    val pendingCount: Int = 0,
    val sentTodayCount: Int = 0,
    val failedCount: Int = 0,
    val needsReviewCount: Int = 0,
    val totalCount: Int = 0,
    val selectedWalletFilter: WalletType = WalletType.UNKNOWN, // UNKNOWN = ALL
    val payments: List<PaymentEntity> = emptyList(),
    val smsMessages: List<SmsMessageEntity> = emptyList(),
    val eventLogs: List<EventLogEntity> = emptyList(),
    val config: GatewayConfigEntity = GatewayConfigEntity(),
    val isSyncing: Boolean = false,
    val selectedPayment: PaymentEntity? = null,
    val selectedPaymentMessages: List<SmsMessageEntity> = emptyList(),
    val lastSimulationResult: ProcessSmsResult? = null,
    val userFeedbackMessage: String? = null
)

class MainViewModel(
    private val repository: GatewayRepository,
    private val configRepository: GatewayConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewayUiState())
    val uiState: StateFlow<GatewayUiState> = _uiState.asStateFlow()

    init {
        observeConfiguration()
        observeMetrics()
        observePayments()
        observeMessages()
        observeLogs()
        checkPosConnection()
    }

    private fun observeConfiguration() {
        configRepository.configFlow.onEach { cfg ->
            _uiState.update {
                it.copy(
                    config = cfg,
                    isGatewayActive = cfg.isGatewayActive
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun observeMetrics() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        combine(
            repository.getPendingCount(),
            repository.getSentTodayCount(startOfDay),
            repository.getFailedCount(),
            repository.getNeedsReviewCount(),
            repository.getTotalCount()
        ) { pending, sent, failed, review, total ->
            _uiState.update {
                it.copy(
                    pendingCount = pending,
                    sentTodayCount = sent,
                    failedCount = failed,
                    needsReviewCount = review,
                    totalCount = total
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun observePayments() {
        viewModelScope.launch {
            repository.observeAllPayments().collect { list ->
                _uiState.update { it.copy(payments = list) }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            repository.observeAllMessages().collect { list ->
                _uiState.update { it.copy(smsMessages = list) }
            }
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            repository.observeLogs().collect { list ->
                _uiState.update { it.copy(eventLogs = list) }
            }
        }
    }

    fun setWalletFilter(wallet: WalletType) {
        _uiState.update { it.copy(selectedWalletFilter = wallet) }
    }

    fun toggleGatewayActive(active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.setGatewayActive(active)
            repository.logEvent(
                "GATEWAY_STATUS",
                if (active) "تم تشغيل Gateway يدوياً" else "تم إيقاف Gateway يدوياً",
                level = if (active) "INFO" else "WARN"
            )
        }
    }

    fun checkPosConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(posConnectionStatus = PosConnectionStatus.CHECKING) }
            val isOnline = repository.checkPosConnection()
            _uiState.update {
                it.copy(
                    posConnectionStatus = if (isOnline) PosConnectionStatus.CONNECTED else PosConnectionStatus.DISCONNECTED
                )
            }
        }
    }

    fun syncPendingNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncing = true) }
            val count = repository.syncPendingPayments()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    userFeedbackMessage = if (count > 0) "تمت مزامنة $count حوالة مع POS بنجاح" else "لا توجد حوالات معلقة أو تعذر الوصول لـ POS"
                )
            }
            checkPosConnection()
        }
    }

    fun retryPayment(payment: PaymentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.syncSinglePayment(payment)
            _uiState.update {
                it.copy(
                    userFeedbackMessage = if (ok) "تم إرسال الحوالة بنجاح إلى POS" else "فشل الاتصال بـ POS، الحوالة لا تزال في الطابور"
                )
            }
            // Refresh details if currently open
            selectPaymentForDetails(payment.paymentId)
        }
    }

    fun selectPaymentForDetails(paymentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val payment = repository.getPaymentById(paymentId)
            val msgs = repository.getMessagesForPayment(paymentId)
            _uiState.update {
                it.copy(
                    selectedPayment = payment,
                    selectedPaymentMessages = msgs
                )
            }
        }
    }

    fun dismissPaymentDetails() {
        _uiState.update { it.copy(selectedPayment = null, selectedPaymentMessages = emptyList()) }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(userFeedbackMessage = null) }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
        }
    }

    /**
     * Simulates receiving an incoming SMS.
     * Crucial for developer & emulator testing.
     */
    fun simulateSms(sender: String, messageBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val smsId = UUID.randomUUID().toString()
            val result = repository.processIncomingSms(
                smsId = smsId,
                sender = sender,
                body = messageBody,
                receivedAt = System.currentTimeMillis()
            )
            _uiState.update {
                it.copy(
                    lastSimulationResult = result,
                    userFeedbackMessage = "تمت محاكاة وصول SMS: ${result.message}"
                )
            }
        }
    }

    fun updateConfig(newConfig: GatewayConfigEntity, enteredPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val valid = configRepository.verifyPin(enteredPin)
            if (!valid) {
                onError("رمز PIN غير صحيح!")
                return@launch
            }
            configRepository.updateConfig(newConfig)
            repository.logEvent("CONFIG_UPDATED", "تم تحديث إعدادات Gateway بنجاح", level = "INFO")
            onSuccess()
        }
    }
}
