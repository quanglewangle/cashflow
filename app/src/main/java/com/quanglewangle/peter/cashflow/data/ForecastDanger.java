package com.quanglewangle.peter.cashflow.data;

public class ForecastDanger {
    // Server-supplied fields
    public int periodYear;
    public int periodMonth;
    public double broughtForward;
    public double minBalance;
    public int minBalanceDay;
    public double carriedForward;
    /** Below-£0 streak length if minBalance < 0, else days until the next in-payment lifts the low point. */
    public int lowDays;
    /** True if that streak/wait wasn't resolved within the server's lookahead window -- lowDays is a lower bound. */
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
