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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.preferences.LocalCurrencyConfig
import com.example.ui.components.CategoryBadge
import com.example.ui.components.HigContentUnavailable
import com.example.ui.components.HigInsetDivider
import com.example.ui.components.HigInsetGroup
import com.example.ui.components.HigProgressTrack
import com.example.ui.components.HigSectionHeader
import com.example.ui.components.HigSheetNavHeader
import com.example.ui.components.higSuccessColor
import com.example.ui.components.higWarningColor
import com.example.ui.state.BudgetDashboardSummary
import com.example.ui.state.BudgetHealthStatus
import com.example.ui.state.CategoryBudgetItem
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.parseHexColorOrNull
import com.example.util.CurrencyUtils

@Composable
fun BudgetsScreen(
    summary: BudgetDashboardSummary,
    onSetCategoryBudget: (CategoryEntity, Long?) -> Unit,
    onCreateCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingItem by remember { mutableStateOf<CategoryBudgetItem?>(null) }

    editingItem?.let { item ->
        CategoryBudgetEditorSheet(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { newLimitCents ->
                onSetCategoryBudget(item.category, newLimitCents)
                editingItem = null
            },
        )
    }

    val totalCategories = summary.budgetedItems.size + summary.unbudgetedItems.size
    if (totalCategories == 0) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            HigContentUnavailable(
                icon = Icons.Rounded.AccountBalanceWallet,
                title = stringResource(R.string.budgets_empty_title),
                subtitle = stringResource(R.string.budgets_empty_subtitle),
                actionLabel = "Create Category",
                onActionClick = onCreateCategoryClick,
                actionTestTag = "budgets_empty_create_category",
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("budgets_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "budget_overview_card") {
            MonthlyBudgetOverviewCard(summary = summary)
        }

        if (summary.budgetedItems.isNotEmpty()) {
            item(key = "budgeted_categories_section") {
                Column {
                    HigSectionHeader(
                        title = stringResource(R.string.budgets_section_limits),
                        trailing = "${summary.budgetedItems.size} active",
                    )
                    HigInsetGroup {
                        summary.budgetedItems.forEachIndexed { index, item ->
                            BudgetedCategoryRow(
                                item = item,
                                onClick = { editingItem = item },
                            )
                            if (index < summary.budgetedItems.lastIndex) {
                                HigInsetDivider(startIndent = 68.dp)
                            }
                        }
                    }
                }
            }
        }

        if (summary.unbudgetedItems.isNotEmpty()) {
            item(key = "unbudgeted_categories_section") {
                Column {
                    HigSectionHeader(
                        title = stringResource(R.string.budgets_section_unset),
                        trailing = "${summary.unbudgetedItems.size}",
                    )
                    HigInsetGroup {
                        summary.unbudgetedItems.forEachIndexed { index, item ->
                            UnbudgetedCategoryRow(
                                item = item,
                                onClick = { editingItem = item },
                            )
                            if (index < summary.unbudgetedItems.lastIndex) {
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
private fun MonthlyBudgetOverviewCard(
    summary: BudgetDashboardSummary,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val successColor = higSuccessColor()
    val warningColor = higWarningColor()
    val errorColor = MaterialTheme.colorScheme.error

    val statusColor = when {
        summary.hasBreaches -> errorColor
        summary.hasWarnings -> warningColor
        summary.budgetedCategoryCount > 0 -> successColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when {
        summary.hasBreaches -> Icons.Rounded.ErrorOutline
        summary.hasWarnings -> Icons.Rounded.WarningAmber
        else -> Icons.Rounded.CheckCircle
    }

    val statusText = when {
        summary.hasBreaches -> {
            val n = summary.breachedItems.size
            "$n ${if (n == 1) "Category" else "Categories"} Over Budget"
        }
        summary.hasWarnings -> {
            val n = summary.warningItems.size
            "$n Approaching Limit"
        }
        summary.budgetedCategoryCount > 0 -> "All Categories Within Limit"
        else -> "No Category Limits Set"
    }

    val overallProgress = if (summary.totalBudgetedLimitCents > 0L) {
        summary.totalSpentInBudgetedCents.toFloat() / summary.totalBudgetedLimitCents.toFloat()
    } else {
        0f
    }

    HigInsetGroup(
        modifier = Modifier.testTag("budgets_overview_card"),
    ) {
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
                    text = summary.monthLabel.ifEmpty { "This Month" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.14f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (summary.totalBudgetedLimitCents > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = CurrencyUtils.formatCents(summary.totalSpentInBudgetedCents, currencyConfig),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = MonoFontFamily,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "of ${CurrencyUtils.formatCents(summary.totalBudgetedLimitCents, currencyConfig)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    HigProgressTrack(
                        progress = overallProgress,
                        color = statusColor,
                        height = 8.dp,
                    )
                }

                if (summary.hasBreaches) {
                    HigInsetDivider(startIndent = 0.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total Over Budget",
                            style = MaterialTheme.typography.bodyMedium,
                            color = errorColor,
                        )
                        Text(
                            text = "+${CurrencyUtils.formatCents(summary.totalOverageCents, currencyConfig)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = errorColor,
                        )
                    }
                }
            } else {
                Text(
                    text = "Set monthly limits below to track spending per category and get instant breach alerts on your Dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetedCategoryRow(
    item: CategoryBudgetItem,
    onClick: () -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val warningColor = higWarningColor()
    val errorColor = MaterialTheme.colorScheme.error
    val categoryColor = parseHexColorOrNull(item.category.colorHex) ?: MaterialTheme.colorScheme.primary

    val progressColor = when (item.status) {
        BudgetHealthStatus.BREACHED -> errorColor
        BudgetHealthStatus.WARNING -> warningColor
        BudgetHealthStatus.ON_TRACK, null -> categoryColor
    }

    val statusCaption = when (item.status) {
        BudgetHealthStatus.BREACHED ->
            "Over by ${CurrencyUtils.formatCents(item.overageCents, currencyConfig)}"
        BudgetHealthStatus.WARNING ->
            "${CurrencyUtils.formatCents(item.remainingCents ?: 0L, currencyConfig)} left"
        BudgetHealthStatus.ON_TRACK, null ->
            "${CurrencyUtils.formatCents(item.remainingCents ?: 0L, currencyConfig)} left"
    }

    val statusCaptionColor = when (item.status) {
        BudgetHealthStatus.BREACHED -> errorColor
        BudgetHealthStatus.WARNING -> warningColor
        BudgetHealthStatus.ON_TRACK, null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("budget_row_${item.category.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(
                name = item.category.name,
                colorHex = item.category.colorHex,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusCaption,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (item.status == BudgetHealthStatus.BREACHED) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = statusCaptionColor,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.formatCents(item.spentThisMonthCents, currencyConfig),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (item.status == BudgetHealthStatus.BREACHED) errorColor else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "of ${CurrencyUtils.formatCents(item.monthlyLimitCents ?: 0L, currencyConfig)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Edit budget for ${item.category.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HigProgressTrack(
                progress = item.progressRatio,
                color = progressColor,
                height = 6.dp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${item.percentUsed}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = statusCaptionColor,
            )
        }
    }
}

@Composable
private fun UnbudgetedCategoryRow(
    item: CategoryBudgetItem,
    onClick: () -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("budget_set_${item.category.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(
            name = item.category.name,
            colorHex = item.category.colorHex,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.category.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.spentThisMonthCents > 0L) {
                    "Spent ${CurrencyUtils.formatCents(item.spentThisMonthCents, currencyConfig)} this month"
                } else {
                    "No spending this month"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.budgets_set_limit),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Set budget for ${item.category.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryBudgetEditorSheet(
    item: CategoryBudgetItem,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by remember(item) {
        mutableStateOf(
            item.monthlyLimitCents?.let { CurrencyUtils.centsToInputString(it, currencyConfig.currency) } ?: "",
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedCents = CurrencyUtils.parseDollarsToCents(amountText)
    val canSave = parsedCents != null && parsedCents > 0L

    val quickPresetMajorUnits = remember(currencyConfig.currency) {
        when (currencyConfig.currency.code) {
            "INR" -> listOf(2000L, 5000L, 10000L, 25000L)
            "JPY" -> listOf(10000L, 30000L, 50000L, 100000L)
            else -> listOf(100L, 250L, 500L, 1000L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HigSheetNavHeader(
                title = "Monthly Limit",
                onCancel = onDismiss,
                onPrimaryAction = {
                    val cents = CurrencyUtils.parseDollarsToCents(amountText)
                    if (cents == null || cents <= 0L) {
                        validationError = "Enter a valid monthly limit greater than zero"
                    } else {
                        onSave(cents)
                    }
                },
                primaryEnabled = canSave,
                primaryTestTag = "save_category_budget_button",
                cancelTestTag = "cancel_category_budget_button",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Category context header
                HigInsetGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(
                            name = item.category.name,
                            colorHex = item.category.colorHex,
                            size = 44.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.category.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Spent this month: ${CurrencyUtils.formatCents(item.spentThisMonthCents, currencyConfig)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Limit input
                Column {
                    HigSectionHeader(title = "MONTHLY CAP (${currencyConfig.currency.code})")
                    HigInsetGroup {
                        TextField(
                            value = amountText,
                            onValueChange = { input ->
                                val allowed = if (currencyConfig.currency.defaultFractionDigits == 0) {
                                    input.filter { it.isDigit() }
                                } else {
                                    input.filter { it.isDigit() || it == '.' }
                                }
                                amountText = allowed
                                validationError = null
                            },
                            placeholder = {
                                Text(
                                    text = if (currencyConfig.currency.defaultFractionDigits == 0) "0" else "0.00",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonoFontFamily),
                                )
                            },
                            prefix = {
                                Text(
                                    text = "${currencyConfig.currency.symbol} ",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (currencyConfig.currency.defaultFractionDigits == 0) {
                                    KeyboardType.Number
                                } else {
                                    KeyboardType.Decimal
                                },
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("category_budget_amount_input"),
                        )
                    }
                    if (validationError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                // Quick preset suggestions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickPresetMajorUnits.forEach { major ->
                        val presetCents = major * 100L
                        val label = CurrencyUtils.formatCents(
                            presetCents,
                            currencyConfig.copy(showDecimals = false),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    amountText = major.toString()
                                    validationError = null
                                }
                                .testTag("budget_preset_$major"),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // Remove existing limit row
                if (item.monthlyLimitCents != null) {
                    HigInsetGroup {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button) { onSave(null) }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("category_budget_remove_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.budgets_remove_limit),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
