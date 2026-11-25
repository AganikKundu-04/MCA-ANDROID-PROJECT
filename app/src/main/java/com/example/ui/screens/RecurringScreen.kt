package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.RecurringExpenseEntity
import com.example.data.model.RecurrenceFrequency
import com.example.data.preferences.LocalCurrencyConfig
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategorySelectorRow
import com.example.ui.components.EpochDayDatePickerDialog
import com.example.ui.components.HigContentUnavailable
import com.example.ui.components.HigInsetDivider
import com.example.ui.components.HigInsetGroup
import com.example.ui.components.HigSectionHeader
import com.example.ui.components.HigSegmentedControl
import com.example.ui.components.HigSheetNavHeader
import com.example.ui.state.RecurringUiState
import com.example.ui.state.RecurringWithCategory
import com.example.ui.theme.MonoFontFamily
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@Composable
fun RecurringScreen(
    uiState: RecurringUiState,
    onAddRecurringClick: () -> Unit,
    onEditRecurringClick: (RecurringExpenseEntity) -> Unit,
    onToggleActive: (RecurringExpenseEntity, Boolean) -> Unit,
    onDeleteRecurringConfirm: (RecurringExpenseEntity) -> Unit,
    onCheckDueNowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<RecurringExpenseEntity?>(null) }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.recurring_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recurring_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecurringConfirm(rule)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_recurring"),
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

    val currencyConfig = LocalCurrencyConfig.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("recurring_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Overview Inset Card
        item(key = "recurring_overview") {
            HigInsetGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recurring_monthly_est),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${uiState.activeCount} active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = CurrencyUtils.formatCents(uiState.estimatedMonthlyCents, currencyConfig),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                HigInsetDivider(startIndent = 16.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onCheckDueNowClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("sync_due_recurring_button"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.recurring_check_due),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "Auto-logs on due date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (uiState.rules.isEmpty()) {
            item(key = "recurring_empty") {
                HigContentUnavailable(
                    icon = Icons.Rounded.Autorenew,
                    title = stringResource(R.string.recurring_empty_title),
                    subtitle = stringResource(R.string.recurring_empty_subtitle),
                    actionLabel = "Add Recurring",
                    onActionClick = onAddRecurringClick,
                    actionTestTag = "empty_add_recurring_button",
                )
            }
        } else {
            item(key = "recurring_rules_group") {
                Column {
                    HigSectionHeader(
                        title = "SCHEDULES",
                        trailing = "${uiState.rules.size} total",
                    )
                    HigInsetGroup {
                        uiState.rules.forEachIndexed { index, item ->
                            HigRecurringRow(
                                item = item,
                                onClick = { onEditRecurringClick(item.rule) },
                                onToggleActive = { active -> onToggleActive(item.rule, active) },
                                onDeleteClick = { pendingDelete = item.rule },
                            )
                            if (index < uiState.rules.lastIndex) {
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
private fun HigRecurringRow(
    item: RecurringWithCategory,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val rule = item.rule

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .testTag("recurring_row_${rule.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(
                name = item.category?.name,
                colorHex = item.category?.colorHex,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rule.isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${rule.frequency.displayName} · ${item.category?.name ?: "Uncategorized"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = CurrencyUtils.formatCents(rule.amountCents, currencyConfig),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (rule.isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (rule.isActive) {
                    "Next due ${DateUtils.formatShortDate(rule.nextDueDateEpochDay)}"
                } else {
                    "Paused · Starts ${DateUtils.formatShortDate(rule.startDateEpochDay)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_recurring_${rule.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${rule.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Switch(
                    checked = rule.isActive,
                    onCheckedChange = onToggleActive,
                    modifier = Modifier.testTag("toggle_recurring_${rule.id}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorSheet(
    editingRule: RecurringExpenseEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onQuickCreateCategory: () -> Unit,
    onSave: (
        name: String,
        amountCents: Long,
        categoryId: Long?,
        frequency: RecurrenceFrequency,
        startDateEpochDay: Long,
        isActive: Boolean,
        note: String,
    ) -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(editingRule) { mutableStateOf(editingRule?.name.orEmpty()) }
    var amountText by remember(editingRule) {
        mutableStateOf(
            editingRule?.amountCents?.let { CurrencyUtils.centsToInputString(it, currencyConfig.currency) }.orEmpty(),
        )
    }
    var selectedCategoryId by remember(editingRule) { mutableStateOf(editingRule?.categoryId) }
    var frequency by remember(editingRule) {
        mutableStateOf(editingRule?.frequency ?: RecurrenceFrequency.MONTHLY)
    }
    var startDateEpochDay by remember(editingRule) {
        mutableLongStateOf(editingRule?.startDateEpochDay ?: DateUtils.todayEpochDay())
    }
    var isActive by remember(editingRule) { mutableStateOf(editingRule?.isActive ?: true) }
    var note by remember(editingRule) { mutableStateOf(editingRule?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedCents = CurrencyUtils.parseDollarsToCents(amountText)
    val canSave = name.trim().isNotEmpty() && parsedCents != null && parsedCents > 0L

    if (showDatePicker) {
        EpochDayDatePickerDialog(
            initialEpochDay = startDateEpochDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { startDateEpochDay = it },
        )
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HigSheetNavHeader(
                title = stringResource(
                    if (editingRule == null) R.string.recurring_add_title else R.string.recurring_edit_title,
                ),
                onCancel = onDismiss,
                onPrimaryAction = {
                    val cents = CurrencyUtils.parseDollarsToCents(amountText)
                    when {
                        name.trim().isEmpty() -> validationError = "Please enter an expense name"
                        cents == null || cents <= 0L -> validationError = "Enter an amount greater than zero"
                        else -> onSave(
                            name.trim(),
                            cents,
                            selectedCategoryId,
                            frequency,
                            startDateEpochDay,
                            isActive,
                            note.trim(),
                        )
                    }
                },
                primaryEnabled = canSave,
                primaryTestTag = "save_recurring_button",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    HigSectionHeader(title = "DETAILS")
                    HigInsetGroup {
                        TextField(
                            value = amountText,
                            onValueChange = { input ->
                                amountText = if (currencyConfig.currency.defaultFractionDigits == 0) {
                                    input.filter { it.isDigit() }
                                } else {
                                    input.filter { it.isDigit() || it == '.' }
                                }
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
                                .padding(horizontal = 4.dp)
                                .testTag("recurring_amount_input"),
                        )

                        HigInsetDivider(startIndent = 16.dp)

                        TextField(
                            value = name,
                            onValueChange = {
                                name = it
                                validationError = null
                            },
                            placeholder = { Text("Expense name (e.g. Apartment Rent)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .testTag("recurring_name_input"),
                        )

                        HigInsetDivider(startIndent = 16.dp)

                        TextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = { Text("Optional note") },
                            maxLines = 2,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .testTag("recurring_note_input"),
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

                Column {
                    HigSectionHeader(title = "FREQUENCY")
                    HigSegmentedControl(
                        options = RecurrenceFrequency.entries,
                        selected = frequency,
                        label = { it.displayName },
                        onSelect = { frequency = it },
                        testTagPrefix = "recurring_freq",
                    )
                }

                Column {
                    HigSectionHeader(title = "SCHEDULE")
                    HigInsetGroup {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button) { showDatePicker = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("recurring_start_date_button"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp),
                                )
                                Text(
                                    text = "Start Date",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = DateUtils.formatShortDate(startDateEpochDay),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Choose start date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        HigInsetDivider(startIndent = 16.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Log Active",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Automatically creates expenses on due dates",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isActive,
                                onCheckedChange = { isActive = it },
                                modifier = Modifier.testTag("recurring_active_switch"),
                            )
                        }
                    }
                }

                Column {
                    HigSectionHeader(title = "CATEGORY")
                    CategorySelectorRow(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = { selectedCategoryId = it },
                        onAddCategoryClick = onQuickCreateCategory,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
