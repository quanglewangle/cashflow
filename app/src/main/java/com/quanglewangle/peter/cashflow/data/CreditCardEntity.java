package com.quanglewangle.peter.cashflow.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "credit_cards")
public class CreditCardEntity {
    @PrimaryKey
    public long id;

    public String name;
    public int statementDay;
    public int paymentDueDay;
}
