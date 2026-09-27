package com.quanglewangle.peter.cashflow.data;

/** A savings account (e.g. Marcus). Not cached locally -- always fetched fresh,
 *  since currentBalance is server-computed and moves with interest/transfers. */
public class SavingsAccount {
    public long id;
    public String name;
    public double openingBalance;
    public String openingDate;   // "YYYY-MM-DD" -- openingBalance is as at the end of this day
    public double interestRate;  // AER, percent
    public int interestDay;
    public double currentBalance;
}
