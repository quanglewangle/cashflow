package com.quanglewangle.peter.cashflow.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One line item for one month -- what the forecast/entries screens actually show. */
@Entity(tableName = "entries")
public class EntryEntity {
    @PrimaryKey
    public long id;

    public Long recurringItemId; // null for a one-off not backed by a template
    public long categoryId;
    public int periodYear;
    public int periodMonth; // 1-12
    public String name;
    public String itemType;        // income | expense | savings
    public double plannedAmount;
    public Double actualAmount;    // set once incurred
    public String status;          // planned | incurred
    public Long creditCardId;
    public Integer dueDay;
}
