package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.EntryEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One month's line items: mark bills paid (with the actual amount), or add a one-off entry. */
public class EntriesActivity extends AppCompatActivity {

    public static final String EXTRA_YEAR = "year";
    public static final String EXTRA_MONTH = "month";

    private static final String[] ITEM_TYPES = {"income", "expense", "savings"};

    private int year, month;
    private SwipeRefreshLayout swipeRefresh;
    private EntryAdapter adapter;
    private Repository repo;
    private List<CategoryEntity> categories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entries);

        year = getIntent().getIntExtra(EXTRA_YEAR, 0);
        month = getIntent().getIntExtra(EXTRA_MONTH, 0);
        repo = Repository.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(new DateFormatSymbols(Locale.UK).getMonths()[month - 1] + " " + year);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new EntryAdapter(new ArrayList<>(), this::showMarkIncurredDialog);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddEntryDialog());
        swipeRefresh.setOnRefreshListener(this::loadEntries);

        repo.getCategories((cats, fromCache) -> categories = cats);
        loadEntries();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadEntries() {
        swipeRefresh.setRefreshing(true);
        repo.loadPeriod(year, month, (entries, fromCache) -> {
            adapter.setItems(entries);
            if (!fromCache) swipeRefresh.setRefreshing(false);
        });
    }

    private void showMarkIncurredDialog(EntryEntity entry) {
        android.view.View formView = getLayoutInflater().inflate(R.layout.dialog_mark_incurred, null);
        EditText inputActual = formView.findViewById(R.id.inputActualAmount);
        double prefill = entry.actualAmount != null ? entry.actualAmount : entry.plannedAmount;
        inputActual.setText(String.valueOf(prefill));

        boolean isIncome = "income".equals(entry.itemType);
        new AlertDialog.Builder(this)
                .setTitle(entry.name)
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(isIncome ? "Mark received" : "Mark paid", (dialog, which) -> {
                    Double amount = parseDoubleOrNull(inputActual.getText().toString());
                    if (amount == null) {
                        Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    entry.actualAmount = amount;
                    entry.status = "incurred";
                    repo.updateEntry(entry, this::loadEntries, this::showError);
                })
                .setNeutralButton("Revert to planned", (dialog, which) -> {
                    entry.actualAmount = null;
                    entry.status = "planned";
                    repo.updateEntry(entry, this::loadEntries, this::showError);
                })
                .show();
    }

    private void showAddEntryDialog() {
        if (categories.isEmpty()) {
            Toast.makeText(this, "Still loading categories, try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }

        android.view.View formView = getLayoutInflater().inflate(R.layout.dialog_add_entry, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        Spinner spinnerItemType = formView.findViewById(R.id.spinnerItemType);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);

        List<String> categoryNames = new ArrayList<>();
        for (CategoryEntity c : categories) categoryNames.add(c.name);
        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categoryNames));
        spinnerItemType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ITEM_TYPES));

        new AlertDialog.Builder(this)
                .setTitle("Add one-off entry")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    Double amount = parseDoubleOrNull(inputAmount.getText().toString());
                    if (name.isEmpty() || amount == null) {
                        Toast.makeText(this, "Description and amount are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    EntryEntity entry = new EntryEntity();
                    entry.recurringItemId = null;
                    entry.categoryId = categories.get(spinnerCategory.getSelectedItemPosition()).id;
                    entry.periodYear = year;
                    entry.periodMonth = month;
                    entry.name = name;
                    entry.itemType = ITEM_TYPES[spinnerItemType.getSelectedItemPosition()];
                    entry.plannedAmount = amount;
                    entry.status = "planned";
                    repo.addEntry(entry, this::loadEntries, this::showError);
                })
                .show();
    }

    private void showError(String error) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    private Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
