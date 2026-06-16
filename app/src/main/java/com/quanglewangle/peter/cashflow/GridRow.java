package com.quanglewangle.peter.cashflow;

/** One row of the spreadsheet-style grid: a label plus one pre-formatted cell per month column. */
class GridRow {
    String label;
    String[] cells;
    /** income | expense | savings | null -- null means no type-based color (headers/balances). */
    String itemType;
    boolean bold;

    GridRow(String label, String[] cells, String itemType, boolean bold) {
        this.label = label;
        this.cells = cells;
        this.itemType = itemType;
        this.bold = bold;
    }
}
