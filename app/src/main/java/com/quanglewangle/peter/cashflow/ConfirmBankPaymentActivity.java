package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.EntryEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Floating confirm dialog opened by tapping a detected Lloyds current-account
 *  payment notification (see BankPaymentListenerService). If the amount and
 *  date plausibly match exactly one already-planned cash entry, offers to
 *  mark that entry paid/received instead of creating a duplicate; otherwise
 *  falls back to adding a new one-off, pre-filled and already incurred since
 *  the notification means it's already happened. */
public class ConfirmBankPaymentActivity extends AppCompatActivity {
    static final String EXTRA_DESCRIPTION = "description";
    static final String EXTRA_AMOUNT = "amount";
    static final String EXTRA_ITEM_TYPE = "item_type"; // "income" | "expense"
    static final String EXTRA_DATE = "date"; // "yyyy-MM-dd"
    static final String EXTRA_NOTIF_ID = "notif_id";

    private static final String[] ITEM_TYPES = {"income", "expense", "savings"};
    // A same-day match is the common case; a few days' slack covers a
    // notification landing a little ahead of or behind the planned due day.
    private static final int DUE_DAY_SLACK = 3;

    private Repository repo;
    private List<CategoryEntity> categories = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = Repository.getInstance(this);

        String description = getIntent().getStringExtra(EXTRA_DESCRIPTION);
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        String itemType = getIntent().getStringExtra(EXTRA_ITEM_TYPE);
        String dateIso = getIntent().getStringExtra(EXTRA_DATE);
        int notifId = getIntent().getIntExtra(EXTRA_NOTIF_ID, -1);
        if (notifId != -1) NotificationManagerCompat.from(this).cancel(notifId);

        if (description == null || itemType == null || dateIso == null || !dateIso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            finish();
            return;
        }

        int year = Integer.parseInt(dateIso.substring(0, 4));
        int month = Integer.parseInt(dateIso.substring(5, 7));
        int day = Integer.parseInt(dateIso.substring(8, 10));

        repo.getCategories((cats, fromCache) -> categories = cats);

