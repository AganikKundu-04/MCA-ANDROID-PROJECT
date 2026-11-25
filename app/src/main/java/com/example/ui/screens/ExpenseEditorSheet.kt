package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.ExpenseEntity
import com.example.data.preferences.LocalCurrencyConfig
import com.example.ui.components.CategorySelectorRow
import com.example.ui.components.EpochDayDatePickerDialog
import com.example.ui.components.HigInsetDivider
import com.example.ui.components.HigInsetGroup
import com.example.ui.components.HigSectionHeader
import com.example.ui.components.HigSheetNavHeader
import com.example.ui.theme.MonoFontFamily
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorSheet(
    editingExpense: ExpenseEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onQuickCreateCategory: () -> Unit,
    onSave: (title: String, amountCents: Long, categoryId: Long?, dateEpochDay: Long, note: String) -> Unit,
    onDelete: ((ExpenseEntity) -> Unit)? = null,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(editingExpense) { mutableStateOf(editingExpense?.title.orEmpty()) }
    var amountText by remember(editingExpense) {
        mutableStateOf(
            editingExpense?.amountCents?.let { CurrencyUtils.centsToInputString(it, currencyConfig.currency) }.orEmpty(),
        )
    }
    var selectedCategoryId by remember(editingExpense) { mutableStateOf(editingExpense?.categoryId) }
    var dateEpochDay by remember(editingExpense) {
        mutableLongStateOf(editingExpense?.dateEpochDay ?: DateUtils.todayEpochDay())
    }
    var note by remember(editingExpense) { mutableStateOf(editingExpense?.note.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedCents = CurrencyUtils.parseDollarsToCents(amountText)
    val canSave = title.trim().isNotEmpty() && parsedCents != null && parsedCents > 0L

    if (showDatePicker) {
        EpochDayDatePickerDialog(
            initialEpochDay = dateEpochDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { dateEpochDay = it },
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
                    if (editingExpense == null) R.string.expenses_add_title else R.string.expenses_edit_title,
                ),
                onCancel = onDismiss,
                onPrimaryAction = {
                    val cents = CurrencyUtils.parseDollarsToCents(amountText)
                    when {
                        title.trim().isEmpty() -> validationError = "Please enter an expense name"
                        cents == null || cents <= 0L -> validationError = "Enter an amount greater than zero"
                        else -> onSave(title.trim(), cents, selectedCategoryId, dateEpochDay, note.trim())
                    }
                },
                primaryEnabled = canSave,
                primaryTestTag = "save_expense_button",
                cancelTestTag = "cancel_expense_button",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Amount & Title Inset Group
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
                                .testTag("expense_amount_input"),
                        )

                        HigInsetDivider(startIndent = 16.dp)

                        TextField(
                            value = title,
                            onValueChange = {
                                title = it
                                validationError = null
                            },
                            placeholder = { Text("Expense name (e.g. Grocery run)") },
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
                                .testTag("expense_title_input"),
                        )

                        HigInsetDivider(startIndent = 16.dp)

                        TextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = { Text("Optional note") },
                            maxLines = 3,
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
                                .testTag("expense_note_input"),
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

                // Category Selector
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

                // Date Row
                Column {
                    HigSectionHeader(title = "DATE")
                    HigInsetGroup {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button) { showDatePicker = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("expense_date_picker_button"),
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
                                    text = "Date",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = DateUtils.formatShortDate(dateEpochDay),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Choose date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // Destructive delete row when editing
                if (editingExpense != null && onDelete != null) {
                    HigInsetGroup {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button) {
                                    onDelete(editingExpense)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("sheet_delete_expense_button"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Delete Expense",
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
