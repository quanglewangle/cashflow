package com.quanglewangle.peter.cashflow.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EntryDao {

    @Query("SELECT * FROM entries WHERE periodYear = :year AND periodMonth = :month ORDER BY id")
    List<EntryEntity> getForPeriod(int year, int month);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EntryEntity> entries);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EntryEntity entry);

    @Query("DELETE FROM entries WHERE periodYear = :year AND periodMonth = :month")
    void deleteForPeriod(int year, int month);

    @Query("DELETE FROM entries WHERE id = :id")
    void deleteById(long id);
}