        // Repository.loadPeriod posts back twice (cache, then a fresh fetch) --
        // act on whichever arrives first, same as GooglePayListenerService's
        // credit-card lookup, so this still works offline instead of hanging
        // if the fresh fetch never lands.
        AtomicBoolean handled = new AtomicBoolean(false);
        repo.loadPeriod(year, month, (entries, fromCache) -> {
            if (!handled.compareAndSet(false, true)) return;
            EntryEntity match = findMatch(entries, itemType, amount, day);
            if (match != null) {
                showMatchDialog(match, description, amount, itemType, dateIso, year, month);
            } else {
                showAddOneOffDialog(description, amount, itemType, dateIso, year, month);
            }
        });
    }

    /** The one planned, cash (not card-tagged) entry of the matching type whose
     *  amount is within a penny and whose due day is within DUE_DAY_SLACK of
     *  the notification's date -- null if none or more than one plausibly fit,
     *  since guessing wrong is worse than just falling back to a manual add. */
    @Nullable
    private EntryEntity findMatch(List<EntryEntity> entries, String itemType, double amount, int day) {
        EntryEntity found = null;
        for (EntryEntity e : entries) {
            if (e.creditCardId != null) continue;
            if (!"planned".equals(e.status)) continue;
            if (!itemType.equals(e.itemType)) continue;
            if (Math.abs(e.plannedAmount - amount) > 0.005) continue;
            if (e.dueDay == null || Math.abs(e.dueDay - day) > DUE_DAY_SLACK) continue;
            if (found != null) return null; // ambiguous
            found = e;
        }
        return found;
    }

    private void showMatchDialog(EntryEntity match, String description, double amount, String itemType,
                                  String dateIso, int year, int month) {
        boolean isIncome = "income".equals(itemType);
        new AlertDialog.Builder(this)
                .setTitle(isIncome ? "Payment received" : "Payment sent")
                .setMessage(description + " — £" + String.format(Locale.UK, "%.2f", amount) +
                        "\n\nThis looks like it might be \"" + match.name + "\" (planned £" +
                        String.format(Locale.UK, "%.2f", match.plannedAmount) +
                        (match.dueDay != null ? ", due " + Util.ordinal(match.dueDay) : "") + ").")
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .setNeutralButton("Add as new one-off instead", (d, w) ->
                        showAddOneOffDialog(description, amount, itemType, dateIso, year, month))
                .setPositiveButton(isIncome ? "Mark received" : "Mark paid", (d, w) -> {
                    match.actualAmount = amount;
                    match.status = "incurred";
                    repo.updateEntry(match,
                            () -> {
                                Toast.makeText(this, "Marked " + match.name + " as " +
                                        (isIncome ? "received" : "paid"), Toast.LENGTH_SHORT).show();
                                finish();
                            },
                            err -> {
                                Toast.makeText(this, "Failed to update: " + err, Toast.LENGTH_LONG).show();
                                finish();
                            });
                })
                .show();
    }

    private void showAddOneOffDialog(String description, double amount, String itemType, String dateIso,
                                      int year, int month) {
        if (categories.isEmpty()) {
            Toast.makeText(this, "Still loading categories, try again in a moment", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        View formView = LayoutInflater.from(this).inflate(R.layout.dialog_add_entry, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        Spinner spinnerItemType = formView.findViewById(R.id.spinnerItemType);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDueDay = formView.findViewById(R.id.inputDueDay);
        Spinner spinnerCreditCard = formView.findViewById(R.id.spinnerCreditCard);

        List<CategoryEntity> grouped = Util.groupCategoriesByParent(categories);
        List<String> catNames = new ArrayList<>();
        for (CategoryEntity c : grouped) catNames.add(Util.categoryLabel(c));
        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catNames));
        spinnerItemType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ITEM_TYPES));
        spinnerItemType.setSelection("income".equals(itemType) ? 0 : 1);
        // This came from the current account, not a card -- leave the optional
        // card field as (none) and don't offer a choice.
        spinnerCreditCard.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"(none)"}));

        inputName.setText(description);
        inputAmount.setText(String.format(Locale.UK, "%.2f", amount));
        int day = Integer.parseInt(dateIso.substring(8, 10));
        inputDueDay.setText(String.valueOf(day));

        new AlertDialog.Builder(this)
                .setTitle("Add one-off entry")
                .setView(formView)
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .setPositiveButton("Add", (d, w) -> {
                    String name = inputName.getText().toString().trim();
                    Double amt = parseDoubleOrNull(inputAmount.getText().toString());
                    if (name.isEmpty() || amt == null) {
                        Toast.makeText(this, "Description and amount are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    EntryEntity entry = new EntryEntity();
                    entry.recurringItemId = null;
                    entry.categoryId = grouped.get(spinnerCategory.getSelectedItemPosition()).id;
                    entry.periodYear = year;
                    entry.periodMonth = month;
                    entry.name = name;
                    entry.itemType = ITEM_TYPES[spinnerItemType.getSelectedItemPosition()];
                    entry.plannedAmount = amt;
                    entry.actualAmount = amt;
                    entry.status = "incurred";
                    entry.dueDay = parseIntOrNull(inputDueDay.getText().toString());
                    entry.creditCardId = null;
                    repo.addEntry(entry,
                            () -> { Toast.makeText(this, "Added " + name, Toast.LENGTH_SHORT).show(); finish(); },
                            err -> { Toast.makeText(this, "Failed to add: " + err, Toast.LENGTH_LONG).show(); finish(); });
                })
                .show();
    }

    @Nullable
    private Double parseDoubleOrNull(String s) {
        try {
            return s.trim().isEmpty() ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Integer parseIntOrNull(String s) {
        try {
            return s.trim().isEmpty() ? null : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
