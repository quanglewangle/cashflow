package com.quanglewangle.peter.cashflow.data;

public class RecurringCardPurchase {
    public long id;
    public long creditCardId;
    public String description;
    public double amount;
    public String frequency; // "monthly" | "annual"
    public int dayOfMonth;
    public Integer targetMonth; // annual only, null for monthly
    public boolean active;
}
