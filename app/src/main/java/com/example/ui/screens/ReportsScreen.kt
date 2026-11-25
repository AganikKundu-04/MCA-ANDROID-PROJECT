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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.preferences.LocalCurrencyConfig
import com.example.ui.components.CategoryColorDot
import com.example.ui.components.CategoryPieChart
import com.example.ui.components.HigContentUnavailable
import com.example.ui.components.HigFilterPill
import com.example.ui.components.HigInsetDivider
import com.example.ui.components.HigInsetGroup
import com.example.ui.components.HigSectionHeader
import com.example.ui.components.HigSegmentedControl
import com.example.ui.components.SpendingBarChart
import com.example.ui.components.resolveSliceColor
import com.example.ui.state.DateRangePreset
import com.example.ui.state.ReportFilterState
import com.example.ui.state.ReportSummary
import com.example.ui.state.TimeGranularity
import com.example.ui.state.UNCATEGORIZED_FILTER_ID
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.parseHexColorOrNull
import com.example.util.CurrencyUtils
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    filter: ReportFilterState,
    summary: ReportSummary,
    categories: List<CategoryEntity>,
    onSelectDatePreset: (DateRangePreset) -> Unit,
    onCustomDateRangeSelected: (Long, Long) -> Unit,
    onToggleCategoryFilter: (Long) -> Unit,
    onClearCategoryFilters: () -> Unit,
    onSelectGranularity: (TimeGranularity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var selectedSliceIndex by remember(summary.categorySlices) { mutableStateOf<Int?>(null) }

    if (showCustomRangeDialog) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = DateUtils.epochDayToUtcMillis(
                filter.customStartEpochDay ?: summary.effectiveStartEpochDay,
            ),
            initialSelectedEndDateMillis = DateUtils.epochDayToUtcMillis(
                filter.customEndEpochDay ?: summary.effectiveEndEpochDay,
            ),
        )
        DatePickerDialog(
            onDismissRequest = { showCustomRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = pickerState.selectedStartDateMillis
                        val endMillis = pickerState.selectedEndDateMillis ?: startMillis
                        if (startMillis != null && endMillis != null) {
                            onCustomDateRangeSelected(
                                DateUtils.utcMillisToEpochDay(startMillis),
                                DateUtils.utcMillisToEpochDay(endMillis),
                            )
                        }
                        showCustomRangeDialog = false
                    },
                    modifier = Modifier.testTag("custom_range_confirm"),
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.height(460.dp),
            )
        }
    }

    val currencyConfig = LocalCurrencyConfig.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 1. Date Range & Category Filter Controls
        item(key = "report_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HigSectionHeader(
                    title = "PERIOD",
                    trailing = DateUtils.formatDateRange(
                        summary.effectiveStartEpochDay,
                        summary.effectiveEndEpochDay,
                    ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateRangePreset.entries.forEach { preset ->
                        val selected = filter.preset == preset
                        HigFilterPill(
                            label = preset.label,
                            selected = selected,
                            onClick = {
                                if (preset == DateRangePreset.CUSTOM) {
                                    showCustomRangeDialog = true
                                } else {
                                    onSelectDatePreset(preset)
                                }
                            },
                            modifier = Modifier.testTag("report_preset_${preset.name.lowercase()}"),
                        )
                    }
                }

                // Multi-select Category Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HigFilterPill(
                        label = stringResource(R.string.expenses_all_categories),
                        selected = filter.selectedCategoryIds.isEmpty(),
                        onClick = onClearCategoryFilters,
                        modifier = Modifier.testTag("report_cat_all"),
                    )
                    categories.forEach { category ->
                        val isSelected = category.id in filter.selectedCategoryIds
                        HigFilterPill(
                            label = category.name,
                            selected = isSelected,
                            onClick = { onToggleCategoryFilter(category.id) },
                            accentColor = parseHexColorOrNull(category.colorHex),
                            leadingDotHex = category.colorHex,
                            showCheckWhenSelected = true,
                            modifier = Modifier.testTag("report_cat_${category.id}"),
                        )
                    }
                    HigFilterPill(
                        label = stringResource(R.string.expenses_uncategorized),
                        selected = UNCATEGORIZED_FILTER_ID in filter.selectedCategoryIds,
                        onClick = { onToggleCategoryFilter(UNCATEGORIZED_FILTER_ID) },
                        showCheckWhenSelected = true,
                        modifier = Modifier.testTag("report_cat_uncategorized"),
                    )
                }
            }
        }

        if (summary.transactionCount == 0) {
            item(key = "reports_empty") {
                HigContentUnavailable(
                    icon = Icons.Rounded.PieChart,
                    title = stringResource(R.string.reports_empty_title),
                    subtitle = stringResource(R.string.reports_empty_subtitle),
                    actionLabel = if (filter.selectedCategoryIds.isNotEmpty() || filter.preset != DateRangePreset.ALL_TIME) {
                        "Show All Time"
                    } else {
                        null
                    },
                    onActionClick = {
                        onClearCategoryFilters()
                        onSelectDatePreset(DateRangePreset.ALL_TIME)
                    },
                    actionTestTag = "reports_reset_filters",
                )
            }
        } else {
            // 2. Inset Grouped 2x2 KPI Overview
            item(key = "kpi_grid") {
                HigInsetGroup {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ReportMetricCell(
                            label = stringResource(R.string.reports_total_spent),
                            value = CurrencyUtils.formatCents(summary.totalCents, currencyConfig),
                            isMono = true,
                            modifier = Modifier.weight(1f),
                        )
                        ReportMetricCell(
                            label = stringResource(R.string.reports_daily_avg),
                            value = CurrencyUtils.formatCents(summary.dailyAverageCents, currencyConfig),
                            isMono = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HigInsetDivider(startIndent = 16.dp, endIndent = 16.dp)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ReportMetricCell(
                            label = stringResource(R.string.reports_transactions),
                            value = summary.transactionCount.toString(),
                            isMono = true,
                            modifier = Modifier.weight(1f),
                        )
                        ReportMetricCell(
                            label = stringResource(R.string.reports_top_category),
                            value = summary.topCategoryName,
                            isMono = false,
                            dotColorHex = summary.topCategoryColorHex,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 3. Spending Distribution Pie Chart & Breakdown
            item(key = "pie_chart_section") {
                Column {
                    HigSectionHeader(
                        title = stringResource(R.string.reports_distribution_title),
                        trailing = "${summary.categorySlices.size} ${if (summary.categorySlices.size == 1) "category" else "categories"}",
                    )
                    HigInsetGroup {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        ) {
                            CategoryPieChart(
                                slices = summary.categorySlices,
                                totalCents = summary.totalCents,
                                selectedIndex = selectedSliceIndex,
                                onSliceSelected = { selectedSliceIndex = it },
                            )
                        }

                        HigInsetDivider(startIndent = 16.dp)

                        summary.categorySlices.forEachIndexed { index, slice ->
                            val isSelected = selectedSliceIndex == index
                            val sliceColor = resolveSliceColor(slice.colorHex, index)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    )
                                    .clickable(role = Role.Button) {
                                        selectedSliceIndex = if (isSelected) null else index
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(sliceColor),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = slice.label,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${slice.transactionCount} ${if (slice.transactionCount == 1) "entry" else "entries"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CurrencyUtils.formatCents(slice.amountCents, currencyConfig),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${(slice.percentage * 100f).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (index < summary.categorySlices.lastIndex) {
                                HigInsetDivider(startIndent = 40.dp)
                            }
                        }
                    }
                }
            }

            // 4. Spending Over Time Bar Chart with Apple HIG Segmented Granularity Control
            item(key = "bar_chart_section") {
                Column {
                    HigSectionHeader(
                        title = stringResource(R.string.reports_over_time_title),
                        trailing = summary.effectiveGranularity.label,
                    )
                    HigInsetGroup {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            HigSegmentedControl(
                                options = TimeGranularity.entries,
                                selected = filter.granularity,
                                label = { it.label },
                                onSelect = onSelectGranularity,
                                testTagPrefix = "granularity",
                            )
                            SpendingBarChart(buckets = summary.timeBuckets)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricCell(
    label: String,
    value: String,
    isMono: Boolean,
    modifier: Modifier = Modifier,
    dotColorHex: String? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dotColorHex != null) {
                CategoryColorDot(colorHex = dotColorHex, size = 9.dp)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = if (isMono) MonoFontFamily else MaterialTheme.typography.titleLarge.fontFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
