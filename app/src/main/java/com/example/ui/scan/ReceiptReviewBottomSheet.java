package com.example.ui.scan;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.SpendingApp;
import com.example.data.local.CategoryEntity;
import com.example.data.local.ExpenseEntity;
import com.example.data.repository.SpendingRepository;
import com.example.databinding.BottomSheetReceiptReviewBinding;
import com.example.domain.CategoryClassifier;
import com.example.domain.MoneyFormatter;
import com.example.domain.PaymentMode;
import com.example.ml.MirageRepository;
import com.example.ml.ParsedReceipt;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Instant confirmation bottom sheet for Share-to-Scan payment screenshots.
 * Displays a loading state while Mirage runs, renders the cropped preview, editable fields,
 * prominent warning chips (FAILED/PENDING, CREDIT, Duplicate UTR, Partial parse), and one-tap Save.
 */
public class ReceiptReviewBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TEMP_IMAGE_PATH = "arg_temp_image_path";

    private BottomSheetReceiptReviewBinding binding;
    private SpendingRepository spendingRepository;
    private MirageRepository mirageRepository;

    @Nullable
    private File tempImageFile;
    @Nullable
    private Bitmap activePreviewBitmap;
    @Nullable
    private Long selectedCategoryId;
    private long parsedTimestampMillis = System.currentTimeMillis();
    @NonNull
    private List<CategoryEntity> categories = new ArrayList<>();

    @NonNull
    public static ReceiptReviewBottomSheet newInstance(@NonNull String tempImagePath) {
        ReceiptReviewBottomSheet sheet = new ReceiptReviewBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TEMP_IMAGE_PATH, tempImagePath);
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
        binding = BottomSheetReceiptReviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SpendingApp app = (SpendingApp) requireActivity().getApplication();
        spendingRepository = app.getAppContainer().spendingRepository;
        mirageRepository = app.getAppContainer().mirageRepository;

        Bundle args = getArguments();
        String path = args != null ? args.getString(ARG_TEMP_IMAGE_PATH) : null;
        tempImageFile = (path != null && !path.isEmpty()) ? new File(path) : null;

        binding.btnDiscardScan.setOnClickListener(v -> dismiss());
        binding.btnConfirmSaveScan.setOnClickListener(v -> saveReviewedExpense());

        startOfflineScanPipeline();
    }

    private void startOfflineScanPipeline() {
        if (tempImageFile == null || !tempImageFile.exists()) {
            showScanFailureFallback();
            return;
        }

        spendingRepository.getAllCategoriesAsync().thenAccept(loadedCats -> {
            categories = loadedCats != null ? loadedCats : new ArrayList<>();
            mirageRepository.scanReceiptFile(tempImageFile).whenComplete((scanResult, error) -> {
                if (!isAdded()) return;
                if (error != null || scanResult == null) {
                    requireActivity().runOnUiThread(this::showScanFailureFallback);
                    return;
                }

                ParsedReceipt receipt = scanResult.receipt;
                spendingRepository.findDuplicateByUtr(receipt.utrReference).whenComplete((dupEntity, dupErr) -> {
                    if (!isAdded()) return;
                    boolean isDuplicate = (dupErr == null && dupEntity != null);
                    requireActivity().runOnUiThread(() -> bindScanResult(scanResult, isDuplicate));
                });
            });
        });
    }

    private void bindScanResult(@NonNull MirageRepository.ScanResult result, boolean isDuplicateUtr) {
        if (binding == null) return;

        binding.layoutScanningState.setVisibility(View.GONE);
        binding.layoutReviewForm.setVisibility(View.VISIBLE);

        activePreviewBitmap = result.previewThumbnail;
        if (activePreviewBitmap != null && !activePreviewBitmap.isRecycled()) {
            binding.ivReceiptPreview.setImageBitmap(activePreviewBitmap);
        }

        ParsedReceipt receipt = result.receipt;
        parsedTimestampMillis = receipt.timestampMillis;

        String metaTitle = result.usedSimulatedFallback
                ? "Offline Pipeline Ready (Preview OCR)"
                : "Extracted via On-Device Mirage";
        StringBuilder metaDetail = new StringBuilder();
        metaDetail.append(result.inferenceDurationMs).append(" ms");
        if (result.smartCropped) {
            metaDetail.append(" · ").append(getString(R.string.review_crop_badge));
        }
        binding.tvScanMetaTitle.setText(metaTitle);
        binding.tvScanMetaDetail.setText(metaDetail.toString());

        // Populate editable fields
        if (receipt.hasAmount()) {
            binding.etReviewAmount.setText(MoneyFormatter.formatPaiseForInput(receipt.amountPaise));
        }
        if (!receipt.merchant.isEmpty()) {
            binding.etReviewMerchant.setText(receipt.merchant);
        }
        if (receipt.utrReference != null) {
            binding.etReviewUtr.setText(receipt.utrReference);
        }

        // Payment mode
        if (receipt.paymentMode == PaymentMode.CARD) {
            binding.chipReviewCard.setChecked(true);
        } else if (receipt.paymentMode == PaymentMode.CASH) {
            binding.chipReviewCash.setChecked(true);
        } else {
            binding.chipReviewUpi.setChecked(true);
        }

        // Category chips + auto-classification
        renderCategoryChips();
        String predictedCategory = CategoryClassifier.classify(receipt.merchant, null);
        selectCategoryByName(predictedCategory);

        // Warning chips
        renderWarningChips(receipt, isDuplicateUtr, result.usedSimulatedFallback);
    }

    private void showScanFailureFallback() {
        if (binding == null) return;
        binding.layoutScanningState.setVisibility(View.GONE);
        binding.layoutReviewForm.setVisibility(View.VISIBLE);
        binding.tvScanMetaTitle.setText("Manual Entry Required");
        binding.tvScanMetaDetail.setText(R.string.review_scan_error);

        renderCategoryChips();
        selectCategoryByName(CategoryClassifier.CATEGORY_OTHER);

        binding.chipGroupWarnings.removeAllViews();
        binding.chipGroupWarnings.setVisibility(View.VISIBLE);
        addWarningChip(
                getString(R.string.review_warning_partial),
                ContextCompat.getColor(requireContext(), R.color.lf_budget_warning_container),
                ContextCompat.getColor(requireContext(), R.color.lf_budget_warning)
        );
    }

    private void renderWarningChips(
            @NonNull ParsedReceipt receipt,
            boolean isDuplicateUtr,
            boolean usedSimulatedFallback
    ) {
        binding.chipGroupWarnings.removeAllViews();
        int dangerBg = ContextCompat.getColor(requireContext(), R.color.lf_budget_exceeded_container);
        int dangerFg = ContextCompat.getColor(requireContext(), R.color.lf_budget_exceeded);
        int warnBg = ContextCompat.getColor(requireContext(), R.color.lf_budget_warning_container);
        int warnFg = ContextCompat.getColor(requireContext(), R.color.lf_budget_warning);
        int neutralBg = ContextCompat.getColor(requireContext(), R.color.lf_surface_variant);
        int neutralFg = ContextCompat.getColor(requireContext(), R.color.lf_on_surface_variant);

        if (receipt.status == ParsedReceipt.ReceiptStatus.FAILED) {
            addWarningChip(getString(R.string.review_warning_failed), dangerBg, dangerFg);
        } else if (receipt.status == ParsedReceipt.ReceiptStatus.PENDING) {
            addWarningChip(getString(R.string.review_warning_pending), warnBg, warnFg);
        }

        if (receipt.isIncomingCredit()) {
            addWarningChip(getString(R.string.review_warning_credit), warnBg, warnFg);
        }

        if (isDuplicateUtr) {
            addWarningChip(getString(R.string.review_warning_duplicate), dangerBg, dangerFg);
        }

        if (receipt.partialParse || receipt.amountCorrectedFromQuantization) {
            addWarningChip(getString(R.string.review_warning_partial), warnBg, warnFg);
        }

        if (usedSimulatedFallback) {
            addWarningChip(getString(R.string.review_warning_simulated), neutralBg, neutralFg);
        }

        binding.chipGroupWarnings.setVisibility(
                binding.chipGroupWarnings.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void addWarningChip(@NonNull String label, @ColorInt int bgColor, @ColorInt int fgColor) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setChipIconResource(R.drawable.ic_warning);
        chip.setChipIconTint(ColorStateList.valueOf(fgColor));
        chip.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
        chip.setTextColor(fgColor);
        chip.setChipStrokeWidth(0f);
        binding.chipGroupWarnings.addView(chip);
    }

    private void renderCategoryChips() {
        binding.chipGroupReviewCategories.removeAllViews();
        for (CategoryEntity cat : categories) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setTag(cat.id);
            chip.setText(cat.name);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> selectedCategoryId = cat.id);
            binding.chipGroupReviewCategories.addView(chip);
        }
    }

    private void selectCategoryByName(@NonNull String targetName) {
        for (int i = 0; i < binding.chipGroupReviewCategories.getChildCount(); i++) {
            View child = binding.chipGroupReviewCategories.getChildAt(i);
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

    private void saveReviewedExpense() {
        binding.tilReviewAmount.setError(null);
        binding.tilReviewMerchant.setError(null);

        String rawAmount = binding.etReviewAmount.getText() != null
                ? binding.etReviewAmount.getText().toString()
                : "";
        long amountPaise = MoneyFormatter.parseRupeesToPaise(rawAmount);
        if (amountPaise <= 0L) {
            binding.tilReviewAmount.setError(getString(R.string.error_invalid_amount));
            return;
        }

        String merchant = binding.etReviewMerchant.getText() != null
                ? binding.etReviewMerchant.getText().toString().trim()
                : "";
        if (merchant.isEmpty()) {
            binding.tilReviewMerchant.setError(getString(R.string.error_empty_merchant));
            return;
        }

        String utr = binding.etReviewUtr.getText() != null
                ? binding.etReviewUtr.getText().toString().trim()
                : "";

        PaymentMode mode = PaymentMode.UPI;
        int checkedMode = binding.chipGroupReviewMode.getCheckedChipId();
        if (checkedMode == R.id.chipReviewCard) {
            mode = PaymentMode.CARD;
        } else if (checkedMode == R.id.chipReviewCash) {
            mode = PaymentMode.CASH;
        }

        ExpenseEntity expense = new ExpenseEntity(
                amountPaise,
                merchant,
                selectedCategoryId,
                mode.name(),
                parsedTimestampMillis,
                "Scanned from payment receipt",
                utr.isEmpty() ? null : utr
        );

        binding.btnConfirmSaveScan.setEnabled(false);
        spendingRepository.saveExpense(expense).thenRun(() -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(this::dismissAllowingStateLoss);
            }
        });
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        cleanupTempResources();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.ivReceiptPreview.setImageDrawable(null);
            binding = null;
        }
    }

    private void cleanupTempResources() {
        if (activePreviewBitmap != null && !activePreviewBitmap.isRecycled()) {
            activePreviewBitmap.recycle();
            activePreviewBitmap = null;
        }
        if (tempImageFile != null && tempImageFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempImageFile.delete();
        }
    }
}
