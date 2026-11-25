package com.example.ui.chart;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.R;
import com.example.domain.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Canvas donut chart for category spend breakdowns:
 * - Exact 360-degree normalized arc geometry (no slice overlap even with tiny categories)
 * - Smooth entrance sweep and slice expansion animations (honoring system Reduce Motion)
 * - Touch-hit slice selection & legend sync with tabular numerals and auto-fit center readout
 * - Crisp 1dp hairline inner guide ring matching unslop + Apple HIG visual craft
 */
public class CategoryDonutChartView extends View {

    public static final class Slice {
        @Nullable
        public final Long categoryId;
        @NonNull
        public final String label;
        public final int color;
        public final long amountPaise;
        public final int transactionCount;

        public Slice(
                @Nullable Long categoryId,
                @NonNull String label,
                int color,
                long amountPaise,
                int transactionCount
        ) {
            this.categoryId = categoryId;
            this.label = label;
            this.color = color;
            this.amountPaise = Math.max(0L, amountPaise);
            this.transactionCount = Math.max(0, transactionCount);
        }

        public Slice(@Nullable Long categoryId, @NonNull String label, int color, long amountPaise) {
            this(categoryId, label, color, amountPaise, 0);
        }
    }

    public interface OnSliceSelectedListener {
        void onSliceSelected(int index, @Nullable Slice slice);
    }

    public static final float START_ANGLE = -90f;
    public static final float GAP_DEGREES = 2.4f;
    public static final float MIN_SLICE_DEGREES = 3.0f;

    private static final long SWEEP_ANIM_DURATION_MS = 380L;
    private static final long SELECTION_ANIM_DURATION_MS = 180L;

    private final List<Slice> slices = new ArrayList<>();
    private final List<Float> sliceStartAngles = new ArrayList<>();
    private final List<Float> sliceSweepAngles = new ArrayList<>();
    private final RectF arcBounds = new RectF();
    private final RectF selectedArcBounds = new RectF();

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerHairlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerSubLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long totalPaise = 0L;
    private int selectedIndex = -1;
    private float sweepProgress = 1.0f;
    private float selectionProgress = 0.0f;
    private boolean hasAnimatedOnce = false;

    private float baseStrokePx;
    private float selectedExtraStrokePx;
    private float selectedOutsetPx;
    private float defaultCenterValueSizePx;
    private float lastCenterX;
    private float lastCenterY;
    private float lastRadius;

    @Nullable
    private ValueAnimator sweepAnimator;
    @Nullable
    private ValueAnimator selectionAnimator;
    @Nullable
    private OnSliceSelectedListener listener;

