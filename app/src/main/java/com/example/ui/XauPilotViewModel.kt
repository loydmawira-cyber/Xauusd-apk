package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.XauPilotRepository
import com.example.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ScreenDestination {
    object Home : ScreenDestination()
    object Marketplace : ScreenDestination()
    data class EaDetail(val eaId: String) : ScreenDestination()
    object Accounts : ScreenDestination()
    object Positions : ScreenDestination()
    data class EaSettings(val installationId: String) : ScreenDestination()
    object VpsManagement : ScreenDestination()
    object RiskManagement : ScreenDestination()
    object Profile : ScreenDestination()
    object BridgeGuide : ScreenDestination()
}

class XauPilotViewModel(
    private val repository: XauPilotRepository = XauPilotRepository.getInstance()
) : ViewModel() {

    // Current navigation state
    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    // Backstack for custom navigation
    private val screenBackStack = mutableListOf<ScreenDestination>()

    // Exposed Flows from Repository
    val accounts = repository.accounts
    val selectedAccountId = repository.selectedAccountId
    val eaProducts = repository.eaProducts
    val licenses = repository.licenses
    val installations = repository.installations
    val selectedInstallationId = repository.selectedInstallationId
    val parameterDefinitions = repository.parameterDefinitions
    val presets = repository.presets
    val configAuditHistory = repository.configAuditHistory
    val riskProfiles = repository.riskProfiles
    val positions = repository.positions
    val pendingOrders = repository.pendingOrders
    val tradeHistory = repository.tradeHistory
    val marketQuote = repository.marketQuote
    val candlesticks = repository.candlesticks
    val vpsInstances = repository.vpsInstances
    val vpsLogs = repository.vpsLogs
    val notifications = repository.notifications
    val apiSettings = repository.apiSettings
    val authSession = repository.authSession
    val backendSyncStatus = repository.backendSyncStatus

    fun refreshBackend() {
        repository.syncFromBackend()
    }

    // Temporary draft values while in EA Settings screen
    private val _draftConfigValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftConfigValues: StateFlow<Map<String, String>> = _draftConfigValues.asStateFlow()

    // Status message / snackbar
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Settings Mode: Standard vs Advanced
    private val _isAdvancedMode = MutableStateFlow(false)
    val isAdvancedMode: StateFlow<Boolean> = _isAdvancedMode.asStateFlow()

    // Emergency Stop Dialog State
    private val _emergencyDialogVisible = MutableStateFlow(false)
    val emergencyDialogVisible: StateFlow<Boolean> = _emergencyDialogVisible.asStateFlow()

    private val _selectedEmergencyPolicy = MutableStateFlow(EmergencyStopPolicy.CLOSE_NEW_ENTRIES_ONLY)
    val selectedEmergencyPolicy: StateFlow<EmergencyStopPolicy> = _selectedEmergencyPolicy.asStateFlow()

    fun navigateTo(destination: ScreenDestination) {
        if (_currentScreen.value != destination) {
            screenBackStack.add(_currentScreen.value)
            _currentScreen.value = destination
        }
    }

    fun navigateBack(): Boolean {
        if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeAt(screenBackStack.size - 1)
            return true
        }
        return false
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun showUserMessage(msg: String) {
        _userMessage.value = msg
    }

    fun selectAccount(id: String) {
        repository.selectAccount(id)
    }

    fun selectInstallation(id: String) {
        repository.selectInstallation(id)
        val inst = installations.value.find { it.id == id }
        if (inst != null) {
            _draftConfigValues.value = inst.currentValues
        }
    }

    fun setAdvancedMode(isAdvanced: Boolean) {
        _isAdvancedMode.value = isAdvanced
    }

    fun updateDraftParam(key: String, value: String) {
        _draftConfigValues.update { it + (key to value) }
    }

    fun submitDraftConfiguration(installationId: String) {
        viewModelScope.launch {
            val success = repository.updateEaConfiguration(installationId, _draftConfigValues.value)
            if (success) {
                _userMessage.value = "Configuration queued for EA deployment (v+1)"
            } else {
                _userMessage.value = "Configuration rejected by Account Risk Rules!"
            }
        }
    }

    fun applyPreset(installationId: String, preset: EaPreset) {
        _draftConfigValues.value = preset.values
        repository.applyPreset(installationId, preset)
        _userMessage.value = "Applied preset '${preset.name}'"
    }

    fun saveCurrentAsPreset(eaProductId: String, name: String, desc: String) {
        repository.savePreset(eaProductId, name, desc, _draftConfigValues.value)
        _userMessage.value = "Saved preset '$name'"
    }

    // Remote Commands
    fun openEmergencyDialog() {
        _emergencyDialogVisible.value = true
    }

    fun dismissEmergencyDialog() {
        _emergencyDialogVisible.value = false
    }

    fun setEmergencyPolicy(policy: EmergencyStopPolicy) {
        _selectedEmergencyPolicy.value = policy
    }

    fun executeEmergencyStop(accountId: String) {
        viewModelScope.launch {
            val policy = _selectedEmergencyPolicy.value
            _emergencyDialogVisible.value = false
            val success = repository.sendAccountCommand(accountId, CommandType.EMERGENCY_STOP, policy)
            _userMessage.value = if (success) {
                "Emergency Stop triggered on Account ($policy)"
            } else {
                "Emergency Stop was rejected or could not be delivered"
            }
        }
    }

    fun pauseAccount(accountId: String) {
        viewModelScope.launch {
            val success = repository.sendAccountCommand(accountId, CommandType.PAUSE)
            _userMessage.value = if (success) "Paused all EAs on Account" else "Pause command failed"
        }
    }

    fun resumeAccount(accountId: String) {
        viewModelScope.launch {
            val success = repository.sendAccountCommand(accountId, CommandType.RESUME)
            _userMessage.value = if (success) "Resumed active EAs on Account" else "Resume command failed"
        }
    }

    fun setInstallationStatus(installationId: String, status: EaRuntimeStatus) {
        viewModelScope.launch {
            repository.setInstallationStatus(installationId, status)
            _userMessage.value = "EA status changed to ${status.name}"
        }
    }

    // Add MT5 Account
    fun addAccount(label: String, broker: String, server: String, accountNum: String, env: AccountEnvironment, balance: Double) {
        val acc = repository.addAccount(label, broker, server, accountNum, env, balance)
        _userMessage.value = "Added MT5 Account: ${acc.label}"
    }

    fun removeAccount(id: String) {
        repository.removeAccount(id)
        _userMessage.value = "Account disconnected and removed"
    }

    // Assign EA
    fun assignEa(ea: EaProduct, account: BrokerAccount, magicNumber: Long, tf: String) {
        val inst = repository.assignEaToAccount(ea, account, magicNumber, tf)
        _userMessage.value = "Successfully assigned ${ea.name} to ${account.label}"
        navigateTo(ScreenDestination.EaSettings(inst.id))
    }

    // Purchase License
    fun purchaseLicense(eaProductId: String, type: LicenseType) {
        val lic = repository.purchaseLicense(eaProductId, type)
        _userMessage.value = "Purchased ${type.name} license for ${lic.eaProductName}"
    }

    // VPS controls
    fun restartVps(vpsId: String) {
        repository.restartVps(vpsId)
        _userMessage.value = "Reboot sequence initiated for VPS"
    }

    fun stopVps(vpsId: String) {
        repository.stopVps(vpsId)
        _userMessage.value = "VPS halted"
    }

    fun placeOrder(
        accountId: String,
        eaInstallationId: String,
        eaName: String,
        direction: TradeDirection,
        lotSize: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        magicNumber: Long
    ) {
        viewModelScope.launch {
            val pos = repository.placeOrder(
                accountId, eaInstallationId, eaName, direction, lotSize, stopLoss, takeProfit, magicNumber
            )
            _userMessage.value = "Executed ${direction.name} order ($lotSize lots) • Ticket #${pos.ticket}"
        }
    }

    fun closePosition(ticket: Long) {
        viewModelScope.launch {
            val success = repository.closePosition(ticket)
            if (success) {
                _userMessage.value = "Position #$ticket closed successfully"
            }
        }
    }

    fun deletePreset(id: String) {
        repository.deletePreset(id)
        _userMessage.value = "Preset deleted"
    }

    fun saveRiskProfile(profile: AccountRiskProfile) {
        repository.saveRiskProfile(profile)
        _userMessage.value = "Account risk profile updated & enforced"
    }

    fun toggleDemoMode(isDemo: Boolean) {
        repository.toggleDemoMode(isDemo)
        _userMessage.value = if (isDemo) "Demo Sandbox Mode enabled" else "Switched to Live API Mode"
    }

    fun updateApiUrl(url: String) {
        repository.updateApiBaseUrl(url)
        _userMessage.value = "API endpoint updated"
    }
}
