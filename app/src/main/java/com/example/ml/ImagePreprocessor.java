package com.example.ml;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Memory-safe preprocessing pipeline turning a shared UPI screenshot into Mirage's 512x512 packed RGB tensor.
 * Applies conditional smart cropping ONLY for tall full-screen screenshots (aspect ratio h/w > 1.7).
 */
public final class ImagePreprocessor {

    public static final int MIRAGE_INPUT_SIZE = 512;
    public static final float FULLSCREEN_ASPECT_THRESHOLD = 1.7f;
    private static final float STATUS_NAV_TRIM_RATIO = 0.10f;
    private static final int PREVIEW_MAX_DIM = 420;
    private static final int MAX_DECODE_DIM = 1536;

    private ImagePreprocessor() {
    }

    public static final class PreprocessedImage {
        @NonNull
        public final byte[] rgbPixels;
        public final int width;
        public final int height;
        @NonNull
        public final Bitmap previewThumbnail;
        public final boolean smartCropped;

        public PreprocessedImage(
                @NonNull byte[] rgbPixels,
                int width,
                int height,
                @NonNull Bitmap previewThumbnail,
                boolean smartCropped
        ) {
            this.rgbPixels = rgbPixels;
            this.width = width;
            this.height = height;
            this.previewThumbnail = previewThumbnail;
            this.smartCropped = smartCropped;
        }
    }

    /**
     * Immediately copies a shared content:// Uri into an app-private cache file while the
     * caller's temporary read permission grant is still active.
     */
    @NonNull
    public static File copyUriToCacheFile(
            @NonNull ContentResolver resolver,
            @NonNull Uri sourceUri,
            @NonNull File cacheDir
    ) throws IOException {
        File scansDir = new File(cacheDir, "shared_receipts");
        if (!scansDir.exists() && !scansDir.mkdirs()) {
            throw new IOException("Unable to create shared_receipts cache directory");
        }
        File target = new File(scansDir, "receipt_" + System.currentTimeMillis() + ".img");
        try (InputStream in = resolver.openInputStream(sourceUri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("ContentResolver returned null stream for URI");
            }
            byte[] buf = new byte[16 * 1024];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
        return target;
    }

    @NonNull
    public static PreprocessedImage preprocessFile(@NonNull File imageFile) throws IOException {
        int orientation = readExifOrientation(imageFile);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = new FileInputStream(imageFile)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Unsupported or corrupt image file");
        }

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_DIM);
        decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded;
        try (InputStream in = new FileInputStream(imageFile)) {
            decoded = BitmapFactory.decodeStream(in, null, decodeOpts);
        }
        if (decoded == null) {
            throw new IOException("Failed to decode receipt bitmap");
        }

        return transformDecodedBitmap(decoded, orientation);
    }

    @NonNull
    public static PreprocessedImage preprocessUri(
            @NonNull ContentResolver resolver,
            @NonNull Uri imageUri
    ) throws IOException {
        int orientation = readExifOrientation(resolver, imageUri);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(imageUri)) {
            if (in == null) {
                throw new IOException("Cannot open image stream");
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Unsupported or corrupt image URI");
        }

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_DIM);
        decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(imageUri)) {
            if (in == null) {
                throw new IOException("Cannot reopen image stream");
            }
            decoded = BitmapFactory.decodeStream(in, null, decodeOpts);
        }
        if (decoded == null) {
            throw new IOException("Failed to decode receipt bitmap from URI");
        }

        return transformDecodedBitmap(decoded, orientation);
    }

    /**
     * Pure geometric helper exposed for unit testing smart-crop threshold logic.
     */
    public static boolean shouldApplySmartCrop(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        return ((float) height / (float) width) > FULLSCREEN_ASPECT_THRESHOLD;
    }

    @NonNull
    private static PreprocessedImage transformDecodedBitmap(@NonNull Bitmap decoded, int exifOrientation) {
        Bitmap oriented = null;
        Bitmap cropped = null;
        Bitmap resized = null;
        try {
            oriented = applyExifTransform(decoded, exifOrientation);
            if (oriented != decoded) {
                decoded.recycle();
            }

            boolean shouldCrop = shouldApplySmartCrop(oriented.getWidth(), oriented.getHeight());
            if (shouldCrop) {
                int h = oriented.getHeight();
                int topTrim = Math.round(h * STATUS_NAV_TRIM_RATIO);
                int bottomTrim = Math.round(h * STATUS_NAV_TRIM_RATIO);
                int croppedHeight = Math.max(1, h - topTrim - bottomTrim);
                cropped = Bitmap.createBitmap(oriented, 0, topTrim, oriented.getWidth(), croppedHeight);
                if (cropped != oriented) {
                    oriented.recycle();
                }
            } else {
                cropped = oriented;
            }

            Bitmap preview = createPreviewThumbnail(cropped);
            resized = Bitmap.createScaledBitmap(cropped, MIRAGE_INPUT_SIZE, MIRAGE_INPUT_SIZE, true);
            if (resized != cropped) {
                cropped.recycle();
            }

            byte[] rgb = extractPackedRgb(resized);
            resized.recycle();
            resized = null;

            return new PreprocessedImage(rgb, MIRAGE_INPUT_SIZE, MIRAGE_INPUT_SIZE, preview, shouldCrop);
        } catch (RuntimeException ex) {
            safeRecycle(resized);
            safeRecycle(cropped);
            safeRecycle(oriented);
            safeRecycle(decoded);
            throw ex;
        }
    }

    @NonNull
    private static Bitmap createPreviewThumbnail(@NonNull Bitmap source) {
        int w = source.getWidth();
        int h = source.getHeight();
        float scale = Math.min(1.0f, (float) PREVIEW_MAX_DIM / (float) Math.max(w, h));
        int targetW = Math.max(1, Math.round(w * scale));
        int targetH = Math.max(1, Math.round(h * scale));
        if (targetW == w && targetH == h) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        return Bitmap.createScaledBitmap(source, targetW, targetH, true);
    }

    @NonNull
    private static byte[] extractPackedRgb(@NonNull Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        byte[] rgb = new byte[width * height * 3];
        int out = 0;
        for (int pixel : argb) {
            rgb[out++] = (byte) ((pixel >> 16) & 0xFF); // R
            rgb[out++] = (byte) ((pixel >> 8) & 0xFF);  // G
            rgb[out++] = (byte) (pixel & 0xFF);         // B
        }
        return rgb;
    }

    private static int calculateInSampleSize(int rawWidth, int rawHeight, int maxDim) {
        int sampleSize = 1;
        int maxRaw = Math.max(rawWidth, rawHeight);
        while ((maxRaw / (sampleSize * 2)) >= maxDim) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    private static int readExifOrientation(@NonNull File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static int readExifOrientation(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    @NonNull
    private static Bitmap applyExifTransform(@NonNull Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270f);
                matrix.postScale(-1f, 1f);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static void safeRecycle(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
