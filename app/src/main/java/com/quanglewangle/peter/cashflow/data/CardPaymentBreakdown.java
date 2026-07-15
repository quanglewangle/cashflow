package com.quanglewangle.peter.cashflow.data;

import java.util.ArrayList;
import java.util.List;

public class CardPaymentBreakdown {
    public CardCheckpoint checkpoint; // null if no checkpoint anchors this period
    public List<CardPurchase> coveredByCheckpoint = new ArrayList<>(); // already folded into checkpoint.balance
    public List<CardPurchase> purchases = new ArrayList<>(); // added on top -- contributes to total
    public List<EntryEntity> oneOffs = new ArrayList<>(); // card-tagged one-offs added on top (e.g. a sundries buffer)
    public EntryEntity unpaidPriorBill; // null if none -- netted out of total, see backend sumUnpaidPriorCardBills
    public double total;
}
