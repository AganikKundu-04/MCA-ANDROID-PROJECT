package com.example.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.R;
import com.example.SpendingApp;
import com.example.data.local.BudgetEntity;
import com.example.data.local.CategoryEntity;
import com.example.data.local.CategorySpendTuple;
import com.example.databinding.DialogSetBudgetBinding;
import com.example.databinding.FragmentSettingsBinding;
import com.example.databinding.ItemCategoryBudgetBinding;
import com.example.domain.BudgetStatus;
import com.example.domain.DateGroupingUtil;
import com.example.domain.MoneyFormatter;
import com.example.ml.MirageStatus;
import com.example.ui.MainActivity;
import com.example.ui.common.ColorUtil;
import com.example.ui.common.ThemePreferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Budget configuration, per-category monthly limits, SAF CSV export, and On-Device Mirage status card.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    @Nullable
    private BudgetEntity latestOverallBudget;
    @NonNull
    private List<CategoryEntity> latestCategories = new ArrayList<>();
    @NonNull
    private Map<Long, Long> categoryBudgetMap = new HashMap<>();
    @NonNull
    private Map<Long, Long> categorySpendMap = new HashMap<>();

    private final ActivityResultLauncher<String> createCsvDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), this::writeCsvToUri);

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SpendingApp app = (SpendingApp) requireActivity().getApplication();
        viewModel = new ViewModelProvider(
                this,
                new SettingsViewModel.Factory(
                        app.getAppContainer().spendingRepository,
                        app.getAppContainer().mirageRepository
                )
        ).get(SettingsViewModel.class);

        binding.btnEditOverallBudget.setOnClickListener(v -> {
            long currentLimit = latestOverallBudget != null ? latestOverallBudget.limitPaise : 0L;
            showBudgetEditorDialog(
                    getString(R.string.settings_overall_budget_title),
                    currentLimit,
                    newLimitPaise -> viewModel.saveOverallBudget(newLimitPaise)
            );
        });

        binding.btnExportCsv.setOnClickListener(v -> {
            String fileName = "ledgerflow_expenses_" + DateGroupingUtil.toMonthKey(System.currentTimeMillis()) + ".csv";
            createCsvDocumentLauncher.launch(fileName);
        });

        binding.btnSettingsScanReceipt.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).launchGalleryReceiptPicker();
            }
        });

        setupAppearanceToggle();
        renderMirageStatus();
        observeViewModel();
    }

    private void setupAppearanceToggle() {
        int savedMode = ThemePreferences.getSavedMode(requireContext());
        if (savedMode == ThemePreferences.MODE_LIGHT) {
            binding.toggleThemeMode.check(R.id.btnThemeLight);
        } else if (savedMode == ThemePreferences.MODE_DARK) {
            binding.toggleThemeMode.check(R.id.btnThemeDark);
        } else {
            binding.toggleThemeMode.check(R.id.btnThemeSystem);
        }

        binding.toggleThemeMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int selectedMode = ThemePreferences.MODE_SYSTEM;
            if (checkedId == R.id.btnThemeLight) {
                selectedMode = ThemePreferences.MODE_LIGHT;
            } else if (checkedId == R.id.btnThemeDark) {
                selectedMode = ThemePreferences.MODE_DARK;
            }
            if (ThemePreferences.getSavedMode(requireContext()) != selectedMode) {
                group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                ThemePreferences.saveAndApplyMode(requireContext(), selectedMode);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && viewModel != null) {
            renderMirageStatus();
        }
    }

    private void observeViewModel() {
        viewModel.getOverallBudget().observe(getViewLifecycleOwner(), budget -> {
            latestOverallBudget = budget;
            if (budget != null && budget.limitPaise > 0L) {
                binding.tvOverallBudgetValue.setText(MoneyFormatter.formatInr(budget.limitPaise));
                binding.btnEditOverallBudget.setText(R.string.settings_edit_budget);
            } else {
                binding.tvOverallBudgetValue.setText(R.string.settings_overall_budget_unset);
                binding.btnEditOverallBudget.setText(R.string.settings_set_budget);
            }
        });

        viewModel.getCategories().observe(getViewLifecycleOwner(), cats -> {
            latestCategories = cats != null ? cats : new ArrayList<>();
            renderCategoryBudgets();
        });

        viewModel.getAllBudgets().observe(getViewLifecycleOwner(), budgets -> {
            categoryBudgetMap.clear();
            if (budgets != null) {
                for (BudgetEntity b : budgets) {
                    if (b.categoryId != null && b.limitPaise > 0L) {
                        categoryBudgetMap.put(b.categoryId, b.limitPaise);
                    }
                }
            }
            renderCategoryBudgets();
        });

        viewModel.getCurrentMonthBreakdown().observe(getViewLifecycleOwner(), tuples -> {
            categorySpendMap.clear();
            if (tuples != null) {
                for (CategorySpendTuple t : tuples) {
                    if (t.categoryId != null) {
                        categorySpendMap.put(t.categoryId, t.totalPaise);
                    }
                }
            }
            renderCategoryBudgets();
        });
    }

    private void renderCategoryBudgets() {
        binding.containerCategoryBudgets.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int i = 0; i < latestCategories.size(); i++) {
            CategoryEntity category = latestCategories.get(i);
            ItemCategoryBudgetBinding row =
                    ItemCategoryBudgetBinding.inflate(inflater, binding.containerCategoryBudgets, false);

            int dotColor = ColorUtil.parseHexOrDefault(category.colorHex);
            row.viewCatBudgetDot.setBackground(ColorUtil.createCircleDot(dotColor));
            row.tvCatBudgetName.setText(category.name);

            long limitPaise = categoryBudgetMap.getOrDefault(category.id, 0L);
            long spentPaise = categorySpendMap.getOrDefault(category.id, 0L);

            if (limitPaise > 0L) {
                BudgetStatus status = BudgetStatus.evaluate(spentPaise, limitPaise);
                int pct = BudgetStatus.calculatePercent(spentPaise, limitPaise);
                int accent = ColorUtil.budgetAccentColor(requireContext(), status);

                row.tvCatBudgetAmount.setText(
                        MoneyFormatter.formatInr(spentPaise) + " / " + MoneyFormatter.formatInr(limitPaise));
                row.tvCatBudgetAmount.setTextColor(accent);
                row.progressCatBudget.setVisibility(View.VISIBLE);
                row.progressCatBudget.setIndicatorColor(accent);
                row.progressCatBudget.setProgressCompat(Math.min(pct, 100), true);
            } else {
                row.tvCatBudgetAmount.setText("Set limit");
                row.tvCatBudgetAmount.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.lf_on_surface_variant));
                row.progressCatBudget.setVisibility(View.GONE);
            }

            row.getRoot().setOnClickListener(v -> showBudgetEditorDialog(
                    category.name + " Monthly Limit",
                    limitPaise,
                    newLimitPaise -> viewModel.saveCategoryBudget(category.id, newLimitPaise)
            ));

            binding.containerCategoryBudgets.addView(row.getRoot());
            if (i < latestCategories.size() - 1) {
                View divider = new View(requireContext());
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Math.round(getResources().getDisplayMetrics().density * 0.5f))
                );
                lp.setMarginStart(Math.round(getResources().getDisplayMetrics().density * 20f));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lf_separator));
                binding.containerCategoryBudgets.addView(divider);
            }
        }
    }

    private void renderMirageStatus() {
        MirageStatus status = viewModel.getMirageStatus();
        binding.tvMirageEngineLabel.setText(status.engineLabel);
        binding.tvMirageDetail.setText(status.detailMessage);

        if (status.isNativeReady()) {
            binding.tvMirageBadge.setText(status.modelLoadedInMemory ? "LOADED" : "READY");
            binding.tvMirageBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.lf_budget_healthy));
            binding.tvMirageBadge.setBackground(ColorUtil.createPillBadge(
                    requireContext(), ContextCompat.getColor(requireContext(), R.color.lf_budget_healthy_container)));
        } else {
            binding.tvMirageBadge.setText("FALLBACK");
            binding.tvMirageBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.lf_budget_warning));
            binding.tvMirageBadge.setBackground(ColorUtil.createPillBadge(
                    requireContext(), ContextCompat.getColor(requireContext(), R.color.lf_budget_warning_container)));
        }

        String checklist = (status.modelAssetPresent ? "[OK] " : "[MISSING] ") + "assets/mirage.cact\n"
                + (status.nativeLibraryPresent ? "[OK] " : "[MISSING] ") + "jniLibs/arm64-v8a/libcactus.so";
        binding.tvMirageChecklist.setText(checklist);
    }

    private interface BudgetSaveCallback {
        void onSave(long limitPaise);
    }

    private void showBudgetEditorDialog(
            @NonNull String title,
            long currentLimitPaise,
            @NonNull BudgetSaveCallback callback
    ) {
        DialogSetBudgetBinding dialogBinding = DialogSetBudgetBinding.inflate(LayoutInflater.from(requireContext()));
        if (currentLimitPaise > 0L) {
            dialogBinding.etBudgetAmount.setText(MoneyFormatter.formatPaiseForInput(currentLimitPaise));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.action_clear_limit, (d, which) -> callback.onSave(0L))
                .setPositiveButton(R.string.action_save, (d, which) -> {
                    String raw = dialogBinding.etBudgetAmount.getText() != null
                            ? dialogBinding.etBudgetAmount.getText().toString().trim()
                            : "";
                    if (raw.isEmpty()) {
                        callback.onSave(0L);
                        return;
                    }
                    long parsedPaise = MoneyFormatter.parseRupeesToPaise(raw);
                    if (parsedPaise <= 0L) {
                        Snackbar.make(binding.settingsRoot, R.string.error_invalid_amount, Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    callback.onSave(parsedPaise);
                })
                .show();
    }

    private void writeCsvToUri(@Nullable Uri destinationUri) {
        if (destinationUri == null || binding == null) {
            return;
        }
        SpendingApp app = (SpendingApp) requireActivity().getApplication();
        viewModel.buildCsvExportAsync().thenAcceptAsync(payload -> {
            if (payload.rowCount == 0) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        Snackbar.make(binding.settingsRoot, R.string.export_empty, Snackbar.LENGTH_SHORT).show();
                    }
                });
                return;
            }
            try (OutputStream os = requireContext().getContentResolver().openOutputStream(destinationUri)) {
                if (os == null) {
                    throw new IOException("Unable to open SAF output stream");
                }
                os.write(payload.csvContent.getBytes(StandardCharsets.UTF_8));
                os.flush();
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        Snackbar.make(
                                binding.settingsRoot,
                                getString(R.string.export_success, payload.rowCount),
                                Snackbar.LENGTH_LONG
                        ).show();
                    }
                });
            } catch (IOException ex) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        Snackbar.make(binding.settingsRoot, R.string.export_failed, Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        }, app.getAppContainer().ioExecutor);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
