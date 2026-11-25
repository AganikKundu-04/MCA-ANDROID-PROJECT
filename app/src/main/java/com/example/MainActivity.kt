package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tonality
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.LedgerDatabase
import com.example.data.local.RecurringExpenseEntity
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.LocalCurrencyConfig
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.repository.LedgerRepository
import com.example.ui.screens.AppearanceSheet
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.CategoryEditorDialog
import com.example.ui.screens.ExpenseEditorSheet
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.RecurringEditorSheet
import com.example.ui.screens.RecurringScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsSheet
import com.example.ui.state.LedgerSnackbarEvent
import com.example.ui.state.LedgerTab
import com.example.ui.state.LedgerViewModel
import com.example.ui.theme.LedgerFlowTheme
import com.example.ui.theme.LocalLedgerDarkTheme
import com.example.ui.theme.resolveIsDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = LedgerDatabase.getInstance(applicationContext)
        val repository = LedgerRepository(database.ledgerDao())
        val themePreferences = ThemePreferencesRepository(applicationContext)
        val appPreferences = AppPreferencesRepository(applicationContext)

        setContent {
            val viewModel: LedgerViewModel = viewModel(
                factory = LedgerViewModel.provideFactory(repository, themePreferences, appPreferences),
            )
            val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
            val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
            val isDark = resolveIsDarkTheme(themeSettings.themeMode)

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            LedgerFlowTheme(settings = themeSettings) {
                CompositionLocalProvider(LocalCurrencyConfig provides appSettings.currencyConfig) {
                    LedgerFlowApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerFlowApp(
    viewModel: LedgerViewModel,
) {
    val expensesState by viewModel.expensesUiState.collectAsStateWithLifecycle()
    val budgetSummary by viewModel.budgetSummary.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categorySummaries by viewModel.categorySummaries.collectAsStateWithLifecycle()
    val recurringState by viewModel.recurringUiState.collectAsStateWithLifecycle()
    val reportFilter by viewModel.reportFilter.collectAsStateWithLifecycle()
    val reportSummary by viewModel.reportSummary.collectAsStateWithLifecycle()
    val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(LedgerTab.EXPENSES) }

    // Modal editors state
    var showExpenseSheet by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    var showRecurringSheet by remember { mutableStateOf(false) }
    var editingRecurring by remember { mutableStateOf<RecurringExpenseEntity?>(null) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryDialogError by remember { mutableStateOf<String?>(null) }

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Automatically log any newly due recurring expenses when the app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDueRecurringExpenses(notifyIfNone = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { event ->
            when (event) {
                is LedgerSnackbarEvent.Info -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short,
                    )
                }
                is LedgerSnackbarEvent.ExpenseDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted \"${event.expense.title}\"",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreExpense(event.expense)
                    }
                }
            }
        }
    }

    BackHandler(enabled = currentTab != LedgerTab.EXPENSES) {
        currentTab = LedgerTab.EXPENSES
    }

    if (showSettingsSheet) {
        SettingsSheet(
            appSettings = appSettings,
            themeSettings = themeSettings,
            onSelectCurrency = viewModel::setCurrency,
            onToggleShowDecimals = viewModel::setShowDecimals,
            onSetMonthlyBudgetCents = viewModel::setMonthlyBudgetCents,
            onSelectDefaultReportPreset = viewModel::setDefaultReportPreset,
            onSelectWeekStartDay = viewModel::setWeekStartDay,
            onOpenAppearance = { showAppearanceSheet = true },
            onDismiss = { showSettingsSheet = false },
        )
    }

    if (showAppearanceSheet) {
        AppearanceSheet(
            settings = themeSettings,
            onSelectThemeMode = viewModel::setThemeMode,
            onToggleDynamicColor = viewModel::setUseDynamicColor,
            onSelectAccentStyle = viewModel::setAccentStyle,
            onToggleHighContrast = viewModel::setHighContrast,
            onDismiss = { showAppearanceSheet = false },
        )
    }

    if (showExpenseSheet) {
        ExpenseEditorSheet(
            editingExpense = editingExpense,
            categories = categories,
            onDismiss = {
                showExpenseSheet = false
                editingExpense = null
            },
            onQuickCreateCategory = {
                editingCategory = null
                categoryDialogError = null
                showCategoryDialog = true
            },
            onSave = { title, amountCents, categoryId, dateEpochDay, note ->
                viewModel.saveExpense(
                    existing = editingExpense,
                    title = title,
                    amountCents = amountCents,
                    categoryId = categoryId,
                    dateEpochDay = dateEpochDay,
                    note = note,
                ) { result ->
                    if (result.isSuccess) {
                        showExpenseSheet = false
                        editingExpense = null
                    }
                }
            },
            onDelete = { expense ->
                viewModel.deleteExpense(expense)
            },
        )
    }

    if (showRecurringSheet) {
        RecurringEditorSheet(
            editingRule = editingRecurring,
            categories = categories,
            onDismiss = {
                showRecurringSheet = false
                editingRecurring = null
            },
            onQuickCreateCategory = {
                editingCategory = null
                categoryDialogError = null
                showCategoryDialog = true
            },
            onSave = { name, amountCents, categoryId, frequency, startDateEpochDay, isActive, note ->
                viewModel.saveRecurringExpense(
                    existing = editingRecurring,
                    name = name,
                    amountCents = amountCents,
                    categoryId = categoryId,
                    frequency = frequency,
                    startDateEpochDay = startDateEpochDay,
                    isActive = isActive,
                    note = note,
                ) { result ->
                    if (result.isSuccess) {
                        showRecurringSheet = false
                        editingRecurring = null
                    }
                }
            },
        )
    }

    if (showCategoryDialog) {
        CategoryEditorDialog(
            editingCategory = editingCategory,
            externalError = categoryDialogError,
            onDismiss = {
                showCategoryDialog = false
                editingCategory = null
                categoryDialogError = null
            },
            onSave = { name, colorHex ->
                viewModel.saveCategory(
                    existing = editingCategory,
                    name = name,
                    colorHex = colorHex,
                ) { result ->
                    result.onSuccess {
                        showCategoryDialog = false
                        editingCategory = null
                        categoryDialogError = null
                    }.onFailure { err ->
                        categoryDialogError = err.message ?: "Unable to save category"
                    }
                }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        val topBarTitle = when (currentTab) {
            LedgerTab.EXPENSES -> stringResource(R.string.expenses_title)
            LedgerTab.BUDGETS -> stringResource(R.string.budgets_title)
            LedgerTab.REPORTS -> stringResource(R.string.reports_title)
            LedgerTab.RECURRING -> stringResource(R.string.recurring_title)
            LedgerTab.CATEGORIES -> stringResource(R.string.categories_title)
        }

        val appearanceIcon = when (themeSettings.themeMode) {
            ThemeMode.SYSTEM -> Icons.Rounded.Tonality
            ThemeMode.LIGHT -> Icons.Rounded.LightMode
            ThemeMode.DARK -> Icons.Rounded.DarkMode
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = topBarTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        // Primary contextual Apple HIG top-bar '+' creation button (no intrusive FAB)
                        when (currentTab) {
                            LedgerTab.EXPENSES -> {
                                TopBarPrimaryAddButton(
                                    contentDesc = "Add expense",
                                    tag = "fab_add_expense",
                                    onClick = {
                                        editingExpense = null
                                        showExpenseSheet = true
                                    },
                                )
                            }
                            LedgerTab.RECURRING -> {
                                TopBarPrimaryAddButton(
                                    contentDesc = "Add recurring expense",
                                    tag = "fab_add_recurring",
                                    onClick = {
                                        editingRecurring = null
                                        showRecurringSheet = true
                                    },
                                )
                            }
                            LedgerTab.CATEGORIES -> {
                                TopBarPrimaryAddButton(
                                    contentDesc = "New category",
                                    tag = "fab_add_category",
                                    onClick = {
                                        editingCategory = null
                                        categoryDialogError = null
                                        showCategoryDialog = true
                                    },
                                )
                            }
                            LedgerTab.BUDGETS, LedgerTab.REPORTS -> Unit
                        }

                        val appearanceDesc = stringResource(R.string.appearance_button_desc)
                        IconButton(
                            onClick = { showAppearanceSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = appearanceDesc }
                                .testTag("open_appearance_button"),
                        ) {
                            Icon(
                                imageVector = appearanceIcon,
                                contentDescription = appearanceDesc,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        val settingsDesc = stringResource(R.string.settings_button_desc)
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(48.dp)
                                .semantics { contentDescription = settingsDesc }
                                .testTag("open_settings_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = settingsDesc,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    HigTabBar(
                        currentTab = currentTab,
                        hasBudgetBreaches = budgetSummary.hasBreaches,
                        onTabSelected = { currentTab = it },
                    )
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(Modifier.weight(0.1f))
                        LedgerTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Box {
                                        Icon(
                                            imageVector = tab.icon(selected),
                                            contentDescription = tab.label,
                                        )
                                        if (tab == LedgerTab.BUDGETS && budgetSummary.hasBreaches) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 3.dp, y = (-2).dp)
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error),
                                            )
                                        }
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                    ) {
                        when (currentTab) {
                            LedgerTab.EXPENSES -> ExpensesScreen(
                                uiState = expensesState,
                                budgetSummary = budgetSummary,
                                categories = categories,
                                monthlyBudgetCents = appSettings.monthlyBudgetCents,
                                onSearchChange = viewModel::updateSearchQuery,
                                onCategoryFilterSelect = viewModel::selectExpenseCategoryFilter,
                                onOpenBudgets = { currentTab = LedgerTab.BUDGETS },
                                onAddExpenseClick = {
                                    editingExpense = null
                                    showExpenseSheet = true
                                },
                                onExpenseClick = { expense ->
                                    editingExpense = expense
                                    showExpenseSheet = true
                                },
                                onDeleteExpenseClick = viewModel::deleteExpense,
                            )
                            LedgerTab.BUDGETS -> BudgetsScreen(
                                summary = budgetSummary,
                                onSetCategoryBudget = viewModel::setCategoryMonthlyBudget,
                                onCreateCategoryClick = {
                                    editingCategory = null
                                    categoryDialogError = null
                                    showCategoryDialog = true
                                },
                            )
                            LedgerTab.REPORTS -> ReportsScreen(
                                filter = reportFilter,
                                summary = reportSummary,
                                categories = categories,
                                onSelectDatePreset = viewModel::selectReportDatePreset,
                                onCustomDateRangeSelected = viewModel::setCustomReportDateRange,
                                onToggleCategoryFilter = viewModel::toggleReportCategoryFilter,
                                onClearCategoryFilters = viewModel::clearReportCategoryFilters,
                                onSelectGranularity = viewModel::setReportGranularity,
                            )
                            LedgerTab.RECURRING -> RecurringScreen(
                                uiState = recurringState,
                                onAddRecurringClick = {
                                    editingRecurring = null
                                    showRecurringSheet = true
                                },
                                onEditRecurringClick = { rule ->
                                    editingRecurring = rule
                                    showRecurringSheet = true
                                },
                                onToggleActive = viewModel::toggleRecurringActive,
                                onDeleteRecurringConfirm = viewModel::deleteRecurringExpense,
                                onCheckDueNowClick = {
                                    viewModel.refreshDueRecurringExpenses(notifyIfNone = true)
                                },
                            )
                            LedgerTab.CATEGORIES -> CategoriesScreen(
                                summaries = categorySummaries,
                                onAddCategoryClick = {
                                    editingCategory = null
                                    categoryDialogError = null
                                    showCategoryDialog = true
                                },
                                onEditCategoryClick = { cat ->
                                    editingCategory = cat
                                    categoryDialogError = null
                                    showCategoryDialog = true
                                },
                                onDeleteCategoryConfirm = viewModel::deleteCategory,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBarPrimaryAddButton(
    contentDesc: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = contentDesc,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Authentic Apple HIG translucent 5-tab bar: crisp top hairline separator,
 * calm monochrome inactive tint, primary accent active tint, breach indicator dot on Budgets,
 * and proper WindowInsets.navigationBars padding.
 */
@Composable
private fun HigTabBar(
    currentTab: LedgerTab,
    hasBudgetBreaches: Boolean,
    onTabSelected: (LedgerTab) -> Unit,
) {
    val isDark = LocalLedgerDarkTheme.current
    val barColor = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }

    Surface(
        color = barColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LedgerTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(role = Role.Tab) { onTabSelected(tab) }
                            .testTag("nav_${tab.name.lowercase()}"),
                    ) {
                        Box {
                            Icon(
                                imageVector = tab.icon(selected),
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(23.dp),
                            )
                            if (tab == LedgerTab.BUDGETS && hasBudgetBreaches) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .testTag("nav_budgets_breach_dot"),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun LedgerTab.icon(selected: Boolean): ImageVector = when (this) {
    LedgerTab.EXPENSES -> if (selected) Icons.AutoMirrored.Rounded.ReceiptLong else Icons.AutoMirrored.Outlined.ReceiptLong
    LedgerTab.BUDGETS -> if (selected) Icons.Rounded.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
    LedgerTab.REPORTS -> if (selected) Icons.Rounded.PieChart else Icons.Outlined.PieChart
    LedgerTab.RECURRING -> if (selected) Icons.Rounded.Autorenew else Icons.Outlined.Autorenew
    LedgerTab.CATEGORIES -> if (selected) Icons.Rounded.Category else Icons.Outlined.Category
}
