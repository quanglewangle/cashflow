package com.quanglewangle.peter.cashflow;

/** One row of the spreadsheet-style grid: a label plus one pre-formatted cell per month column. */
class GridRow {
    String label;
    String[] cells;
    /** income | expense | savings | null -- null means no type-based color (headers/balances). */
    String itemType;
    boolean bold;
    /** True if this item is paid via a credit card -- it doesn't move the cash
     *  balance, so it's greyed out and excluded from the category total. */
    boolean paidByCard;

    GridRow(String label, String[] cells, String itemType, boolean bold) {
        this(label, cells, itemType, bold, false);
    }

    GridRow(String label, String[] cells, String itemType, boolean bold, boolean paidByCard) {
        this.label = label;
        this.cells = cells;
        this.itemType = itemType;
        this.bold = bold;
        this.paidByCard = paidByCard;
    }
}
