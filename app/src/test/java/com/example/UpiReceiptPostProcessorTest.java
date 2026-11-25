package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.domain.PaymentMode;
import com.example.ml.ImagePreprocessor;
import com.example.ml.ParsedReceipt;
import com.example.ml.UpiReceiptPostProcessor;

import org.junit.Test;

public class UpiReceiptPostProcessorTest {

    private static final long FIXED_NOW = 1_732_000_000_000L;

    @Test
    public void parsesCleanGooglePayJson() {
        String raw = "{\"amount\": \"₹349.00\", \"merchant\": \"Paid to Zomato Online (zomato@hdfcbank)\", "
                + "\"status\": \"SUCCESS\", \"direction\": \"DEBIT\", "
                + "\"date_time\": \"2025-02-14 19:30\", \"reference_id\": \"431829401827\", "
                + "\"payment_mode\": \"UPI\"}";

        ParsedReceipt receipt = UpiReceiptPostProcessor.process(raw, FIXED_NOW);

        assertEquals(34900L, receipt.amountPaise);
        assertEquals("Zomato Online", receipt.merchant);
        assertEquals(ParsedReceipt.ReceiptStatus.SUCCESS, receipt.status);
        assertEquals(ParsedReceipt.TransactionDirection.DEBIT, receipt.direction);
        assertEquals("431829401827", receipt.utrReference);
        assertEquals(PaymentMode.UPI, receipt.paymentMode);
        assertFalse(receipt.partialParse);
        assertFalse(receipt.amountCorrectedFromQuantization);
    }

    @Test
    public void repairsTwoBitQuantizationRupeeMisreadAsLeadingTwoWithSpace() {
        String raw = "{\"amount\": \"2 1,249.50\", \"merchant\": \"swiggy.online@icici\", "
                + "\"status\": \"Completed\", \"direction\": \"DEBIT\", \"reference_id\": \"UTR: 501234567890\"}";

        ParsedReceipt receipt = UpiReceiptPostProcessor.process(raw, FIXED_NOW);

        assertEquals(124950L, receipt.amountPaise);
        assertTrue(receipt.amountCorrectedFromQuantization);
        assertEquals("Swiggy", receipt.merchant);
        assertEquals(ParsedReceipt.ReceiptStatus.SUCCESS, receipt.status);
        assertEquals("501234567890", receipt.utrReference);
    }

    @Test
    public void repairsRupeeMisreadAsQuestionMarkAndCapitalR() {
        String rawQuestion = "{\"amount\": \"?899.25\", \"merchant\": \"Blinkit\", \"status\": \"SUCCESS\"}";
        ParsedReceipt r1 = UpiReceiptPostProcessor.process(rawQuestion, FIXED_NOW);
        assertEquals(89925L, r1.amountPaise);
        assertTrue(r1.amountCorrectedFromQuantization);

        String rawR = "{\"amount\": \"R450\", \"merchant\": \"Rapido Auto\", \"status\": \"SUCCESS\"}";
        ParsedReceipt r2 = UpiReceiptPostProcessor.process(rawR, FIXED_NOW);
        assertEquals(45000L, r2.amountPaise);
        assertTrue(r2.amountCorrectedFromQuantization);
    }

    @Test
    public void handlesMarkdownFencedAndMalformedJsonWithTrailingComma() {
        String raw = "```json\n"
                + "{ amount: '₹620', merchant: 'Paytm Uber India', status: 'PAID', "
                + "direction: 'DEBIT', reference_id: '412345678901', }\n"
                + "```";

        ParsedReceipt receipt = UpiReceiptPostProcessor.process(raw, FIXED_NOW);

        assertEquals(62000L, receipt.amountPaise);
        assertEquals("Paytm Uber India", receipt.merchant);
        assertEquals(ParsedReceipt.ReceiptStatus.SUCCESS, receipt.status);
        assertEquals("412345678901", receipt.utrReference);
    }

    @Test
    public void flagsFailedAndPendingReceipts() {
        String failedRaw = "{\"amount\": \"₹2,500\", \"merchant\": \"Amazon Pay\", "
                + "\"status\": \"PAYMENT FAILED\", \"direction\": \"DEBIT\", \"reference_id\": \"498765432109\"}";
        ParsedReceipt failed = UpiReceiptPostProcessor.process(failedRaw, FIXED_NOW);
        assertEquals(ParsedReceipt.ReceiptStatus.FAILED, failed.status);
        assertTrue(failed.isFailedOrPending());

        String pendingRaw = "PhonePe Transaction Pending\nPaid to Airtel Postpaid\n₹799\nUPI Ref No: 411122223333";
        ParsedReceipt pending = UpiReceiptPostProcessor.process(pendingRaw, FIXED_NOW);
        assertEquals(ParsedReceipt.ReceiptStatus.PENDING, pending.status);
        assertTrue(pending.isFailedOrPending());
        assertEquals(79900L, pending.amountPaise);
        assertEquals("411122223333", pending.utrReference);
    }

    @Test
    public void flagsIncomingCreditTransaction() {
        String creditRaw = "{\"amount\": \"₹5,000\", \"merchant\": \"Received from rahul.sharma@okhdfcbank\", "
                + "\"status\": \"SUCCESS\", \"direction\": \"CREDIT\", \"reference_id\": \"455566667777\"}";

        ParsedReceipt receipt = UpiReceiptPostProcessor.process(creditRaw, FIXED_NOW);

        assertTrue(receipt.isIncomingCredit());
        assertEquals(ParsedReceipt.TransactionDirection.CREDIT, receipt.direction);
        assertEquals("Rahul Sharma", receipt.merchant);
        assertEquals(500000L, receipt.amountPaise);
    }

    @Test
    public void fallsBackCleanlyOnUnstructuredPhonePeText() {
        String unstructured = "PhonePe Payment Successful\n"
                + "Paid to: Third Wave Coffee\n"
                + "Amount: Rs. 285.50\n"
                + "UPI Ref ID 432198765432";

        ParsedReceipt receipt = UpiReceiptPostProcessor.process(unstructured, FIXED_NOW);

        assertEquals(28550L, receipt.amountPaise);
        assertEquals("Third Wave Coffee", receipt.merchant);
        assertEquals("432198765432", receipt.utrReference);
        assertEquals(ParsedReceipt.ReceiptStatus.SUCCESS, receipt.status);
        assertEquals(ParsedReceipt.TransactionDirection.DEBIT, receipt.direction);
        assertTrue(receipt.partialParse);
    }

    @Test
    public void handlesEmptyOutputWithoutCrashing() {
        ParsedReceipt receipt = UpiReceiptPostProcessor.process("   ", FIXED_NOW);
        assertEquals(0L, receipt.amountPaise);
        assertTrue(receipt.partialParse);
        assertNotNull(receipt.warnings);
        assertFalse(receipt.warnings.isEmpty());
    }

    @Test
    public void smartCropOnlyTriggersForFullscreenPhoneScreenshots() {
        // 1080x2400 phone screenshot -> ratio 2.22 > 1.7 -> crop
        assertTrue(ImagePreprocessor.shouldApplySmartCrop(1080, 2400));
        // 1080x1080 already-cropped square receipt -> ratio 1.0 -> preserve intact
        assertFalse(ImagePreprocessor.shouldApplySmartCrop(1080, 1080));
        // 1000x1600 cropped receipt -> ratio 1.6 -> preserve intact
        assertFalse(ImagePreprocessor.shouldApplySmartCrop(1000, 1600));
    }
}