    public CategoryDonutChartView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public CategoryDonutChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CategoryDonutChartView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        baseStrokePx = dpToPx(22f);
        selectedExtraStrokePx = dpToPx(5f);
        selectedOutsetPx = dpToPx(3f);
        defaultCenterValueSizePx = spToPx(21f);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(baseStrokePx);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.lf_surface_container_high));

        innerHairlinePaint.setStyle(Paint.Style.STROKE);
        innerHairlinePaint.setStrokeWidth(dpToPx(1f));
        innerHairlinePaint.setColor(ContextCompat.getColor(context, R.color.lf_outline_variant));

        centerDotPaint.setStyle(Paint.Style.FILL);

        Typeface displayTypeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans);
        Typeface bodyTypeface = ResourcesCompat.getFont(context, R.font.inter);

        centerValuePaint.setTextAlign(Paint.Align.CENTER);
        centerValuePaint.setColor(ContextCompat.getColor(context, R.color.lf_on_surface));
        centerValuePaint.setTextSize(defaultCenterValueSizePx);
        centerValuePaint.setFakeBoldText(true);
        centerValuePaint.setFontFeatureSettings("tnum");
        if (displayTypeface != null) {
            centerValuePaint.setTypeface(Typeface.create(displayTypeface, Typeface.BOLD));
        }

        centerLabelPaint.setTextAlign(Paint.Align.CENTER);
        centerLabelPaint.setColor(ContextCompat.getColor(context, R.color.lf_on_surface_variant));
        centerLabelPaint.setTextSize(spToPx(12f));
        if (bodyTypeface != null) {
            centerLabelPaint.setTypeface(bodyTypeface);
        }

        centerSubLabelPaint.setTextAlign(Paint.Align.CENTER);
        centerSubLabelPaint.setColor(ContextCompat.getColor(context, R.color.lf_on_surface_variant));
        centerSubLabelPaint.setTextSize(spToPx(11f));
        centerSubLabelPaint.setFontFeatureSettings("tnum");
        if (bodyTypeface != null) {
            centerSubLabelPaint.setTypeface(bodyTypeface);
        }

        setClickable(true);
    }

    public void setOnSliceSelectedListener(@Nullable OnSliceSelectedListener listener) {
        this.listener = listener;
    }

    public void setSlices(@NonNull List<Slice> newSlices) {
        slices.clear();
        sliceStartAngles.clear();
        sliceSweepAngles.clear();
        totalPaise = 0L;

        for (Slice slice : newSlices) {
            if (slice.amountPaise > 0L) {
                slices.add(slice);
                totalPaise += slice.amountPaise;
            }
        }

        if (selectedIndex >= slices.size()) {
            selectedIndex = -1;
            selectionProgress = 0f;
        }

        recomputeAngles();
        updateAccessibilityDescription();

        if (!slices.isEmpty() && !hasAnimatedOnce) {
            hasAnimatedOnce = true;
            startSweepAnimation();
        } else {
            sweepProgress = 1.0f;
            invalidate();
        }
    }

    public void setSelectedIndex(int index) {
        int normalized = (index >= 0 && index < slices.size()) ? index : -1;
        if (this.selectedIndex != normalized) {
            this.selectedIndex = normalized;
            updateAccessibilityDescription();
            startSelectionAnimation(normalized >= 0);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Pure geometric helper computing exact non-overlapping sweep angles summing to (360 - totalGap)
     * while guaranteeing each slice receives at least minSliceDegrees when feasible.
     */
    @NonNull
    public static float[] computeNormalizedSweeps(
            @NonNull long[] amountsPaise,
            float gapDegrees,
            float minSliceDegrees
    ) {
        int count = amountsPaise.length;
        if (count == 0) {
            return new float[0];
        }
        long sum = 0L;
        for (long a : amountsPaise) {
            if (a > 0L) {
                sum += a;
            }
        }
        if (sum <= 0L) {
            return new float[count];
        }

        float totalGap = count > 1 ? count * gapDegrees : 0f;
        float available = Math.max(0f, 360f - totalGap);
        if (count == 1) {
            return new float[]{360f};
        }

        float effectiveMin = Math.min(minSliceDegrees, available / count);
        float[] sweeps = new float[count];
        boolean[] clamped = new boolean[count];

        float remainingDegrees = available;
        long remainingAmount = sum;

        // Iteratively pin slices that fall below effectiveMin so larger slices absorb the delta cleanly
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < count; i++) {
                if (clamped[i] || amountsPaise[i] <= 0L) {
                    continue;
                }
                float raw = (float) (((double) amountsPaise[i] / (double) remainingAmount) * remainingDegrees);
                if (raw < effectiveMin) {
                    clamped[i] = true;
                    sweeps[i] = effectiveMin;
                    remainingDegrees -= effectiveMin;
                    remainingAmount -= amountsPaise[i];
                    changed = true;
                }
            }
        } while (changed && remainingAmount > 0L);

        for (int i = 0; i < count; i++) {
            if (!clamped[i] && amountsPaise[i] > 0L && remainingAmount > 0L) {
                sweeps[i] = (float) (((double) amountsPaise[i] / (double) remainingAmount) * remainingDegrees);
            }
        }
        return sweeps;
    }

    /**
     * Pure angle hit-test helper returning the slice index for a normalized clockwise angle in [0, 360).
     */
    public static int hitTestNormalizedAngle(
            float normalizedDegrees,
            @NonNull float[] sweeps,
            float gapDegrees
    ) {
        int count = sweeps.length;
        if (count == 0) {
            return -1;
        }
        float angle = ((normalizedDegrees % 360f) + 360f) % 360f;
        float cursor = 0f;
        float stepGap = count > 1 ? gapDegrees : 0f;
        for (int i = 0; i < count; i++) {
            float end = cursor + sweeps[i];
            if (angle >= cursor && angle <= end) {
                return i;
            }
            cursor = end + stepGap;
        }
        return -1;
    }

    private void recomputeAngles() {
        sliceStartAngles.clear();
        sliceSweepAngles.clear();
        if (slices.isEmpty() || totalPaise <= 0L) {
            return;
        }
        int count = slices.size();
        long[] amounts = new long[count];
        for (int i = 0; i < count; i++) {
            amounts[i] = slices.get(i).amountPaise;
        }
        float[] sweeps = computeNormalizedSweeps(amounts, GAP_DEGREES, MIN_SLICE_DEGREES);
        float current = START_ANGLE;
        float stepGap = count > 1 ? GAP_DEGREES : 0f;
        for (int i = 0; i < count; i++) {
            sliceStartAngles.add(current);
            sliceSweepAngles.add(sweeps[i]);
            current += sweeps[i] + stepGap;
        }
    }

    private void startSweepAnimation() {
        cancelAnimator(sweepAnimator);
        if (!ValueAnimator.areAnimatorsEnabled()) {
            sweepProgress = 1.0f;
            invalidate();
            return;
        }
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f);
        sweepAnimator.setDuration(SWEEP_ANIM_DURATION_MS);
        sweepAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
        sweepAnimator.addUpdateListener(anim -> {
            sweepProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        sweepAnimator.start();
    }

    private void startSelectionAnimation(boolean hasSelection) {
        cancelAnimator(selectionAnimator);
        float target = hasSelection ? 1.0f : 0.0f;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            selectionProgress = target;
            invalidate();
            return;
        }
        selectionAnimator = ValueAnimator.ofFloat(selectionProgress, target);
        selectionAnimator.setDuration(SELECTION_ANIM_DURATION_MS);
        selectionAnimator.setInterpolator(new DecelerateInterpolator());
        selectionAnimator.addUpdateListener(anim -> {
            selectionProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        selectionAnimator.start();
    }

    private void updateAccessibilityDescription() {
        if (slices.isEmpty() || totalPaise <= 0L) {
            setContentDescription("Category spending chart. No spending recorded.");
            return;
        }
        if (selectedIndex >= 0 && selectedIndex < slices.size()) {
            Slice sel = slices.get(selectedIndex);
            int pct = (int) Math.round(((double) sel.amountPaise * 100.0) / (double) totalPaise);
            setContentDescription("Category spending chart. Selected " + sel.label + ": "
                    + MoneyFormatter.formatInr(sel.amountPaise) + ", " + pct + " percent of "
                    + MoneyFormatter.formatInr(totalPaise) + " total.");
        } else {
            setContentDescription("Category spending chart. Total " + MoneyFormatter.formatInr(totalPaise)
                    + " across " + slices.size() + " categories. Tap a slice to inspect.");
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float pad = (baseStrokePx + selectedExtraStrokePx) / 2f + selectedOutsetPx + dpToPx(6f);
        float size = Math.min(width, height) - (pad * 2f);
        if (size <= 0f) {
            return;
        }

        lastCenterX = width / 2f;
        lastCenterY = height / 2f;
        lastRadius = size / 2f;

        arcBounds.set(
                lastCenterX - lastRadius,
                lastCenterY - lastRadius,
                lastCenterX + lastRadius,
                lastCenterY + lastRadius
        );

        // Crisp 1dp hairline inner guide ring framing the center readout
        float innerGuideRadius = Math.max(0f, lastRadius - (baseStrokePx / 2f) - dpToPx(8f));
        if (innerGuideRadius > 0f) {
            canvas.drawCircle(lastCenterX, lastCenterY, innerGuideRadius, innerHairlinePaint);
        }

        if (slices.isEmpty() || totalPaise <= 0L) {
            canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint);
            drawCenterReadout(canvas, MoneyFormatter.formatInr(0L), "No spending", null, 0, innerGuideRadius);
            return;
        }

        float maxSweepAngle = 360f * sweepProgress;
        for (int i = 0; i < slices.size(); i++) {
            float startAngle = sliceStartAngles.get(i);
            float relStart = startAngle - START_ANGLE;
            if (relStart >= maxSweepAngle) {
                continue;
            }
            float fullSweep = sliceSweepAngles.get(i);
            float visibleSweep = Math.min(fullSweep, maxSweepAngle - relStart);
            if (visibleSweep <= 0f) {
                continue;
            }

            Slice slice = slices.get(i);
            boolean isSelected = (i == selectedIndex);
            int baseColor = slice.color;
            int drawColor;
            if (selectedIndex >= 0 && !isSelected) {
                float dimFactor = 1.0f - (0.65f * selectionProgress);
                drawColor = applyAlpha(baseColor, dimFactor);
            } else {
                drawColor = baseColor;
            }

            arcPaint.setColor(drawColor);
            if (isSelected) {
                float extraStroke = selectedExtraStrokePx * selectionProgress;
                float outset = selectedOutsetPx * selectionProgress;
                arcPaint.setStrokeWidth(baseStrokePx + extraStroke);
                selectedArcBounds.set(
                        arcBounds.left - outset,
                        arcBounds.top - outset,
                        arcBounds.right + outset,
                        arcBounds.bottom + outset
                );
                canvas.drawArc(selectedArcBounds, startAngle, visibleSweep, false, arcPaint);
            } else {
                arcPaint.setStrokeWidth(baseStrokePx);
                canvas.drawArc(arcBounds, startAngle, visibleSweep, false, arcPaint);
            }
        }

        if (selectedIndex >= 0 && selectedIndex < slices.size()) {
            Slice selected = slices.get(selectedIndex);
            int pct = (int) Math.round(((double) selected.amountPaise * 100.0) / (double) totalPaise);
            String sub = pct + "% of month"
                    + (selected.transactionCount > 0 ? " · " + selected.transactionCount + " tx" : "");
            drawCenterReadout(
                    canvas,
                    MoneyFormatter.formatCompactInr(selected.amountPaise),
                    truncateLabel(selected.label, 14),
                    sub,
                    selected.color,
                    innerGuideRadius
            );
        } else {
            String sub = slices.size() == 1 ? "1 category" : slices.size() + " categories";
            drawCenterReadout(
                    canvas,
                    MoneyFormatter.formatCompactInr(totalPaise),
                    "Month total",
                    sub,
                    0,
                    innerGuideRadius
            );
        }
    }

    private void drawCenterReadout(
            @NonNull Canvas canvas,
            @NonNull String value,
            @NonNull String label,
            @Nullable String subLabel,
            int indicatorColor,
            float innerGuideRadius
    ) {
        // Auto-fit primary tabular amount inside the inner ring so large font scales never overlap arcs
        float maxTextWidth = Math.max(dpToPx(48f), (innerGuideRadius * 2f) - dpToPx(20f));
        centerValuePaint.setTextSize(defaultCenterValueSizePx);
        float measuredValWidth = centerValuePaint.measureText(value);
        if (measuredValWidth > maxTextWidth && measuredValWidth > 0f) {
            float scaledSize = defaultCenterValueSizePx * (maxTextWidth / measuredValWidth);
            centerValuePaint.setTextSize(Math.max(spToPx(13f), scaledSize));
        }

        Paint.FontMetrics valM = centerValuePaint.getFontMetrics();
        Paint.FontMetrics lblM = centerLabelPaint.getFontMetrics();
        Paint.FontMetrics subM = centerSubLabelPaint.getFontMetrics();

        float valueH = valM.descent - valM.ascent;
        float labelH = lblM.descent - lblM.ascent;
        float subH = (subLabel != null) ? (subM.descent - subM.ascent) : 0f;
        float gap1 = dpToPx(3f);
        float gap2 = (subLabel != null) ? dpToPx(2f) : 0f;
        float totalH = valueH + gap1 + labelH + gap2 + subH;

        float topY = lastCenterY - (totalH / 2f);
        float valueBaseline = topY - valM.ascent;
        float labelBaseline = topY + valueH + gap1 - lblM.ascent;

        canvas.drawText(value, lastCenterX, valueBaseline, centerValuePaint);

        if (indicatorColor != 0) {
            float dotRadius = dpToPx(3.5f);
            float dotGap = dpToPx(5f);
            float labelWidth = centerLabelPaint.measureText(label);
            float totalRowW = (dotRadius * 2f) + dotGap + labelWidth;
            float rowLeft = lastCenterX - (totalRowW / 2f);
            float dotCx = rowLeft + dotRadius;
            float dotCy = labelBaseline + ((lblM.ascent + lblM.descent) / 2f);

            centerDotPaint.setColor(indicatorColor);
            canvas.drawCircle(dotCx, dotCy, dotRadius, centerDotPaint);

            float textCenterX = rowLeft + (dotRadius * 2f) + dotGap + (labelWidth / 2f);
            canvas.drawText(label, textCenterX, labelBaseline, centerLabelPaint);
        } else {
            canvas.drawText(label, lastCenterX, labelBaseline, centerLabelPaint);
        }

        if (subLabel != null) {
            float subBaseline = topY + valueH + gap1 + labelH + gap2 - subM.ascent;
            canvas.drawText(subLabel, lastCenterX, subBaseline, centerSubLabelPaint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (slices.isEmpty() || totalPaise <= 0L) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - lastCenterX;
            float dy = event.getY() - lastCenterY;
            float dist = (float) Math.hypot(dx, dy);

            float innerBound = lastRadius - baseStrokePx - dpToPx(8f);
            float outerBound = lastRadius + baseStrokePx + dpToPx(10f);

            int nextSelection = -1;
            if (dist >= innerBound && dist <= outerBound) {
                double angle = Math.toDegrees(Math.atan2(dy, dx));
                float normalized = (float) ((angle - START_ANGLE + 360.0) % 360.0);
                float[] sweeps = new float[sliceSweepAngles.size()];
                for (int i = 0; i < sweeps.length; i++) {
                    sweeps[i] = sliceSweepAngles.get(i);
                }
                int hit = hitTestNormalizedAngle(normalized, sweeps, GAP_DEGREES);
                if (hit >= 0) {
                    nextSelection = (selectedIndex == hit) ? -1 : hit;
                }
            }

            selectedIndex = nextSelection;
            updateAccessibilityDescription();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            startSelectionAnimation(selectedIndex >= 0);
            performClick();

            if (listener != null) {
                Slice selected = (selectedIndex >= 0 && selectedIndex < slices.size())
                        ? slices.get(selectedIndex)
                        : null;
                listener.onSliceSelected(selectedIndex, selected);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnimator(sweepAnimator);
        cancelAnimator(selectionAnimator);
    }

    private void cancelAnimator(@Nullable ValueAnimator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }

    private int applyAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, factor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @NonNull
    private String truncateLabel(@NonNull String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars - 1) + "…";
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }
}
