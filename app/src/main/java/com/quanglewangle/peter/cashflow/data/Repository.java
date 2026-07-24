package com.quanglewangle.peter.cashflow.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.quanglewangle.peter.cashflow.api.ApiService;
import com.quanglewangle.peter.cashflow.data.CardCheckpoint;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bridges the REST API and the Room cache, same role as rocloc's Repository:
 * the API is the source of truth, Room is just what the UI reads so the
 * app still shows last-known data offline.
 */
public class Repository {

    private static Repository instance;

    public static synchronized Repository getInstance(Context context) {
        if (instance == null) instance = new Repository(context.getApplicationContext());
        return instance;
    }

    public interface ListCallback<T> {
        void onResult(List<T> list, boolean fromCache);
    }

    public interface ErrorCallback {
        void onError(String error);
    }

    private final AppDatabase db;
    private final ApiService api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Repository(Context context) {
        db = AppDatabase.getInstance(context);
        api = new ApiService();
    }

    public ApiService api() {
        return api;
    }

    // ---- categories ----

    public void getCategories(ListCallback<CategoryEntity> callback) {
        executor.execute(() -> {
            List<CategoryEntity> cached = db.categoryDao().getAll();
            main.post(() -> callback.onResult(cached, true));
            api.getCategories(new ApiService.Callback<List<CategoryEntity>>() {
                @Override public void onSuccess(List<CategoryEntity> fresh) {
                    executor.execute(() -> {
                        db.categoryDao().deleteAll();
                        db.categoryDao().insertAll(fresh);
                    });
                    main.post(() -> callback.onResult(fresh, false));
                }
                @Override public void onError(String error) { /* keep showing cache */ }
            });
        });
    }

