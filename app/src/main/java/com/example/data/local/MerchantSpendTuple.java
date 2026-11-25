package com.example.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;

import java.util.Objects;

/**
 * Aggregated spend per merchant for top merchants analytics.
 */
public class MerchantSpendTuple {

    @NonNull
    @ColumnInfo(name = "merchant_title")
    public String merchantTitle;

    @ColumnInfo(name = "total_paise")
    public long totalPaise;

    @ColumnInfo(name = "tx_count")
    public int transactionCount;

    public MerchantSpendTuple(
            @NonNull String merchantTitle,
            long totalPaise,
            int transactionCount
    ) {
        this.merchantTitle = merchantTitle;
        this.totalPaise = totalPaise;
        this.transactionCount = transactionCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantSpendTuple)) return false;
        MerchantSpendTuple that = (MerchantSpendTuple) o;
        return totalPaise == that.totalPaise
                && transactionCount == that.transactionCount
                && merchantTitle.equals(that.merchantTitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(merchantTitle, totalPaise, transactionCount);
    }
}
