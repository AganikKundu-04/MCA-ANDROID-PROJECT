package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.preferences.LocalCurrencyConfig
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryColorDot
import com.example.ui.components.HigContentUnavailable
import com.example.ui.components.HigFilterPill
import com.example.ui.components.HigInsetDivider
import com.example.ui.components.HigInsetGroup
import com.example.ui.components.HigProgressTrack
import com.example.ui.components.HigSectionHeader
import com.example.ui.components.higWarningColor
import com.example.ui.state.BudgetDashboardSummary
import com.example.ui.state.ExpenseWithCategory
import com.example.ui.state.ExpensesUiState
import com.example.ui.state.UNCATEGORIZED_FILTER_ID
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.parseHexColorOrNull
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@Composable
fun ExpensesScreen(
    uiState: ExpensesUiState,
    budgetSummary: BudgetDashboardSummary,
    categories: List<CategoryEntity>,
    monthlyBudgetCents: Long?,
    onSearchChange: (String) -> Unit,
    onCategoryFilterSelect: (Long?) -> Unit,
    onOpenBudgets: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit,
    onDeleteExpenseClick: (ExpenseEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    pendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.expenses_delete_confirm_title)) },
            text = { Text(stringResource(R.string.expenses_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteExpenseClick(expense)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_expense"),
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Calm Apple HIG Monthly Summary Inset Card (content-first, no decorative gradients)
        item(key = "dashboard_summary_card") {
            DashboardMonthlySummaryCard(
                monthTotalCents = uiState.currentMonthTotalCents,
                monthCount = uiState.currentMonthCount,
                activeRecurringMonthlyCents = uiState.activeRecurringMonthlyCents,
                monthlyBudgetCents = monthlyBudgetCents,
            )
        }

        // 2. Category Budget Breach & Warning Monitor
        if (budgetSummary.hasBreaches) {
            item(key = "dashboard_budget_breach_alert") {
                DashboardBudgetBreachCard(
                    summary = budgetSummary,
                    onReviewBudgets = onOpenBudgets,
                )
            }
        } else if (budgetSummary.hasWarnings) {
            item(key = "dashboard_budget_warning_alert") {
                DashboardBudgetWarningCard(
                    summary = budgetSummary,
                    onReviewBudgets = onOpenBudgets,
                )
            }
        }

        // 3. iOS-style Search Bar + Category Filter Pills
        item(key = "search_and_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HigSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchChange,
                    placeholder = stringResource(R.string.expenses_search_placeholder),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HigFilterPill(
                        label = stringResource(R.string.expenses_all_categories),
                        selected = uiState.selectedCategoryFilterId == null,
                        onClick = { onCategoryFilterSelect(null) },
                        modifier = Modifier.testTag("filter_cat_all"),
                    )
                    categories.forEach { category ->
                        HigFilterPill(
                            label = category.name,
                            selected = uiState.selectedCategoryFilterId == category.id,
                            onClick = {
                                val next = if (uiState.selectedCategoryFilterId == category.id) null else category.id
                                onCategoryFilterSelect(next)
                            },
                            accentColor = parseHexColorOrNull(category.colorHex),
                            leadingDotHex = category.colorHex,
                            modifier = Modifier.testTag("filter_cat_${category.id}"),
                        )
                    }
                    HigFilterPill(
                        label = stringResource(R.string.expenses_uncategorized),
                        selected = uiState.selectedCategoryFilterId == UNCATEGORIZED_FILTER_ID,
                        onClick = {
                            val next = if (uiState.selectedCategoryFilterId == UNCATEGORIZED_FILTER_ID) {
                                null
                            } else {
                                UNCATEGORIZED_FILTER_ID
                            }
                            onCategoryFilterSelect(next)
                        },
                        modifier = Modifier.testTag("filter_cat_uncategorized"),
                    )
                }
            }
        }

        // 4. Empty States or Grouped Daily Expense Lists
        if (uiState.totalExpenseCount == 0) {
            item(key = "empty_ledger") {
                HigContentUnavailable(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = stringResource(R.string.expenses_empty_title),
                    subtitle = stringResource(R.string.expenses_empty_subtitle),
                    actionLabel = "Add Expense",
                    onActionClick = onAddExpenseClick,
                    actionTestTag = "empty_add_expense_button",
                )
            }
        } else if (uiState.groups.isEmpty()) {
            item(key = "empty_filtered") {
                HigContentUnavailable(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.expenses_filtered_empty_title),
                    subtitle = stringResource(R.string.expenses_filtered_empty_subtitle),
                    actionLabel = "Clear Filters",
                    onActionClick = {
                        onSearchChange("")
                        onCategoryFilterSelect(null)
                    },
                    actionTestTag = "clear_expense_filters_button",
                )
            }
        } else {
            items(
                items = uiState.groups,
                key = { "day_group_${it.dateEpochDay}" },
            ) { group ->
                val currencyConfig = LocalCurrencyConfig.current
                Column {
                    HigSectionHeader(
                        title = group.headerLabel,
                        trailing = CurrencyUtils.formatCents(group.dayTotalCents, currencyConfig),
                    )
                    HigInsetGroup {
                        group.items.forEachIndexed { index, item ->
                            HigExpenseRow(
                                item = item,
                                onClick = { onExpenseClick(item.expense) },
                                onDeleteClick = { pendingDelete = item.expense },
                            )
                            if (index < group.items.lastIndex) {
                                HigInsetDivider(startIndent = 68.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMonthlySummaryCard(
    monthTotalCents: Long,
    monthCount: Int,
    activeRecurringMonthlyCents: Long,
    monthlyBudgetCents: Long?,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val monthLabel = remember { DateUtils.formatMonthYear(DateUtils.todayEpochDay()) }
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = higWarningColor()

    HigInsetGroup(modifier = Modifier.testTag("dashboard_summary_card")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$monthCount ${if (monthCount == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = CurrencyUtils.formatCents(monthTotalCents, currencyConfig),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("dashboard_month_total"),
            )

            if (monthlyBudgetCents != null && monthlyBudgetCents > 0L) {
                val ratio = monthTotalCents.toFloat() / monthlyBudgetCents.toFloat()
                val pct = ((monthTotalCents * 100L) / monthlyBudgetCents).toInt()
                val overBudget = monthTotalCents > monthlyBudgetCents
                val barColor = when {
                    overBudget -> errorColor
                    pct >= 80 -> warningColor
                    else -> MaterialTheme.colorScheme.primary
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HigProgressTrack(
                        progress = ratio,
                        color = barColor,
                        height = 6.dp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (overBudget) {
                                "Overall limit exceeded by ${CurrencyUtils.formatCents(monthTotalCents - monthlyBudgetCents, currencyConfig)}"
                            } else {
                                "$pct% of ${CurrencyUtils.formatCents(monthlyBudgetCents, currencyConfig)} overall limit"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overBudget) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (activeRecurringMonthlyCents > 0L) {
                HigInsetDivider(startIndent = 0.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Autorenew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Recurring Commitment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${CurrencyUtils.formatCents(activeRecurringMonthlyCents, currencyConfig)}/mo",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardBudgetBreachCard(
    summary: BudgetDashboardSummary,
    onReviewBudgets: () -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val errorColor = MaterialTheme.colorScheme.error
    val count = summary.breachedItems.size

    HigInsetGroup(
        borderColor = errorColor.copy(alpha = 0.35f),
        modifier = Modifier.testTag("dashboard_breach_banner"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(errorColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Budget breach alert",
                        tint = errorColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_breach_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = errorColor,
                    )
                    Text(
                        text = "$count ${if (count == 1) "category" else "categories"} exceeded by ${CurrencyUtils.formatCents(summary.totalOverageCents, currencyConfig)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.breachedItems.take(3).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            CategoryColorDot(colorHex = item.category.colorHex, size = 9.dp)
                            Text(
                                text = item.category.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "+${CurrencyUtils.formatCents(item.overageCents, currencyConfig)} over",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = errorColor,
                        )
                    }
                }
                if (summary.breachedItems.size > 3) {
                    Text(
                        text = "+${summary.breachedItems.size - 3} more categories over budget",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HigInsetDivider(startIndent = 16.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onReviewBudgets)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("dashboard_open_budgets_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_manage_budgets),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DashboardBudgetWarningCard(
    summary: BudgetDashboardSummary,
    onReviewBudgets: () -> Unit,
) {
    val warningColor = higWarningColor()
    val count = summary.warningItems.size
    val topWarning = summary.warningItems.first()

    HigInsetGroup(
        borderColor = warningColor.copy(alpha = 0.35f),
        modifier = Modifier.testTag("dashboard_warning_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onReviewBudgets)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(warningColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Budget warning",
                    tint = warningColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_warning_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = warningColor,
                )
                Text(
                    text = if (count == 1) {
                        "${topWarning.category.name} is at ${topWarning.percentUsed}% of monthly limit"
                    } else {
                        "${topWarning.category.name} (${topWarning.percentUsed}%) and ${count - 1} more near limit"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Review budgets",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HigSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_search_input"),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onQueryChange("") }
                        .testTag("expense_search_clear"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HigExpenseRow(
    item: ExpenseWithCategory,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .testTag("expense_row_${item.expense.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(
            name = item.category?.name,
            colorHex = item.category?.colorHex,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.expense.isAutoLogged) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.expenses_auto_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            val metaText = buildString {
                append(item.category?.name ?: stringResource(R.string.expenses_uncategorized))
                if (item.expense.note.isNotBlank()) {
                    append(" · ")
                    append(item.expense.note)
                }
            }
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = CurrencyUtils.formatCents(item.expense.amountCents, currencyConfig),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("delete_expense_${item.expense.id}"),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${item.expense.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