    public void addCategory(String name, String itemType, int sortOrder, Runnable onDone, ErrorCallback onError) {
        api.addCategory(name, itemType, sortOrder, new ApiService.Callback<Long>() {
            @Override public void onSuccess(Long id) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    // ---- credit cards ----

    public void getCreditCards(ListCallback<CreditCardEntity> callback) {
        executor.execute(() -> {
            List<CreditCardEntity> cached = db.creditCardDao().getAll();
            main.post(() -> callback.onResult(cached, true));
            api.getCreditCards(new ApiService.Callback<List<CreditCardEntity>>() {
                @Override public void onSuccess(List<CreditCardEntity> fresh) {
                    executor.execute(() -> {
                        db.creditCardDao().deleteAll();
                        db.creditCardDao().insertAll(fresh);
                    });
                    main.post(() -> callback.onResult(fresh, false));
                }
                @Override public void onError(String error) { /* keep showing cache */ }
            });
        });
    }

    public void addCreditCard(CreditCardEntity card, Runnable onDone, ErrorCallback onError) {
        api.addCreditCard(card, new ApiService.Callback<Long>() {
            @Override public void onSuccess(Long id) {
                card.id = id;
                executor.execute(() -> db.creditCardDao().insertAll(List.of(card)));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void updateCreditCard(CreditCardEntity card, Runnable onDone, ErrorCallback onError) {
        api.updateCreditCard(card, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) {
                executor.execute(() -> db.creditCardDao().insertAll(List.of(card)));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void getCardPurchasesByMonth(int year, int month, ListCallback<CardPurchase> callback) {
        api.getCardPurchasesByMonth(year, month, new ApiService.Callback<List<CardPurchase>>() {
            @Override public void onSuccess(List<CardPurchase> result) { main.post(() -> callback.onResult(result, false)); }
            @Override public void onError(String error) { /* silently ignore */ }
        });
    }

    public void updateCardPurchase(long id, String description, double amount, String purchaseDateIso,
                                    Long categoryId, Runnable onDone, ErrorCallback onError) {
        api.updateCardPurchase(id, description, amount, purchaseDateIso, categoryId, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void deleteCardPurchase(long id, Runnable onDone, ErrorCallback onError) {
        api.deleteCardPurchase(id, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void deleteRecurringCardPurchase(long id, Runnable onDone, ErrorCallback onError) {
        api.deleteRecurringCardPurchase(id, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void getRecurringCardPurchases(long creditCardId, ListCallback<RecurringCardPurchase> callback) {
        api.getRecurringCardPurchases(creditCardId, new ApiService.Callback<List<RecurringCardPurchase>>() {
            @Override public void onSuccess(List<RecurringCardPurchase> result) { main.post(() -> callback.onResult(result, false)); }
            @Override public void onError(String error) { main.post(() -> callback.onResult(new java.util.ArrayList<>(), false)); }
        });
    }

    public void addRecurringCardPurchase(long creditCardId, String description, double amount,
                                          int dayOfMonth, Runnable onDone, ErrorCallback onError) {
        api.addRecurringCardPurchase(creditCardId, description, amount, dayOfMonth, new ApiService.Callback<Long>() {
            @Override public void onSuccess(Long id) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    /** Logs a real card purchase; not cached locally since it's a write-mostly log
     *  and its effect (the recalculated entry) is already covered by entry caching. */
    public void addCardPurchase(long creditCardId, String description, double amount, String purchaseDateIso,
                                 Long categoryId, Runnable onDone, ErrorCallback onError) {
        api.addCardPurchase(creditCardId, description, amount, purchaseDateIso, categoryId, new ApiService.Callback<Long>() {
            @Override public void onSuccess(Long id) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    // ---- recurring items ----

    public void getRecurringItems(ListCallback<RecurringItemEntity> callback) {
        executor.execute(() -> {
            List<RecurringItemEntity> cached = db.recurringItemDao().getAll();
            main.post(() -> callback.onResult(cached, true));
            api.getRecurringItems(new ApiService.Callback<List<RecurringItemEntity>>() {
                @Override public void onSuccess(List<RecurringItemEntity> fresh) {
                    executor.execute(() -> {
                        db.recurringItemDao().deleteAll();
                        db.recurringItemDao().insertAll(fresh);
                    });
                    main.post(() -> callback.onResult(fresh, false));
                }
                @Override public void onError(String error) { /* keep showing cache */ }
            });
        });
    }

    public void addRecurringItem(RecurringItemEntity item, Runnable onDone, ErrorCallback onError) {
        api.addRecurringItem(item, new ApiService.Callback<Long>() {
            @Override public void onSuccess(Long id) {
                item.id = id;
                executor.execute(() -> db.recurringItemDao().insertAll(List.of(item)));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void updateRecurringItem(RecurringItemEntity item, Runnable onDone, ErrorCallback onError) {
        api.updateRecurringItem(item, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) {
                executor.execute(() -> db.recurringItemDao().insertAll(List.of(item)));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void deleteRecurringItem(long id, Runnable onDone, ErrorCallback onError) {
        api.deleteRecurringItem(id, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) {
                executor.execute(() -> db.recurringItemDao().deleteById(id));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    // ---- entries ----

    /** Generates this period's entries from templates (idempotent) then fetches them. */
    public void loadPeriod(int year, int month, ListCallback<EntryEntity> callback) {
        executor.execute(() -> {
            List<EntryEntity> cached = db.entryDao().getForPeriod(year, month);
            main.post(() -> callback.onResult(cached, true));

            api.generatePeriod(year, month, new ApiService.Callback<Integer>() {
                @Override public void onSuccess(Integer created) { fetchEntries(year, month, callback); }
                @Override public void onError(String error) { fetchEntries(year, month, callback); }
            });
        });
    }

    private void fetchEntries(int year, int month, ListCallback<EntryEntity> callback) {
        api.getEntries(year, month, new ApiService.Callback<List<EntryEntity>>() {
            @Override public void onSuccess(List<EntryEntity> fresh) {
                executor.execute(() -> {
                    db.entryDao().deleteForPeriod(year, month);
                    db.entryDao().insertAll(fresh);
                });
                main.post(() -> callback.onResult(fresh, false));
            }
            @Override public void onError(String error) { /* keep showing cache */ }
        });
    }

    public void addEntry(EntryEntity entry, Runnable onDone, ErrorCallback onError) {
        api.addEntry(entry, new ApiService.Callback<Long>() {
            @Override public void onSuccess(Long id) {
                entry.id = id;
                executor.execute(() -> db.entryDao().insert(entry));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void updateEntry(EntryEntity entry, Runnable onDone, ErrorCallback onError) {
        api.updateEntry(entry, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) {
                executor.execute(() -> db.entryDao().insert(entry));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void deleteEntry(long id, Runnable onDone, ErrorCallback onError) {
        api.deleteEntry(id, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) {
                executor.execute(() -> db.entryDao().deleteById(id));
                main.post(onDone);
            }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    // ---- forecast (online only -- computed server-side from checkpoints + entries) ----

    public void getForecastRange(int year, int month, int count, ApiService.Callback<List<ForecastSummary>> callback) {
        // ApiService's callbacks fire on OkHttp's dispatcher thread, not the
        // main thread -- every other Repository method hops back via
        // main.post before touching UI; this one was missing that and could
        // crash (e.g. Toast from a background thread) on error.
        api.getForecastRange(year, month, count, new ApiService.Callback<List<ForecastSummary>>() {
            @Override public void onSuccess(List<ForecastSummary> result) { main.post(() -> callback.onSuccess(result)); }
            @Override public void onError(String error) { main.post(() -> callback.onError(error)); }
        });
    }

    public void getForecastDanger(int year, int month, int count, ApiService.Callback<List<ForecastDanger>> callback) {
        api.getForecastDanger(year, month, count, new ApiService.Callback<List<ForecastDanger>>() {
            @Override public void onSuccess(List<ForecastDanger> result) { main.post(() -> callback.onSuccess(result)); }
            @Override public void onError(String error) { main.post(() -> callback.onError(error)); }
        });
    }

    // ---- card checkpoints ----

    public void getCardCheckpoints(long creditCardId, ListCallback<CardCheckpoint> callback) {
        api.getCardCheckpoints(creditCardId, new ApiService.Callback<List<CardCheckpoint>>() {
            @Override public void onSuccess(List<CardCheckpoint> result) { main.post(() -> callback.onResult(result, false)); }
            @Override public void onError(String error) { main.post(() -> callback.onResult(new java.util.ArrayList<>(), false)); }
        });
    }

    public void getCardPaymentBreakdown(long creditCardId, int year, int month,
                                         ApiService.Callback<com.quanglewangle.peter.cashflow.data.CardPaymentBreakdown> callback) {
        api.getCardPaymentBreakdown(creditCardId, year, month,
                new ApiService.Callback<com.quanglewangle.peter.cashflow.data.CardPaymentBreakdown>() {
            @Override public void onSuccess(com.quanglewangle.peter.cashflow.data.CardPaymentBreakdown result) {
                main.post(() -> callback.onSuccess(result));
            }
            @Override public void onError(String error) { main.post(() -> callback.onError(error)); }
        });
    }

    public void getCardCurrentBalance(long creditCardId,
                                       ApiService.Callback<com.quanglewangle.peter.cashflow.data.CardCurrentBalance> callback) {
        api.getCardCurrentBalance(creditCardId,
                new ApiService.Callback<com.quanglewangle.peter.cashflow.data.CardCurrentBalance>() {
            @Override public void onSuccess(com.quanglewangle.peter.cashflow.data.CardCurrentBalance result) {
                main.post(() -> callback.onSuccess(result));
            }
            @Override public void onError(String error) { main.post(() -> callback.onError(error)); }
        });
    }

    public void addCardCheckpoint(long creditCardId, int year, int month, int day, double balance,
                                   ApiService.Callback<AddCheckpointResult> callback) {
        api.addCardCheckpoint(creditCardId, year, month, day, balance, new ApiService.Callback<AddCheckpointResult>() {
            @Override public void onSuccess(AddCheckpointResult result) { main.post(() -> callback.onSuccess(result)); }
            @Override public void onError(String error) { main.post(() -> callback.onError(error)); }
        });
    }

    public void deleteCardCheckpoint(long id, Runnable onDone, ErrorCallback onError) {
        api.deleteCardCheckpoint(id, new ApiService.Callback<Void>() {
            @Override public void onSuccess(Void v) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    /** Re-anchors the forecast to a real bank balance you just checked. */
    public void addCheckpoint(int periodYear, int periodMonth, int periodDay, double balance, Runnable onDone, ErrorCallback onError) {
        api.addCheckpoint(periodYear, periodMonth, periodDay, balance, new ApiService.Callback<Long>() {
            @Override public void onSuccess(Long id) { main.post(onDone); }
            @Override public void onError(String error) { main.post(() -> onError.onError(error)); }
        });
    }

    public void getCheckpoints(ApiService.Callback<List<BalanceCheckpoint>> callback) {
        api.getCheckpoints(new ApiService.Callback<List<BalanceCheckpoint>>() {
            @Override public void onSuccess(List<BalanceCheckpoint> result) { main.post(() -> callback.onSuccess(result)); }
            @Override public void onError(String error) { main.post(() -> callback.onError(error)); }
        });
    }
}
