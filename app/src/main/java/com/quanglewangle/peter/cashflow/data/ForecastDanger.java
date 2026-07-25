package com.quanglewangle.peter.cashflow.data;

public class ForecastDanger {
    // Server-supplied fields
    public int periodYear;
    public int periodMonth;
    public double broughtForward;
    public double minBalance;
    public int minBalanceDay;
    public double carriedForward;
    /** Consecutive days below £0 for the dip containing minBalanceDay (0 if minBalance >= 0). */
    public int lowDays;
    /** True if that dip hadn't recovered within the server's lookahead window -- lowDays is a lower bound. */
    public boolean lowOngoing;

    // Client-computed simulation fields (zero = no action this month)
    public double simMin;
    public double simCarried;
    public double borrowNeeded;  // borrow from Marcos this month
    public double repayAmount;   // repay to Marcos this month (after min)

    public void initSim() {
        simMin = minBalance;
        simCarried = carriedForward;
        borrowNeeded = 0;
        repayAmount = 0;
    }
}
