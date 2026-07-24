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
    /** Calendar months after the statement closes that payment is due (1 for all 3 seeded cards). */
    public int paymentDueMonthOffset = 1;
    /** True for a fixed installment against a revolving balance (e.g. a promotional
     *  paydown) instead of paying the statement off in full each cycle. */
    public boolean carriesBalance;
}
