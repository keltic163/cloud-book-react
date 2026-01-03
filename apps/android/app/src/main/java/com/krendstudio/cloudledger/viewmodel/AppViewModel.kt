package com.krendstudio.cloudledger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.krendstudio.cloudledger.data.remote.FirebaseProvider
import com.krendstudio.cloudledger.data.repository.LedgerRepository
import com.krendstudio.cloudledger.model.AppUser
import com.krendstudio.cloudledger.model.Defaults
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.ParsedTransaction
import com.krendstudio.cloudledger.model.RecurringTemplate
import com.krendstudio.cloudledger.model.SavedLedger
import com.krendstudio.cloudledger.model.SystemAnnouncement
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionDraft
import com.krendstudio.cloudledger.model.TransactionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AuthUiState(
    val user: AppUser? = null,
    val isLoading: Boolean = true,
    val isMockMode: Boolean = false
)

data class LedgerUiState(
    val savedLedgers: List<SavedLedger> = emptyList(),
    val currentLedgerId: String? = null,
    val isInitializing: Boolean = true
)

class AppViewModel(
    private val ledgerRepository: LedgerRepository
) : ViewModel() {
    private val auth: FirebaseAuth = FirebaseProvider.auth
    private val dateFormatter = DateTimeFormatter.ISO_DATE

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _ledgerState = MutableStateFlow(LedgerUiState())
    val ledgerState: StateFlow<LedgerUiState> = _ledgerState.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _expenseCategories = MutableStateFlow(Defaults.expenseCategories)
    val expenseCategories: StateFlow<List<String>> = _expenseCategories.asStateFlow()

    private val _incomeCategories = MutableStateFlow(Defaults.incomeCategories)
    val incomeCategories: StateFlow<List<String>> = _incomeCategories.asStateFlow()

    private val _members = MutableStateFlow<List<LedgerMember>>(emptyList())
    val members: StateFlow<List<LedgerMember>> = _members.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _announcement = MutableStateFlow<SystemAnnouncement?>(null)
    val announcement: StateFlow<SystemAnnouncement?> = _announcement.asStateFlow()

    private val _recurringTemplates = MutableStateFlow<List<RecurringTemplate>>(emptyList())
    val recurringTemplates: StateFlow<List<RecurringTemplate>> = _recurringTemplates.asStateFlow()

    private var ledgerJob: Job? = null
    private var metaJob: Job? = null
    private var transactionsJob: Job? = null
    private var recurringJob: Job? = null
    private var currentLedgerId: String? = null
    private var mockMode = false

    init {
        observeAuthState()
        observeSystemAnnouncement()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authStateFlow().collect { user ->
                if (mockMode) return@collect
                updateAuthState(user, isMock = false)
            }
        }
    }

    private fun authStateFlow() = callbackFlow<AppUser?> {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAppUser())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.toAppUser())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun enterMockMode() {
        mockMode = true
        val mockUser = AppUser(
            uid = "mock-user-123",
            displayName = "體驗使用者",
            email = "demo@example.com",
            photoUrl = null
        )
        updateAuthState(mockUser, isMock = true)
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return runCatching {
            mockMode = false
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
        }
    }

    fun signOut() {
        val user = _authState.value.user
        if (mockMode) {
            mockMode = false
            if (user != null) {
                viewModelScope.launch { ledgerRepository.clearLocalData(user.uid) }
            }
            updateAuthState(null, isMock = false)
            return
        }
        if (user != null) {
            viewModelScope.launch { ledgerRepository.clearLocalData(user.uid) }
        }
        auth.signOut()
    }

    suspend fun refreshUserProfile(): Result<Unit> {
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        if (_authState.value.isMockMode) return Result.success(Unit)
        return ledgerRepository.refreshUserProfile(user).map { Unit }
    }

    suspend fun syncCurrentLedger(forceFull: Boolean = false): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val user = _authState.value.user
        return runCatching {
            ledgerRepository.syncLedgerMeta(ledgerId).getOrThrow()
            ledgerRepository.syncTransactions(ledgerId, forceFull).getOrThrow()
            if (user != null) {
                ledgerRepository.syncRecurringTemplates(ledgerId, user.uid).getOrThrow()
            }
        }
    }

    suspend fun createLedger(name: String): Result<SavedLedger> {
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val safeName = if (name.isBlank()) "未命名帳本" else name
        return if (_authState.value.isMockMode) {
            val now = System.currentTimeMillis()
            val entry = SavedLedger("mock-ledger-$now", safeName, now)
            val newList = _ledgerState.value.savedLedgers.filter { it.id != entry.id } + entry
            ledgerRepository.updateLocalProfile(user.uid, entry.id, newList)
            Result.success(entry)
        } else {
            ledgerRepository.createLedger(user, safeName)
        }
    }

    suspend fun joinLedger(ledgerId: String): Result<SavedLedger> {
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val trimmed = ledgerId.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty ledger id"))
        }
        return if (_authState.value.isMockMode) {
            val now = System.currentTimeMillis()
            val entry = SavedLedger(trimmed, "本機帳本", now)
            val newList = _ledgerState.value.savedLedgers.filter { it.id != trimmed } + entry
            ledgerRepository.updateLocalProfile(user.uid, trimmed, newList)
            Result.success(entry)
        } else {
            ledgerRepository.joinLedger(user, trimmed)
        }
    }

    suspend fun switchLedger(ledgerId: String): Result<Unit> {
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val trimmed = ledgerId.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty ledger id"))
        }
        return if (_authState.value.isMockMode) {
            val saved = _ledgerState.value.savedLedgers.map {
                if (it.id == trimmed) it.copy(lastAccessedAt = System.currentTimeMillis()) else it
            }
            ledgerRepository.updateLocalProfile(user.uid, trimmed, saved)
            Result.success(Unit)
        } else {
            ledgerRepository.switchLedger(user, trimmed)
        }
    }

    suspend fun leaveLedger(ledgerId: String): Result<String?> {
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val trimmed = ledgerId.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty ledger id"))
        }
        return if (_authState.value.isMockMode) {
            val saved = _ledgerState.value.savedLedgers.filter { it.id != trimmed }
            val nextLedgerId = if (currentLedgerId == trimmed) {
                saved.firstOrNull()?.id
            } else {
                currentLedgerId
            }
            ledgerRepository.updateLocalProfile(user.uid, nextLedgerId, saved)
            Result.success(nextLedgerId)
        } else {
            ledgerRepository.leaveLedger(user, trimmed, currentLedgerId)
        }
    }

    suspend fun addCategory(type: TransactionType, category: String): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val name = category.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty category"))
        }

        val updatedExpense = if (type == TransactionType.EXPENSE) {
            (_expenseCategories.value + name).distinct()
        } else {
            _expenseCategories.value
        }
        val updatedIncome = if (type == TransactionType.INCOME) {
            (_incomeCategories.value + name).distinct()
        } else {
            _incomeCategories.value
        }

        return if (_authState.value.isMockMode) {
            _expenseCategories.value = updatedExpense
            _incomeCategories.value = updatedIncome
            Result.success(Unit)
        } else {
            ledgerRepository.updateCategories(ledgerId, updatedExpense, updatedIncome).onSuccess {
                _expenseCategories.value = updatedExpense
                _incomeCategories.value = updatedIncome
            }
        }
    }

    suspend fun deleteCategory(type: TransactionType, category: String): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val name = category.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty category"))
        }

        val updatedExpense = if (type == TransactionType.EXPENSE) {
            _expenseCategories.value.filterNot { it == name }
        } else {
            _expenseCategories.value
        }
        val updatedIncome = if (type == TransactionType.INCOME) {
            _incomeCategories.value.filterNot { it == name }
        } else {
            _incomeCategories.value
        }

        return if (_authState.value.isMockMode) {
            _expenseCategories.value = updatedExpense
            _incomeCategories.value = updatedIncome
            Result.success(Unit)
        } else {
            ledgerRepository.updateCategories(ledgerId, updatedExpense, updatedIncome).onSuccess {
                _expenseCategories.value = updatedExpense
                _incomeCategories.value = updatedIncome
            }
        }
    }

    suspend fun resetCategories(): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val expense = Defaults.expenseCategories
        val income = Defaults.incomeCategories
        return if (_authState.value.isMockMode) {
            _expenseCategories.value = expense
            _incomeCategories.value = income
            Result.success(Unit)
        } else {
            ledgerRepository.updateCategories(ledgerId, expense, income).onSuccess {
                _expenseCategories.value = expense
                _incomeCategories.value = income
            }
        }
    }

    suspend fun addTransaction(
        amount: Double,
        type: TransactionType,
        category: String,
        description: String,
        rewards: Double,
        date: LocalDate,
        targetUserUid: String?
    ): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val result = ledgerRepository.addTransaction(
            ledgerId = ledgerId,
            user = user,
            amount = amount,
            type = type,
            category = category,
            description = description,
            rewards = rewards,
            date = date.format(dateFormatter),
            targetUserUid = targetUserUid
        )
        if (result.isSuccess) {
            ledgerRepository.syncTransactions(ledgerId)
        }
        return result
    }

    suspend fun parseSmartInput(
        text: String,
        categories: List<String>,
        apiKey: String?
    ): Result<ParsedTransaction> {
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        return runCatching {
            val callable = FirebaseFunctions.getInstance().getHttpsCallable("parseTransaction")
            val result = callable.call(
                mapOf(
                    "text" to text,
                    "categories" to categories,
                    "apiKey" to apiKey
                )
            ).await()
            val data = result.data as? Map<*, *> ?: error("Invalid response")
            val amount = (data["amount"] as? Number)?.toDouble() ?: error("Missing amount")
            val typeRaw = data["type"] as? String ?: TransactionType.EXPENSE.name
            val type = runCatching { TransactionType.valueOf(typeRaw) }.getOrDefault(TransactionType.EXPENSE)
            val category = data["category"] as? String ?: ""
            val description = data["description"] as? String ?: ""
            val rewards = (data["rewards"] as? Number)?.toDouble() ?: 0.0
            val date = data["date"] as? String
            ParsedTransaction(amount, type, category, description, rewards, date)
        }
    }

    suspend fun importTransactions(items: List<TransactionDraft>): Result<Int> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        var successCount = 0
        items.forEach { item ->
            ledgerRepository.addTransaction(
                ledgerId = ledgerId,
                user = user,
                amount = item.amount,
                type = item.type,
                category = item.category,
                description = item.description,
                rewards = item.rewards,
                date = item.date,
                targetUserUid = item.targetUserUid
            ).onSuccess {
                successCount += 1
            }
        }
        return Result.success(successCount)
    }

    suspend fun updateTransaction(
        transactionId: String,
        updates: Map<String, Any?>,
        expectedUpdatedAt: Long?
    ): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        ledgerRepository.syncTransactions(ledgerId)
        val result = ledgerRepository.updateTransaction(ledgerId, transactionId, updates, expectedUpdatedAt)
        if (result.isSuccess) {
            ledgerRepository.syncTransactions(ledgerId)
        }
        return result
    }

    suspend fun deleteTransaction(transactionId: String): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val result = ledgerRepository.deleteTransaction(ledgerId, transactionId)
        if (result.isSuccess) {
            ledgerRepository.syncTransactions(ledgerId)
        }
        return result
    }

    suspend fun createRecurringTemplate(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        intervalMonths: Int,
        executeDay: Int,
        nextRunAt: Long,
        totalRuns: Int?,
        remainingRuns: Int?
    ): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val result = ledgerRepository.createRecurringTemplate(
            ledgerId = ledgerId,
            userId = user.uid,
            title = title,
            amount = amount,
            type = if (type == TransactionType.EXPENSE) "expense" else "income",
            category = category,
            note = null,
            intervalMonths = intervalMonths,
            executeDay = executeDay,
            nextRunAt = nextRunAt,
            totalRuns = totalRuns,
            remainingRuns = remainingRuns
        )
        if (result.isSuccess) {
            ledgerRepository.syncRecurringTemplates(ledgerId, user.uid)
        }
        return result
    }

    suspend fun toggleRecurringActive(templateId: String, isActive: Boolean): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val result = ledgerRepository.updateRecurringActive(templateId, isActive)
        if (result.isSuccess) {
            ledgerRepository.syncRecurringTemplates(ledgerId, user.uid)
        }
        return result
    }

    suspend fun deleteRecurringTemplate(templateId: String): Result<Unit> {
        val ledgerId = _ledgerState.value.currentLedgerId
            ?: return Result.failure(IllegalStateException("Missing ledger"))
        val user = _authState.value.user ?: return Result.failure(IllegalStateException("Missing user"))
        val result = ledgerRepository.deleteRecurringTemplate(templateId)
        if (result.isSuccess) {
            ledgerRepository.syncRecurringTemplates(ledgerId, user.uid)
        }
        return result
    }

    fun setSelectedDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    private fun updateAuthState(user: AppUser?, isMock: Boolean) {
        _authState.value = AuthUiState(
            user = user,
            isLoading = false,
            isMockMode = isMock
        )
        startLedgerSession(user)
    }

    private fun startLedgerSession(user: AppUser?) {
        ledgerJob?.cancel()
        if (user == null) {
            currentLedgerId = null
            _ledgerState.value = LedgerUiState(isInitializing = false)
            clearLedgerObservers()
            return
        }

        _ledgerState.value = _ledgerState.value.copy(isInitializing = true)
        ledgerJob = viewModelScope.launch {
            ledgerRepository.observeUserProfile(user.uid).collect { profile ->
                _ledgerState.value = LedgerUiState(
                    savedLedgers = profile.savedLedgers,
                    currentLedgerId = profile.lastLedgerId,
                    isInitializing = false
                )
                if (currentLedgerId != profile.lastLedgerId) {
                    currentLedgerId = profile.lastLedgerId
                    startLedgerObservers(profile.lastLedgerId)
                }
            }
        }

        if (!_authState.value.isMockMode) {
            viewModelScope.launch {
                ledgerRepository.refreshUserProfile(user)
            }
        }
    }

    private fun startLedgerObservers(ledgerId: String?) {
        clearLedgerObservers()
        if (ledgerId.isNullOrBlank()) {
            _transactions.value = emptyList()
            _expenseCategories.value = Defaults.expenseCategories
            _incomeCategories.value = Defaults.incomeCategories
            _members.value = emptyList()
            _recurringTemplates.value = emptyList()
            return
        }

        metaJob = viewModelScope.launch {
            ledgerRepository.observeLedgerMeta(ledgerId)
                .catch { }
                .collect { meta ->
                _expenseCategories.value = meta.expenseCategories
                _incomeCategories.value = meta.incomeCategories
                _members.value = meta.members
            }
        }

        transactionsJob = viewModelScope.launch {
            ledgerRepository.observeTransactions(ledgerId)
                .catch { }
                .collect { items ->
                _transactions.value = items
            }
        }

        val user = _authState.value.user
        if (user != null) {
            recurringJob = viewModelScope.launch {
                ledgerRepository.observeRecurringTemplates(ledgerId, user.uid)
                    .catch { }
                    .collect { items ->
                    _recurringTemplates.value = items
                }
            }
        }

        viewModelScope.launch {
            ledgerRepository.syncLedgerMeta(ledgerId)
            ledgerRepository.syncTransactions(ledgerId, forceFull = true)
            if (user != null) {
                ledgerRepository.syncRecurringTemplates(ledgerId, user.uid)
            }
        }
    }

    private fun clearLedgerObservers() {
        metaJob?.cancel()
        transactionsJob?.cancel()
        metaJob = null
        transactionsJob = null
        recurringJob?.cancel()
        recurringJob = null
    }

    private fun observeSystemAnnouncement() {
        viewModelScope.launch {
            ledgerRepository.observeSystemAnnouncement()
                .catch { }
                .collect { announcement ->
                _announcement.value = announcement
            }
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toAppUser(): AppUser {
        val name = displayName ?: email ?: "使用者"
        return AppUser(
            uid = uid,
            displayName = name,
            email = email,
            photoUrl = photoUrl?.toString()
        )
    }

    class Factory(private val ledgerRepository: LedgerRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AppViewModel(ledgerRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
