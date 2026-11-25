package com.example.ml;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deterministic preview/test implementation of MirageRepository.
 * Runs the REAL ImagePreprocessor and UpiReceiptPostProcessor end-to-end so Share-to-Scan
 * and unit tests can be exercised before mirage.cact and libcactus.so are dropped in.
 */
public class FakeMirageRepository implements MirageRepository {

    private static final String[] SAMPLE_MIRAGE_OUTPUTS = new String[]{
            "{\"amount\": \"₹349.00\", \"merchant\": \"Paid to Zomato Online (zomato@hdfcbank)\", "
                    + "\"status\": \"SUCCESS\", \"direction\": \"DEBIT\", "
                    + "\"reference_id\": \"431829401827\", \"payment_mode\": \"UPI\"}",
            "{\"amount\": \"2 185.50\", \"merchant\": \"uber.india@okaxis\", "
                    + "\"status\": \"SUCCESS\", \"direction\": \"DEBIT\", "
                    + "\"reference_id\": \"431950112233\", \"payment_mode\": \"UPI\"}",
            "{\"amount\": \"R640\", \"merchant\": \"Blinkit Groceries\", "
                    + "\"status\": \"SUCCESS\", \"direction\": \"DEBIT\", "
                    + "\"reference_id\": \"432011998877\", \"payment_mode\": \"UPI\"}"
    };

    @NonNull
    private final ExecutorService executor;

    public FakeMirageRepository() {
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-mirage-thread"));
    }

    @Override
    @NonNull
    public CompletableFuture<ScanResult> scanReceiptFile(@NonNull File imageFile) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                ImagePreprocessor.PreprocessedImage pre = ImagePreprocessor.preprocessFile(imageFile);
                int idx = Math.abs(imageFile.getName().hashCode()) % SAMPLE_MIRAGE_OUTPUTS.length;
                String simulatedRaw = SAMPLE_MIRAGE_OUTPUTS[idx];
                ParsedReceipt parsed = UpiReceiptPostProcessor.process(simulatedRaw, System.currentTimeMillis());
                long elapsed = System.currentTimeMillis() - start;
                return new ScanResult(parsed, pre.previewThumbnail, pre.smartCropped, true, elapsed);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to preprocess image: " + ex.getMessage(), ex);
            }
        }, executor);
    }

    @Override
    @NonNull
    public MirageStatus getStatus() {
        return new MirageStatus(
                false,
                false,
                false,
                true,
                "FakeMirageRepository (Test/Preview Mode)",
                "Drop mirage.cact and libcactus.so to switch to native Cactus Compute inference."
        );
    }

    @Override
    public void releaseResources() {
        // No native memory allocated
    }
}
