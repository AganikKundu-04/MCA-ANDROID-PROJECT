package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.RecurringExpenseEntity
import com.example.data.model.RecurrenceFrequency
import com.example.data.preferences.AccentStyle
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.AppSettings
import com.example.data.preferences.CurrencyOption
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.preferences.ThemeSettings
import com.example.data.preferences.WeekStartDay
import com.example.data.repository.LedgerRepository
import com.example.util.CurrencyUtils
import com.example.util.DateUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val themePreferences: ThemePreferencesRepository,
    private val appPreferences: AppPreferencesRepository,
) : ViewModel() {

    val themeSettings: StateFlow<ThemeSettings> = themePreferences.themeSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSettings())

    val appSettings: StateFlow<AppSettings> = appPreferences.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryFilterId = MutableStateFlow<Long?>(null)
    private val _reportFilter = MutableStateFlow(ReportFilterState())
    val reportFilter: StateFlow<ReportFilterState> = _reportFilter.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<LedgerSnackbarEvent>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<LedgerSnackbarEvent> = _snackbarEvents.asSharedFlow()

    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expensesUiState: StateFlow<ExpensesUiState> = combine(
        repository.expenses,
        repository.categories,
        repository.recurringExpenses,
        _searchQuery,
        _selectedCategoryFilterId,
    ) { expenses, categories, recurring, query, catFilter ->
        val categoryMap = categories.associateBy { it.id }
        val today = DateUtils.todayEpochDay()
        val monthStart = DateUtils.startOfMonthEpochDay(today)
        val monthEnd = DateUtils.endOfMonthEpochDay(today)

        val currentMonthExpenses = expenses.filter { it.dateEpochDay in monthStart..monthEnd }
        val trimmedQuery = query.trim().lowercase()

        val filtered = expenses.filter { expense ->
            val catMatch = when (catFilter) {
                null -> true
                UNCATEGORIZED_FILTER_ID -> expense.categoryId == null
                else -> expense.categoryId == catFilter
            }
            val queryMatch = if (trimmedQuery.isEmpty()) {
                true
            } else {
                val catName = expense.categoryId?.let { categoryMap[it]?.name }.orEmpty()
                expense.title.lowercase().contains(trimmedQuery) ||
                    expense.note.lowercase().contains(trimmedQuery) ||
                    catName.lowercase().contains(trimmedQuery)
            }
            catMatch && queryMatch
        }

        val groups = filtered
            .groupBy { it.dateEpochDay }
            .entries
            .sortedByDescending { it.key }
            .map { (day, dayItems) ->
                DailyExpenseGroup(
                    dateEpochDay = day,
                    headerLabel = DateUtils.formatRelativeDateHeader(day, today),
                    dayTotalCents = dayItems.sumOf { it.amountCents },
                    items = dayItems.map { ExpenseWithCategory(it, it.categoryId?.let(categoryMap::get)) },
                )
            }

        val activeMonthlyRecurring = recurring
            .filter { it.isActive }
            .sumOf { (it.amountCents * it.frequency.monthlyMultiplier).roundToLong() }

        ExpensesUiState(
            searchQuery = query,
            selectedCategoryFilterId = catFilter,
            currentMonthTotalCents = currentMonthExpenses.sumOf { it.amountCents },
            currentMonthCount = currentMonthExpenses.size,
            filteredTotalCents = filtered.sumOf { it.amountCents },
            totalExpenseCount = expenses.size,
            activeRecurringMonthlyCents = activeMonthlyRecurring,
            groups = groups,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    val categorySummaries: StateFlow<List<CategorySummary>> = combine(
        repository.categories,
        repository.expenses,
    ) { categories, expenses ->
        val byCat = expenses.groupBy { it.categoryId }
        categories.map { category ->
            val items = byCat[category.id].orEmpty()
            CategorySummary(
                category = category,
                expenseCount = items.size,
                totalCents = items.sumOf { it.amountCents },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recurringUiState: StateFlow<RecurringUiState> = combine(
        repository.recurringExpenses,
        repository.categories,
    ) { rules, categories ->
        val categoryMap = categories.associateBy { it.id }
        val activeRules = rules.filter { it.isActive }
        val estimatedMonthly = activeRules.sumOf {
            (it.amountCents * it.frequency.monthlyMultiplier).roundToLong()
        }
        RecurringUiState(
            rules = rules.map { RecurringWithCategory(it, it.categoryId?.let(categoryMap::get)) },
            activeCount = activeRules.size,
            estimatedMonthlyCents = estimatedMonthly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    val reportSummary: StateFlow<ReportSummary> = combine(
        repository.expenses,
        repository.categories,
        _reportFilter,
        appSettings,
    ) { expenses, categories, filter, settings ->
        ReportAnalytics.computeReport(
            expenses = expenses,
            categoriesById = categories.associateBy { it.id },
            filter = filter,
            todayEpochDay = DateUtils.todayEpochDay(),
            startWeekOnSunday = settings.weekStartDay == WeekStartDay.SUNDAY,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportSummary())

    val budgetSummary: StateFlow<BudgetDashboardSummary> = combine(
        repository.categories,
        repository.expenses,
    ) { categories, expenses ->
        BudgetAnalytics.evaluateMonthlyBudgets(
            categories = categories,
            expenses = expenses,
            referenceEpochDay = DateUtils.todayEpochDay(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetDashboardSummary())

    init {
        viewModelScope.launch {
            val savedSettings = appPreferences.appSettings.first()
            _reportFilter.update { it.copy(preset = savedSettings.defaultReportPreset) }
            val logged = repository.initialize()
            if (logged > 0) {
                emitAutoLoggedMessage(logged)
            }
        }
    }

    fun refreshDueRecurringExpenses(notifyIfNone: Boolean = false) {
        viewModelScope.launch {
            val logged = repository.processDueRecurringExpenses()
            when {
                logged > 0 -> emitAutoLoggedMessage(logged)
                notifyIfNone -> _snackbarEvents.tryEmit(
                    LedgerSnackbarEvent.Info("All recurring expenses are up to date"),
                )
            }
        }
    }

    // --- Expense Filtering ---
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectExpenseCategoryFilter(categoryId: Long?) {
        _selectedCategoryFilterId.value = categoryId
    }

    // --- Expense CRUD ---
    fun saveExpense(
        existing: ExpenseEntity?,
        title: String,
        amountCents: Long,
        categoryId: Long?,
        dateEpochDay: Long,
        note: String,
        onResult: (Result<Long>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = repository.saveExpense(
                existingId = existing?.id,
                title = title,
                amountCents = amountCents,
                categoryId = categoryId,
                dateEpochDay = dateEpochDay,
                note = note,
                recurringExpenseId = existing?.recurringExpenseId,
                isAutoLogged = existing?.isAutoLogged ?: false,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
            )
            onResult(result)
            if (result.isSuccess) {
                val breach = repository.checkCategoryMonthlyBreach(
                    categoryId = categoryId,
                    expenseDateEpochDay = dateEpochDay,
                )
                val message = if (breach != null) {
                    val (breachedCategory, overageCents) = breach
                    val formattedOverage = CurrencyUtils.formatCents(
                        overageCents,
                        appSettings.value.currencyConfig,
                    )
                    "Budget alert: ${breachedCategory.name} is $formattedOverage over monthly limit"
                } else if (existing == null) {
                    "Expense added"
                } else {
                    "Expense updated"
                }
                _snackbarEvents.tryEmit(LedgerSnackbarEvent.Info(message))
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense).onSuccess {
                _snackbarEvents.tryEmit(LedgerSnackbarEvent.ExpenseDeleted(expense))
            }
        }
    }

    fun restoreExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.restoreExpense(expense)
        }
    }

    // --- Category CRUD ---
    fun saveCategory(
        existing: CategoryEntity?,
        name: String,
        colorHex: String?,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result: Result<Unit> = if (existing == null) {
                repository.createCategory(name, colorHex).map { }
            } else {
                repository.updateCategory(existing, name, colorHex)
            }
            onResult(result)
            if (result.isSuccess) {
                _snackbarEvents.tryEmit(
                    LedgerSnackbarEvent.Info(
                        if (existing == null) "Category \"$name\" created" else "Category updated",
                    ),
                )
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category).onSuccess {
                if (_selectedCategoryFilterId.value == category.id) {
                    _selectedCategoryFilterId.value = null
                }
                _reportFilter.update { state ->
                    state.copy(selectedCategoryIds = state.selectedCategoryIds - category.id)
                }
                _snackbarEvents.tryEmit(LedgerSnackbarEvent.Info("Category \"${category.name}\" deleted"))
            }
        }
    }

    fun setCategoryMonthlyBudget(category: CategoryEntity, budgetCents: Long?) {
        viewModelScope.launch {
            repository.setCategoryMonthlyBudget(category.id, budgetCents).onSuccess {
                val message = if (budgetCents != null && budgetCents > 0L) {
                    val formatted = CurrencyUtils.formatCents(budgetCents, appSettings.value.currencyConfig)
                    "Set ${category.name} limit to $formatted/mo"
                } else {
                    "Removed monthly limit for ${category.name}"
                }
                _snackbarEvents.tryEmit(LedgerSnackbarEvent.Info(message))
            }
        }
    }

    // --- Recurring CRUD ---
    fun saveRecurringExpense(
        existing: RecurringExpenseEntity?,
        name: String,
        amountCents: Long,
        categoryId: Long?,
        frequency: RecurrenceFrequency,
        startDateEpochDay: Long,
        isActive: Boolean,
        note: String,
        onResult: (Result<Int>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = repository.saveRecurringExpense(
                existing = existing,
                name = name,
                amountCents = amountCents,
                categoryId = categoryId,
                frequency = frequency,
                startDateEpochDay = startDateEpochDay,
                isActive = isActive,
                note = note,
            )
            onResult(result)
            result.onSuccess { autoLogged ->
                val message = when {
                    autoLogged > 0 -> "Saved & auto-logged $autoLogged due ${if (autoLogged == 1) "entry" else "entries"}"
                    existing == null -> "Recurring schedule added"
                    else -> "Recurring schedule updated"
                }
                _snackbarEvents.tryEmit(LedgerSnackbarEvent.Info(message))
            }
        }
    }

    fun toggleRecurringActive(rule: RecurringExpenseEntity, active: Boolean) {
        viewModelScope.launch {
            repository.toggleRecurringActive(rule, active).onSuccess { logged ->
                if (logged > 0) {
                    emitAutoLoggedMessage(logged)
                }
            }
        }
    }

    fun deleteRecurringExpense(rule: RecurringExpenseEntity) {
        viewModelScope.launch {
            repository.deleteRecurringExpense(rule).onSuccess {
                _snackbarEvents.tryEmit(LedgerSnackbarEvent.Info("Recurring rule removed"))
            }
        }
    }

    // --- Report Filters ---
    fun selectReportDatePreset(preset: DateRangePreset) {
        _reportFilter.update { it.copy(preset = preset) }
    }

    fun setCustomReportDateRange(startEpochDay: Long, endEpochDay: Long) {
        _reportFilter.update {
            it.copy(
                preset = DateRangePreset.CUSTOM,
                customStartEpochDay = startEpochDay,
                customEndEpochDay = endEpochDay,
            )
        }
    }

    fun toggleReportCategoryFilter(categoryId: Long) {
        _reportFilter.update { state ->
            val updated = if (categoryId in state.selectedCategoryIds) {
                state.selectedCategoryIds - categoryId
            } else {
                state.selectedCategoryIds + categoryId
            }
            state.copy(selectedCategoryIds = updated)
        }
    }

    fun clearReportCategoryFilters() {
        _reportFilter.update { it.copy(selectedCategoryIds = emptySet()) }
    }

    fun setReportGranularity(granularity: TimeGranularity) {
        _reportFilter.update { it.copy(granularity = granularity) }
    }

    // --- Appearance / Device Theme Preferences ---
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setUseDynamicColor(enabled) }
    }

    fun setAccentStyle(accent: AccentStyle) {
        viewModelScope.launch { themePreferences.setAccentStyle(accent) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setHighContrast(enabled) }
    }

    // --- App Settings Preferences ---
    fun setCurrency(currency: CurrencyOption) {
        viewModelScope.launch {
            appPreferences.setCurrency(currency)
            _snackbarEvents.tryEmit(
                LedgerSnackbarEvent.Info("Currency set to ${currency.code} (${currency.symbol})"),
            )
        }
    }

    fun setShowDecimals(show: Boolean) {
        viewModelScope.launch { appPreferences.setShowDecimals(show) }
    }

    fun setMonthlyBudgetCents(budgetCents: Long?) {
        viewModelScope.launch {
            appPreferences.setMonthlyBudgetCents(budgetCents)
            _snackbarEvents.tryEmit(
                LedgerSnackbarEvent.Info(
                    if (budgetCents != null) "Monthly spending limit saved" else "Monthly spending limit cleared",
                ),
            )
        }
    }

    fun setDefaultReportPreset(preset: DateRangePreset) {
        viewModelScope.launch {
            appPreferences.setDefaultReportPreset(preset)
            _reportFilter.update { it.copy(preset = preset) }
        }
    }

    fun setWeekStartDay(day: WeekStartDay) {
        viewModelScope.launch { appPreferences.setWeekStartDay(day) }
    }

    private fun emitAutoLoggedMessage(count: Int) {
        val noun = if (count == 1) "recurring expense" else "recurring expenses"
        _snackbarEvents.tryEmit(LedgerSnackbarEvent.Info("Auto-logged $count due $noun"))
    }

    companion object {
        fun provideFactory(
            repository: LedgerRepository,
            themePreferences: ThemePreferencesRepository,
            appPreferences: AppPreferencesRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LedgerViewModel(repository, themePreferences, appPreferences) as T
                }
            }
    }
}
