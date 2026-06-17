package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.RecurringCardPurchase;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Manage credit cards: name + statement closing day + payment due day. */
public class CardsFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private CreditCardAdapter adapter;
    private Repository repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = Repository.getInstance(requireContext());

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Logging a purchase is the everyday action; editing a card's own
        // parameters (name/dates) is rare, so it's tucked behind the edit icon.
        adapter = new CreditCardAdapter(new ArrayList<>(), this::showAddPurchaseDialog,
                this::showSubscriptionsDialog, this::showEditDialog);
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.fabAdd).setOnClickListener(v -> showEditDialog(null));
        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
    }

    private void loadAll() {
        swipeRefresh.setRefreshing(true);
        repo.getCreditCards((cards, fromCache) -> {
            adapter.setItems(cards);
            if (!fromCache) swipeRefresh.setRefreshing(false);
        });
    }

    private void showEditDialog(@Nullable CreditCardEntity existing) {
        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_credit_card, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        EditText inputStatementDay = formView.findViewById(R.id.inputStatementDay);
        EditText inputDueDay = formView.findViewById(R.id.inputDueDay);
        EditText inputDueMonthOffset = formView.findViewById(R.id.inputDueMonthOffset);

        if (existing != null) {
            inputName.setText(existing.name);
            inputStatementDay.setText(String.valueOf(existing.statementDay));
            inputDueDay.setText(String.valueOf(existing.paymentDueDay));
            inputDueMonthOffset.setText(String.valueOf(existing.paymentDueMonthOffset));
        } else {
            inputDueMonthOffset.setText("1");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add card" : "Edit card")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    Integer statementDay = parseDay(inputStatementDay.getText().toString());
                    Integer dueDay = parseDay(inputDueDay.getText().toString());
                    Integer dueMonthOffset = parseOffset(inputDueMonthOffset.getText().toString());
                    if (name.isEmpty() || statementDay == null || dueDay == null || dueMonthOffset == null) {
                        Toast.makeText(getContext(), "Name, both days, and the month offset are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CreditCardEntity card = existing != null ? existing : new CreditCardEntity();
                    card.name = name;
                    card.statementDay = statementDay;
                    card.paymentDueDay = dueDay;
                    card.paymentDueMonthOffset = dueMonthOffset;

                    if (existing == null) {
                        repo.addCreditCard(card, this::loadAll, this::showError);
                    } else {
                        repo.updateCreditCard(card, this::loadAll, this::showError);
                    }
                })
                .show();
    }

    /** Logs a real purchase against a card; the server works out which payment
     *  period (which month's bill) it lands on from the statement/due dates. */
    private void showAddPurchaseDialog(CreditCardEntity card) {
        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_card_purchase, null);
        EditText inputDescription = formView.findViewById(R.id.inputDescription);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDate = formView.findViewById(R.id.inputDate);
        inputDate.setText(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK).format(new java.util.Date()));

        new AlertDialog.Builder(requireContext())
                .setTitle("Log purchase on " + card.name)
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String description = inputDescription.getText().toString().trim();
                    Double amount = parseDoubleOrNull(inputAmount.getText().toString());
                    String date = inputDate.getText().toString().trim();
                    if (description.isEmpty() || amount == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Toast.makeText(getContext(), "Description, amount, and a YYYY-MM-DD date are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int[] period = paymentPeriodFor(card, date);
                    int payYear = period[0], payMonth = period[1];
                    repo.addCardPurchase(card.id, description, amount, date, () -> {
                        // Refresh the payment month's entries so the updated amount is cached
                        repo.loadPeriod(payYear, payMonth, (entries, fromCache) -> {});
                        String monthName = new DateFormatSymbols(Locale.UK).getMonths()[payMonth - 1];
                        if (getContext() != null)
                            Toast.makeText(getContext(),
                                    "Logged — " + card.name + " " + monthName + " payment updated",
                                    Toast.LENGTH_LONG).show();
                    }, this::showError);
                })
                .show();
    }

    @Nullable
    private Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showSubscriptionsDialog(CreditCardEntity card) {
        repo.getRecurringCardPurchases(card.id, (subs, fromCache) -> {
            if (getContext() == null) return;
            String[] items = new String[subs.size()];
            for (int i = 0; i < subs.size(); i++) {
                RecurringCardPurchase s = subs.get(i);
                items[i] = s.description + " — £" + String.format(Locale.UK, "%.2f", s.amount)
                        + " (" + Util.ordinal(s.dayOfMonth) + " each month)";
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(card.name + " subscriptions")
                    .setItems(items.length > 0 ? items : new String[]{"No subscriptions yet"},
                            (d, which) -> {
                                if (subs.isEmpty()) return;
                                RecurringCardPurchase sub = subs.get(which);
                                new AlertDialog.Builder(requireContext())
                                        .setTitle("Delete subscription?")
                                        .setMessage(sub.description + " — £" + String.format(Locale.UK, "%.2f", sub.amount))
                                        .setPositiveButton("Delete", (d2, w2) ->
                                                repo.deleteRecurringCardPurchase(sub.id,
                                                        () -> showSubscriptionsDialog(card),
                                                        this::showError))
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            })
                    .setPositiveButton("Add subscription", (d, w) -> showAddSubscriptionDialog(card))
                    .setNegativeButton("Close", null)
                    .show();
        });
    }

    private void showAddSubscriptionDialog(CreditCardEntity card) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad * 2, pad, pad * 2, 0);

        EditText inputDescription = new EditText(getContext());
        inputDescription.setHint("Description (e.g. Netflix)");
        inputDescription.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(inputDescription);

        EditText inputAmount = new EditText(getContext());
        inputAmount.setHint("Amount");
        inputAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        inputAmount.setLayoutParams(lp);
        layout.addView(inputAmount);

        EditText inputDay = new EditText(getContext());
        inputDay.setHint("Day of month (1–31)");
        inputDay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputDay.setLayoutParams(lp);
        layout.addView(inputDay);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add subscription to " + card.name)
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    String desc = inputDescription.getText().toString().trim();
                    Double amount = parseDoubleOrNull(inputAmount.getText().toString());
                    Integer day = parseDay(inputDay.getText().toString());
                    if (desc.isEmpty() || amount == null || day == null) {
                        Toast.makeText(getContext(), "Description, amount, and day are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repo.addRecurringCardPurchase(card.id, desc, amount, day,
                            () -> showSubscriptionsDialog(card), this::showError);
                })
                .show();
    }

    private void showError(String error) {
        if (getContext() != null) Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
    }

    @Nullable
    private Integer parseDay(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return (v >= 1 && v <= 31) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Integer parseOffset(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return (v >= 1 && v <= 3) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Mirrors the Go backend's paymentPeriodFor: returns {year, month} of the bill payment. */
    private int[] paymentPeriodFor(CreditCardEntity card, String dateIso) {
        int year, month, day;
        try {
            year  = Integer.parseInt(dateIso.substring(0, 4));
            month = Integer.parseInt(dateIso.substring(5, 7));
            day   = Integer.parseInt(dateIso.substring(8, 10));
        } catch (Exception e) {
            java.util.Calendar now = java.util.Calendar.getInstance();
            year  = now.get(java.util.Calendar.YEAR);
            month = now.get(java.util.Calendar.MONTH) + 1;
            day   = now.get(java.util.Calendar.DAY_OF_MONTH);
        }
        if (day > card.statementDay) {
            month++; if (month > 12) { month = 1; year++; }
        }
        for (int i = 0; i < card.paymentDueMonthOffset; i++) {
            month++; if (month > 12) { month = 1; year++; }
        }
        return new int[]{year, month};
    }
}
