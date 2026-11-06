package com.example.core.data

import android.content.Context
import com.example.core.database.*
import com.example.core.model.*
import com.example.core.network.ApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class XauPilotRepository private constructor(context: Context? = null) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun currentUtc(): String = dateFormat.format(Date())

    // Database handle
    private val database: XauPilotDatabase? = context?.let { XauPilotDatabase.getInstance(it) }
    private val dao: XauPilotDao? = database?.xauPilotDao()

    // --- StateFlows ---
    private val _apiSettings = MutableStateFlow(ApiSettings(isDemoMode = true))
    val apiSettings: StateFlow<ApiSettings> = _apiSettings.asStateFlow()

    private val _authSession = MutableStateFlow<AuthSession?>(
        AuthSession(
            accessToken = "xau_sec_demo_token_889211",
            refreshToken = "xau_sec_refresh_994124",
            user = User(
                id = "usr_pilot_01",
                email = "trader@xaupilot.com",
                fullName = "Pilot Algo Trader [DEMO]",
                role = CustomerRole.CUSTOMER,
                tenantId = "ten_gold_alpha_99",
                mfaEnabled = true,
                createdAt = "2026-01-10 08:30:00"
            )
        )
    )
    val authSession: StateFlow<AuthSession?> = _authSession.asStateFlow()

    // Accounts
    private val _accounts = MutableStateFlow<List<BrokerAccount>>(createInitialAccounts())
    val accounts: StateFlow<List<BrokerAccount>> = _accounts.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(_accounts.value.firstOrNull()?.id)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    // EA Marketplace Catalog
    private val _eaProducts = MutableStateFlow<List<EaProduct>>(createInitialEaProducts())
    val eaProducts: StateFlow<List<EaProduct>> = _eaProducts.asStateFlow()

    // Licenses
    private val _licenses = MutableStateFlow<List<EaLicense>>(createInitialLicenses())
    val licenses: StateFlow<List<EaLicense>> = _licenses.asStateFlow()

    // Installations
    private val _installations = MutableStateFlow<List<EaInstallation>>(createInitialInstallations())
    val installations: StateFlow<List<EaInstallation>> = _installations.asStateFlow()

    private val _selectedInstallationId = MutableStateFlow<String?>(_installations.value.firstOrNull()?.id)
    val selectedInstallationId: StateFlow<String?> = _selectedInstallationId.asStateFlow()

    // Parameter Definitions
    private val _parameterDefinitions = MutableStateFlow<Map<String, List<ParameterDefinition>>>(createInitialParameterDefinitions())
    val parameterDefinitions: StateFlow<Map<String, List<ParameterDefinition>>> = _parameterDefinitions.asStateFlow()

    // Presets
    private val _presets = MutableStateFlow<List<EaPreset>>(createInitialPresets())
    val presets: StateFlow<List<EaPreset>> = _presets.asStateFlow()

    // Configuration Audit Trail
    private val _configAuditHistory = MutableStateFlow<List<ConfigAuditEntry>>(createInitialAuditTrail())
    val configAuditHistory: StateFlow<List<ConfigAuditEntry>> = _configAuditHistory.asStateFlow()

    // Risk Profiles
    private val _riskProfiles = MutableStateFlow<Map<String, AccountRiskProfile>>(
        mapOf("acc_mt5_demo_01" to AccountRiskProfile(accountId = "acc_mt5_demo_01"))
    )
    val riskProfiles: StateFlow<Map<String, AccountRiskProfile>> = _riskProfiles.asStateFlow()

    // Trading data
    private val _positions = MutableStateFlow<List<Position>>(createInitialPositions())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    private val _pendingOrders = MutableStateFlow<List<PendingOrder>>(createInitialPendingOrders())
    val pendingOrders: StateFlow<List<PendingOrder>> = _pendingOrders.asStateFlow()

    private val _tradeHistory = MutableStateFlow<List<ClosedTrade>>(createInitialTradeHistory())
    val tradeHistory: StateFlow<List<ClosedTrade>> = _tradeHistory.asStateFlow()

    // Live Market Quote
    private val _marketQuote = MutableStateFlow(
        MarketQuote(
            symbol = "XAUUSD",
            bid = 2654.30,
            ask = 2654.55,
            spreadPoints = 25.0,
            high24h = 2672.80,
            low24h = 2642.10,
            change24hPercent = +0.84,
            lastUpdatedUtc = currentUtc(),
            isSimulated = true
        )
    )
    val marketQuote: StateFlow<MarketQuote> = _marketQuote.asStateFlow()

    // Candlesticks
    private val _candlesticks = MutableStateFlow<List<Candlestick>>(generateInitialCandles())
    val candlesticks: StateFlow<List<Candlestick>> = _candlesticks.asStateFlow()

    // VPS
    private val _vpsInstances = MutableStateFlow<List<VpsInstance>>(createInitialVpsInstances())
    val vpsInstances: StateFlow<List<VpsInstance>> = _vpsInstances.asStateFlow()

    private val _vpsLogs = MutableStateFlow<List<VpsLogEntry>>(createInitialVpsLogs())
    val vpsLogs: StateFlow<List<VpsLogEntry>> = _vpsLogs.asStateFlow()

    // Notifications
    private val _notifications = MutableStateFlow<List<AppNotification>>(createInitialNotifications())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    // Backend Connection Status
    private val _backendSyncStatus = MutableStateFlow("CONNECTED")
    val backendSyncStatus: StateFlow<String> = _backendSyncStatus.asStateFlow()

    init {
        initRoomSync()
        startMarketSimulationEngine()
    }

    private fun initRoomSync() {
        if (dao == null) return

        scope.launch {
            val initialAccounts = createInitialAccounts()
            dao.getAllAccounts().firstOrNull()?.let { existing ->
                if (existing.isEmpty()) {
                    dao.insertAccounts(initialAccounts.map { it.toEntity() })
                    dao.insertInstallations(createInitialInstallations().map { it.toEntity() })
                    dao.insertLicenses(createInitialLicenses().map { it.toEntity() })
                    dao.insertPositions(createInitialPositions().map { it.toEntity() })
                    dao.insertClosedTrades(createInitialTradeHistory().map { it.toEntity() })
                    dao.insertPresets(createInitialPresets().map { it.toEntity() })
                }
            }

            launch {
                dao.getAllAccounts().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _accounts.value = entities.map { it.toModel() }
                    }
                }
            }

            launch {
                dao.getAllInstallations().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _installations.value = entities.map { it.toModel() }
                    }
                }
            }

            launch {
                dao.getAllLicenses().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _licenses.value = entities.map { it.toModel() }
                    }
                }
            }

            launch {
                dao.getAllPositions().collect { entities ->
                    _positions.value = entities.map { it.toModel() }
                }
            }

            launch {
                dao.getAllClosedTrades().collect { entities ->
                    _tradeHistory.value = entities.map { it.toModel() }
                }
            }

            launch {
                dao.getAllPresets().collect { entities ->
                    if (entities.isNotEmpty()) {
                        _presets.value = entities.map { it.toModel() }
                    }
                }
            }
        }
    }

    // --- Market Simulation Engine (GATED STRICTLY TO DEMO MODE) ---
    private fun startMarketSimulationEngine() {
        scope.launch {
            while (isActive) {
                delay(2200)
                // Strict check: Only simulate when demo mode is active!
                if (_apiSettings.value.isDemoMode) {
                    val delta = (Random.nextDouble() - 0.48) * 0.45
                    val currentBid = (_marketQuote.value.bid + delta).coerceIn(2550.0, 2750.0)
                    val roundedBid = Math.round(currentBid * 100.0) / 100.0
                    val spread = 0.25
                    val roundedAsk = Math.round((roundedBid + spread) * 100.0) / 100.0

                    _marketQuote.value = _marketQuote.value.copy(
                        bid = roundedBid,
                        ask = roundedAsk,
                        spreadPoints = 25.0,
                        lastUpdatedUtc = currentUtc(),
                        isSimulated = true
                    )

                    // Update floating P/L for open positions in demo
                    _positions.update { list ->
                        list.map { pos ->
                            val diff = if (pos.direction == TradeDirection.BUY) {
                                roundedBid - pos.openPrice
                            } else {
                                pos.openPrice - roundedAsk
                            }
                            val pl = diff * pos.lotSize * 100.0
                            pos.copy(
                                currentPrice = if (pos.direction == TradeDirection.BUY) roundedBid else roundedAsk,
                                floatingProfitLoss = Math.round(pl * 100.0) / 100.0
                            )
                        }
                    }

                    // Refresh Account equity & balance in demo
                    _accounts.update { accs ->
                        accs.map { acc ->
                            val accPositions = _positions.value.filter { it.brokerAccountId == acc.id }
                            val totalFloating = accPositions.sumOf { it.floatingProfitLoss }
                            val newEquity = Math.round((acc.balance + totalFloating) * 100.0) / 100.0
                            acc.copy(
                                equity = newEquity,
                                freeMargin = Math.round((newEquity - acc.usedMargin) * 100.0) / 100.0,
                                marginLevelPercent = if (acc.usedMargin > 0) Math.round((newEquity / acc.usedMargin * 10000.0)) / 100.0 else 0.0,
                                lastHeartbeatUtc = currentUtc()
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Mode & API Configuration ---

    fun selectAccount(accountId: String) {
        _selectedAccountId.value = accountId
        if (!_apiSettings.value.isDemoMode) {
            refreshAccountDataFromBackend(accountId)
        }
    }

    fun selectInstallation(installationId: String) {
        _selectedInstallationId.value = installationId
    }

    fun toggleDemoMode(isDemo: Boolean) {
        _apiSettings.update { it.copy(isDemoMode = isDemo) }
        _marketQuote.update { it.copy(isSimulated = isDemo) }

        if (!isDemo) {
            // Live / Production Mode: trigger real backend sync
            syncFromBackend()
        }
    }

    fun updateApiBaseUrl(url: String) {
        _apiSettings.update { it.copy(customApiBaseUrl = url) }
        ApiClient.setBaseUrl(url)
        if (!_apiSettings.value.isDemoMode) {
            syncFromBackend()
        }
    }

    // --- Production Backend Synchronizer ---
    fun syncFromBackend() {
        scope.launch {
            try {
                _backendSyncStatus.value = "CONNECTING"
                val accountsResp = ApiClient.apiService.getBrokerAccounts()
                if (accountsResp.isSuccessful && accountsResp.body() != null) {
                    val remoteAccounts = accountsResp.body()!!
                    _accounts.value = remoteAccounts
                    _backendSyncStatus.value = "CONNECTED"
                }

                val eaResp = ApiClient.apiService.getEaProducts()
                if (eaResp.isSuccessful && eaResp.body() != null) {
                    _eaProducts.value = eaResp.body()!!
                }

                val licResp = ApiClient.apiService.getLicenses()
                if (licResp.isSuccessful && licResp.body() != null) {
                    _licenses.value = licResp.body()!!
                }

                val instResp = ApiClient.apiService.getInstallations()
                if (instResp.isSuccessful && instResp.body() != null) {
                    _installations.value = instResp.body()!!
                }

                val vpsResp = ApiClient.apiService.getVpsInstances()
                if (vpsResp.isSuccessful && vpsResp.body() != null) {
                    _vpsInstances.value = vpsResp.body()!!
                }

                _selectedAccountId.value?.let { accId ->
                    refreshAccountDataFromBackend(accId)
                }
            } catch (e: Exception) {
                _backendSyncStatus.value = "ERROR: ${e.message}"
                addNotification("Sync Warning", "Could not reach remote engine: ${e.localizedMessage}", NotificationType.HEARTBEAT_ALERT)
            }
        }
    }

    private fun refreshAccountDataFromBackend(accountId: String) {
        scope.launch {
            try {
                val posResp = ApiClient.apiService.getPositions(accountId)
                if (posResp.isSuccessful && posResp.body() != null) {
                    _positions.value = posResp.body()!!
                }

                val tradesResp = ApiClient.apiService.getTradesHistory(accountId)
                if (tradesResp.isSuccessful && tradesResp.body() != null) {
                    _tradeHistory.value = tradesResp.body()!!
                }

                val riskResp = ApiClient.apiService.getAccountRiskProfile(accountId)
                if (riskResp.isSuccessful && riskResp.body() != null) {
                    _riskProfiles.update { it + (accountId to riskResp.body()!!) }
                }
            } catch (e: Exception) {
                // Silently record warning, do not crash UI
                android.util.Log.w("XauPilotRepository", "Account data sync failed: ${e.message}")
            }
        }
    }

    // --- Remote EA Configuration Management (NO FAKE 1.2S SIMULATION IN PROD) ---
    suspend fun updateEaConfiguration(installationId: String, newValues: Map<String, String>): Boolean {
        val installation = _installations.value.find { it.id == installationId } ?: return false
        val account = _accounts.value.find { it.id == installation.brokerAccountId }
        val risk = _riskProfiles.value[account?.id] ?: AccountRiskProfile(accountId = installation.brokerAccountId)

        // 1. Client-Side Account Risk Profile Boundary Check
        val requestedLot = newValues["lot_size"]?.toDoubleOrNull() ?: 0.01
        if (requestedLot > risk.maxLotSizePerTrade) {
            addAuditEntry(
                version = installation.currentConfigVersion + 1,
                paramKey = "lot_size",
                oldVal = installation.currentValues["lot_size"] ?: "0.01",
                newVal = requestedLot.toString(),
                status = ConfigStatus.REJECTED,
                reason = "Exceeds Account Max Lot Size Limit (${risk.maxLotSizePerTrade} lots)"
            )
            addNotification("Config Rejected", "Lot size $requestedLot exceeds account safety limit", NotificationType.CONFIG_REJECTED)
            return false
        }

        val nextVersion = installation.currentConfigVersion + 1

        if (_apiSettings.value.isDemoMode) {
            // DEMO Sandbox Mode: Simulate pending -> applied with explicit demo audit trail
            _installations.update { list ->
                list.map { inst ->
                    if (inst.id == installationId) {
                        inst.copy(
                            pendingConfigVersion = nextVersion,
                            configStatus = ConfigStatus.PENDING
                        )
                    } else inst
                }
            }

            delay(1200) // Simulated delay ONLY in sandbox demo mode
            _installations.update { list ->
                list.map { inst ->
                    if (inst.id == installationId) {
                        val updated = inst.copy(
                            currentConfigVersion = nextVersion,
                            pendingConfigVersion = null,
                            configStatus = ConfigStatus.APPLIED,
                            currentValues = newValues
                        )
                        scope.launch { dao?.updateInstallation(updated.toEntity()) }
                        updated
                    } else inst
                }
            }
            newValues.forEach { (k, v) ->
                val old = installation.currentValues[k] ?: ""
                if (old != v) {
                    addAuditEntry(
                        version = nextVersion,
                        paramKey = k,
                        oldVal = old,
                        newVal = v,
                        status = ConfigStatus.APPLIED,
                        reason = "[DEMO SIMULATION] Confirmed by testbed"
                    )
                }
            }
            addNotification("[DEMO] Config Applied", "EA acknowledged version #$nextVersion successfully", NotificationType.CONFIG_APPLIED)
            return true
        } else {
            // PRODUCTION LIVE MODE: True Backend & Bridge Confirmation Flow
            // NO fake timer! Stored in PENDING state until confirmed by remote MQL5 bridge
            try {
                val resp = ApiClient.apiService.updateConfiguration(installationId, newValues)
                if (resp.isSuccessful) {
                    _installations.update { list ->
                        list.map { inst ->
                            if (inst.id == installationId) {
                                inst.copy(
                                    pendingConfigVersion = nextVersion,
                                    configStatus = ConfigStatus.PENDING
                                )
                            } else inst
                        }
                    }
                    addAuditEntry(
                        version = nextVersion,
                        paramKey = "all",
                        oldVal = "v${installation.currentConfigVersion}",
                        newVal = "v$nextVersion",
                        status = ConfigStatus.PENDING,
                        reason = "Queued in Cloud. Awaiting EA WebRequest acknowledgement."
                    )
                    addNotification("Config Queued", "Version #$nextVersion queued for deployment", NotificationType.CONFIG_APPLIED)
                    return true
                } else {
                    val errBody = resp.errorBody()?.string() ?: "Rejected by server"
                    addAuditEntry(
                        version = nextVersion,
                        paramKey = "all",
                        oldVal = "",
                        newVal = "",
                        status = ConfigStatus.REJECTED,
                        reason = errBody
                    )
                    addNotification("Config Rejected", errBody, NotificationType.CONFIG_REJECTED)
                    return false
                }
            } catch (e: Exception) {
                addAuditEntry(
                    version = nextVersion,
                    paramKey = "all",
                    oldVal = "",
                    newVal = "",
                    status = ConfigStatus.FAILED,
                    reason = "Network transmission failed: ${e.localizedMessage}"
                )
                addNotification("Network Error", "Could not dispatch configuration: ${e.localizedMessage}", NotificationType.CONFIG_REJECTED)
                return false
            }
        }
    }

    // Remote Account Control
    suspend fun sendAccountCommand(accountId: String, commandType: CommandType, emergencyPolicy: EmergencyStopPolicy? = null): Boolean {
        if (!_apiSettings.value.isDemoMode) {
            try {
                when (commandType) {
                    CommandType.PAUSE -> ApiClient.apiService.pauseAccount(accountId)
                    CommandType.RESUME -> ApiClient.apiService.resumeAccount(accountId)
                    CommandType.STOP -> ApiClient.apiService.pauseAccount(accountId)
                    CommandType.EMERGENCY_STOP -> ApiClient.apiService.emergencyStopAccount(
                        accountId,
                        mapOf("policy" to (emergencyPolicy?.name ?: "CLOSE_NEW_ENTRIES_ONLY"))
                    )
                }
            } catch (e: Exception) {
                addNotification("Command Failed", "Network failure: ${e.localizedMessage}", NotificationType.SYSTEM_SECURITY)
                return false
            }
        }

        val title = "Command Sent: ${commandType.name}"
        val msg = "Dispatched ${commandType.name} to Account $accountId (Policy: ${emergencyPolicy ?: "Default Safe"})"
        addNotification(title, msg, NotificationType.SYSTEM_SECURITY)

        when (commandType) {
            CommandType.PAUSE -> {
                _installations.update { list ->
                    list.map { inst ->
                        if (inst.brokerAccountId == accountId) inst.copy(runtimeStatus = EaRuntimeStatus.PAUSED)
                        else inst
                    }
                }
            }
            CommandType.RESUME -> {
                _installations.update { list ->
                    list.map { inst ->
                        if (inst.brokerAccountId == accountId) inst.copy(runtimeStatus = EaRuntimeStatus.RUNNING)
                        else inst
                    }
                }
            }
            CommandType.STOP -> {
                _installations.update { list ->
                    list.map { inst ->
                        if (inst.brokerAccountId == accountId) inst.copy(runtimeStatus = EaRuntimeStatus.STOPPED)
                        else inst
                    }
                }
            }
            CommandType.EMERGENCY_STOP -> {
                _installations.update { list ->
                    list.map { inst ->
                        if (inst.brokerAccountId == accountId) inst.copy(runtimeStatus = EaRuntimeStatus.STOPPED)
                        else inst
                    }
                }
                if (emergencyPolicy == EmergencyStopPolicy.CLOSE_ALL_MARKET_ORDERS) {
                    val closing = _positions.value.filter { it.brokerAccountId == accountId }
                    val newClosed = closing.map { pos ->
                        ClosedTrade(
                            ticket = pos.ticket,
                            brokerAccountId = pos.brokerAccountId,
                            eaName = pos.eaName,
                            symbol = pos.symbol,
                            direction = pos.direction,
                            lotSize = pos.lotSize,
                            openPrice = pos.openPrice,
                            closePrice = pos.currentPrice,
                            stopLoss = pos.stopLoss,
                            takeProfit = pos.takeProfit,
                            netProfitLoss = pos.floatingProfitLoss,
                            commission = pos.commission,
                            swap = pos.swap,
                            openTimeUtc = pos.openTimeUtc,
                            closeTimeUtc = currentUtc()
                        )
                    }
                    _tradeHistory.update { newClosed + it }
                    _positions.update { list -> list.filter { it.brokerAccountId != accountId } }

                    dao?.deletePositionsByAccount(accountId)
                    dao?.insertClosedTrades(newClosed.map { it.toEntity() })
                }
            }
        }
        return true
    }

    suspend fun setInstallationStatus(installationId: String, newStatus: EaRuntimeStatus) {
        _installations.update { list ->
            list.map { inst ->
                if (inst.id == installationId) {
                    val updated = inst.copy(runtimeStatus = newStatus)
                    scope.launch { dao?.updateInstallation(updated.toEntity()) }
                    updated
                } else inst
            }
        }
        addNotification("EA Status Updated", "Installation $installationId changed to ${newStatus.name}", NotificationType.HEARTBEAT_ALERT)
    }

    // Presets
    fun applyPreset(installationId: String, preset: EaPreset) {
        scope.launch {
            updateEaConfiguration(installationId, preset.values)
        }
    }

    fun savePreset(eaProductId: String, name: String, description: String, values: Map<String, String>) {
        val newPreset = EaPreset(
            id = "pre_${UUID.randomUUID().toString().take(8)}",
            eaProductId = eaProductId,
            name = name,
            description = description,
            values = values,
            isSystemDefault = false
        )
        _presets.update { it + newPreset }
        scope.launch { dao?.insertPreset(newPreset.toEntity()) }
    }

    fun deletePreset(id: String) {
        _presets.update { list -> list.filter { it.id != id } }
        scope.launch { dao?.deletePresetById(id) }
    }

    // Accounts
    fun addAccount(
        label: String,
        brokerName: String,
        serverName: String,
        accountNumber: String,
        environment: AccountEnvironment,
        initialBalance: Double = 10000.0
    ): BrokerAccount {
        val masked = accountNumber.take(4) + "****" + accountNumber.takeLast(2)
        val newAcc = BrokerAccount(
            id = "acc_mt5_${UUID.randomUUID().toString().take(6)}",
            tenantId = "ten_gold_alpha_99",
            label = if (_apiSettings.value.isDemoMode) "$label [DEMO]" else label,
            brokerName = brokerName,
            serverName = serverName,
            accountNumberMasked = masked,
            environment = environment,
            connectionStatus = ConnectionStatus.CONNECTED,
            balance = initialBalance,
            equity = initialBalance,
            freeMargin = initialBalance,
            usedMargin = 0.0,
            marginLevelPercent = 0.0,
            assignedEaCount = 0,
            lastHeartbeatUtc = currentUtc(),
            hostingType = HostingType.MANAGED_VPS
        )
        _accounts.update { it + newAcc }
        _riskProfiles.update { it + (newAcc.id to AccountRiskProfile(accountId = newAcc.id)) }

        scope.launch {
            dao?.insertAccount(newAcc.toEntity())
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.addBrokerAccount(
                        mapOf(
                            "label" to label,
                            "brokerName" to brokerName,
                            "serverName" to serverName,
                            "accountNumber" to accountNumber,
                            "environment" to environment.name,
                            "initialBalance" to initialBalance
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote account creation failed: ${e.message}")
                }
            }
        }
        return newAcc
    }

    fun removeAccount(accountId: String) {
        _accounts.update { list -> list.filter { it.id != accountId } }
        _installations.update { list -> list.filter { it.brokerAccountId != accountId } }
        _positions.update { list -> list.filter { it.brokerAccountId != accountId } }

        scope.launch {
            dao?.deleteAccountById(accountId)
            dao?.deleteInstallationsByAccount(accountId)
            dao?.deletePositionsByAccount(accountId)
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.deleteBrokerAccount(accountId)
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote account deletion failed: ${e.message}")
                }
            }
        }
    }

    fun saveRiskProfile(profile: AccountRiskProfile) {
        _riskProfiles.update { it + (profile.accountId to profile) }
        scope.launch {
            dao?.insertRiskProfile(
                RiskProfileEntity(
                    accountId = profile.accountId,
                    maxDailyLossAmount = profile.maxDailyLossAmount,
                    maxTotalExposureLots = profile.maxTotalExposureLots,
                    maxLotSizePerTrade = profile.maxLotSizePerTrade,
                    maxOpenPositions = profile.maxOpenPositions,
                    maxSpreadPoints = profile.maxSpreadPoints,
                    minMarginLevelPercent = profile.minMarginLevelPercent,
                    emergencyStopPolicy = profile.emergencyStopPolicy.name
                )
            )
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.updateAccountRiskProfile(profile.accountId, profile)
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote risk profile update failed: ${e.message}")
                }
            }
        }
    }

    // EA Assignment
    fun assignEaToAccount(
        eaProduct: EaProduct,
        account: BrokerAccount,
        magicNumber: Long,
        timeframe: String = "M5"
    ): EaInstallation {
        val defaults = _parameterDefinitions.value[eaProduct.id]?.associate { it.key to it.defaultValue } ?: emptyMap()
        val newInst = EaInstallation(
            id = "inst_${UUID.randomUUID().toString().take(6)}",
            tenantId = account.tenantId,
            eaProductId = eaProduct.id,
            eaProductName = eaProduct.name,
            brokerAccountId = account.id,
            brokerAccountLabel = account.label,
            magicNumber = magicNumber,
            symbol = "XAUUSD",
            timeframe = timeframe,
            runtimeStatus = EaRuntimeStatus.RUNNING,
            currentConfigVersion = 1,
            configStatus = ConfigStatus.APPLIED,
            currentValues = defaults,
            lastHeartbeatUtc = currentUtc(),
            hostingType = account.hostingType
        )
        _installations.update { it + newInst }
        _accounts.update { accs ->
            accs.map { if (it.id == account.id) it.copy(assignedEaCount = it.assignedEaCount + 1) else it }
        }

        scope.launch {
            dao?.insertInstallation(newInst.toEntity())
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.createInstallation(
                        mapOf(
                            "eaProductId" to eaProduct.id,
                            "brokerAccountId" to account.id,
                            "magicNumber" to magicNumber,
                            "timeframe" to timeframe
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote EA installation failed: ${e.message}")
                }
            }
        }
        addNotification("EA Assigned", "${eaProduct.name} assigned to ${account.label} (Magic #$magicNumber)", NotificationType.TRADE_EVENT)
        return newInst
    }

    // License Purchase
    fun purchaseLicense(eaProductId: String, licenseType: LicenseType): EaLicense {
        val ea = _eaProducts.value.find { it.id == eaProductId }
        val newLic = EaLicense(
            id = "lic_${UUID.randomUUID().toString().take(8)}",
            eaProductId = eaProductId,
            eaProductName = ea?.name ?: "XAU Expert",
            tenantId = "ten_gold_alpha_99",
            licenseKeyMasked = "XAU-${licenseType.name.take(3)}-****-992B",
            licenseType = licenseType,
            status = LicenseStatus.ACTIVE,
            startDate = currentUtc(),
            expiryDate = if (licenseType == LicenseType.LIFETIME) null else "2027-11-05 00:00:00",
            maxAccounts = if (licenseType == LicenseType.LIFETIME) 10 else 3,
            maxInstallations = if (licenseType == LicenseType.LIFETIME) 25 else 5
        )
        _licenses.update { it + newLic }

        scope.launch {
            dao?.insertLicense(newLic.toEntity())
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.purchaseLicense(
                        mapOf(
                            "ea_product_id" to eaProductId,
                            "license_type" to licenseType.name
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote license purchase failed: ${e.message}")
                }
            }
        }
        addNotification("License Activated", "${ea?.name} ${licenseType.name} license is active", NotificationType.TRADE_EVENT)
        return newLic
    }

    // Orders
    suspend fun placeOrder(
        accountId: String,
        eaInstallationId: String,
        eaName: String,
        direction: TradeDirection,
        lotSize: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        magicNumber: Long
    ): Position {
        val quote = _marketQuote.value
        val openPrice = if (direction == TradeDirection.BUY) quote.ask else quote.bid
        val ticket = 91800000L + Random.nextLong(10000, 99999)

        val newPos = Position(
            ticket = ticket,
            brokerAccountId = accountId,
            eaInstallationId = eaInstallationId,
            eaName = eaName,
            symbol = "XAUUSD",
            direction = direction,
            lotSize = lotSize,
            openPrice = openPrice,
            currentPrice = openPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            floatingProfitLoss = 0.0,
            magicNumber = magicNumber,
            openTimeUtc = currentUtc()
        )

        _positions.update { listOf(newPos) + it }
        dao?.insertPosition(newPos.toEntity())

        val marginRequired = (lotSize * 100.0 * openPrice) / 500.0
        _accounts.update { accs ->
            accs.map {
                if (it.id == accountId) it.copy(usedMargin = it.usedMargin + marginRequired)
                else it
            }
        }
        _installations.update { list ->
            list.map {
                if (it.id == eaInstallationId) it.copy(openPositionsCount = it.openPositionsCount + 1)
                else it
            }
        }

        addNotification("Trade Executed", "${direction.name} $lotSize XAUUSD @ $openPrice (Ticket #$ticket)", NotificationType.TRADE_EVENT)
        return newPos
    }

    suspend fun closePosition(ticket: Long): Boolean {
        val pos = _positions.value.find { it.ticket == ticket } ?: return false

        val quote = _marketQuote.value
        val closePrice = if (pos.direction == TradeDirection.BUY) quote.bid else quote.ask
        val diff = if (pos.direction == TradeDirection.BUY) closePrice - pos.openPrice else pos.openPrice - closePrice
        val finalPl = Math.round(diff * pos.lotSize * 100.0 * 100.0) / 100.0

        val closed = ClosedTrade(
            ticket = pos.ticket,
            brokerAccountId = pos.brokerAccountId,
            eaName = pos.eaName,
            symbol = pos.symbol,
            direction = pos.direction,
            lotSize = pos.lotSize,
            openPrice = pos.openPrice,
            closePrice = closePrice,
            stopLoss = pos.stopLoss,
            takeProfit = pos.takeProfit,
            netProfitLoss = finalPl,
            commission = pos.commission,
            swap = pos.swap,
            openTimeUtc = pos.openTimeUtc,
            closeTimeUtc = currentUtc()
        )

        _positions.update { list -> list.filter { it.ticket != ticket } }
        _tradeHistory.update { listOf(closed) + it }

        dao?.deletePositionByTicket(ticket)
        dao?.insertClosedTrade(closed.toEntity())

        _accounts.update { accs ->
            accs.map { acc ->
                if (acc.id == pos.brokerAccountId) {
                    val updatedBal = Math.round((acc.balance + finalPl) * 100.0) / 100.0
                    acc.copy(balance = updatedBal)
                } else acc
            }
        }
        _installations.update { list ->
            list.map { inst ->
                if (inst.id == pos.eaInstallationId) {
                    inst.copy(
                        openPositionsCount = (inst.openPositionsCount - 1).coerceAtLeast(0),
                        totalProfitLossUsd = inst.totalProfitLossUsd + finalPl
                    )
                } else inst
            }
        }

        val sign = if (finalPl >= 0) "+" else ""
        addNotification("Trade Closed", "Position #$ticket closed at $closePrice (P/L: $sign$$finalPl)", NotificationType.TRADE_EVENT)
        return true
    }

    // VPS actions
    fun restartVps(vpsId: String) {
        _vpsInstances.update { list ->
            list.map { if (it.id == vpsId) it.copy(status = VpsStatus.REBOOTING) else it }
        }
        addVpsLog("WARN", "Manual VPS reboot requested from Mobile App")
        scope.launch {
            if (!_apiSettings.value.isDemoMode) {
                try {
                    ApiClient.apiService.restartVps(vpsId)
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote VPS restart failed: ${e.message}")
                }
            }
            delay(2500)
            _vpsInstances.update { list ->
                list.map { if (it.id == vpsId) it.copy(status = VpsStatus.RUNNING, lastHeartbeatUtc = currentUtc()) else it }
            }
            addVpsLog("INFO", "Windows Server 2022 Datacenter reboot completed. MT5 Terminal resumed.")
            addNotification("VPS Reconnected", "Managed VPS instance rebooted and operational", NotificationType.VPS_STATUS)
        }
    }

    fun stopVps(vpsId: String) {
        _vpsInstances.update { list ->
            list.map { if (it.id == vpsId) it.copy(status = VpsStatus.STOPPED) else it }
        }
        addVpsLog("SECURITY", "VPS terminal halted by user command")
        if (!_apiSettings.value.isDemoMode) {
            scope.launch {
                try {
                    ApiClient.apiService.stopVps(vpsId)
                } catch (e: Exception) {
                    android.util.Log.e("XauPilotRepository", "Remote VPS stop failed: ${e.message}")
                }
            }
        }
    }

    private fun addVpsLog(level: String, msg: String) {
        val entry = VpsLogEntry(timestampUtc = currentUtc(), level = level, message = msg)
        _vpsLogs.update { listOf(entry) + it.take(49) }
    }

    private fun addNotification(title: String, message: String, type: NotificationType) {
        val notif = AppNotification(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            type = type,
            timestampUtc = currentUtc(),
            read = false
        )
        _notifications.update { listOf(notif) + it.take(49) }
    }

    private fun addAuditEntry(version: Int, paramKey: String, oldVal: String, newVal: String, status: ConfigStatus, reason: String?) {
        val entry = ConfigAuditEntry(
            version = version,
            parameterKey = paramKey,
            oldValue = oldVal,
            newValue = newVal,
            submittedAtUtc = currentUtc(),
            appliedAtUtc = if (status == ConfigStatus.APPLIED) currentUtc() else null,
            status = status,
            rejectionReason = reason
        )
        _configAuditHistory.update { listOf(entry) + it }
    }

    // --- Room Mappings ---
    private fun BrokerAccount.toEntity() = BrokerAccountEntity(
        id = id,
        tenantId = tenantId,
        label = label,
        brokerName = brokerName,
        serverName = serverName,
        accountNumberMasked = accountNumberMasked,
        environment = environment.name,
        connectionStatus = connectionStatus.name,
        currency = currency,
        balance = balance,
        equity = equity,
        freeMargin = freeMargin,
        usedMargin = usedMargin,
        marginLevelPercent = marginLevelPercent,
        leverage = leverage,
        assignedEaCount = assignedEaCount,
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = hostingType.name
    )

    private fun BrokerAccountEntity.toModel() = BrokerAccount(
        id = id,
        tenantId = tenantId,
        label = label,
        brokerName = brokerName,
        serverName = serverName,
        accountNumberMasked = accountNumberMasked,
        environment = if (environment == "LIVE") AccountEnvironment.LIVE else AccountEnvironment.DEMO,
        connectionStatus = ConnectionStatus.valueOf(connectionStatus),
        currency = currency,
        balance = balance,
        equity = equity,
        freeMargin = freeMargin,
        usedMargin = usedMargin,
        marginLevelPercent = marginLevelPercent,
        leverage = leverage,
        assignedEaCount = assignedEaCount,
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = HostingType.valueOf(hostingType)
    )

    private fun EaInstallation.toEntity() = EaInstallationEntity(
        id = id,
        tenantId = tenantId,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        brokerAccountId = brokerAccountId,
        brokerAccountLabel = brokerAccountLabel,
        magicNumber = magicNumber,
        symbol = symbol,
        timeframe = timeframe,
        runtimeStatus = runtimeStatus.name,
        currentConfigVersion = currentConfigVersion,
        pendingConfigVersion = pendingConfigVersion,
        configStatus = configStatus.name,
        valuesJson = mapToJson(currentValues),
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = hostingType.name,
        vpsInstanceId = vpsInstanceId,
        openPositionsCount = openPositionsCount,
        totalProfitLossUsd = totalProfitLossUsd
    )

    private fun EaInstallationEntity.toModel() = EaInstallation(
        id = id,
        tenantId = tenantId,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        brokerAccountId = brokerAccountId,
        brokerAccountLabel = brokerAccountLabel,
        magicNumber = magicNumber,
        symbol = symbol,
        timeframe = timeframe,
        runtimeStatus = EaRuntimeStatus.valueOf(runtimeStatus),
        currentConfigVersion = currentConfigVersion,
        pendingConfigVersion = pendingConfigVersion,
        configStatus = ConfigStatus.valueOf(configStatus),
        currentValues = jsonToMap(valuesJson),
        lastHeartbeatUtc = lastHeartbeatUtc,
        hostingType = HostingType.valueOf(hostingType),
        vpsInstanceId = vpsInstanceId,
        openPositionsCount = openPositionsCount,
        totalProfitLossUsd = totalProfitLossUsd
    )

    private fun EaLicense.toEntity() = EaLicenseEntity(
        id = id,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        tenantId = tenantId,
        licenseKeyMasked = licenseKeyMasked,
        licenseType = licenseType.name,
        status = status.name,
        startDate = startDate,
        expiryDate = expiryDate,
        maxAccounts = maxAccounts,
        maxInstallations = maxInstallations,
        currentActiveInstallations = currentActiveInstallations
    )

    private fun EaLicenseEntity.toModel() = EaLicense(
        id = id,
        eaProductId = eaProductId,
        eaProductName = eaProductName,
        tenantId = tenantId,
        licenseKeyMasked = licenseKeyMasked,
        licenseType = LicenseType.valueOf(licenseType),
        status = LicenseStatus.valueOf(status),
        startDate = startDate,
        expiryDate = expiryDate,
        maxAccounts = maxAccounts,
        maxInstallations = maxInstallations,
        currentActiveInstallations = currentActiveInstallations
    )

    private fun Position.toEntity() = PositionEntity(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaInstallationId = eaInstallationId,
        eaName = eaName,
        symbol = symbol,
        direction = direction.name,
        lotSize = lotSize,
        openPrice = openPrice,
        currentPrice = currentPrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        floatingProfitLoss = floatingProfitLoss,
        swap = swap,
        commission = commission,
        magicNumber = magicNumber,
        openTimeUtc = openTimeUtc
    )

    private fun PositionEntity.toModel() = Position(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaInstallationId = eaInstallationId,
        eaName = eaName,
        symbol = symbol,
        direction = TradeDirection.valueOf(direction),
        lotSize = lotSize,
        openPrice = openPrice,
        currentPrice = currentPrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        floatingProfitLoss = floatingProfitLoss,
        swap = swap,
        commission = commission,
        magicNumber = magicNumber,
        openTimeUtc = openTimeUtc
    )

    private fun ClosedTrade.toEntity() = ClosedTradeEntity(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaName = eaName,
        symbol = symbol,
        direction = direction.name,
        lotSize = lotSize,
        openPrice = openPrice,
        closePrice = closePrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        netProfitLoss = netProfitLoss,
        commission = commission,
        swap = swap,
        openTimeUtc = openTimeUtc,
        closeTimeUtc = closeTimeUtc
    )

    private fun ClosedTradeEntity.toModel() = ClosedTrade(
        ticket = ticket,
        brokerAccountId = brokerAccountId,
        eaName = eaName,
        symbol = symbol,
        direction = TradeDirection.valueOf(direction),
        lotSize = lotSize,
        openPrice = openPrice,
        closePrice = closePrice,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        netProfitLoss = netProfitLoss,
        commission = commission,
        swap = swap,
        openTimeUtc = openTimeUtc,
        closeTimeUtc = closeTimeUtc
    )

    private fun EaPreset.toEntity() = PresetEntity(
        id = id,
        eaProductId = eaProductId,
        name = name,
        description = description,
        valuesJson = mapToJson(values),
        isSystemDefault = isSystemDefault
    )

    private fun PresetEntity.toModel() = EaPreset(
        id = id,
        eaProductId = eaProductId,
        name = name,
        description = description,
        values = jsonToMap(valuesJson),
        isSystemDefault = isSystemDefault
    )

    private fun mapToJson(map: Map<String, String>): String {
        val entries = map.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" }
        return "{$entries}"
    }

    private fun jsonToMap(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        val result = mutableMapOf<String, String>()
        val pattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
        pattern.findAll(json).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    // --- Seed Builders with Explicit [DEMO / SIMULATED] Markings ---

    private fun createInitialAccounts(): List<BrokerAccount> = listOf(
        BrokerAccount(
            id = "acc_mt5_demo_01",
            tenantId = "ten_gold_alpha_99",
            label = "[DEMO] Primary IC Markets XAU",
            brokerName = "IC Markets Global",
            serverName = "ICMarkets-Demo02",
            accountNumberMasked = "5591****02",
            environment = AccountEnvironment.DEMO,
            connectionStatus = ConnectionStatus.CONNECTED,
            balance = 25000.0,
            equity = 25340.50,
            freeMargin = 24740.50,
            usedMargin = 600.0,
            marginLevelPercent = 4223.4,
            leverage = 500,
            assignedEaCount = 2,
            lastHeartbeatUtc = currentUtc(),
            hostingType = HostingType.MANAGED_VPS
        ),
        BrokerAccount(
            id = "acc_mt5_demo_02",
            tenantId = "ten_gold_alpha_99",
            label = "[DEMO] Scalp Reserve Demo",
            brokerName = "Exness Technologies",
            serverName = "Exness-Trial04",
            accountNumberMasked = "7730****88",
            environment = AccountEnvironment.DEMO,
            connectionStatus = ConnectionStatus.CONNECTED,
            balance = 10000.0,
            equity = 10125.00,
            freeMargin = 9875.00,
            usedMargin = 250.0,
            marginLevelPercent = 4050.0,
            leverage = 1000,
            assignedEaCount = 1,
            lastHeartbeatUtc = currentUtc(),
            hostingType = HostingType.CUSTOMER_HOSTED
        )
    )

    private fun createInitialEaProducts(): List<EaProduct> = listOf(
        EaProduct(
            id = "ea_gold_pro",
            productCode = "XAU-PRO-01",
            name = "Gold Pro EA",
            description = "Institutional multi-session liquidity sweep & momentum trading engine for XAUUSD with dynamic volatility trailing stops.",
            strategyCategory = StrategyCategory.GOLD_TREND,
            currentVersion = "2.4.0",
            supportedInstruments = listOf("XAUUSD", "GOLD"),
            supportedTimeframes = listOf("M5", "M15", "H1"),
            minDepositUsd = 2000.0,
            defaultMagicNumber = 108821,
            releaseNotes = "v2.4.0: Added remote runtime parameter synchronization, dynamic spread filter & NY open volatility throttle.",
            riskDisclosures = "Trading gold carries high risk of loss. Past backtests do not guarantee future performance.",
            documentationUrl = "https://docs.xaupilot.com/ea/gold-pro",
            monthlyPriceUsd = 79.0,
            annualPriceUsd = 690.0,
            lifetimePriceUsd = 1290.0
        ),
        EaProduct(
            id = "ea_gold_scalper",
            productCode = "XAU-SCL-02",
            name = "Gold Scalper EA",
            description = "High-frequency micro-trend exploitation strategy capitalizing on London & New York session liquidity bursts.",
            strategyCategory = StrategyCategory.GOLD_SCALPING,
            currentVersion = "3.1.2",
            supportedInstruments = listOf("XAUUSD"),
            supportedTimeframes = listOf("M1", "M5"),
            minDepositUsd = 1000.0,
            defaultMagicNumber = 209934,
            releaseNotes = "v3.1.2: Improved slippage compensation and order execution latency safeguards.",
            riskDisclosures = "Requires ultra-low latency VPS execution (sub 2ms) to prevent adverse slippage.",
            documentationUrl = "https://docs.xaupilot.com/ea/gold-scalper",
            monthlyPriceUsd = 89.0,
            annualPriceUsd = 790.0,
            lifetimePriceUsd = 1450.0
        ),
        EaProduct(
            id = "ea_gold_swing",
            productCode = "XAU-SWG-03",
            name = "Gold Swing EA",
            description = "Macro structural swing trader tracking major daily support/resistance zones and gold central bank demand cycles.",
            strategyCategory = StrategyCategory.GOLD_SWING,
            currentVersion = "1.8.0",
            supportedInstruments = listOf("XAUUSD"),
            supportedTimeframes = listOf("H1", "H4", "D1"),
            minDepositUsd = 5000.0,
            defaultMagicNumber = 301145,
            releaseNotes = "v1.8.0: Integrated macro interest rate trend bias filter.",
            riskDisclosures = "Holding positions overnight is subject to broker swap charges.",
            documentationUrl = "https://docs.xaupilot.com/ea/gold-swing",
            monthlyPriceUsd = 69.0,
            annualPriceUsd = 590.0,
            lifetimePriceUsd = 990.0
        ),
        EaProduct(
            id = "ea_gold_breakout",
            productCode = "XAU-BRK-04",
            name = "Gold Breakout EA",
            description = "London range compression breakout algorithm with smart false-breakout detection and order-cancellation timers.",
            strategyCategory = StrategyCategory.GOLD_BREAKOUT,
            currentVersion = "2.0.1",
            supportedInstruments = listOf("XAUUSD"),
            supportedTimeframes = listOf("M15", "H1"),
            minDepositUsd = 2500.0,
            defaultMagicNumber = 405512,
            releaseNotes = "v2.0.1: Added Asian high/low range projection box filter.",
            riskDisclosures = "False breakouts during major economic news releases may trigger stop losses.",
            documentationUrl = "https://docs.xaupilot.com/ea/gold-breakout",
            monthlyPriceUsd = 75.0,
            annualPriceUsd = 650.0,
            lifetimePriceUsd = 1190.0
        )
    )

    private fun createInitialLicenses(): List<EaLicense> = listOf(
        EaLicense(
            id = "lic_gold_pro_01",
            eaProductId = "ea_gold_pro",
            eaProductName = "Gold Pro EA",
            tenantId = "ten_gold_alpha_99",
            licenseKeyMasked = "XAU-LIF-****-992B",
            licenseType = LicenseType.LIFETIME,
            status = LicenseStatus.ACTIVE,
            startDate = "2026-01-10 09:00:00",
            expiryDate = null,
            maxAccounts = 10,
            maxInstallations = 25,
            currentActiveInstallations = 1
        ),
        EaLicense(
            id = "lic_gold_scalp_02",
            eaProductId = "ea_gold_scalper",
            eaProductName = "Gold Scalper EA",
            tenantId = "ten_gold_alpha_99",
            licenseKeyMasked = "XAU-ANN-****-418C",
            licenseType = LicenseType.ANNUAL,
            status = LicenseStatus.ACTIVE,
            startDate = "2026-03-01 10:00:00",
            expiryDate = "2027-03-01 10:00:00",
            maxAccounts = 3,
            maxInstallations = 5,
            currentActiveInstallations = 1
        )
    )

    private fun createInitialInstallations(): List<EaInstallation> = listOf(
        EaInstallation(
            id = "inst_01_pro_acc1",
            tenantId = "ten_gold_alpha_99",
            eaProductId = "ea_gold_pro",
            eaProductName = "Gold Pro EA",
            brokerAccountId = "acc_mt5_demo_01",
            brokerAccountLabel = "[DEMO] Primary IC Markets XAU",
            magicNumber = 108821,
            symbol = "XAUUSD",
            timeframe = "M5",
            runtimeStatus = EaRuntimeStatus.RUNNING,
            currentConfigVersion = 3,
            configStatus = ConfigStatus.APPLIED,
            currentValues = mapOf(
                "lot_size" to "0.10",
                "risk_percent" to "1.5",
                "stop_loss_points" to "120",
                "take_profit_points" to "240",
                "max_open_trades" to "3",
                "trading_direction" to "BOTH",
                "trailing_stop_enabled" to "true",
                "trailing_start_points" to "60",
                "trailing_step_points" to "20",
                "break_even_enabled" to "true",
                "break_even_trigger" to "50",
                "max_spread_points" to "35",
                "trading_session" to "LONDON_NY",
                "atr_period" to "14",
                "rsi_period" to "14",
                "rsi_overbought" to "70",
                "rsi_oversold" to "30",
                "magic_number" to "108821"
            ),
            lastHeartbeatUtc = currentUtc(),
            hostingType = HostingType.MANAGED_VPS,
            vpsInstanceId = "vps_ld4_01",
            openPositionsCount = 2,
            totalProfitLossUsd = +340.50
        ),
        EaInstallation(
            id = "inst_02_scalp_acc1",
            tenantId = "ten_gold_alpha_99",
            eaProductId = "ea_gold_scalper",
            eaProductName = "Gold Scalper EA",
            brokerAccountId = "acc_mt5_demo_01",
            brokerAccountLabel = "[DEMO] Primary IC Markets XAU",
            magicNumber = 209934,
            symbol = "XAUUSD",
            timeframe = "M1",
            runtimeStatus = EaRuntimeStatus.RUNNING,
            currentConfigVersion = 1,
            configStatus = ConfigStatus.APPLIED,
            currentValues = mapOf(
                "lot_size" to "0.05",
                "risk_percent" to "1.0",
                "stop_loss_points" to "60",
                "take_profit_points" to "90",
                "max_open_trades" to "2",
                "trading_direction" to "BOTH",
                "trailing_stop_enabled" to "true",
                "trailing_start_points" to "30",
                "trailing_step_points" to "10",
                "break_even_enabled" to "true",
                "break_even_trigger" to "25",
                "max_spread_points" to "28",
                "trading_session" to "NY_ONLY",
                "magic_number" to "209934"
            ),
            lastHeartbeatUtc = currentUtc(),
            hostingType = HostingType.MANAGED_VPS,
            vpsInstanceId = "vps_ld4_01",
            openPositionsCount = 1,
            totalProfitLossUsd = +112.00
        )
    )

    private fun createInitialParameterDefinitions(): Map<String, List<ParameterDefinition>> = mapOf(
        "ea_gold_pro" to listOf(
            ParameterDefinition("lot_size", "Fixed Lot Size", "Trade volume per order if risk percentage is zero", ParameterDataType.DECIMAL, "0.10", 0.01, 10.0, 0.01, "lots", "Money Management", ParameterVisibility.STANDARD),
            ParameterDefinition("risk_percent", "Risk Percentage", "Dynamic equity risk percentage per trade", ParameterDataType.DECIMAL, "1.5", 0.1, 5.0, 0.1, "%", "Money Management", ParameterVisibility.STANDARD),
            ParameterDefinition("stop_loss_points", "Stop Loss", "Protective stop in points (10 points = 1 pip / 0.10c)", ParameterDataType.INTEGER, "120", 20.0, 1000.0, 10.0, "pts", "Risk & Stop Management", ParameterVisibility.STANDARD),
            ParameterDefinition("take_profit_points", "Take Profit", "Target profit in points", ParameterDataType.INTEGER, "240", 20.0, 2000.0, 10.0, "pts", "Risk & Stop Management", ParameterVisibility.STANDARD),
            ParameterDefinition("max_open_trades", "Max Open Trades", "Maximum concurrent positions for this EA", ParameterDataType.INTEGER, "3", 1.0, 10.0, 1.0, "trades", "Money Management", ParameterVisibility.STANDARD),
            ParameterDefinition("trading_direction", "Trading Direction", "Direction filter for new orders", ParameterDataType.ENUM, "BOTH", category = "Execution", visibility = ParameterVisibility.STANDARD, enumOptions = listOf("BOTH", "BUY_ONLY", "SELL_ONLY")),
            ParameterDefinition("trailing_stop_enabled", "Trailing Stop", "Enables profit-protecting trailing stop", ParameterDataType.BOOLEAN, "true", category = "Trailing Stop", visibility = ParameterVisibility.STANDARD),
            ParameterDefinition("break_even_enabled", "Break-Even Trigger", "Moves SL to entry once in specified profit", ParameterDataType.BOOLEAN, "true", category = "Trailing Stop", visibility = ParameterVisibility.STANDARD),
            ParameterDefinition("max_spread_points", "Max Spread Allowed", "Blocks execution if spread exceeds threshold", ParameterDataType.INTEGER, "35", 10.0, 100.0, 5.0, "pts", "Safety Filters", ParameterVisibility.STANDARD),
            ParameterDefinition("trading_session", "Trading Sessions", "Permitted trading windows", ParameterDataType.ENUM, "LONDON_NY", category = "Execution", visibility = ParameterVisibility.STANDARD, enumOptions = listOf("ALL_DAY", "LONDON_ONLY", "NY_ONLY", "LONDON_NY")),
            ParameterDefinition("trailing_start_points", "Trailing Activation", "Profit points required before trailing engages", ParameterDataType.INTEGER, "60", 10.0, 500.0, 10.0, "pts", "Trailing Stop", ParameterVisibility.ADVANCED),
            ParameterDefinition("trailing_step_points", "Trailing Step", "Increment step for trailing stop adjustments", ParameterDataType.INTEGER, "20", 5.0, 100.0, 5.0, "pts", "Trailing Stop", ParameterVisibility.ADVANCED),
            ParameterDefinition("break_even_trigger", "Break-Even Points", "Profit points before moving SL to breakeven", ParameterDataType.INTEGER, "50", 10.0, 300.0, 5.0, "pts", "Trailing Stop", ParameterVisibility.ADVANCED),
            ParameterDefinition("atr_period", "ATR Volatility Period", "Lookback period for dynamic volatility calculations", ParameterDataType.INTEGER, "14", 5.0, 50.0, 1.0, "bars", "Indicator Settings", ParameterVisibility.ADVANCED),
            ParameterDefinition("rsi_period", "RSI Momentum Period", "Lookback period for relative strength filtering", ParameterDataType.INTEGER, "14", 2.0, 30.0, 1.0, "bars", "Indicator Settings", ParameterVisibility.ADVANCED),
            ParameterDefinition("rsi_overbought", "RSI Overbought Filter", "Threshold above which buy entries are suppressed", ParameterDataType.INTEGER, "70", 50.0, 95.0, 1.0, "level", "Indicator Settings", ParameterVisibility.ADVANCED),
            ParameterDefinition("rsi_oversold", "RSI Oversold Filter", "Threshold below which sell entries are suppressed", ParameterDataType.INTEGER, "30", 5.0, 50.0, 1.0, "level", "Indicator Settings", ParameterVisibility.ADVANCED),
            ParameterDefinition("magic_number", "EA Magic Number", "Unique identification number for MT5 order routing", ParameterDataType.INTEGER, "108821", 100000.0, 999999.0, 1.0, "", "Routing & Identity", ParameterVisibility.ADVANCED, requiresRestart = true)
        ),
        "ea_gold_scalper" to listOf(
            ParameterDefinition("lot_size", "Fixed Lot Size", "Order volume", ParameterDataType.DECIMAL, "0.05", 0.01, 5.0, 0.01, "lots", "Money Management", ParameterVisibility.STANDARD),
            ParameterDefinition("risk_percent", "Risk Percentage", "Equity risk %", ParameterDataType.DECIMAL, "1.0", 0.1, 3.0, 0.1, "%", "Money Management", ParameterVisibility.STANDARD),
            ParameterDefinition("stop_loss_points", "Stop Loss", "Points for scalp SL", ParameterDataType.INTEGER, "60", 15.0, 300.0, 5.0, "pts", "Risk & Stop Management", ParameterVisibility.STANDARD),
            ParameterDefinition("take_profit_points", "Take Profit", "Points for scalp TP", ParameterDataType.INTEGER, "90", 20.0, 500.0, 5.0, "pts", "Risk & Stop Management", ParameterVisibility.STANDARD),
            ParameterDefinition("max_spread_points", "Max Spread Allowed", "Max spread for scalp execution", ParameterDataType.INTEGER, "28", 10.0, 50.0, 1.0, "pts", "Safety Filters", ParameterVisibility.STANDARD),
            ParameterDefinition("trailing_stop_enabled", "Trailing Stop", "Enable trailing", ParameterDataType.BOOLEAN, "true", category = "Trailing Stop", visibility = ParameterVisibility.STANDARD),
            ParameterDefinition("trailing_start_points", "Trailing Activation", "Profit before trailing", ParameterDataType.INTEGER, "30", 10.0, 100.0, 5.0, "pts", "Trailing Stop", ParameterVisibility.ADVANCED),
            ParameterDefinition("magic_number", "EA Magic Number", "Order routing number", ParameterDataType.INTEGER, "209934", 100000.0, 999999.0, 1.0, "", "Routing & Identity", ParameterVisibility.ADVANCED, requiresRestart = true)
        )
    )

    private fun createInitialPresets(): List<EaPreset> = listOf(
        EaPreset(
            id = "pre_pro_conservative",
            eaProductId = "ea_gold_pro",
            name = "Conservative Gold",
            description = "Tight 0.05 lot size, 1.0% risk, 100pt SL, London-NY only. Ideal for funded challenge preservation.",
            values = mapOf(
                "lot_size" to "0.05",
                "risk_percent" to "1.0",
                "stop_loss_points" to "100",
                "take_profit_points" to "200",
                "max_open_trades" to "2",
                "max_spread_points" to "25",
                "trailing_stop_enabled" to "true",
                "break_even_enabled" to "true"
            ),
            isSystemDefault = true
        ),
        EaPreset(
            id = "pre_pro_balanced",
            eaProductId = "ea_gold_pro",
            name = "Balanced Growth",
            description = "Standard 0.10 lot size, 1.5% risk, 120pt SL, dynamic ATR volatility filter.",
            values = mapOf(
                "lot_size" to "0.10",
                "risk_percent" to "1.5",
                "stop_loss_points" to "120",
                "take_profit_points" to "240",
                "max_open_trades" to "3",
                "max_spread_points" to "35",
                "trailing_stop_enabled" to "true",
                "break_even_enabled" to "true"
            ),
            isSystemDefault = true
        ),
        EaPreset(
            id = "pre_pro_aggressive",
            eaProductId = "ea_gold_pro",
            name = "High Alpha / Scalp Heavy",
            description = "0.20 lot size, 2.5% risk, faster trailing stops for high volatility sessions.",
            values = mapOf(
                "lot_size" to "0.20",
                "risk_percent" to "2.5",
                "stop_loss_points" to "140",
                "take_profit_points" to "300",
                "max_open_trades" to "4",
                "max_spread_points" to "40",
                "trailing_stop_enabled" to "true",
                "break_even_enabled" to "false"
            ),
            isSystemDefault = false
        )
    )

    private fun createInitialAuditTrail(): List<ConfigAuditEntry> = listOf(
        ConfigAuditEntry(
            version = 3,
            parameterKey = "lot_size",
            oldValue = "0.05",
            newValue = "0.10",
            submittedAtUtc = "2026-11-05 18:20:10",
            appliedAtUtc = "2026-11-05 18:20:12",
            status = ConfigStatus.APPLIED
        ),
        ConfigAuditEntry(
            version = 2,
            parameterKey = "max_spread_points",
            oldValue = "45",
            newValue = "35",
            submittedAtUtc = "2026-11-04 12:15:00",
            appliedAtUtc = "2026-11-04 12:15:02",
            status = ConfigStatus.APPLIED
        ),
        ConfigAuditEntry(
            version = 1,
            parameterKey = "lot_size",
            oldValue = "0.01",
            newValue = "0.05",
            submittedAtUtc = "2026-11-03 09:00:00",
            appliedAtUtc = "2026-11-03 09:00:02",
            status = ConfigStatus.APPLIED
        )
    )

    private fun createInitialPositions(): List<Position> = listOf(
        Position(
            ticket = 91827411,
            brokerAccountId = "acc_mt5_demo_01",
            eaInstallationId = "inst_01_pro_acc1",
            eaName = "Gold Pro EA",
            symbol = "XAUUSD",
            direction = TradeDirection.BUY,
            lotSize = 0.10,
            openPrice = 2651.80,
            currentPrice = 2654.30,
            stopLoss = 2639.80,
            takeProfit = 2675.80,
            floatingProfitLoss = +250.00,
            magicNumber = 108821,
            openTimeUtc = "2026-11-05 17:42:15"
        ),
        Position(
            ticket = 91827495,
            brokerAccountId = "acc_mt5_demo_01",
            eaInstallationId = "inst_01_pro_acc1",
            eaName = "Gold Pro EA",
            symbol = "XAUUSD",
            direction = TradeDirection.BUY,
            lotSize = 0.10,
            openPrice = 2653.40,
            currentPrice = 2654.30,
            stopLoss = 2641.40,
            takeProfit = 2677.40,
            floatingProfitLoss = +90.50,
            magicNumber = 108821,
            openTimeUtc = "2026-11-05 19:10:04"
        ),
        Position(
            ticket = 91828102,
            brokerAccountId = "acc_mt5_demo_01",
            eaInstallationId = "inst_02_scalp_acc1",
            eaName = "Gold Scalper EA",
            symbol = "XAUUSD",
            direction = TradeDirection.BUY,
            lotSize = 0.05,
            openPrice = 2652.10,
            currentPrice = 2654.30,
            stopLoss = 2646.10,
            takeProfit = 2661.10,
            floatingProfitLoss = +110.00,
            magicNumber = 209934,
            openTimeUtc = "2026-11-05 20:05:32"
        )
    )

    private fun createInitialPendingOrders(): List<PendingOrder> = listOf(
        PendingOrder(
            ticket = 91829900,
            brokerAccountId = "acc_mt5_demo_01",
            eaInstallationId = "inst_01_pro_acc1",
            eaName = "Gold Pro EA",
            symbol = "XAUUSD",
            orderType = OrderType.BUY_LIMIT,
            lotSize = 0.10,
            targetPrice = 2645.00,
            currentMarketPrice = 2654.30,
            stopLoss = 2633.00,
            takeProfit = 2669.00,
            magicNumber = 108821,
            placedTimeUtc = "2026-11-05 18:00:00"
        )
    )

    private fun createInitialTradeHistory(): List<ClosedTrade> = listOf(
        ClosedTrade(91825001, "acc_mt5_demo_01", "Gold Pro EA", "XAUUSD", TradeDirection.BUY, 0.10, 2642.50, 2654.20, 2630.50, 2654.20, +117.00, -0.60, 0.0, "2026-11-05 13:10:00", "2026-11-05 15:45:10"),
        ClosedTrade(91824290, "acc_mt5_demo_01", "Gold Scalper EA", "XAUUSD", TradeDirection.SELL, 0.05, 2655.00, 2648.50, 2661.00, 2648.50, +32.50, -0.30, 0.0, "2026-11-05 11:22:15", "2026-11-05 12:05:00"),
        ClosedTrade(91823901, "acc_mt5_demo_01", "Gold Pro EA", "XAUUSD", TradeDirection.BUY, 0.10, 2640.00, 2635.00, 2635.00, 2664.00, -50.00, -0.60, 0.0, "2026-11-04 19:30:00", "2026-11-04 21:15:30"),
        ClosedTrade(91822840, "acc_mt5_demo_01", "Gold Pro EA", "XAUUSD", TradeDirection.BUY, 0.10, 2631.20, 2646.80, 2619.20, 2646.80, +156.00, -0.60, 0.0, "2026-11-04 14:00:00", "2026-11-04 18:30:20")
    )

    private fun generateInitialCandles(): List<Candlestick> {
        val list = mutableListOf<Candlestick>()
        var base = 2640.0
        val now = System.currentTimeMillis()
        for (i in 30 downTo 0) {
            val open = base
            val delta = (Random.nextDouble() - 0.45) * 3.5
            val close = open + delta
            val high = maxOf(open, close) + Random.nextDouble() * 2.0
            val low = minOf(open, close) - Random.nextDouble() * 2.0
            list.add(
                Candlestick(
                    timestamp = now - (i * 300_000L),
                    open = Math.round(open * 100.0) / 100.0,
                    high = Math.round(high * 100.0) / 100.0,
                    low = Math.round(low * 100.0) / 100.0,
                    close = Math.round(close * 100.0) / 100.0,
                    volume = Random.nextLong(200, 1800)
                )
            )
            base = close
        }
        return list
    }

    private fun createInitialVpsInstances(): List<VpsInstance> = listOf(
        VpsInstance(
            id = "vps_ld4_01",
            name = "[DEMO] XAU VPS Primary London",
            planName = "Enterprise Low-Latency (London LD4)",
            ipAddress = "185.220.101.45",
            os = "Windows Server 2022 Datacenter",
            cpuCores = 4,
            ramGb = 8,
            ssdGb = 160,
            region = "London, UK (0.8ms to Equinix MT5 servers)",
            status = VpsStatus.RUNNING,
            mt5TerminalStatus = "MetaTrader 5 Build 4480 Running",
            uptimeHours = 412,
            cpuUsagePercent = 14,
            ramUsagePercent = 38,
            lastHeartbeatUtc = currentUtc()
        )
    )

    private fun createInitialVpsLogs(): List<VpsLogEntry> = listOf(
        VpsLogEntry(currentUtc(), "INFO", "XauPilotBridge.mqh heartbeat acknowledged. Latency: 1.2ms"),
        VpsLogEntry("2026-11-05 20:45:00", "INFO", "Position #91828102 opened: BUY 0.05 XAUUSD @ 2652.10"),
        VpsLogEntry("2026-11-05 19:10:04", "INFO", "Position #91827495 opened: BUY 0.10 XAUUSD @ 2653.40"),
        VpsLogEntry("2026-11-05 18:20:12", "INFO", "Configuration v3 acknowledged and active: lot_size=0.10"),
        VpsLogEntry("2026-11-05 18:00:00", "SECURITY", "Tenant authorization check PASSED for ten_gold_alpha_99")
    )

    private fun createInitialNotifications(): List<AppNotification> = listOf(
        AppNotification(
            id = "notif_01",
            title = "EA Heartbeat Active",
            message = "Gold Pro EA reported healthy heartbeat on ICMarkets-Demo02",
            type = NotificationType.HEARTBEAT_ALERT,
            timestampUtc = currentUtc()
        ),
        AppNotification(
            id = "notif_02",
            title = "Remote Config Applied",
            message = "Configuration version #3 successfully applied to Gold Pro EA",
            type = NotificationType.CONFIG_APPLIED,
            timestampUtc = "2026-11-05 18:20:12"
        ),
        AppNotification(
            id = "notif_03",
            title = "[DEMO] Environment Active",
            message = "All operations currently routed through simulated demo safety sandbox",
            type = NotificationType.SYSTEM_SECURITY,
            timestampUtc = "2026-11-05 12:00:00"
        )
    )

    companion object {
        @Volatile
        private var INSTANCE: XauPilotRepository? = null

        fun initialize(context: Context): XauPilotRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: XauPilotRepository(context).also { INSTANCE = it }
            }
        }

        fun getInstance(context: Context? = null): XauPilotRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: XauPilotRepository(context).also { INSTANCE = it }
            }
        }
    }
}
