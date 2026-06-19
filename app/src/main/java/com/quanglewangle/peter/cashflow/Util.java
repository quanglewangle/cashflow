package com.quanglewangle.peter.cashflow;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;

import java.util.ArrayList;
import java.util.Calendar;
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

    /** Last Monday-Friday of the given month (1-based month). */
    static int lastWorkingDayOfMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        return cal.get(Calendar.DAY_OF_MONTH);
    }

    /** Day-of-month numbers when a four_weekly item falls in (year, month).
     *  anchorDate is "YYYY-MM-DD". Returns empty array if no occurrence. */
    static int[] fourWeeklyDaysInMonth(String anchorDate, int year, int month) {
        if (anchorDate == null || anchorDate.length() < 10) return new int[0];
        try {
            String[] p = anchorDate.split("-");
            Calendar anchor = Calendar.getInstance();
            anchor.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
            anchor.set(Calendar.MILLISECOND, 0);

            Calendar monthStart = Calendar.getInstance();
            monthStart.set(year, month - 1, 1, 0, 0, 0);
            monthStart.set(Calendar.MILLISECOND, 0);

            Calendar monthEnd = (Calendar) monthStart.clone();
            monthEnd.add(Calendar.MONTH, 1);

            Calendar cur = (Calendar) anchor.clone();
            while (cur.before(monthStart)) cur.add(Calendar.DAY_OF_MONTH, 28);

            ArrayList<Integer> days = new ArrayList<>();
            while (cur.before(monthEnd)) {
                days.add(cur.get(Calendar.DAY_OF_MONTH));
                cur.add(Calendar.DAY_OF_MONTH, 28);
            }
            return days.stream().mapToInt(Integer::intValue).toArray();
        } catch (Exception e) {
            return new int[0];
        }
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
