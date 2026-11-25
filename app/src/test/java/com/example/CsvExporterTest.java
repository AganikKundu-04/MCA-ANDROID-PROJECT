package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.domain.CsvExporter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CsvExporterTest {

    @Test
    public void escapesCommasQuotesAndNewlinesPerRfc4180() {
        assertEquals("PlainMerchant", CsvExporter.escapeField("PlainMerchant"));
        assertEquals("\"Cafe, Bistro\"", CsvExporter.escapeField("Cafe, Bistro"));
        assertEquals("\"Joe's \"\"Best\"\" Chai\"", CsvExporter.escapeField("Joe's \"Best\" Chai"));
        assertEquals("\"Line 1\nLine 2\"", CsvExporter.escapeField("Line 1\nLine 2"));
        assertEquals("", CsvExporter.escapeField(null));
    }

    @Test
    public void exportsHeaderAndFormattedPaiseRows() {
        CsvExporter.CsvRow row1 = new CsvExporter.CsvRow(
                1_732_000_000_000L,
                "Zomato, Koramangala",
                "Food & Dining",
                "UPI",
                34_950L,
                "431829401827",
                "Dinner \"team\""
        );
        CsvExporter.CsvRow row2 = new CsvExporter.CsvRow(
                1_732_000_000_000L,
                "Uber",
                "Transport",
                "CARD",
                12_000L,
                null,
                null
        );

        String csv = CsvExporter.export(Arrays.asList(row1, row2));
        String[] lines = csv.split("\r\n");

        assertEquals(3, lines.length);
        assertEquals("Date,Merchant,Category,Payment Mode,Amount (INR),UTR Reference,Notes", lines[0]);
        assertTrue(lines[1].contains("\"Zomato, Koramangala\""));
        assertTrue(lines[1].contains(",349.50,"));
        assertTrue(lines[1].contains(",431829401827,"));
        assertTrue(lines[1].contains("\"Dinner \"\"team\"\"\""));
        assertTrue(lines[2].contains(",Uber,Transport,CARD,120.00,,"));
    }

    @Test
    public void exportsHeaderOnlyWhenEmpty() {
        String csv = CsvExporter.export(Collections.emptyList());
        assertEquals("Date,Merchant,Category,Payment Mode,Amount (INR),UTR Reference,Notes\r\n", csv);
    }
}
