package com.quanglewangle.peter.cashflow.api;

import com.quanglewangle.peter.cashflow.data.BalanceCheckpoint;
import com.quanglewangle.peter.cashflow.data.CardPurchase;
import com.quanglewangle.peter.cashflow.data.RecurringCardPurchase;
import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.EntryEntity;
import com.quanglewangle.peter.cashflow.data.ForecastSummary;
import com.quanglewangle.peter.cashflow.data.RecurringItemEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * REST client for the cashflow backend on fimblefowl.co.uk.
 * Same shape as rocloc's ApiService: plain OkHttp + org.json, callback-based.
 */
public class ApiService {

    private static final String BASE_URL = "https://fimblefowl.co.uk/cashflow/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    /** Optional -- set if the server is deployed with CASHFLOW_WRITE_TOKEN configured. */
    private String writeToken;

    public void setWriteToken(String token) {
        this.writeToken = token;
    }

    private Request.Builder authed(Request.Builder b) {
        if (writeToken != null && !writeToken.isEmpty()) {
            b.header("X-Write-Token", writeToken);
        }
        return b;
    }

    // ---- generic helpers ----

    private void enqueue(Request request, Callback<JSONObject> callback) {
        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject obj = new JSONObject(body.isEmpty() ? "{}" : body);
                    if (!response.isSuccessful()) {
                        callback.onError(obj.optString("error", "HTTP " + response.code()));
                        return;
                    }
                    callback.onSuccess(obj);
                } catch (Exception ex) {
                    callback.onError("Parse error: " + ex.getMessage());
                }
            }
        });
    }

    private void enqueueArray(Request request, Callback<JSONArray> callback) {
        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError("Network: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body() != null ? response.body().string() : "[]";
                    if (!response.isSuccessful()) {
                        try {
                            callback.onError(new JSONObject(body).optString("error", "HTTP " + response.code()));
                        } catch (Exception ignored) {
                            callback.onError("HTTP " + response.code());
                        }
                        return;
                    }
                    callback.onSuccess(new JSONArray(body));
                } catch (Exception ex) {
                    callback.onError("Parse error: " + ex.getMessage());
                }
            }
        });
    }

    private RequestBody jsonBody(JSONObject obj) {
        return RequestBody.create(obj.toString(), JSON);
    }

    /** JSONObject.put declares JSONException, but it only ever fails on non-finite
     *  numbers, which none of our fields are -- so swallow it as unchecked here. */
    private static JSONObject set(JSONObject obj, String key, Object value) {
        try {
            obj.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return obj;
    }

    // ---- categories ----

    public void getCategories(Callback<List<CategoryEntity>> callback) {
        Request request = new Request.Builder().url(BASE_URL + "categories").build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<CategoryEntity> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseCategory(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    public void addCategory(String name, String itemType, int sortOrder, Callback<Long> callback) {
        JSONObject body = new JSONObject();
        set(body, "name", name);
        set(body, "item_type", itemType);
        set(body, "sort_order", sortOrder);
        Request request = authed(new Request.Builder().url(BASE_URL + "categories").post(jsonBody(body))).build();
        enqueue(request, idCallback(callback));
    }

    private CategoryEntity parseCategory(JSONObject o) {
        CategoryEntity c = new CategoryEntity();
        c.id = o.optLong("id");
        c.name = o.optString("name");
        c.itemType = o.optString("item_type");
        c.sortOrder = o.optInt("sort_order");
        return c;
    }

    // ---- credit cards ----

    public void getCreditCards(Callback<List<CreditCardEntity>> callback) {
        Request request = new Request.Builder().url(BASE_URL + "credit-cards").build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<CreditCardEntity> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseCard(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    public void addCreditCard(CreditCardEntity card, Callback<Long> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "credit-cards").post(jsonBody(toJson(card)))).build();
        enqueue(request, idCallback(callback));
    }

    public void updateCreditCard(CreditCardEntity card, Callback<Void> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "credit-cards/" + card.id).put(jsonBody(toJson(card)))).build();
        enqueue(request, voidCallback(callback));
    }

    private JSONObject toJson(CreditCardEntity card) {
        JSONObject body = new JSONObject();
        set(body, "name", card.name);
        set(body, "statement_day", card.statementDay);
        set(body, "payment_due_day", card.paymentDueDay);
        set(body, "payment_due_month_offset", card.paymentDueMonthOffset);
        return body;
    }

    private CreditCardEntity parseCard(JSONObject o) {
        CreditCardEntity c = new CreditCardEntity();
        c.id = o.optLong("id");
        c.name = o.optString("name");
        c.statementDay = o.optInt("statement_day");
        c.paymentDueDay = o.optInt("payment_due_day");
        c.paymentDueMonthOffset = o.optInt("payment_due_month_offset", 1);
        return c;
    }

    public void getCardPurchasesByMonth(int year, int month, Callback<List<CardPurchase>> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "card-purchases?year=" + year + "&month=" + month)
                .build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<CardPurchase> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseCardPurchase(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    private CardPurchase parseCardPurchase(JSONObject o) {
        CardPurchase p = new CardPurchase();
        p.id = o.optLong("id");
        p.creditCardId = o.optLong("credit_card_id");
        p.description = o.optString("description");
        p.amount = o.optDouble("amount", 0);
        p.purchaseDate = o.optString("purchase_date");
        p.recurringPurchaseId = o.isNull("recurring_purchase_id") ? null : o.optLong("recurring_purchase_id");
        p.categoryId = o.isNull("category_id") ? null : o.optLong("category_id");
        return p;
    }

    public void deleteRecurringCardPurchase(long id, Callback<Void> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "recurring-card-purchases/" + id).delete()).build();
        enqueue(request, voidCallback(callback));
    }

    public void getRecurringCardPurchases(long creditCardId, Callback<List<RecurringCardPurchase>> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "recurring-card-purchases?credit_card_id=" + creditCardId)
                .build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<RecurringCardPurchase> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseRecurringCardPurchase(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    public void addRecurringCardPurchase(long creditCardId, String description, double amount,
                                          int dayOfMonth, Callback<Long> callback) {
        JSONObject body = new JSONObject();
        set(body, "credit_card_id", creditCardId);
        set(body, "description", description);
        set(body, "amount", amount);
        set(body, "frequency", "monthly");
        set(body, "day_of_month", dayOfMonth);
        Request request = authed(new Request.Builder().url(BASE_URL + "recurring-card-purchases").post(jsonBody(body))).build();
        enqueue(request, idCallback(callback));
    }

    private RecurringCardPurchase parseRecurringCardPurchase(JSONObject o) {
        RecurringCardPurchase r = new RecurringCardPurchase();
        r.id = o.optLong("id");
        r.creditCardId = o.optLong("credit_card_id");
        r.description = o.optString("description");
        r.amount = o.optDouble("amount", 0);
        r.frequency = o.optString("frequency", "monthly");
        r.dayOfMonth = o.optInt("day_of_month", 1);
        r.targetMonth = o.isNull("target_month") ? null : o.optInt("target_month");
        r.active = o.optBoolean("active", true);
        return r;
    }

    public void updateCardPurchase(long id, String description, double amount, String purchaseDateIso, Long categoryId, Callback<Void> callback) {
        JSONObject body = new JSONObject();
        set(body, "description", description);
        set(body, "amount", amount);
        set(body, "purchase_date", purchaseDateIso);
        if (categoryId != null) set(body, "category_id", categoryId);
        Request request = authed(new Request.Builder().url(BASE_URL + "card-purchases/" + id).put(jsonBody(body))).build();
        enqueue(request, voidCallback(callback));
    }

    public void deleteCardPurchase(long id, Callback<Void> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "card-purchases/" + id).delete()).build();
        enqueue(request, voidCallback(callback));
    }

    /** Logs a real purchase against a card; the server recalculates that purchase's
     *  payment-period entry from the running total of everything logged for it. */
    public void addCardPurchase(long creditCardId, String description, double amount, String purchaseDateIso, Long categoryId, Callback<Long> callback) {
        JSONObject body = new JSONObject();
        set(body, "credit_card_id", creditCardId);
        set(body, "description", description);
        set(body, "amount", amount);
        set(body, "purchase_date", purchaseDateIso);
        if (categoryId != null) set(body, "category_id", categoryId);
        Request request = authed(new Request.Builder().url(BASE_URL + "card-purchases").post(jsonBody(body))).build();
        enqueue(request, idCallback(callback));
    }

    // ---- recurring items ----

    public void getRecurringItems(Callback<List<RecurringItemEntity>> callback) {
        Request request = new Request.Builder().url(BASE_URL + "recurring-items").build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<RecurringItemEntity> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseRecurringItem(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    public void addRecurringItem(RecurringItemEntity item, Callback<Long> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "recurring-items").post(jsonBody(toJson(item)))).build();
        enqueue(request, idCallback(callback));
    }

    public void updateRecurringItem(RecurringItemEntity item, Callback<Void> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "recurring-items/" + item.id).put(jsonBody(toJson(item)))).build();
        enqueue(request, voidCallback(callback));
    }

    public void deleteRecurringItem(long id, Callback<Void> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "recurring-items/" + id).delete()).build();
        enqueue(request, voidCallback(callback));
    }

    private JSONObject toJson(RecurringItemEntity item) {
        JSONObject body = new JSONObject();
        set(body, "category_id", item.categoryId);
        set(body, "name", item.name);
        set(body, "item_type", item.itemType);
        set(body, "frequency", item.frequency);
        set(body, "default_amount", item.defaultAmount);
        set(body, "due_day", item.dueDay);
        set(body, "target_month", item.targetMonth);
        set(body, "anchor_date", item.anchorDate);
        set(body, "credit_card_id", item.creditCardId);
        set(body, "active", item.active);
        set(body, "notes", item.notes);
        return body;
    }

    private RecurringItemEntity parseRecurringItem(JSONObject o) {
        RecurringItemEntity r = new RecurringItemEntity();
        r.id = o.optLong("id");
        r.categoryId = o.optLong("category_id");
        r.name = o.optString("name");
        r.itemType = o.optString("item_type");
        r.frequency = o.optString("frequency");
        r.defaultAmount = o.isNull("default_amount") ? null : o.optDouble("default_amount");
        r.dueDay = o.isNull("due_day") ? null : o.optInt("due_day");
        r.targetMonth = o.isNull("target_month") ? null : o.optInt("target_month");
        r.creditCardId = o.isNull("credit_card_id") ? null : o.optLong("credit_card_id");
        r.active = o.optBoolean("active", true);
        r.notes = o.isNull("notes") ? null : o.optString("notes");
        r.anchorDate = o.isNull("anchor_date") ? null : o.optString("anchor_date");
        return r;
    }

    // ---- entries ----

    public void getEntries(int year, int month, Callback<List<EntryEntity>> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "entries?year=" + year + "&month=" + month)
                .build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<EntryEntity> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseEntry(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    public void addEntry(EntryEntity entry, Callback<Long> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "entries").post(jsonBody(toJson(entry)))).build();
        enqueue(request, idCallback(callback));
    }

    public void updateEntry(EntryEntity entry, Callback<Void> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "entries/" + entry.id).put(jsonBody(toJson(entry)))).build();
        enqueue(request, voidCallback(callback));
    }

    public void deleteEntry(long id, Callback<Void> callback) {
        Request request = authed(new Request.Builder().url(BASE_URL + "entries/" + id).delete()).build();
        enqueue(request, voidCallback(callback));
    }

    private JSONObject toJson(EntryEntity e) {
        JSONObject body = new JSONObject();
        set(body, "recurring_item_id", e.recurringItemId);
        set(body, "category_id", e.categoryId);
        set(body, "period_year", e.periodYear);
        set(body, "period_month", e.periodMonth);
        set(body, "name", e.name);
        set(body, "item_type", e.itemType);
        set(body, "planned_amount", e.plannedAmount);
        set(body, "actual_amount", e.actualAmount);
        set(body, "status", e.status);
        set(body, "credit_card_id", e.creditCardId);
        set(body, "due_day", e.dueDay);
        return body;
    }

    private EntryEntity parseEntry(JSONObject o) {
        EntryEntity e = new EntryEntity();
        e.id = o.optLong("id");
        e.recurringItemId = o.isNull("recurring_item_id") ? null : o.optLong("recurring_item_id");
        e.categoryId = o.optLong("category_id");
        e.periodYear = o.optInt("period_year");
        e.periodMonth = o.optInt("period_month");
        e.name = o.optString("name");
        e.itemType = o.optString("item_type");
        e.plannedAmount = o.optDouble("planned_amount", 0);
        e.actualAmount = o.isNull("actual_amount") ? null : o.optDouble("actual_amount");
        e.status = o.optString("status", "planned");
        e.creditCardId = o.isNull("credit_card_id") ? null : o.optLong("credit_card_id");
        e.dueDay = o.isNull("due_day") ? null : o.optInt("due_day");
        return e;
    }

    // ---- periods / forecast ----

    public void generatePeriod(int year, int month, Callback<Integer> callback) {
        Request request = authed(new Request.Builder()
                .url(BASE_URL + "periods/generate?year=" + year + "&month=" + month)
                .post(RequestBody.create(new byte[0], null))).build();
        enqueue(request, new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject obj) { callback.onSuccess(obj.optInt("created", 0)); }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    public void getForecastRange(int year, int month, int count, Callback<List<ForecastSummary>> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "forecast/range?year=" + year + "&month=" + month + "&count=" + count)
                .build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<ForecastSummary> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseForecast(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    private ForecastSummary parseForecast(JSONObject o) {
        ForecastSummary f = new ForecastSummary();
        f.periodYear = o.optInt("period_year");
        f.periodMonth = o.optInt("period_month");
        f.broughtForward = o.optDouble("brought_forward", 0);
        f.income = o.optDouble("income", 0);
        f.expense = o.optDouble("expense", 0);
        f.savings = o.optDouble("savings", 0);
        f.carriedForward = o.optDouble("carried_forward", 0);
        return f;
    }

    public void getForecastDanger(int year, int month, int count, Callback<List<com.quanglewangle.peter.cashflow.data.ForecastDanger>> callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "forecast/danger?year=" + year + "&month=" + month + "&count=" + count)
                .build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<com.quanglewangle.peter.cashflow.data.ForecastDanger> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    com.quanglewangle.peter.cashflow.data.ForecastDanger d = new com.quanglewangle.peter.cashflow.data.ForecastDanger();
                    d.periodYear = o.optInt("period_year");
                    d.periodMonth = o.optInt("period_month");
                    d.broughtForward = o.optDouble("brought_forward", 0);
                    d.minBalance = o.optDouble("min_balance", 0);
                    d.minBalanceDay = o.optInt("min_balance_day");
                    d.carriedForward = o.optDouble("carried_forward", 0);
                    out.add(d);
                }
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    // ---- balance checkpoints ----
    // Re-anchors the forecast to a real bank balance, same habit as
    // hand-correcting "brought forward" in the spreadsheet -- add a new
    // checkpoint any time instead of trusting one fixed opening balance
    // to stay accurate forever.

    public void addCheckpoint(int periodYear, int periodMonth, int periodDay, double balance, Callback<Long> callback) {
        JSONObject body = new JSONObject();
        set(body, "period_year", periodYear);
        set(body, "period_month", periodMonth);
        set(body, "period_day", periodDay);
        set(body, "balance", balance);
        Request request = authed(new Request.Builder().url(BASE_URL + "checkpoints").post(jsonBody(body))).build();
        enqueue(request, idCallback(callback));
    }

    public void getCheckpoints(Callback<List<BalanceCheckpoint>> callback) {
        Request request = new Request.Builder().url(BASE_URL + "checkpoints").build();
        enqueueArray(request, new Callback<JSONArray>() {
            @Override public void onSuccess(JSONArray arr) {
                List<BalanceCheckpoint> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(parseCheckpoint(arr.optJSONObject(i)));
                callback.onSuccess(out);
            }
            @Override public void onError(String error) { callback.onError(error); }
        });
    }

    private BalanceCheckpoint parseCheckpoint(JSONObject o) {
        BalanceCheckpoint c = new BalanceCheckpoint();
        c.id = o.optLong("id");
        c.periodYear = o.optInt("period_year");
        c.periodMonth = o.optInt("period_month");
        c.periodDay = o.optInt("period_day", 1);
        c.balance = o.optDouble("balance", 0);
        return c;
    }

    // ---- shared callback adapters ----

    private Callback<JSONObject> idCallback(Callback<Long> callback) {
        return new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject obj) { callback.onSuccess(obj.optLong("id")); }
            @Override public void onError(String error) { callback.onError(error); }
        };
    }

    private Callback<JSONObject> voidCallback(Callback<Void> callback) {
        return new Callback<JSONObject>() {
            @Override public void onSuccess(JSONObject obj) { callback.onSuccess(null); }
            @Override public void onError(String error) { callback.onError(error); }
        };
    }
}
