package com.example.ui.expense;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.R;
import com.example.SpendingApp;
import com.example.data.local.CategoryEntity;
import com.example.data.local.ExpenseEntity;
import com.example.data.repository.SpendingRepository;
import com.example.databinding.BottomSheetAddExpenseBinding;
import com.example.domain.CategoryClassifier;
import com.example.domain.DateGroupingUtil;
import com.example.domain.MoneyFormatter;
import com.example.domain.PaymentMode;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheetDialogFragment for creating or editing a spending transaction,
 * with live deterministic CategoryClassifier suggestions as the merchant is typed.
 */
public class AddExpenseBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_EXPENSE_ID = "arg_expense_id";

    private BottomSheetAddExpenseBinding binding;
    private SpendingRepository repository;

    private long editingExpenseId = 0L;
    private long selectedTimestampMillis = System.currentTimeMillis();
    private long existingCreatedAtMillis = 0L;
    @Nullable
    private Long selectedCategoryId = null;
    private boolean userManuallyPickedCategory = false;
    private boolean bindingEditData = false;

    @NonNull
    private List<CategoryEntity> loadedCategories = new ArrayList<>();

    @NonNull
    public static AddExpenseBottomSheet newForAdd() {
        return new AddExpenseBottomSheet();
    }

    @NonNull
    public static AddExpenseBottomSheet newForEdit(long expenseId) {
        AddExpenseBottomSheet sheet = new AddExpenseBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_EXPENSE_ID, expenseId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = BottomSheetAddExpenseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SpendingApp app = (SpendingApp) requireActivity().getApplication();
        repository = app.getAppContainer().spendingRepository;

        Bundle args = getArguments();
        if (args != null) {
            editingExpenseId = args.getLong(ARG_EXPENSE_ID, 0L);
        }

        if (editingExpenseId > 0L) {
            binding.tvSheetTitle.setText(R.string.sheet_edit_expense_title);
        } else {
            binding.tvSheetTitle.setText(R.string.sheet_add_expense_title);
        }

        updateDateButtonLabel();
        binding.btnPickDate.setOnClickListener(v -> showDatePicker());
        binding.btnCancelExpense.setOnClickListener(v -> dismiss());
        binding.btnSaveExpense.setOnClickListener(v -> attemptSave());

        binding.etMerchant.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (bindingEditData || userManuallyPickedCategory) {
                    return;
                }
                String merchantText = s != null ? s.toString() : "";
                String predicted = CategoryClassifier.classify(merchantText, null);
                selectCategoryChipByName(predicted);
            }
        });

        loadCategoriesAndExisting();
    }

    private void loadCategoriesAndExisting() {
        repository.getAllCategoriesAsync().thenAccept(categories -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                loadedCategories = categories != null ? categories : new ArrayList<>();
                renderCategoryChips();
                if (editingExpenseId > 0L) {
                    loadExistingExpense(editingExpenseId);
                } else {
                    selectCategoryChipByName(CategoryClassifier.CATEGORY_OTHER);
                }
            });
        });
    }

    private void loadExistingExpense(long expenseId) {
        repository.findExpenseById(expenseId).thenAccept(existing -> {
            if (!isAdded() || existing == null) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                bindingEditData = true;
                userManuallyPickedCategory = true;
                existingCreatedAtMillis = existing.createdAt;
                selectedTimestampMillis = existing.timestamp;
                updateDateButtonLabel();

                binding.etAmount.setText(MoneyFormatter.formatPaiseForInput(existing.amountPaise));
                binding.etMerchant.setText(existing.merchantTitle);
                if (existing.utrReference != null) {
                    binding.etUtr.setText(existing.utrReference);
                }
                if (existing.notes != null) {
                    binding.etNotes.setText(existing.notes);
                }

                PaymentMode mode = PaymentMode.fromStorageValue(existing.paymentMode);
                if (mode == PaymentMode.CARD) {
                    binding.chipModeCard.setChecked(true);
                } else if (mode == PaymentMode.CASH) {
                    binding.chipModeCash.setChecked(true);
                } else {
                    binding.chipModeUpi.setChecked(true);
                }

                selectCategoryChipById(existing.categoryId);
                bindingEditData = false;
            });
        });
    }

    private void renderCategoryChips() {
        binding.chipGroupSheetCategories.removeAllViews();
        for (CategoryEntity cat : loadedCategories) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setTag(cat.id);
            chip.setText(cat.name);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> {
                userManuallyPickedCategory = true;
                selectedCategoryId = cat.id;
            });
            binding.chipGroupSheetCategories.addView(chip);
        }
    }

    private void selectCategoryChipByName(@NonNull String targetName) {
        for (int i = 0; i < binding.chipGroupSheetCategories.getChildCount(); i++) {
            View child = binding.chipGroupSheetCategories.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (targetName.equalsIgnoreCase(chip.getText().toString())) {
                    chip.setChecked(true);
                    Object tag = chip.getTag();
                    if (tag instanceof Long) {
                        selectedCategoryId = (Long) tag;
                    }
                    return;
                }
            }
        }
    }

    private void selectCategoryChipById(@Nullable Long categoryId) {
        if (categoryId == null) {
            selectCategoryChipByName(CategoryClassifier.CATEGORY_OTHER);
            return;
        }
        for (int i = 0; i < binding.chipGroupSheetCategories.getChildCount(); i++) {
            View child = binding.chipGroupSheetCategories.getChildAt(i);
            if (child instanceof Chip && categoryId.equals(child.getTag())) {
                ((Chip) child).setChecked(true);
                selectedCategoryId = categoryId;
                return;
            }
        }
        selectCategoryChipByName(CategoryClassifier.CATEGORY_OTHER);
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.field_date_label)
                .setSelection(selectedTimestampMillis)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null) {
                selectedTimestampMillis = selection;
                updateDateButtonLabel();
            }
        });
        picker.show(getChildFragmentManager(), "expense_date_picker");
    }

    private void updateDateButtonLabel() {
        binding.btnPickDate.setText(DateGroupingUtil.formatDateShort(selectedTimestampMillis));
    }

    private void attemptSave() {
        binding.tilAmount.setError(null);
        binding.tilMerchant.setError(null);

        String rawAmount = binding.etAmount.getText() != null ? binding.etAmount.getText().toString() : "";
        long amountPaise = MoneyFormatter.parseRupeesToPaise(rawAmount);
        if (amountPaise <= 0L) {
            binding.tilAmount.setError(getString(R.string.error_invalid_amount));
            return;
        }

        String merchant = binding.etMerchant.getText() != null
                ? binding.etMerchant.getText().toString().trim()
                : "";
        if (merchant.isEmpty()) {
            binding.tilMerchant.setError(getString(R.string.error_empty_merchant));
            return;
        }

        String utr = binding.etUtr.getText() != null ? binding.etUtr.getText().toString().trim() : "";
        String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

        PaymentMode mode = resolveSelectedPaymentMode();
        long createdAt = existingCreatedAtMillis > 0L ? existingCreatedAtMillis : System.currentTimeMillis();

        ExpenseEntity entity = new ExpenseEntity(
                editingExpenseId,
                amountPaise,
                merchant,
                selectedCategoryId,
                mode.name(),
                selectedTimestampMillis,
                notes.isEmpty() ? null : notes,
                utr.isEmpty() ? null : utr,
                createdAt
        );

        binding.btnSaveExpense.setEnabled(false);
        repository.saveExpense(entity).thenRun(() -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(this::dismissAllowingStateLoss);
            }
        });
    }

    @NonNull
    private PaymentMode resolveSelectedPaymentMode() {
        int checkedId = binding.chipGroupPaymentMode.getCheckedChipId();
        if (checkedId == R.id.chipModeCard) {
            return PaymentMode.CARD;
        }
        if (checkedId == R.id.chipModeCash) {
            return PaymentMode.CASH;
        }
        return PaymentMode.UPI;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
