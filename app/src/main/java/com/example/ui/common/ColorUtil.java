package com.example.ui.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.domain.BudgetStatus;

/**
 * Safe color parsing, circular category dot drawables, and semantic budget badge styling.
 */
public final class ColorUtil {

    private static final int FALLBACK_COLOR = 0xFF64748B;

    private ColorUtil() {
    }

    @ColorInt
    public static int parseHexOrDefault(@Nullable String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return FALLBACK_COLOR;
        }
        try {
            return Color.parseColor(hex.trim());
        } catch (IllegalArgumentException ex) {
            return FALLBACK_COLOR;
        }
    }

    @NonNull
    public static GradientDrawable createCircleDot(@ColorInt int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    @NonNull
    public static GradientDrawable createPillBadge(@NonNull Context context, @ColorInt int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        float radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 999f, context.getResources().getDisplayMetrics());
        drawable.setCornerRadius(radius);
        return drawable;
    }

    @ColorInt
    public static int budgetAccentColor(@NonNull Context context, @NonNull BudgetStatus status) {
        switch (status) {
            case EXCEEDED_100:
                return ContextCompat.getColor(context, R.color.lf_budget_exceeded);
            case WARNING_80:
                return ContextCompat.getColor(context, R.color.lf_budget_warning);
            case HEALTHY:
            case UNSET:
            default:
                return ContextCompat.getColor(context, R.color.lf_budget_healthy);
        }
    }

    @ColorInt
    public static int budgetContainerColor(@NonNull Context context, @NonNull BudgetStatus status) {
        switch (status) {
            case EXCEEDED_100:
                return ContextCompat.getColor(context, R.color.lf_budget_exceeded_container);
            case WARNING_80:
                return ContextCompat.getColor(context, R.color.lf_budget_warning_container);
            case HEALTHY:
            case UNSET:
            default:
                return ContextCompat.getColor(context, R.color.lf_budget_healthy_container);
        }
    }
}
