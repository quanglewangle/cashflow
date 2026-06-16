package com.quanglewangle.peter.cashflow.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * The reusable template for a budget line (e.g. "Council Tax", "glasses").
 * Paying a bill never edits this row -- only the EntryEntity for that
 * period does -- so annual items keep generating a fresh entry every year
 * instead of being zeroed out and forgotten.
 */
@Entity(tableName = "recurring_items")
public class RecurringItemEntity {
    @PrimaryKey
    public long id;

    public long categoryId;
    public String name;
    public String itemType;     // income | expense | savings
    public String frequency;    // monthly | annual | irregular
    public Double defaultAmount;
    public Integer dueDay;      // 1-31, monthly/annual items
    public Integer targetMonth; // 1-12, annual items only
    public Long creditCardId;
    public boolean active;
    public String notes;
}
