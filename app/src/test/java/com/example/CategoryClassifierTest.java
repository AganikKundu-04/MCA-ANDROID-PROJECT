package com.example;

import static org.junit.Assert.assertEquals;

import com.example.domain.CategoryClassifier;

import org.junit.Test;

public class CategoryClassifierTest {

    @Test
    public void classifiesFoodMerchants() {
        assertEquals(CategoryClassifier.CATEGORY_FOOD, CategoryClassifier.classify("Zomato Online", null));
        assertEquals(CategoryClassifier.CATEGORY_FOOD, CategoryClassifier.classify("Swiggy Food", null));
        assertEquals(CategoryClassifier.CATEGORY_FOOD, CategoryClassifier.classify("Third Wave Coffee", null));
    }

    @Test
    public void classifiesTransportMerchants() {
        assertEquals(CategoryClassifier.CATEGORY_TRANSPORT, CategoryClassifier.classify("Uber India", null));
        assertEquals(CategoryClassifier.CATEGORY_TRANSPORT, CategoryClassifier.classify("Ola Cabs", null));
        assertEquals(CategoryClassifier.CATEGORY_TRANSPORT, CategoryClassifier.classify("Rapido Bike", null));
        assertEquals(CategoryClassifier.CATEGORY_TRANSPORT, CategoryClassifier.classify("IRCTC Rail", null));
    }

    @Test
    public void classifiesShoppingAndBills() {
        assertEquals(CategoryClassifier.CATEGORY_SHOPPING, CategoryClassifier.classify("Amazon Pay Seller", null));
        assertEquals(CategoryClassifier.CATEGORY_SHOPPING, CategoryClassifier.classify("Flipkart Internet", null));
        assertEquals(CategoryClassifier.CATEGORY_BILLS, CategoryClassifier.classify("Jio Prepaid Recharge", null));
        assertEquals(CategoryClassifier.CATEGORY_BILLS, CategoryClassifier.classify("Airtel Fiber", null));
        assertEquals(CategoryClassifier.CATEGORY_BILLS, CategoryClassifier.classify("BESCOM Electricity", null));
    }

    @Test
    public void classifiesGroceriesHealthAndEntertainment() {
        assertEquals(CategoryClassifier.CATEGORY_GROCERIES, CategoryClassifier.classify("Blinkit", null));
        assertEquals(CategoryClassifier.CATEGORY_GROCERIES, CategoryClassifier.classify("Zepto Now", null));
        assertEquals(CategoryClassifier.CATEGORY_HEALTH, CategoryClassifier.classify("Apollo Pharmacy", null));
        assertEquals(CategoryClassifier.CATEGORY_ENTERTAINMENT, CategoryClassifier.classify("BookMyShow", null));
    }

    @Test
    public void fallsBackToOtherForUnknownOrBlankMerchants() {
        assertEquals(CategoryClassifier.CATEGORY_OTHER, CategoryClassifier.classify("Ramesh Kumar", null));
        assertEquals(CategoryClassifier.CATEGORY_OTHER, CategoryClassifier.classify("", null));
        assertEquals(CategoryClassifier.CATEGORY_OTHER, CategoryClassifier.classify(null, null));
    }
}
