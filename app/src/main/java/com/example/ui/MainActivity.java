package com.example.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.R;
import com.example.SpendingApp;
import com.example.databinding.ActivityMainBinding;
import com.example.ui.scan.ReceiptReviewBottomSheet;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Single-activity host with Edge-to-Edge WindowInsets, NavHostFragment + BottomNavigationView,
 * and lifecycle-safe ACTION_SEND Share-to-Scan handling for both cold and warm starts.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_RECEIPT_REVIEW = "receipt_review_sheet";

    private ActivityMainBinding binding;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickReceiptLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    ingestSharedImageUri(uri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyEdgeToEdgeInsets();
        setupNavigation();

        // Handle cold-start ACTION_SEND only once; consume the intent immediately so rotation never re-triggers
        if (savedInstanceState == null) {
            handlePossibleShareIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePossibleShareIntent(intent);
    }

    public void launchGalleryReceiptPicker() {
        pickReceiptLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCoordinator, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.navHostFragment.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0
            );
            binding.bottomNav.setPadding(
                    systemBars.left,
                    0,
                    systemBars.right,
                    systemBars.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        binding.bottomTabBarContainer.post(() -> {
            if (binding == null) return;
            int navHeight = binding.bottomTabBarContainer.getHeight();
            if (navHeight > 0) {
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) binding.navHostFragment.getLayoutParams();
                if (lp.bottomMargin != navHeight) {
                    lp.bottomMargin = navHeight;
                    binding.navHostFragment.setLayoutParams(lp);
                }
            }
        });
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
        }
    }

    private void handlePossibleShareIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        String type = intent.getType();
        if (!Intent.ACTION_SEND.equals(action) || type == null || !type.startsWith("image/")) {
            return;
        }

        Uri sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);

        // Immediately consume/clear the intent so configuration changes never re-trigger scanning
        intent.setAction(Intent.ACTION_MAIN);
        intent.removeExtra(Intent.EXTRA_STREAM);
        setIntent(new Intent(Intent.ACTION_MAIN));

        if (sharedUri != null) {
            ingestSharedImageUri(sharedUri);
        }
    }

    /**
     * Opens the shared content:// InputStream immediately while the temporary URI read grant
     * is active, then copies bytes into an internal cache file on a background IO thread
     * before launching ReceiptReviewBottomSheet.
     */
    private void ingestSharedImageUri(@NonNull Uri uri) {
        final InputStream openedStream;
        try {
            openedStream = getContentResolver().openInputStream(uri);
        } catch (SecurityException | IOException ex) {
            Snackbar.make(binding.rootCoordinator, R.string.review_scan_error, Snackbar.LENGTH_LONG).show();
            return;
        }

        if (openedStream == null) {
            Snackbar.make(binding.rootCoordinator, R.string.review_scan_error, Snackbar.LENGTH_LONG).show();
            return;
        }

        SpendingApp app = (SpendingApp) getApplication();
        app.getAppContainer().ioExecutor.execute(() -> {
            File targetFile = null;
            try (InputStream in = openedStream) {
                File dir = new File(getCacheDir(), "shared_receipts");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("Unable to create shared_receipts directory");
                }
                targetFile = new File(dir, "receipt_" + System.currentTimeMillis() + ".img");
                try (FileOutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buf = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                    out.flush();
                }

                final String absolutePath = targetFile.getAbsolutePath();
                runOnUiThread(() -> showReceiptReviewSheet(absolutePath));
            } catch (IOException ex) {
                if (targetFile != null && targetFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    targetFile.delete();
                }
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && binding != null) {
                        Snackbar.make(binding.rootCoordinator, R.string.review_scan_error, Snackbar.LENGTH_LONG)
                                .show();
                    }
                });
            }
        });
    }

    private void showReceiptReviewSheet(@NonNull String tempImagePath) {
        if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) {
            return;
        }
        ReceiptReviewBottomSheet existing =
                (ReceiptReviewBottomSheet) getSupportFragmentManager().findFragmentByTag(TAG_RECEIPT_REVIEW);
        if (existing != null) {
            existing.dismissAllowingStateLoss();
        }
        ReceiptReviewBottomSheet.newInstance(tempImagePath)
                .show(getSupportFragmentManager(), TAG_RECEIPT_REVIEW);
    }
}
