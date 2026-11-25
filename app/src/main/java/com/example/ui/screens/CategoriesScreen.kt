package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.preferences.LocalCurrencyConfig
import com.example.ui.components.CategoryBadge
import com.example.ui.components.HigContentUnavailable
import com.example.ui.components.HigInsetDivider
import com.example.ui.components.HigInsetGroup
import com.example.ui.components.HigSectionHeader
import com.example.ui.components.HigSheetNavHeader
import com.example.ui.state.CategorySummary
import com.example.ui.theme.CategoryColorPresets
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.contrastingForeground
import com.example.ui.theme.parseHexColorOrNull
import com.example.util.CurrencyUtils

@Composable
fun CategoriesScreen(
    summaries: List<CategorySummary>,
    onAddCategoryClick: () -> Unit,
    onEditCategoryClick: (CategoryEntity) -> Unit,
    onDeleteCategoryConfirm: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<CategorySummary?>(null) }

    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.categories_delete_confirm_title)) },
            text = {
                Text(
                    if (summary.expenseCount > 0) {
                        "\"${summary.category.name}\" has ${summary.expenseCount} assigned ${if (summary.expenseCount == 1) "expense" else "expenses"}. Deleting it will keep those expenses as Uncategorized."
                    } else {
                        "Delete \"${summary.category.name}\" from your custom categories?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategoryConfirm(summary.category)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_category"),
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

    if (summaries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            HigContentUnavailable(
                icon = Icons.Rounded.Category,
                title = stringResource(R.string.categories_empty_title),
                subtitle = stringResource(R.string.categories_empty_subtitle),
                actionLabel = "New Category",
                onActionClick = onAddCategoryClick,
                actionTestTag = "empty_add_category_button",
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("categories_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "categories_inset_group") {
            Column {
                HigSectionHeader(
                    title = "CUSTOM CATEGORIES",
                    trailing = "${summaries.size}",
                )
                HigInsetGroup {
                    summaries.forEachIndexed { index, summary ->
                        HigCategoryRow(
                            summary = summary,
                            onEditClick = { onEditCategoryClick(summary.category) },
                            onDeleteClick = { pendingDelete = summary },
                        )
                        if (index < summaries.lastIndex) {
                            HigInsetDivider(startIndent = 68.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HigCategoryRow(
    summary: CategorySummary,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val currencyConfig = LocalCurrencyConfig.current
    val category = summary.category

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onEditClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
            .testTag("category_row_${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(
            name = category.name,
            colorHex = category.colorHex,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                category.colorHex?.let { hex ->
                    Text(
                        text = hex,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val meta = buildString {
                append("${summary.expenseCount} ${if (summary.expenseCount == 1) "entry" else "entries"}")
                if (summary.totalCents > 0L) {
                    append(" · ${CurrencyUtils.formatCents(summary.totalCents, currencyConfig)}")
                }
                category.monthlyBudgetCents?.takeIf { it > 0L }?.let { cap ->
                    append(" · Limit ${CurrencyUtils.formatCents(cap, currencyConfig)}/mo")
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onEditClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("edit_category_${category.id}"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit ${category.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("delete_category_${category.id}"),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${category.name}",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    editingCategory: CategoryEntity?,
    externalError: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(editingCategory) { mutableStateOf(editingCategory?.name.orEmpty()) }
    var hexInput by remember(editingCategory) { mutableStateOf(editingCategory?.colorHex.orEmpty()) }
    var localError by remember(editingCategory, externalError) { mutableStateOf(externalError) }

    val resolvedColor = parseHexColorOrNull(hexInput)
    val isHexValidOrEmpty = hexInput.isBlank() || resolvedColor != null
    val canSave = name.trim().isNotEmpty() && isHexValidOrEmpty

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
                    if (editingCategory == null) R.string.categories_add_title else R.string.categories_edit_title,
                ),
                onCancel = onDismiss,
                onPrimaryAction = {
                    val trimmed = name.trim()
                    when {
                        trimmed.isEmpty() -> localError = "Category name is required"
                        !isHexValidOrEmpty -> localError = "Enter a valid #RRGGBB color code or leave blank"
                        else -> onSave(trimmed, hexInput.trim().ifEmpty { null })
                    }
                },
                primaryEnabled = canSave,
                primaryTestTag = "save_category_button",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Live Preview + Name Field
                HigInsetGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryBadge(
                            name = name.ifBlank { "?" },
                            colorHex = if (isHexValidOrEmpty) hexInput.ifBlank { null } else null,
                            size = 44.dp,
                        )
                        TextField(
                            value = name,
                            onValueChange = {
                                name = it
                                localError = null
                            },
                            placeholder = { Text(stringResource(R.string.categories_name_label)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("category_name_input"),
                        )
                    }
                }

                // Optional Color Swatches + Hex Code Input
                Column {
                    HigSectionHeader(
                        title = stringResource(R.string.categories_color_label),
                        trailing = if (hexInput.isBlank()) stringResource(R.string.categories_no_color) else hexInput.uppercase(),
                    )
                    HigInsetGroup {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // "No Color" optional swatch
                                val isNoneSelected = hexInput.isBlank()
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = if (isNoneSelected) 2.5.dp else 1.dp,
                                            color = if (isNoneSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape,
                                        )
                                        .clickable(role = Role.RadioButton) {
                                            hexInput = ""
                                            localError = null
                                        }
                                        .testTag("color_swatch_none"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = "No color",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                CategoryColorPresets.forEach { presetHex ->
                                    val presetColor = parseHexColorOrNull(presetHex) ?: Color.Gray
                                    val selected = hexInput.trim().equals(presetHex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(presetColor)
                                            .clickable(role = Role.RadioButton) {
                                                hexInput = presetHex
                                                localError = null
                                            }
                                            .testTag("color_swatch_${presetHex.removePrefix("#")}"),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected color $presetHex",
                                                tint = contrastingForeground(presetColor),
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            HigInsetDivider(startIndent = 0.dp)

                            TextField(
                                value = hexInput,
                                onValueChange = { raw ->
                                    if (raw.length <= 7) {
                                        hexInput = raw.uppercase()
                                        localError = null
                                    }
                                },
                                placeholder = { Text("Hex code (optional, e.g. #007AFF)") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = MonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_hex_input"),
                            )
                        }
                    }
                }

                val displayError = localError ?: if (!isHexValidOrEmpty) "Use #RRGGBB format (e.g. #007AFF)" else null
                if (displayError != null) {
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
