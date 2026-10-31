package com.quanglewangle.peter.cashflow.data;

/** A yearly present allowance (Christmas, a cluster of birthdays) on a card.
 *  Card purchases in the "Presents" category count toward the next occasion on
 *  or after their date; the server holds back what's left on the card's bills
 *  in the run-up to it. Not cached locally. */
public class PresentOccasion {
    public long id;
    public long creditCardId;
    public String name;
    public int month;  // 1-12
    public int day;
    public double allowance;
    // Optional: when shopping for it starts each year (null = from the previous occasion).
    public Integer shopStartMonth;
    public Integer shopStartDay;
    // Server-computed for the next occurrence (on or after today).
    public String nextDate;  // "YYYY-MM-DD"
    public double spent;
    public double remaining;
    public boolean overspent;
}
