package com.quanglewangle.peter.cashflow.data;

import java.util.ArrayList;
import java.util.List;

public class CardPaymentBreakdown {
    public CardCheckpoint checkpoint; // null if no checkpoint anchors this period
    public List<CardPurchase> purchases = new ArrayList<>();
    public double total;
}
