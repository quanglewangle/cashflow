package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.cashflow.data.BalanceCheckpoint;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Lists every balance checkpoint (including the very first "opening
 * balance") and lets you add or correct one for any month -- not just
 * today's, which is all the Forecast tab's quick "Update balance" button
 * covers.
 */
public class BalanceCheckpointsActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private BalanceCheckpointAdapter adapter;
    private Repository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entries); // generic toolbar+list+fab shell, reused as-is

        repo = Repository.getInstance(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Balance checkpoints");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new BalanceCheckpointAdapter(new ArrayList<>(), this::showEditDialog);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(v -> showEditDialog(null));
        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadAll() {
        swipeRefresh.setRefreshing(true);
        repo.getCheckpoints(new com.quanglewangle.peter.cashflow.api.ApiService.Callback<List<BalanceCheckpoint>>() {
            @Override
            public void onSuccess(List<BalanceCheckpoint> result) {
                swipeRefresh.setRefreshing(false);
                adapter.setItems(result);
            }

            @Override
            public void onError(String error) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(BalanceCheckpointsActivity.this, "Couldn't load checkpoints: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showEditDialog(@Nullable BalanceCheckpoint existing) {
        android.view.View formView = getLayoutInflater().inflate(R.layout.dialog_edit_balance_checkpoint, null);
        EditText inputYear = formView.findViewById(R.id.inputYear);
        EditText inputMonth = formView.findViewById(R.id.inputMonth);
        EditText inputDay = formView.findViewById(R.id.inputDay);
        EditText inputBalance = formView.findViewById(R.id.inputBalance);

        Calendar now = Calendar.getInstance();
        if (existing != null) {
            inputYear.setText(String.valueOf(existing.periodYear));
            inputMonth.setText(String.valueOf(existing.periodMonth));
            inputDay.setText(String.valueOf(existing.periodDay > 0 ? existing.periodDay : 1));
            inputBalance.setText(String.valueOf(existing.balance));
        } else {
            inputYear.setText(String.valueOf(now.get(Calendar.YEAR)));
            inputMonth.setText(String.valueOf(now.get(Calendar.MONTH) + 1));
            inputDay.setText(String.valueOf(now.get(Calendar.DAY_OF_MONTH)));
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add checkpoint" : "Edit checkpoint")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    Integer year = parseIntOrNull(inputYear.getText().toString());
                    Integer month = parseMonthOrNull(inputMonth.getText().toString());
                    Integer day = parseDayOrNull(inputDay.getText().toString());
                    Double balance = parseDoubleOrNull(inputBalance.getText().toString());
                    if (year == null || month == null || day == null || balance == null) {
                        Toast.makeText(this, "A valid year, month (1-12), day (1-31), and balance are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repo.addCheckpoint(year, month, day, balance, this::loadAll,
                            error -> Toast.makeText(this, error, Toast.LENGTH_LONG).show());
                })
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

    @Nullable
    private Integer parseMonthOrNull(String s) {
        Integer v = parseIntOrNull(s);
        return (v != null && v >= 1 && v <= 12) ? v : null;
    }

    @Nullable
    private Integer parseDayOrNull(String s) {
        Integer v = parseIntOrNull(s);
        return (v != null && v >= 1 && v <= 31) ? v : null;
    }

    @Nullable
    private Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
