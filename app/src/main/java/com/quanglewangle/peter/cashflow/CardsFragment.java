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

import com.quanglewangle.peter.cashflow.data.CardCheckpoint;
import com.quanglewangle.peter.cashflow.data.CardPurchase;
import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.EntryEntity;
import com.quanglewangle.peter.cashflow.data.RecurringCardPurchase;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
                this::showPurchasesDialog, this::showSubscriptionsDialog,
                this::showCheckpointsDialog, this::showEditDialog);
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
        boolean[] fired = {false};
        repo.getCategories((cats, fromCache) -> {
            if (fired[0]) return;
            if (fromCache && cats.isEmpty()) return;
            fired[0] = true;

            List<CategoryEntity> expenseCats = new ArrayList<>();
            for (CategoryEntity c : cats) if ("expense".equals(c.itemType)) expenseCats.add(c);
            List<CategoryEntity> groupedExpenseCats = Util.groupCategoriesByParent(expenseCats);
            List<String> catNames = new ArrayList<>();
            List<Long> catIds = new ArrayList<>();
            catNames.add("No category");
            catIds.add(null);
            for (CategoryEntity c : groupedExpenseCats) {
                catNames.add(Util.categoryLabel(c));
                catIds.add(c.id);
            }

            View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_card_purchase, null);
            EditText inputDescription = formView.findViewById(R.id.inputDescription);
            EditText inputAmount = formView.findViewById(R.id.inputAmount);
            EditText inputDate = formView.findViewById(R.id.inputDate);
            android.widget.Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
            inputDate.setText(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK).format(new java.util.Date()));

            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, catNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategory.setAdapter(adapter);

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
                        Long categoryId = catIds.get(spinnerCategory.getSelectedItemPosition());
                        int[] period = paymentPeriodFor(card, date);
                        int payYear = period[0], payMonth = period[1];
                        repo.addCardPurchase(card.id, description, amount, date, categoryId, () -> {
                            repo.loadPeriod(payYear, payMonth, (entries, fromCache2) -> {});
                            String monthName = new DateFormatSymbols(Locale.UK).getMonths()[payMonth - 1];
                            if (getContext() != null)
                                Toast.makeText(getContext(),
                                        "Logged — " + card.name + " " + monthName + " payment updated",
                                        Toast.LENGTH_LONG).show();
                        }, this::showError);
                    })
                    .show();
        });
    }

    private void showPurchasesDialog(CreditCardEntity card) {
        boolean[] fired = {false};
        repo.getCategories((cats, fromCache) -> {
            if (fired[0]) return;
            if (fromCache && cats.isEmpty()) return;
            fired[0] = true;
            Map<Long, String> catNames = new HashMap<>();
            for (CategoryEntity c : cats) catNames.put(c.id, c.name);
            showPurchasesDialogWithCategories(card, catNames);
        });
    }

    private void showPurchasesDialogWithCategories(CreditCardEntity card, Map<Long, String> catNames) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int[] yr = {cal.get(java.util.Calendar.YEAR)};
        int[] mo = {cal.get(java.util.Calendar.MONTH) + 1};

        int padPx = (int) (16 * getResources().getDisplayMetrics().density);
        String[] monthNames = new java.text.DateFormatSymbols(Locale.UK).getMonths();

        android.widget.LinearLayout root = new android.widget.LinearLayout(getContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.LinearLayout navRow = new android.widget.LinearLayout(getContext());
        navRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        navRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        navRow.setPadding(padPx, padPx / 2, padPx, padPx / 2);

        android.widget.Button prevBtn = new android.widget.Button(getContext());
        prevBtn.setText("<");
        android.widget.TextView monthLabel = new android.widget.TextView(getContext());
        monthLabel.setGravity(android.view.Gravity.CENTER);
        monthLabel.setTextSize(16);
        android.widget.LinearLayout.LayoutParams expandLP = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        monthLabel.setLayoutParams(expandLP);
        android.widget.Button nextBtn = new android.widget.Button(getContext());
        nextBtn.setText(">");

        navRow.addView(prevBtn);
        navRow.addView(monthLabel);
        navRow.addView(nextBtn);
        root.addView(navRow);

        int maxScrollPx = (int) (280 * getResources().getDisplayMetrics().density);
        android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
        android.widget.LinearLayout listContainer = new android.widget.LinearLayout(getContext());
        listContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        root.addView(scrollView, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, maxScrollPx));

        android.widget.TextView totalView = new android.widget.TextView(getContext());
        totalView.setPadding(padPx, padPx / 2, padPx, padPx / 2);
        totalView.setTextSize(15);
        root.addView(totalView);

        // Fetch all checkpoints for this card once; the load lambda reads from this.
        @SuppressWarnings("unchecked")
        List<CardCheckpoint>[] allCheckpoints = new List[]{new ArrayList<>()};
        repo.getCardCheckpoints(card.id, (cps, fromCache) -> allCheckpoints[0] = cps);

        AlertDialog[] dialog = {null};
        Runnable[] load = {null};
        load[0] = () -> {
            monthLabel.setText(monthNames[mo[0] - 1] + " " + yr[0]);
            listContainer.removeAllViews();
            totalView.setText("Loading…");
            repo.getCardPurchasesByMonth(yr[0], mo[0], (all, fromCache) -> {
                if (getContext() == null) return;
                List<CardPurchase> filtered = new ArrayList<>();
                for (CardPurchase p : all) {
                    if (p.creditCardId == card.id) filtered.add(p);
                }
                filtered.sort((a, b) -> a.purchaseDate.compareTo(b.purchaseDate));

                // Checkpoints for this month, sorted by day.
                List<CardCheckpoint> monthCheckpoints = new ArrayList<>();
                for (CardCheckpoint cp : allCheckpoints[0]) {
                    if (cp.periodYear == yr[0] && cp.periodMonth == mo[0]) monthCheckpoints.add(cp);
                }
                monthCheckpoints.sort((a, b) -> Integer.compare(a.periodDay, b.periodDay));

                listContainer.removeAllViews();
                if (filtered.isEmpty() && monthCheckpoints.isEmpty()) {
                    android.widget.TextView empty = new android.widget.TextView(getContext());
                    empty.setText("No purchases this month");
                    empty.setPadding(padPx, padPx / 2, padPx, padPx / 2);
                    listContainer.addView(empty);
                    totalView.setText("");
                } else {
                    double total = 0;
                    int cpIdx = 0;
                    for (CardPurchase p : filtered) {
                        int purchaseDay = p.purchaseDate.length() >= 10
                                ? Integer.parseInt(p.purchaseDate.substring(8, 10)) : 0;
                        // Insert any checkpoints that fall on or before this purchase's day.
                        while (cpIdx < monthCheckpoints.size()
                                && monthCheckpoints.get(cpIdx).periodDay <= purchaseDay) {
                            listContainer.addView(makeCheckpointRow(monthCheckpoints.get(cpIdx), padPx));
                            cpIdx++;
                        }
                        total += p.amount;
                        android.widget.TextView row = new android.widget.TextView(getContext());
                        String dateStr = p.purchaseDate.length() >= 10
                                ? p.purchaseDate.substring(8, 10) + " "
                                  + monthNames[Integer.parseInt(p.purchaseDate.substring(5, 7)) - 1].substring(0, 3)
                                : "";
                        String catStr = p.categoryId != null ? catNames.get(p.categoryId) : null;
                        String catPart = catStr != null ? " · " + catStr : "";
                        row.setText(dateStr + "  " + p.description + catPart + "  £"
                                + String.format(Locale.UK, "%.2f", p.amount));
                        row.setPadding(padPx, padPx / 3, padPx, padPx / 3);
                        final CardPurchase fp = p;
                        row.setOnClickListener(v -> {
                            if (dialog[0] != null) dialog[0].dismiss();
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Delete purchase?")
                                    .setMessage(fp.description + " — £"
                                            + String.format(Locale.UK, "%.2f", fp.amount))
                                    .setPositiveButton("Delete", (d2, w2) ->
                                            repo.deleteCardPurchase(fp.id, () -> {
                                                showPurchasesDialogWithCategories(card, catNames);
                                            }, this::showError))
                                    .setNegativeButton("Cancel", (d2, w2) -> showPurchasesDialogWithCategories(card, catNames))
                                    .show();
                        });
                        listContainer.addView(row);
                    }
                    // Any remaining checkpoints after all purchases.
                    while (cpIdx < monthCheckpoints.size()) {
                        listContainer.addView(makeCheckpointRow(monthCheckpoints.get(cpIdx), padPx));
                        cpIdx++;
                    }
                    totalView.setText(filtered.isEmpty() ? "" : "Total: £" + String.format(Locale.UK, "%.2f", total));
                }
            });
        };

        prevBtn.setOnClickListener(v -> {
            mo[0]--; if (mo[0] < 1) { mo[0] = 12; yr[0]--; }
            load[0].run();
        });
        nextBtn.setOnClickListener(v -> {
            mo[0]++; if (mo[0] > 12) { mo[0] = 1; yr[0]++; }
            load[0].run();
        });

        load[0].run();

        dialog[0] = new AlertDialog.Builder(requireContext())
                .setTitle(card.name + " purchases")
                .setView(root)
                .setNegativeButton("Close", null)
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

    private android.view.View makeCheckpointRow(CardCheckpoint cp, int padPx) {
        android.widget.TextView v = new android.widget.TextView(getContext());
        String[] monthNames = new java.text.DateFormatSymbols(Locale.UK).getMonths();
        v.setText("▶  " + Util.ordinal(cp.periodDay) + " " + monthNames[cp.periodMonth - 1]
                + "  —  £" + String.format(Locale.UK, "%.2f", cp.balance));
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setTextColor(requireContext().getColor(R.color.colorPrimary));
        v.setPadding(padPx, padPx / 2, padPx, padPx / 2);
        v.setBackgroundColor(0x0F000080);
        return v;
    }

    private void showCheckpointsDialog(CreditCardEntity card) {
        repo.getCardCheckpoints(card.id, (checkpoints, fromCache) -> {
            if (getContext() == null) return;

            int padPx = (int) (16 * getResources().getDisplayMetrics().density);
            String[] monthNames = new java.text.DateFormatSymbols(Locale.UK).getMonths();

            android.widget.LinearLayout root = new android.widget.LinearLayout(getContext());
            root.setOrientation(android.widget.LinearLayout.VERTICAL);

            int maxScrollPx = (int) (240 * getResources().getDisplayMetrics().density);
            android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
            android.widget.LinearLayout listContainer = new android.widget.LinearLayout(getContext());
            listContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            scrollView.addView(listContainer);
            root.addView(scrollView, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, maxScrollPx));

            AlertDialog[] dialog = {null};
            Runnable refresh = () -> showCheckpointsDialog(card);

            if (checkpoints.isEmpty()) {
                android.widget.TextView empty = new android.widget.TextView(getContext());
                empty.setText("No checkpoints yet");
                empty.setPadding(padPx, padPx / 2, padPx, padPx / 2);
                listContainer.addView(empty);
            } else {
                for (CardCheckpoint cp : checkpoints) {
                    String label = Util.ordinal(cp.periodDay) + " "
                            + monthNames[cp.periodMonth - 1] + " " + cp.periodYear
                            + "  —  £" + String.format(Locale.UK, "%.2f", cp.balance);
                    android.widget.TextView row = new android.widget.TextView(getContext());
                    row.setText(label);
                    row.setPadding(padPx, padPx / 3, padPx, padPx / 3);
                    row.setOnClickListener(v -> {
                        if (dialog[0] != null) dialog[0].dismiss();
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Delete checkpoint?")
                                .setMessage(label)
                                .setPositiveButton("Delete", (d2, w2) ->
                                        repo.deleteCardCheckpoint(cp.id, refresh, this::showError))
                                .setNegativeButton("Cancel", (d2, w2) -> showCheckpointsDialog(card))
                                .show();
                    });
                    listContainer.addView(row);
                }
            }

            dialog[0] = new AlertDialog.Builder(requireContext())
                    .setTitle(card.name + " checkpoints")
                    .setView(root)
                    .setPositiveButton("Add checkpoint", (d, w) -> {
                        if (dialog[0] != null) dialog[0].dismiss();
                        showAddCheckpointDialog(card);
                    })
                    .setNegativeButton("Close", null)
                    .show();
        });
    }

    private void showAddCheckpointDialog(CreditCardEntity card) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad * 2, pad, pad * 2, 0);

        java.util.Calendar now = java.util.Calendar.getInstance();

        EditText inputYear = new EditText(getContext());
        inputYear.setHint("Year");
        inputYear.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputYear.setText(String.valueOf(now.get(java.util.Calendar.YEAR)));
        layout.addView(inputYear);

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;

        EditText inputMonth = new EditText(getContext());
        inputMonth.setHint("Month (1–12)");
        inputMonth.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputMonth.setText(String.valueOf(now.get(java.util.Calendar.MONTH) + 1));
        inputMonth.setLayoutParams(lp);
        layout.addView(inputMonth);

        EditText inputDay = new EditText(getContext());
        inputDay.setHint("Day (1–31)");
        inputDay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputDay.setText(String.valueOf(now.get(java.util.Calendar.DAY_OF_MONTH)));
        inputDay.setLayoutParams(lp);
        layout.addView(inputDay);

        EditText inputBalance = new EditText(getContext());
        inputBalance.setHint("Balance owed (£) — expressions allowed, e.g. 120+35-10");
        inputBalance.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputBalance.setLayoutParams(lp);
        layout.addView(inputBalance);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add checkpoint — " + card.name)
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    Integer year = parseIntOrNull(inputYear.getText().toString());
                    Integer month = parseDay(inputMonth.getText().toString());
                    Integer day = parseDay(inputDay.getText().toString());
                    Double balance = Util.evalExpression(inputBalance.getText().toString());
                    if (year == null || month == null || month < 1 || month > 12 || day == null || balance == null) {
                        Toast.makeText(getContext(), "A valid year, month (1–12), day, and balance/expression are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repo.addCardCheckpoint(card.id, year, month, day, balance,
                            new com.quanglewangle.peter.cashflow.api.ApiService.Callback<com.quanglewangle.peter.cashflow.data.AddCheckpointResult>() {
                                @Override public void onSuccess(com.quanglewangle.peter.cashflow.data.AddCheckpointResult result) {
                                    if (result.existingOneOffs.isEmpty()) {
                                        showCheckpointsDialog(card);
                                    } else {
                                        promptRemoveRedundantOneOffs(card, result.existingOneOffs);
                                    }
                                }
                                @Override public void onError(String error) { showError(error); }
                            });
                })
                .show();
    }

    // A fresh, verified checkpoint usually makes an existing card-tagged one-off
    // (e.g. a decaying sundries buffer) for the same payment period redundant --
    // prompt to remove it instead of it being silently forgotten and left to
    // double up on the checkpoint it's now covered by.
    private void promptRemoveRedundantOneOffs(CreditCardEntity card, List<EntryEntity> oneOffs) {
        String[] labels = new String[oneOffs.size()];
        boolean[] checked = new boolean[oneOffs.size()];
        for (int i = 0; i < oneOffs.size(); i++) {
            EntryEntity e = oneOffs.get(i);
            labels[i] = e.name + " — £" + String.format(Locale.UK, "%.2f", e.effectiveAmount);
            checked[i] = true;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove redundant entries?")
                .setMessage("This checkpoint likely already covers these — remove them?")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Remove selected", (dialog, which) -> {
                    for (int i = 0; i < oneOffs.size(); i++) {
                        if (checked[i]) repo.deleteEntry(oneOffs.get(i).id, () -> {}, this::showError);
                    }
                    showCheckpointsDialog(card);
                })
                .setNegativeButton("Keep all", (dialog, which) -> showCheckpointsDialog(card))
                .show();
    }

    @Nullable
    private Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
