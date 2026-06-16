package com.quanglewangle.peter.cashflow;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;

import java.util.List;

class Util {
    private Util() {}

    /** income -> blue, expense -> red, everywhere an amount is shown. Savings
     *  isn't part of that distinction, so it keeps the neutral grey used for
     *  secondary text; null (headers/balance rows, not tied to a type at all)
     *  gets plain dark text instead of being lumped in with that grey. */
    static int colorForItemType(android.content.Context context, String itemType) {
        int colorRes;
        if ("income".equals(itemType)) {
            colorRes = R.color.income;
        } else if ("expense".equals(itemType)) {
            colorRes = R.color.expense;
        } else if (itemType == null) {
            colorRes = R.color.black;
        } else {
            colorRes = R.color.planned;
        }
        return androidx.core.content.ContextCompat.getColor(context, colorRes);
    }

    /** Same as {@link #colorForItemType}, except an amount paid via a credit card is
     *  greyed out -- it doesn't move the cash balance, so it shouldn't read like one
     *  that does. */
    static int colorForAmount(android.content.Context context, String itemType, boolean paidByCard) {
        if (paidByCard) return androidx.core.content.ContextCompat.getColor(context, R.color.planned);
        return colorForItemType(context, itemType);
    }

    /** A recurring item/entry has creditCardId set for two different reasons: most are an
     *  everyday expense charged to that card (doesn't touch cash yet), but the card's own
     *  statement payment is also linked to its card for reference even though paying it
     *  is a real cash expense. The two are told apart by name: the payment item is named
     *  after the card it pays (e.g. "Barclaycard" linked to the Barclaycard), while a
     *  charged purchase has its own distinct name (e.g. "car insurance"). */
    static boolean isChargedToCard(Long creditCardId, String name, List<CreditCardEntity> cards) {
        if (creditCardId == null) return false;
        for (CreditCardEntity card : cards) {
            if (card.id == creditCardId) return !card.name.equalsIgnoreCase(name == null ? "" : name.trim());
        }
        return true;
    }

    /** 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 4 -> "4th", 11-13 -> "th", etc. */
    static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }
}
