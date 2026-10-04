package com.quanglewangle.peter.cashflow;

import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Util {
    private Util() {}

    // Last 4 digits shown in a Google Pay tap-to-pay notification or a PayPal
    // receipt email -> this app's credit card name. Add an entry here if
    // another card gets added to either.
    private static final java.util.Map<String, String> CARD_LAST_FOUR_TO_NAME = new java.util.HashMap<>();
    static {
        CARD_LAST_FOUR_TO_NAME.put("7159", "Visacard");
    }

    /** Reorders categories so each top-level category is immediately followed by
     *  its own subcategories, instead of the server's flat sort_order/name
     *  ordering -- for spinners that should read as a grouped tree. A category
     *  whose parent isn't in this same list (e.g. filtered out by item_type
     *  beforehand) is just treated as top-level itself. */
    static List<CategoryEntity> groupCategoriesByParent(List<CategoryEntity> categories) {
        Map<Long, CategoryEntity> byId = new HashMap<>();
        for (CategoryEntity c : categories) byId.put(c.id, c);
        Map<Long, List<CategoryEntity>> childrenByParent = new LinkedHashMap<>();
        List<CategoryEntity> tops = new ArrayList<>();
        for (CategoryEntity c : categories) {
            if (c.parentId != null && byId.containsKey(c.parentId)) {
                childrenByParent.computeIfAbsent(c.parentId, k -> new ArrayList<>()).add(c);
            } else {
                tops.add(c);
            }
        }
        List<CategoryEntity> result = new ArrayList<>();
        for (CategoryEntity top : tops) {
            result.add(top);
            List<CategoryEntity> children = childrenByParent.get(top.id);
            if (children != null) result.addAll(children);
        }
        return result;
    }

    /** Display label for a category in a grouped spinner -- subcategories get an indent. */
    static String categoryLabel(CategoryEntity c) {
        return c.parentId != null ? "    " + c.name : c.name;
    }

    static String cardNameForLastFour(String lastFour) {
        return CARD_LAST_FOUR_TO_NAME.get(lastFour);
    }

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

    /** Row background highlight: pale red for anything whose name ends in "!" (the
     *  user's own flag for "watch this"), pale green for a transfer to savings,
     *  else 0 for the row's normal background. */
    static int highlightColor(android.content.Context context, String name, String itemType) {
        if (name != null && name.trim().endsWith("!"))
            return androidx.core.content.ContextCompat.getColor(context, R.color.highlightFlagged);
        if ("savings".equals(itemType))
            return androidx.core.content.ContextCompat.getColor(context, R.color.highlightSavings);
        return 0;
    }

    /** Applies highlightColor to a row, restoring defaultBackground when there's none
     *  (rows are recycled, so a highlight must always be cleared as well as set). */
    static void applyHighlight(android.view.View row, android.graphics.drawable.Drawable defaultBackground,
                               String name, String itemType) {
        int color = highlightColor(row.getContext(), name, itemType);
        if (color != 0) row.setBackgroundColor(color);
        else row.setBackground(defaultBackground);
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

    /** Evaluates a simple arithmetic expression (+, -, *, /, parentheses, unary minus),
     *  e.g. "120.50+35-10*2". Returns null if the text is empty or not a valid expression. */
    static Double evalExpression(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            ExprParser parser = new ExprParser(s);
            double result = parser.parseExpression();
            if (!parser.atEnd()) return null;
            return result;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static class ExprParser {
        private final String s;
        private int pos;

        ExprParser(String s) { this.s = s; }

        boolean atEnd() {
            skipSpaces();
            return pos >= s.length();
        }

        double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (peek('+')) { pos++; value += parseTerm(); }
                else if (peek('-')) { pos++; value -= parseTerm(); }
                else break;
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (peek('*')) { pos++; value *= parseFactor(); }
                else if (peek('/')) {
                    pos++;
                    double divisor = parseFactor();
                    if (divisor == 0) throw new ArithmeticException("division by zero");
                    value /= divisor;
                }
                else break;
            }
            return value;
        }

        private double parseFactor() {
            skipSpaces();
            if (peek('-')) { pos++; return -parseFactor(); }
            if (peek('+')) { pos++; return parseFactor(); }
            if (peek('(')) {
                pos++;
                double value = parseExpression();
                skipSpaces();
                if (!peek(')')) throw new IllegalArgumentException("missing )");
                pos++;
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipSpaces();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (pos == start) throw new IllegalArgumentException("expected number at " + pos);
            return Double.parseDouble(s.substring(start, pos));
        }

        private void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private boolean peek(char c) {
            return pos < s.length() && s.charAt(pos) == c;
        }
    }

    /** Extracts the day-of-month from a "yyyy-MM-ddT..." date string, or 0 if unset/unparseable. */
    static int dayOfMonthOrZero(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return 0;
        try {
            return Integer.parseInt(isoDate.substring(8, 10));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Sentinel returned by compareIsoDate when isoDate is missing or unparseable. */
    static final int DATE_UNKNOWN = Integer.MIN_VALUE;

    /**
     * Compares a "yyyy-MM-ddT..." date string against year/month/day, full calendar date (not
     * just day-of-month) -- a settle date can land in a different month than the period it
     * belongs to (paid a few days early/late across a month boundary), so day-of-month alone
     * can't tell whether it's really before or after another date. Returns >0/0/<0 as isoDate is
     * after/on/before the given date, or DATE_UNKNOWN if isoDate can't be parsed.
     */
    static int compareIsoDate(String isoDate, int year, int month, int day) {
        if (isoDate == null || isoDate.length() < 10) return DATE_UNKNOWN;
        try {
            int y = Integer.parseInt(isoDate.substring(0, 4));
            int m = Integer.parseInt(isoDate.substring(5, 7));
            int d = Integer.parseInt(isoDate.substring(8, 10));
            if (y != year) return y - year;
            if (m != month) return m - month;
            return d - day;
        } catch (NumberFormatException e) {
            return DATE_UNKNOWN;
        }
    }

    /** 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 4 -> "4th", 11-13 -> "th", etc. */
    /** Wires the add/edit entry dialog's "Transfer from savings" box: shown only
     *  while the type spinner is on income, labelled with (and tagged with the id
     *  of) the first savings account once it loads. Read back with
     *  {@link #savingsAccountIdFrom}. */
    static void setupFromSavingsCheckbox(com.quanglewangle.peter.cashflow.data.Repository repo,
                                         android.widget.Spinner spinnerItemType, String[] itemTypes,
                                         android.widget.CheckBox box, Long currentAccountId) {
        box.setChecked(currentAccountId != null);
        box.setTag(currentAccountId);
        spinnerItemType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                box.setVisibility("income".equals(itemTypes[pos]) ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        repo.getSavingsAccounts(new com.quanglewangle.peter.cashflow.api.ApiService.Callback<List<com.quanglewangle.peter.cashflow.data.SavingsAccount>>() {
            @Override public void onSuccess(List<com.quanglewangle.peter.cashflow.data.SavingsAccount> accounts) {
                if (accounts.isEmpty()) return;
                com.quanglewangle.peter.cashflow.data.SavingsAccount a = accounts.get(0);
                for (com.quanglewangle.peter.cashflow.data.SavingsAccount x : accounts) {
                    if (currentAccountId != null && x.id == currentAccountId) a = x;
                }
                box.setText("Transfer from " + a.name + " savings");
                box.setTag(a.id);
            }
            @Override public void onError(String error) { /* keep whatever tag we had */ }
        });
    }

    /** The savings_account_id to send for an entry saved from that dialog: the
     *  ticked account for income, null otherwise (the server tags savings-type
     *  entries itself). */
    static Long savingsAccountIdFrom(android.widget.CheckBox box, String itemType) {
        if (!"income".equals(itemType) || !box.isChecked()) return null;
        return (Long) box.getTag();
    }

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
