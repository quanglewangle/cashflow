package com.quanglewangle.peter.cashflow;

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
