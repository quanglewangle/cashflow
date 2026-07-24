package com.quanglewangle.peter.cashflow.data;

/** A carries_balance card's best-estimate running balance right now -- see
 *  the backend's CurrentCardBalance. found is false if the card has no
 *  checkpoint recorded yet. */
public class CardCurrentBalance {
    public double balance;
    public boolean found;
}
