package com.quanglewangle.peter.cashflow.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {
        CategoryEntity.class,
        CreditCardEntity.class,
        RecurringItemEntity.class,
        EntryEntity.class
}, version = 3, exportSchema = false)
// v3: EntryEntity gained dueDay. Bump this any time a Room
// @Entity's fields change -- fallbackToDestructiveMigration() only fires on a
// version change; without one Room throws on open with an identity-hash
// mismatch instead of just recreating the (cache-only, disposable) tables.
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract CategoryDao categoryDao();
    public abstract CreditCardDao creditCardDao();
    public abstract RecurringItemDao recurringItemDao();
    public abstract EntryDao entryDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "cashflow.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return instance;
    }
}
