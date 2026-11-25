package com.example.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic keyword-based classifier mapping Indian merchants & UPI handles
 * to starter category names with a safe fallback to "Other".
 */
public final class CategoryClassifier {

    public static final String CATEGORY_FOOD = "Food & Dining";
    public static final String CATEGORY_TRANSPORT = "Transport";
    public static final String CATEGORY_SHOPPING = "Shopping";
    public static final String CATEGORY_BILLS = "Bills & Utilities";
    public static final String CATEGORY_GROCERIES = "Groceries";
    public static final String CATEGORY_HEALTH = "Health";
    public static final String CATEGORY_ENTERTAINMENT = "Entertainment";
    public static final String CATEGORY_OTHER = "Other";

    private static final Map<String, String[]> CATEGORY_KEYWORDS;

    static {
        Map<String, String[]> map = new LinkedHashMap<>();
        map.put(CATEGORY_GROCERIES, new String[]{
                "blinkit", "zepto", "instamart", "bigbasket", "bbdaily", "dmart", "d-mart",
                "jiomart", "more supermarket", "reliance fresh", "reliance smart", "spencer",
                "natures basket", "milk", "kirana", "supermarket", "grocery", "groceries",
                "vegetables", "fruits", "dairy", "licious", "freshmenu", "country delight"
        });
        map.put(CATEGORY_FOOD, new String[]{
                "zomato", "swiggy", "dominos", "domino's", "pizza hut", "mcdonald", "kfc",
                "burger king", "subway", "starbucks", "third wave", "blue tokai", "chaayos",
                "chai point", "cafe coffee day", "ccd", "haldiram", "bikanervala", "barbeque nation",
                "behrouz", "ovenstory", "faasos", "eatsure", "box8", "bakery", "restaurant",
                "cafe", "coffee", "tea", "biryani", "sweet", "tiffin", "canteen", "dining",
                "dhaba", "hotel", "bistro", "kitchen", "eatery", "ice cream", "baskin"
        });
        map.put(CATEGORY_TRANSPORT, new String[]{
                "uber", "ola", "rapido", "blu smart", "blusmart", "namma yatri", "meru",
                "irctc", "indigo", "air india", "vistara", "akasa", "spicejet", "makemytrip",
                "goibibo", "redbus", "abhibus", "metro", "dmrc", "bmrc", "fastag", "nhai",
                "toll", "petrol", "diesel", "fuel", "hpcl", "bpcl", "iocl", "indian oil",
                "bharat petroleum", "hindustan petroleum", "shell", "nayara", "parking",
                "auto", "cab", "taxi", "railway", "train", "flight", "bus"
        });
        map.put(CATEGORY_SHOPPING, new String[]{
                "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "tatacliq",
                "tata cliq", "croma", "reliance digital", "vijay sales", "decathlon",
                "zudio", "westside", "pantaloons", "shoppers stop", "lifestyle", "h&m",
                "zara", "uniqlo", "ikea", "pepperfry", "urban ladder", "lenskart",
                "firstcry", "boat", "apple store", "samsung", "nike", "adidas", "puma",
                "store", "mart", "emporium", "mall", "electronics", "fashion"
        });
        map.put(CATEGORY_BILLS, new String[]{
                "jio", "airtel", "vi ", "vodafone", "idea", "bsnl", "act fibernet",
                "hathway", "tataplay", "tata play", "d2h", "dish tv", "sun direct",
                "bescom", "msedcl", "tata power", "adani electricity", "bses", "cesc",
                "tneb", "uppcl", "electricity", "power", "water bill", "mahanagar gas",
                "indraprastha gas", "igl", "mgl", "hp gas", "indane", "bharat gas",
                "lpg", "cylinder", "broadband", "wifi", "recharge", "postpaid", "prepaid",
                "bbps", "billdesk", "razorpay utility", "rent", "maintenance", "society",
                "nobroker", "mygate", "cred", "insurance", "lic"
        });
        map.put(CATEGORY_HEALTH, new String[]{
                "apollo", "1mg", "tata 1mg", "pharmeasy", "netmeds", "medplus", "truemeds",
                "practo", "cult.fit", "cultfit", "curefit", "hospital", "clinic", "pharmacy",
                "chemist", "medical", "diagnostic", "pathology", "lal path", "thyrocare",
                "metropolis", "dental", "doctor", "lab", "health", "gym", "fitness"
        });
        map.put(CATEGORY_ENTERTAINMENT, new String[]{
                "bookmyshow", "pvr", "inox", "cinepolis", "netflix", "spotify", "hotstar",
                "jiohotstar", "jiocinema", "sony liv", "sonyliv", "zee5", "prime video",
                "youtube", "apple music", "audible", "kindle", "steam", "playstation",
                "xbox", "district", "insider", "concert", "cinema", "movie", "theatre",
                "gaming", "bowling", "amusement"
        });
        CATEGORY_KEYWORDS = Collections.unmodifiableMap(map);
    }

    private CategoryClassifier() {
    }

    @NonNull
    public static String classify(@Nullable String merchantTitle, @Nullable String notes) {
        StringBuilder combined = new StringBuilder();
        if (merchantTitle != null) {
            combined.append(merchantTitle).append(' ');
        }
        if (notes != null) {
            combined.append(notes);
        }
        String normalized = combined.toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.&'\\- ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return CATEGORY_OTHER;
        }

        // Pad with spaces so short tokens like "vi " match cleanly without false positives.
        String padded = " " + normalized + " ";

        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (keyword.length() <= 3) {
                    if (padded.contains(" " + keyword.trim() + " ")) {
                        return entry.getKey();
                    }
                } else if (normalized.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return CATEGORY_OTHER;
    }
}
