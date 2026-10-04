package com.quanglewangle.peter.cashflow.data;

/** A holiday: extra card spending at a fixed amount per day, on top of the card's
 *  sundries buffer. The server turns it into a buffer on each bill its days fall
 *  into, counting down a day at a time as the holiday goes by. Not cached locally. */
public class Holiday {
    public long id;
    public long creditCardId;
    public String name;
    public String startDate;  // "YYYY-MM-DD", first day
    public String endDate;    // "YYYY-MM-DD", last day (inclusive)
    public double perDay;
    // Server-computed: perDay x every day, and what's still held back after counting down.
    public double total;
    public double remaining;
}
