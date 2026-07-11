package com.quanglewangle.peter.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.cashflow.api.ApiService;
import com.quanglewangle.peter.cashflow.data.BalanceCheckpoint;
import com.quanglewangle.peter.cashflow.data.CardPurchase;
import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.EntryEntity;
import com.quanglewangle.peter.cashflow.data.ForecastSummary;
import com.quanglewangle.peter.cashflow.data.RecurringItemEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Manage the recurring-item templates (monthly / annual / irregular) that generate entries. */
public class ItemsFragment extends Fragment {

    private static final String[] ITEM_TYPES = {"income", "expense", "savings"};
    private static final String[] FREQUENCIES = {"monthly", "three_monthly", "four_weekly", "last_working_day", "annual", "irregular"};

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private RecurringItemAdapter adapter;
    private Repository repo;
    private TextView monthLabel, broughtFwd, carriedFwd, nowBalance;
    private View nowBalanceSection;
    private int displayYear, displayMonth;
    private boolean needsScrollToToday = false;

    private List<CategoryEntity> categories = new ArrayList<>();
    private List<CreditCardEntity> creditCards = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_items, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_items_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_balances) {
            startActivity(new Intent(getContext(), BalanceCheckpointsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_categories) {
            showCategoriesDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = Repository.getInstance(requireContext());

        Calendar now = Calendar.getInstance();
        displayYear = now.get(Calendar.YEAR);
        displayMonth = now.get(Calendar.MONTH) + 1;
        needsScrollToToday = true;

        monthLabel = view.findViewById(R.id.monthLabel);
        broughtFwd = view.findViewById(R.id.broughtFwd);
        carriedFwd = view.findViewById(R.id.carriedFwd);
        nowBalance = view.findViewById(R.id.nowBalance);
        nowBalanceSection = view.findViewById(R.id.nowBalanceSection);
        Button btnPrev = view.findViewById(R.id.btnPrevMonth);
        Button btnNext = view.findViewById(R.id.btnNextMonth);
        updateMonthLabel();

        btnPrev.setOnClickListener(v -> {
            displayMonth--;
            if (displayMonth < 1) { displayMonth = 12; displayYear--; }
            updateMonthLabel();
            adapter.setMonth(displayYear, displayMonth);
            adapter.setOneOffEntries(new ArrayList<>());
            adapter.setEntryAmounts(new ArrayList<>());
            adapter.setCardPurchases(new ArrayList<>());
            needsScrollToToday = isCurrentMonthDisplayed();
            loadBalance();
            loadEntries();
        });
        btnNext.setOnClickListener(v -> {
            displayMonth++;
            if (displayMonth > 12) { displayMonth = 1; displayYear++; }
            updateMonthLabel();
            adapter.setMonth(displayYear, displayMonth);
            adapter.setOneOffEntries(new ArrayList<>());
            adapter.setEntryAmounts(new ArrayList<>());
            adapter.setCardPurchases(new ArrayList<>());
            needsScrollToToday = isCurrentMonthDisplayed();
            loadBalance();
            loadEntries();
        });

        view.findViewById(R.id.balanceSection).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), EntriesActivity.class);
            intent.putExtra(EntriesActivity.EXTRA_YEAR, displayYear);
            intent.putExtra(EntriesActivity.EXTRA_MONTH, displayMonth);
            startActivity(intent);
        });
        view.findViewById(R.id.fabAdd).setOnClickListener(v -> showEditDialog(null));
        view.findViewById(R.id.fabAddPurchase).setOnClickListener(v -> showQuickAddPurchaseDialog());
        view.findViewById(R.id.fabAddOneOff).setOnClickListener(v -> showAddOneOffDialog());

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new RecurringItemAdapter(new ArrayList<>(), this::showEditDialog,
                this::showCheckpointDialog, displayYear, displayMonth);
        adapter.setOnCardPurchaseClick(this::showEditCardPurchaseDialog);
        adapter.setOnEntryClick(this::showEditOneOffDialog);
        adapter.setOnCardItemLongClick(this::showCardPaymentBreakdown);
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
        loadBalance();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) { loadEntries(); loadBalance(); }
    }

    private void loadAll() {
        swipeRefresh.setRefreshing(true);
        repo.getCategories((cats, fromCache) -> {
            categories = cats;
            if (!fromCache) maybeStopRefresh();
        });
        repo.getCreditCards((cards, fromCache) -> {
            creditCards = cards;
            adapter.setCreditCards(cards);
            if (!fromCache) maybeStopRefresh();
        });
        repo.getRecurringItems((items, fromCache) -> {
            adapter.setItems(items);
            updateBalanceLabels();
            updateNowBalance();
            if (!fromCache) maybeStopRefresh();
        });
        loadEntries();
    }

    private void loadEntries() {
        repo.loadPeriod(displayYear, displayMonth, (entries, fromCache) -> {
            if (getContext() == null) return;
            List<EntryEntity> oneOffs = new ArrayList<>();
            for (EntryEntity e : entries) {
                if (e.recurringItemId == null) oneOffs.add(e);
            }
            adapter.setOneOffEntries(oneOffs);
            adapter.setEntryAmounts(entries);
            updateBalanceLabels();
            updateNowBalance();
            if (!fromCache && needsScrollToToday) {
                needsScrollToToday = false;
                scrollToToday();
            }
        });
        repo.getCardPurchasesByMonth(displayYear, displayMonth, (purchases, fromCache) -> {
            if (getContext() == null) return;
            adapter.setCardPurchases(purchases);
            updateNowBalance();
        });
    }

    private boolean isCurrentMonthDisplayed() {
        Calendar now = Calendar.getInstance();
        return displayYear == now.get(Calendar.YEAR) && displayMonth == now.get(Calendar.MONTH) + 1;
    }

    private void scrollToToday() {
        int pos = adapter.getTodayPosition();
        if (pos < 0) return;
        recyclerView.post(() -> {
            LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (lm != null) lm.scrollToPositionWithOffset(pos, recyclerView.getHeight() / 2);
        });
    }

    private void maybeStopRefresh() {
        swipeRefresh.setRefreshing(false);
    }

    private void updateMonthLabel() {
        String month = new DateFormatSymbols(Locale.UK).getMonths()[displayMonth - 1];
        monthLabel.setText(month + " " + displayYear);
    }

    private void updateBalanceLabels() {
        if (getContext() == null) return;
        double bf = adapter.getChainedBroughtForward();
        double cf = adapter.getFinalRunningBalance();
        broughtFwd.setText(Double.isNaN(bf) ? ""
                : String.format(Locale.UK, "Brought fwd: £%.2f", bf));
        carriedFwd.setText(Double.isNaN(cf) ? ""
                : String.format(Locale.UK, "Carried fwd: £%.2f", cf));
    }

    private void loadBalance() {
        broughtFwd.setText("");
        carriedFwd.setText("");
        nowBalanceSection.setVisibility(View.GONE);
        int year = displayYear;
        int month = displayMonth;
        // Server-computed brought-forward as reliable fallback when no checkpoint chains to this month
        repo.getForecastRange(year, month, 1, new ApiService.Callback<List<ForecastSummary>>() {
            @Override public void onSuccess(List<ForecastSummary> result) {
                if (getContext() == null || result.isEmpty()) return;
                adapter.setBroughtForward(result.get(0).broughtForward);
                updateBalanceLabels();
                updateNowBalance();
            }
            @Override public void onError(String error) {}
        });
        repo.getCheckpoints(new ApiService.Callback<List<BalanceCheckpoint>>() {
            @Override public void onSuccess(List<BalanceCheckpoint> checkpoints) {
                if (getContext() == null) return;
                int latestYear = 0, latestMonth = 0, latestDay = 0;
                double latestBalance = Double.NaN;
                int displayPeriod = year * 12 + month;
                for (BalanceCheckpoint cp : checkpoints) {
                    int cpPeriod = cp.periodYear * 12 + cp.periodMonth;
                    if (cpPeriod > displayPeriod) continue;
                    int bestPeriod = latestYear * 12 + latestMonth;
                    if (cpPeriod > bestPeriod
                            || (cpPeriod == bestPeriod && cp.periodDay > latestDay)) {
                        latestYear = cp.periodYear; latestMonth = cp.periodMonth;
                        latestDay = cp.periodDay; latestBalance = cp.balance;
                    }
                }
                adapter.setCheckpoint(latestYear, latestMonth, latestDay, latestBalance);
                updateBalanceLabels();
                updateNowBalance();
            }
            @Override public void onError(String error) {}
        });
    }

    private void updateNowBalance() {
        if (getContext() == null) return;
        double bal = adapter.getTodayBalance();
        if (Double.isNaN(bal)) {
            nowBalanceSection.setVisibility(View.GONE);
        } else {
            nowBalanceSection.setVisibility(View.VISIBLE);
            nowBalance.setText(String.format(Locale.UK, "£%.2f", bal));
            int colorRes = bal < 0 ? R.color.negative : R.color.colorPrimary;
            nowBalance.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), colorRes));
        }
    }

    private void showCheckpointDialog(int day, double suggestedBalance) {
        android.widget.EditText inputBalance = new android.widget.EditText(requireContext());
        inputBalance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        inputBalance.setHint("Balance");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        inputBalance.setPadding(pad, pad, pad, pad);
        if (!Double.isNaN(suggestedBalance)) {
            inputBalance.setText(String.format(Locale.UK, "%.2f", suggestedBalance));
            inputBalance.selectAll();
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Record balance — " + Util.ordinal(day))
                .setView(inputBalance)
                .setPositiveButton("Save", (dialog, which) -> {
                    Double balance = parseDoubleOrNull(inputBalance.getText().toString());
                    if (balance == null) return;
                    repo.addCheckpoint(displayYear, displayMonth, day, balance,
                            this::loadBalance,
                            this::showError);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCardPaymentBreakdown(RecurringItemEntity item, int payYear, int payMonth) {
        if (item.creditCardId == null) return;
        repo.getCardPaymentBreakdown(item.creditCardId, payYear, payMonth,
                new com.quanglewangle.peter.cashflow.api.ApiService.Callback<com.quanglewangle.peter.cashflow.data.CardPaymentBreakdown>() {
            @Override public void onSuccess(com.quanglewangle.peter.cashflow.data.CardPaymentBreakdown b) {
                if (getContext() == null) return;
                StringBuilder sb = new StringBuilder();
                if (b.checkpoint != null) {
                    sb.append(String.format(Locale.UK, "Checkpoint (%s %d) — £%.2f",
                            Util.ordinal(b.checkpoint.periodDay), b.checkpoint.periodMonth, b.checkpoint.balance));
                    if (!b.coveredByCheckpoint.isEmpty()) {
                        double coveredTotal = 0;
                        for (com.quanglewangle.peter.cashflow.data.CardPurchase p : b.coveredByCheckpoint) coveredTotal += p.amount;
                        sb.append(String.format(Locale.UK, "\n  (already covers %d purchase%s totalling £%.2f)",
                                b.coveredByCheckpoint.size(), b.coveredByCheckpoint.size() == 1 ? "" : "s", coveredTotal));
                    }
                    sb.append("\n\nAdded since checkpoint:\n");
                } else {
                    sb.append("No checkpoint anchors this period — summed from all logged purchases:\n");
                }
                if (b.purchases.isEmpty()) {
                    sb.append(b.checkpoint != null ? "  (none)\n" : "No purchases logged.\n");
                } else {
                    for (com.quanglewangle.peter.cashflow.data.CardPurchase p : b.purchases) {
                        String date = p.purchaseDate != null && p.purchaseDate.length() >= 10
                                ? p.purchaseDate.substring(0, 10) : "";
                        sb.append(String.format(Locale.UK, "%s  %s — £%.2f\n", date, p.description, p.amount));
                    }
                }
                sb.append(String.format(Locale.UK, "\nTotal: £%.2f", b.total));

                TextView content = new TextView(requireContext());
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                content.setPadding(pad, pad, pad, pad);
                content.setText(sb.toString());

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(item.name + " — how this was calculated")
                        .setView(content)
                        .setPositiveButton("Close", null)
                        .show();
            }
            @Override public void onError(String error) { showError(error); }
        });
    }

    private void showEditDialog(@Nullable RecurringItemEntity existing) {
        if (categories.isEmpty()) {
            Toast.makeText(getContext(), "Still loading categories, try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }

        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_recurring_item, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        Spinner spinnerItemType = formView.findViewById(R.id.spinnerItemType);
        Spinner spinnerFrequency = formView.findViewById(R.id.spinnerFrequency);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDueDay = formView.findViewById(R.id.inputDueDay);
        EditText inputTargetMonth = formView.findViewById(R.id.inputTargetMonth);
        EditText inputStartYear = formView.findViewById(R.id.inputStartYear);
        EditText inputStartMonth = formView.findViewById(R.id.inputStartMonth);
        EditText inputStartDay = formView.findViewById(R.id.inputStartDay);
        Spinner spinnerCreditCard = formView.findViewById(R.id.spinnerCreditCard);
        CheckBox checkActive = formView.findViewById(R.id.checkActive);

        List<String> categoryNames = new ArrayList<>();
        for (CategoryEntity c : categories) categoryNames.add(c.name);
        spinnerCategory.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryNames));

        spinnerItemType.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, ITEM_TYPES));
        spinnerItemType.setSelection(1); // default to expense (more common)
        spinnerFrequency.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, FREQUENCIES));

        List<String> cardNames = new ArrayList<>();
        cardNames.add("(none)");
        for (CreditCardEntity c : creditCards) cardNames.add(c.name);
        spinnerCreditCard.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, cardNames));

        if (existing != null) {
            inputName.setText(existing.name);
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).id == existing.categoryId) spinnerCategory.setSelection(i);
            }
            for (int i = 0; i < ITEM_TYPES.length; i++) {
                if (ITEM_TYPES[i].equals(existing.itemType)) spinnerItemType.setSelection(i);
            }
            for (int i = 0; i < FREQUENCIES.length; i++) {
                if (FREQUENCIES[i].equals(existing.frequency)) spinnerFrequency.setSelection(i);
            }
            if (existing.defaultAmount != null) inputAmount.setText(String.valueOf(existing.defaultAmount));
            if (existing.dueDay != null) inputDueDay.setText(String.valueOf(existing.dueDay));
            if (existing.targetMonth != null) inputTargetMonth.setText(String.valueOf(existing.targetMonth));
            if (existing.anchorDate != null && existing.anchorDate.length() >= 7) {
                inputStartYear.setText(existing.anchorDate.substring(0, 4));
                inputStartMonth.setText(existing.anchorDate.substring(5, 7).replaceFirst("^0", ""));
                if (existing.anchorDate.length() >= 10) {
                    inputStartDay.setText(existing.anchorDate.substring(8, 10).replaceFirst("^0", ""));
                }
            }
            if (existing.creditCardId != null) {
                for (int i = 0; i < creditCards.size(); i++) {
                    if (creditCards.get(i).id == existing.creditCardId) spinnerCreditCard.setSelection(i + 1);
                }
            }
            checkActive.setChecked(existing.active);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add item" : "Edit item")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    RecurringItemEntity item = existing != null ? existing : new RecurringItemEntity();
                    item.name = inputName.getText().toString().trim();
                    item.categoryId = categories.get(spinnerCategory.getSelectedItemPosition()).id;
                    item.itemType = ITEM_TYPES[spinnerItemType.getSelectedItemPosition()];
                    item.frequency = FREQUENCIES[spinnerFrequency.getSelectedItemPosition()];
                    item.defaultAmount = parseDoubleOrNull(inputAmount.getText().toString());
                    item.dueDay = parseIntOrNull(inputDueDay.getText().toString());
                    item.targetMonth = parseIntOrNull(inputTargetMonth.getText().toString());
                    Integer startYear = parseIntOrNull(inputStartYear.getText().toString());
                    Integer startMonth = parseIntOrNull(inputStartMonth.getText().toString());
                    Integer startDay = parseIntOrNull(inputStartDay.getText().toString());
                    if (startYear != null && startMonth != null) {
                        int d = startDay != null ? startDay : 1;
                        item.anchorDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", startYear, startMonth, d);
                    } else {
                        item.anchorDate = null;
                    }
                    int cardPos = spinnerCreditCard.getSelectedItemPosition();
                    item.creditCardId = cardPos > 0 ? creditCards.get(cardPos - 1).id : null;
                    item.active = checkActive.isChecked();

                    if (item.name.isEmpty()) {
                        Toast.makeText(getContext(), "Name required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (existing == null) {
                        repo.addRecurringItem(item, this::loadAll, this::showError);
                    } else {
                        repo.updateRecurringItem(item, this::loadAll, this::showError);
                    }
                });

        if (existing != null) {
            builder.setNeutralButton("Delete", (dialog, which) ->
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Delete \"" + existing.name + "\"?")
                            .setMessage("This cannot be undone.")
                            .setPositiveButton("Delete", (d2, w2) ->
                                    repo.deleteRecurringItem(existing.id, this::loadAll, this::showError))
                            .setNegativeButton("Cancel", null)
                            .show());
        }

        builder.show();
    }

    private void showEditCardPurchaseDialog(CardPurchase purchase) {
        String datePart = purchase.purchaseDate != null && purchase.purchaseDate.length() >= 10
                ? purchase.purchaseDate.substring(0, 10) : "";

        List<String> catNames = new ArrayList<>();
        List<Long> catIds = new ArrayList<>();
        catNames.add("No category");
        catIds.add(null);
        for (CategoryEntity c : categories) {
            if ("expense".equals(c.itemType)) { catNames.add(c.name); catIds.add(c.id); }
        }

        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_card_purchase, null);
        EditText inputDescription = formView.findViewById(R.id.inputDescription);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDate = formView.findViewById(R.id.inputDate);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        inputDescription.setText(purchase.description);
        inputAmount.setText(String.format(Locale.UK, "%.2f", purchase.amount));
        inputDate.setText(datePart);

        spinnerCategory.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, catNames));
        if (purchase.categoryId != null) {
            for (int i = 0; i < catIds.size(); i++) {
                if (purchase.categoryId.equals(catIds.get(i))) { spinnerCategory.setSelection(i); break; }
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Edit purchase")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String desc = inputDescription.getText().toString().trim();
                    Double amount = parseDoubleOrNull(inputAmount.getText().toString());
                    String date = inputDate.getText().toString().trim();
                    if (desc.isEmpty() || amount == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Toast.makeText(getContext(), "Description, amount, and a YYYY-MM-DD date are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Long categoryId = catIds.get(spinnerCategory.getSelectedItemPosition());
                    repo.updateCardPurchase(purchase.id, desc, amount, date, categoryId,
                            () -> refreshAfterPurchaseChange(purchase, date),
                            this::showError);
                })
                .setNeutralButton("Delete", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            boolean isRecurring = purchase.recurringPurchaseId != null;
            String message = isRecurring
                    ? purchase.description + " is a subscription — this will cancel it in all future months too."
                    : purchase.description + " — £" + String.format(Locale.UK, "%.2f", purchase.amount);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete this purchase?")
                    .setMessage(message)
                    .setPositiveButton("Delete", (d2, w2) -> {
                        dialog.dismiss();
                        if (isRecurring) {
                            // Delete instance first (triggers entry recalculation), then
                            // template (ON DELETE CASCADE removes any other month instances)
                            repo.deleteCardPurchase(purchase.id, () ->
                                    repo.deleteRecurringCardPurchase(purchase.recurringPurchaseId,
                                            () -> refreshAfterPurchaseChange(purchase, null),
                                            this::showError),
                                    this::showError);
                        } else {
                            repo.deleteCardPurchase(purchase.id,
                                    () -> refreshAfterPurchaseChange(purchase, null),
                                    this::showError);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void refreshAfterPurchaseChange(CardPurchase purchase, @Nullable String newDate) {
        loadEntries();
        // Silently refresh payment period entry cache so the card bill total is current
        CreditCardEntity card = null;
        for (CreditCardEntity c : creditCards) {
            if (c.id == purchase.creditCardId) { card = c; break; }
        }
        if (card != null && purchase.purchaseDate != null && purchase.purchaseDate.length() >= 10) {
            String oldDate = purchase.purchaseDate.substring(0, 10);
            int[] oldPeriod = paymentPeriodFor(card, oldDate);
            repo.loadPeriod(oldPeriod[0], oldPeriod[1], (entries, fromCache) -> {});
            if (newDate != null && !newDate.equals(oldDate)) {
                int[] newPeriod = paymentPeriodFor(card, newDate);
                repo.loadPeriod(newPeriod[0], newPeriod[1], (entries, fromCache) -> {});
            }
        }
    }

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
        if (day > card.statementDay) { month++; if (month > 12) { month = 1; year++; } }
        for (int i = 0; i < card.paymentDueMonthOffset; i++) {
            month++; if (month > 12) { month = 1; year++; }
        }
        return new int[]{year, month};
    }

    private void showQuickAddPurchaseDialog() {
        if (creditCards.isEmpty()) {
            Toast.makeText(getContext(), "Still loading cards, try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> catNames = new ArrayList<>();
        List<Long> catIds = new ArrayList<>();
        catNames.add("No category");
        catIds.add(null);
        for (CategoryEntity c : categories) {
            if ("expense".equals(c.itemType)) { catNames.add(c.name); catIds.add(c.id); }
        }

        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_quick_add_purchase, null);
        Spinner spinnerCard = formView.findViewById(R.id.spinnerCard);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        EditText inputDescription = formView.findViewById(R.id.inputDescription);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDate = formView.findViewById(R.id.inputDate);

        List<String> cardNames = new ArrayList<>();
        for (CreditCardEntity c : creditCards) cardNames.add(c.name);
        spinnerCard.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, cardNames));
        spinnerCategory.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, catNames));

        // Default to Visacard if present
        for (int i = 0; i < creditCards.size(); i++) {
            if (creditCards.get(i).name.equalsIgnoreCase("Visacard")) {
                spinnerCard.setSelection(i);
                break;
            }
        }

        // Default date to today
        Calendar now = Calendar.getInstance();
        inputDate.setText(String.format(Locale.UK, "%04d-%02d-%02d",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)));

        new AlertDialog.Builder(requireContext())
                .setTitle("Add purchase")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    CreditCardEntity card = creditCards.get(spinnerCard.getSelectedItemPosition());
                    String desc = inputDescription.getText().toString().trim();
                    Double amount = parseDoubleOrNull(inputAmount.getText().toString());
                    String date = inputDate.getText().toString().trim();
                    if (desc.isEmpty() || amount == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Toast.makeText(getContext(), "Description, amount, and date are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Long categoryId = catIds.get(spinnerCategory.getSelectedItemPosition());
                    repo.addCardPurchase(card.id, desc, amount, date, categoryId, () -> {
                        loadEntries();
                        int[] period = paymentPeriodFor(card, date);
                        repo.loadPeriod(period[0], period[1], (entries, fromCache) -> {});
                    }, this::showError);
                })
                .show();
    }

    private void showAddOneOffDialog() {
        if (categories.isEmpty()) {
            showError("Still loading, try again in a moment");
            return;
        }
        android.view.View formView = getLayoutInflater().inflate(R.layout.dialog_add_entry, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        Spinner spinnerItemType = formView.findViewById(R.id.spinnerItemType);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDueDay = formView.findViewById(R.id.inputDueDay);
        EditText inputDecayPerWeek = formView.findViewById(R.id.inputDecayPerWeek);
        Spinner spinnerCreditCard = formView.findViewById(R.id.spinnerCreditCard);

        List<String> categoryNames = new ArrayList<>();
        for (CategoryEntity c : categories) categoryNames.add(c.name);
        spinnerCategory.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryNames));
        spinnerItemType.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, ITEM_TYPES));
        // Default to expense (more common)
        spinnerItemType.setSelection(1);
        // Default day to today
        inputDueDay.setText(String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)));
        List<String> cardNames = new ArrayList<>();
        cardNames.add("(none)");
        for (CreditCardEntity c : creditCards) cardNames.add(c.name);
        spinnerCreditCard.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, cardNames));

        new AlertDialog.Builder(requireContext())
                .setTitle("Add one-off entry")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    Double amount = parseDoubleOrNull(inputAmount.getText().toString());
                    if (name.isEmpty() || amount == null) {
                        showError("Description and amount are required");
                        return;
                    }
                    EntryEntity entry = new EntryEntity();
                    entry.recurringItemId = null;
                    entry.categoryId = categories.get(spinnerCategory.getSelectedItemPosition()).id;
                    entry.periodYear = displayYear;
                    entry.periodMonth = displayMonth;
                    entry.name = name;
                    entry.itemType = ITEM_TYPES[spinnerItemType.getSelectedItemPosition()];
                    entry.plannedAmount = amount;
                    entry.status = "planned";
                    entry.dueDay = parseIntOrNull(inputDueDay.getText().toString());
                    entry.decayPerWeek = parseDoubleOrNull(inputDecayPerWeek.getText().toString());
                    int cardPos = spinnerCreditCard.getSelectedItemPosition();
                    entry.creditCardId = cardPos > 0 ? creditCards.get(cardPos - 1).id : null;
                    repo.addEntry(entry, () -> {
                        loadEntries();
                        loadBalance();
                    }, this::showError);
                })
                .show();
    }

    private void showEditOneOffDialog(EntryEntity entry) {
        if (categories.isEmpty()) {
            showError("Still loading, try again in a moment");
            return;
        }
        View formView = getLayoutInflater().inflate(R.layout.dialog_add_entry, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        Spinner spinnerItemType = formView.findViewById(R.id.spinnerItemType);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDueDay = formView.findViewById(R.id.inputDueDay);
        EditText inputDecayPerWeek = formView.findViewById(R.id.inputDecayPerWeek);
        Spinner spinnerCreditCard = formView.findViewById(R.id.spinnerCreditCard);

        List<String> categoryNames = new ArrayList<>();
        for (CategoryEntity c : categories) categoryNames.add(c.name);
        spinnerCategory.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryNames));
        spinnerItemType.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, ITEM_TYPES));
        List<String> cardNames = new ArrayList<>();
        cardNames.add("(none)");
        for (CreditCardEntity c : creditCards) cardNames.add(c.name);
        spinnerCreditCard.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, cardNames));

        inputName.setText(entry.name);
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id == entry.categoryId) spinnerCategory.setSelection(i);
        }
        for (int i = 0; i < ITEM_TYPES.length; i++) {
            if (ITEM_TYPES[i].equals(entry.itemType)) spinnerItemType.setSelection(i);
        }
        // Prefill with the undecayed original -- the decayed effectiveAmount is
        // for display/balance math only, not what you'd want to edit from.
        double amount = entry.actualAmount != null ? entry.actualAmount : entry.plannedAmount;
        inputAmount.setText(String.format(Locale.UK, "%.2f", amount));
        if (entry.dueDay != null) inputDueDay.setText(String.valueOf(entry.dueDay));
        if (entry.decayPerWeek != null) inputDecayPerWeek.setText(String.format(Locale.UK, "%.2f", entry.decayPerWeek));
        if (entry.creditCardId != null) {
            for (int i = 0; i < creditCards.size(); i++) {
                if (creditCards.get(i).id == entry.creditCardId) spinnerCreditCard.setSelection(i + 1);
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit one-off entry")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", (dialog, which) ->
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Delete \"" + entry.name + "\"?")
                                .setMessage("This cannot be undone.")
                                .setPositiveButton("Delete", (d2, w2) ->
                                        repo.deleteEntry(entry.id, () -> {
                                            loadEntries();
                                            loadBalance();
                                        }, this::showError))
                                .setNegativeButton("Cancel", null)
                                .show())
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    Double newAmount = parseDoubleOrNull(inputAmount.getText().toString());
                    if (name.isEmpty() || newAmount == null) {
                        showError("Description and amount are required");
                        return;
                    }
                    entry.name = name;
                    entry.categoryId = categories.get(spinnerCategory.getSelectedItemPosition()).id;
                    entry.itemType = ITEM_TYPES[spinnerItemType.getSelectedItemPosition()];
                    entry.plannedAmount = newAmount;
                    if (entry.actualAmount != null) entry.actualAmount = newAmount;
                    entry.dueDay = parseIntOrNull(inputDueDay.getText().toString());
                    // decayStartDate is left untouched -- the server preserves it (or
                    // defaults to today only if this is genuinely new) so editing an
                    // already-decaying entry doesn't restart its clock.
                    entry.decayPerWeek = parseDoubleOrNull(inputDecayPerWeek.getText().toString());
                    int cardPos = spinnerCreditCard.getSelectedItemPosition();
                    entry.creditCardId = cardPos > 0 ? creditCards.get(cardPos - 1).id : null;
                    repo.updateEntry(entry, () -> {
                        loadEntries();
                        loadBalance();
                    }, this::showError);
                })
                .show();
    }

    private void showCategoriesDialog() {
        String[] typeLabels = {"Expense", "Income", "Savings"};
        String[] typeValues = {"expense", "income", "savings"};
        int[] selectedType = {0};

        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad * 2, pad, pad * 2, 0);

        android.widget.TextView existingLabel = new android.widget.TextView(getContext());
        existingLabel.setText(categories.isEmpty() ? "No categories yet" :
                categories.stream().map(c -> c.name + " (" + c.itemType + ")").collect(java.util.stream.Collectors.joining(", ")));
        existingLabel.setTextSize(13);
        layout.addView(existingLabel);

        EditText inputName = new EditText(getContext());
        inputName.setHint("New category name");
        inputName.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        inputName.setLayoutParams(lp);
        layout.addView(inputName);

        Spinner spinnerType = new Spinner(getContext());
        spinnerType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, typeLabels));
        spinnerType.setLayoutParams(lp);
        layout.addView(spinnerType);

        new AlertDialog.Builder(requireContext())
                .setTitle("Categories")
                .setView(layout)
                .setNegativeButton("Close", null)
                .setPositiveButton("Add", (d, w) -> {
                    String name = inputName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    String itemType = typeValues[spinnerType.getSelectedItemPosition()];
                    int sortOrder = (int) categories.stream().filter(c -> c.itemType.equals(itemType)).count();
                    repo.addCategory(name, itemType, sortOrder, () -> {
                        repo.getCategories((cats, fromCache) -> { if (!fromCache) categories = new ArrayList<>(cats); });
                        Toast.makeText(getContext(), name + " added", Toast.LENGTH_SHORT).show();
                    }, this::showError);
                })
                .show();
    }

    private void showError(String error) {
        if (getContext() != null) Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
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
