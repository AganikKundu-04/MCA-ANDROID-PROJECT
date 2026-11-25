package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.ui.chart.CategoryDonutChartView;

import org.junit.Test;

public class CategoryDonutChartMathTest {

    @Test
    public void singleSliceSpansFullCircle() {
        float[] sweeps = CategoryDonutChartView.computeNormalizedSweeps(
                new long[]{50_000L},
                CategoryDonutChartView.GAP_DEGREES,
                CategoryDonutChartView.MIN_SLICE_DEGREES
        );
        assertEquals(1, sweeps.length);
        assertEquals(360f, sweeps[0], 0.01f);
    }

    @Test
    public void multipleTinySlicesNeverExceed360Degrees() {
        long[] amounts = new long[]{1_000_000L, 100L, 100L, 100L, 100L};
        float gap = CategoryDonutChartView.GAP_DEGREES;
        float min = CategoryDonutChartView.MIN_SLICE_DEGREES;

        float[] sweeps = CategoryDonutChartView.computeNormalizedSweeps(amounts, gap, min);

        float total = 0f;
        for (float s : sweeps) {
            assertTrue("Each positive slice must meet min angle", s >= min - 0.01f);
            total += s;
        }
        float totalWithGaps = total + (amounts.length * gap);
        assertEquals(360f, totalWithGaps, 0.05f);
    }

    @Test
    public void hitTestSelectsCorrectSliceAndIgnoresGap() {
        float[] sweeps = new float[]{177.6f, 177.6f};
        float gap = 2.4f;

        assertEquals(0, CategoryDonutChartView.hitTestNormalizedAngle(45f, sweeps, gap));
        assertEquals(-1, CategoryDonutChartView.hitTestNormalizedAngle(178.5f, sweeps, gap));
        assertEquals(1, CategoryDonutChartView.hitTestNormalizedAngle(220f, sweeps, gap));
    }

    @Test
    public void emptyAmountsProduceEmptySweeps() {
        float[] sweeps = CategoryDonutChartView.computeNormalizedSweeps(
                new long[0],
                CategoryDonutChartView.GAP_DEGREES,
                CategoryDonutChartView.MIN_SLICE_DEGREES
        );
        assertEquals(0, sweeps.length);
    }
}
