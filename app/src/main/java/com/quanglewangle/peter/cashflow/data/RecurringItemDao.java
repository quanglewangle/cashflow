package com.quanglewangle.peter.cashflow.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecurringItemDao {

    @Query("SELECT * FROM recurring_items ORDER BY name")
    List<RecurringItemEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RecurringItemEntity> items);

    @Query("DELETE FROM recurring_items WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM recurring_items")
    void deleteAll();
}
