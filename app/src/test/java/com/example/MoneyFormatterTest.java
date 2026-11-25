package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.domain.BudgetStatus;
import com.example.domain.MoneyFormatter;

import org.junit.Test;

public class MoneyFormatterTest {

    @Test
    public void formatsPaiseInIndianLocaleGrouping() {
        String lakh = MoneyFormatter.formatInr(12_500_000L);
        assertTrue("Expected ₹1,25,000, got: " + lakh, lakh.contains("1,25,000") && lakh.startsWith("₹"));

        String fractional = MoneyFormatter.formatInr(49_950L);
        assertTrue("Expected ₹499.50, got: " + fractional, fractional.contains("499.50") && fractional.startsWith("₹"));

        String zero = MoneyFormatter.formatInr(0L);
        assertEquals("₹0", zero);
    }

    @Test
    public void formatsCompactIndianScale() {
        assertEquals("₹12.5k", MoneyFormatter.formatCompactInr(1_250_000L));
        assertEquals("₹1.5L", MoneyFormatter.formatCompactInr(15_000_000L));
        assertEquals("₹1.25Cr", MoneyFormatter.formatCompactInr(1_250_000_000L));
    }

    @Test
    public void parsesRupeesToExactPaiseWithoutFloatingPointError() {
        assertEquals(123_456L, MoneyFormatter.parseRupeesToPaise("1,234.56"));
        assertEquals(45_000L, MoneyFormatter.parseRupeesToPaise("₹450"));
        assertEquals(1999L, MoneyFormatter.parseRupeesToPaise("19.99"));
        assertEquals(5L, MoneyFormatter.parseRupeesToPaise("0.05"));
        assertEquals(-1L, MoneyFormatter.parseRupeesToPaise("0"));
        assertEquals(-1L, MoneyFormatter.parseRupeesToPaise("-100"));
        assertEquals(-1L, MoneyFormatter.parseRupeesToPaise("abc"));
        assertEquals(-1L, MoneyFormatter.parseRupeesToPaise(null));
    }

    @Test
    public void formatsPaiseToExactTwoDecimalCsvString() {
        assertEquals("125.50", MoneyFormatter.formatPaiseToDecimal(12_550L));
        assertEquals("0.05", MoneyFormatter.formatPaiseToDecimal(5L));
        assertEquals("1000.00", MoneyFormatter.formatPaiseToDecimal(100_000L));
    }

    @Test
    public void evaluatesBudgetStatusAtExactIntegerThresholds() {
        assertEquals(BudgetStatus.UNSET, BudgetStatus.evaluate(50_000L, 0L));
        assertEquals(BudgetStatus.HEALTHY, BudgetStatus.evaluate(79_999L, 100_000L));
        assertEquals(BudgetStatus.WARNING_80, BudgetStatus.evaluate(80_000L, 100_000L));
        assertEquals(BudgetStatus.WARNING_80, BudgetStatus.evaluate(99_999L, 100_000L));
        assertEquals(BudgetStatus.EXCEEDED_100, BudgetStatus.evaluate(100_000L, 100_000L));
        assertEquals(BudgetStatus.EXCEEDED_100, BudgetStatus.evaluate(150_000L, 100_000L));
    }
}
