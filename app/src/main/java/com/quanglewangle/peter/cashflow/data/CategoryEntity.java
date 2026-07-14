package com.quanglewangle.peter.cashflow.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Top-level grouping (Income / Committed / Savings), mirrors the server's "categories" table. */
@Entity(tableName = "categories")
public class CategoryEntity {
    @PrimaryKey
    public long id;

    public String name;
    public String itemType;   // income | expense | savings
    public int sortOrder;
    public Long parentId;     // null = top-level category
}
