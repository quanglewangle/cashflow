package com.quanglewangle.peter.cashflow.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CreditCardDao {

    @Query("SELECT * FROM credit_cards ORDER BY name")
    List<CreditCardEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CreditCardEntity> cards);

    @Query("DELETE FROM credit_cards")
    void deleteAll();
}
