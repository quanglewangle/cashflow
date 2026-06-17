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
import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.ForecastSummary;
import com.quanglewangle.peter.cashflow.data.RecurringItemEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

/** Manage the recurring-item templates (monthly / annual / irregular) that generate entries. */
public class ItemsFragment extends Fragment {

    private static final String[] ITEM_TYPES = {"income", "expense", "savings"};
    private static final String[] FREQUENCIES = {"monthly", "four_weekly", "annual", "irregular"};

    private SwipeRefreshLayout swipeRefresh;
    private RecurringItemAdapter adapter;
    private Repository repo;
    private TextView monthLabel, broughtFwd, carriedFwd;
    private int displayYear, displayMonth;

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
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = Repository.getInstance(requireContext());

        Calendar now = Calendar.getInstance();
        displayYear = now.get(Calendar.YEAR);
        displayMonth = now.get(Calendar.MONTH) + 1;

        monthLabel = view.findViewById(R.id.monthLabel);
        broughtFwd = view.findViewById(R.id.broughtFwd);
        carriedFwd = view.findViewById(R.id.carriedFwd);
        Button btnPrev = view.findViewById(R.id.btnPrevMonth);
        Button btnNext = view.findViewById(R.id.btnNextMonth);
        updateMonthLabel();

        btnPrev.setOnClickListener(v -> {
            displayMonth--;
            if (displayMonth < 1) { displayMonth = 12; displayYear--; }
            updateMonthLabel();
            adapter.setMonth(displayYear, displayMonth);
            loadBalance();
        });
        btnNext.setOnClickListener(v -> {
            displayMonth++;
            if (displayMonth > 12) { displayMonth = 1; displayYear++; }
            updateMonthLabel();
            adapter.setMonth(displayYear, displayMonth);
            loadBalance();
        });

        view.findViewById(R.id.balanceSection).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), EntriesActivity.class);
            intent.putExtra(EntriesActivity.EXTRA_YEAR, displayYear);
            intent.putExtra(EntriesActivity.EXTRA_MONTH, displayMonth);
            startActivity(intent);
        });
        view.findViewById(R.id.fabAdd).setOnClickListener(v -> showEditDialog(null));

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new RecurringItemAdapter(new ArrayList<>(), this::showEditDialog,
                this::showCheckpointDialog, displayYear, displayMonth);
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
        loadBalance();
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
            if (!fromCache) maybeStopRefresh();
        });
    }

    private void maybeStopRefresh() {
        swipeRefresh.setRefreshing(false);
    }

    private void updateMonthLabel() {
        String month = new DateFormatSymbols(Locale.UK).getMonths()[displayMonth - 1];
        monthLabel.setText(month + " " + displayYear);
    }

    private void loadBalance() {
        broughtFwd.setText("");
        carriedFwd.setText("");
        int year = displayYear;
        int month = displayMonth;
        repo.getForecastRange(year, month, 1, new ApiService.Callback<List<ForecastSummary>>() {
            @Override public void onSuccess(List<ForecastSummary> result) {
                if (result.isEmpty() || getContext() == null) return;
                ForecastSummary s = result.get(0);
                broughtFwd.setText(String.format(Locale.UK, "Brought fwd: £%.2f", s.broughtForward));
                carriedFwd.setText(String.format(Locale.UK, "Carried fwd: £%.2f", s.carriedForward));
                adapter.setBroughtForward(s.broughtForward);
            }
            @Override public void onError(String error) {}
        });
        repo.getCheckpoints(new ApiService.Callback<List<BalanceCheckpoint>>() {
            @Override public void onSuccess(List<BalanceCheckpoint> checkpoints) {
                if (getContext() == null) return;
                int latestDay = 0;
                double latestBalance = Double.NaN;
                for (BalanceCheckpoint cp : checkpoints) {
                    if (cp.periodYear == year && cp.periodMonth == month && cp.periodDay > latestDay) {
                        latestDay = cp.periodDay;
                        latestBalance = cp.balance;
                    }
                }
                adapter.setCheckpoint(latestDay, latestBalance);
            }
            @Override public void onError(String error) {}
        });
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
        Spinner spinnerCreditCard = formView.findViewById(R.id.spinnerCreditCard);
        CheckBox checkActive = formView.findViewById(R.id.checkActive);

        List<String> categoryNames = new ArrayList<>();
        for (CategoryEntity c : categories) categoryNames.add(c.name);
        spinnerCategory.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryNames));

        spinnerItemType.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, ITEM_TYPES));
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
                    if (startYear != null && startMonth != null) {
                        item.anchorDate = String.format(java.util.Locale.US, "%04d-%02d-01", startYear, startMonth);
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
