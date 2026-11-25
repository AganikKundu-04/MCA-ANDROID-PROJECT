package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.ui.theme.contrastingForeground
import com.example.ui.theme.parseHexColorOrNull
import com.example.util.DateUtils

@Composable
fun CategoryColorDot(
    colorHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val parsed = parseHexColorOrNull(colorHex)
    if (parsed != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(parsed),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

/**
 * Concentric squircle category badge (10dp corner radius nested cleanly inside 16dp inset groups).
 */
@Composable
fun CategoryBadge(
    name: String?,
    colorHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val parsed = parseHexColorOrNull(colorHex)
    val bgColor = parsed ?: MaterialTheme.colorScheme.surfaceVariant
    val fgColor = if (parsed != null) contrastingForeground(parsed) else MaterialTheme.colorScheme.onSurfaceVariant
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = fgColor,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Label,
                contentDescription = "Uncategorized",
                tint = fgColor,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}

@Composable
fun HigFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    leadingDotHex: String? = null,
    showCheckWhenSelected: Boolean = false,
) {
    val resolvedAccent = accentColor ?: MaterialTheme.colorScheme.primary
    val containerColor = if (selected) {
        resolvedAccent
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        if (accentColor != null) contrastingForeground(accentColor) else MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = if (selected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected && showCheckWhenSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp),
                )
            } else if (leadingDotHex != null && !selected) {
                CategoryColorDot(colorHex = leadingDotHex, size = 8.dp)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun CategorySelectorRow(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    onAddCategoryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HigFilterPill(
            label = "Uncategorized",
            selected = selectedCategoryId == null,
            onClick = { onSelectCategory(null) },
            showCheckWhenSelected = true,
            modifier = Modifier.testTag("category_select_none"),
        )

        categories.forEach { category ->
            val isSelected = selectedCategoryId == category.id
            HigFilterPill(
                label = category.name,
                selected = isSelected,
                onClick = { onSelectCategory(category.id) },
                accentColor = parseHexColorOrNull(category.colorHex),
                leadingDotHex = category.colorHex,
                showCheckWhenSelected = true,
                modifier = Modifier.testTag("category_select_${category.id}"),
            )
        }

        if (onAddCategoryClick != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAddCategoryClick)
                    .testTag("quick_add_category_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New category",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpochDayDatePickerDialog(
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DateUtils.epochDayToUtcMillis(initialEpochDay),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onDateSelected(DateUtils.utcMillisToEpochDay(millis))
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("date_picker_confirm"),
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/**
 * Deterministic fallback color when a category has no optional colorHex set.
 */
fun resolveSliceColor(colorHex: String?, index: Int): Color {
    parseHexColorOrNull(colorHex)?.let { return it }
    val fallbacks = listOf(
        Color(0xFF007AFF),
        Color(0xFF5856D6),
        Color(0xFF34C759),
        Color(0xFFFF9500),
        Color(0xFF0D9488),
        Color(0xFFAF52DE),
    )
    return fallbacks[index % fallbacks.size]
}
