package com.example.domain;

import androidx.annotation.NonNull;

/**
 * Semantic budget utilization tier driving color states (emerald, amber >=80%, crimson >=100%).
 */
public enum BudgetStatus {
    UNSET,
    HEALTHY,
    WARNING_80,
    EXCEEDED_100;

    @NonNull
    public static BudgetStatus evaluate(long spentPaise, long limitPaise) {
        if (limitPaise <= 0L) {
            return UNSET;
        }
        long safeSpent = Math.max(0L, spentPaise);
        if (safeSpent >= limitPaise) {
            return EXCEEDED_100;
        }
        // Integer math avoids floating-point drift: spent * 100 >= limit * 80
        if (safeSpent * 100L >= limitPaise * 80L) {
            return WARNING_80;
        }
        return HEALTHY;
    }

    public static int calculatePercent(long spentPaise, long limitPaise) {
        if (limitPaise <= 0L || spentPaise <= 0L) {
            return 0;
        }
        long pct = (spentPaise * 100L) / limitPaise;
        return (int) Math.min(pct, 999L);
    }
}
