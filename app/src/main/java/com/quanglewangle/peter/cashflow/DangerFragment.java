package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.quanglewangle.peter.cashflow.api.ApiService;
import com.quanglewangle.peter.cashflow.data.ForecastDanger;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DangerFragment extends Fragment {

    private static final double DANGER_THRESHOLD = -1000.0;
    private static final int MONTHS_AHEAD = 6;

    private Repository repo;
    private DangerAdapter adapter;
    private TextView bannerText;
    private SwitchMaterial simulateToggle;
    private List<ForecastDanger> rawRows = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_danger, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repo = Repository.getInstance(requireContext());

        bannerText = view.findViewById(R.id.bannerText);
        simulateToggle = view.findViewById(R.id.simulateToggle);

        adapter = new DangerAdapter(new ArrayList<>(), false);
        RecyclerView rv = view.findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        rv.setAdapter(adapter);

        simulateToggle.setOnCheckedChangeListener((btn, checked) -> {
            adapter.setSimulating(checked);
            updateBanner(rawRows, checked);
        });

        load();
    }

    private void load() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;

        repo.getForecastDanger(year, month, MONTHS_AHEAD, new ApiService.Callback<List<ForecastDanger>>() {
            @Override
            public void onSuccess(List<ForecastDanger> result) {
                applySimulation(result);
                rawRows = result;
                boolean sim = simulateToggle.isChecked();
                adapter.setRows(result, sim);
                updateBanner(result, sim);
            }

            @Override
            public void onError(String error) {
                if (getContext() != null)
                    Toast.makeText(getContext(), "Forecast error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Computes simulation fields in-place on each row.
    // Forward pass: for each danger month, borrow what's needed and schedule repayment
    // in the first later month where repaying won't breach the threshold.
    private void applySimulation(List<ForecastDanger> rows) {
        int n = rows.size();
        double[] pendingRepay = new double[n];
        double offset = 0; // cumulative net borrowed (positive = owe Marcos)

        for (ForecastDanger d : rows) d.initSim();

        for (int i = 0; i < n; i++) {
            ForecastDanger d = rows.get(i);

            // Apply any repayments due this month (they happen after the minimum)
            double repayNow = pendingRepay[i];
            d.repayAmount = repayNow;

            // Adjusted min before repay (borrow happened before the min)
            double adjMin = d.minBalance + offset;

            // How much more do we need to borrow to clear the danger threshold?
            double needed = adjMin < DANGER_THRESHOLD
                    ? Math.ceil(DANGER_THRESHOLD - adjMin) : 0;
            d.borrowNeeded = needed;
            offset += needed;

            d.simMin = d.minBalance + offset;

            // Repayment reduces balance after the minimum, so simMin is unaffected
            offset -= repayNow;
            d.simCarried = d.carriedForward + offset;

            // Schedule repayment of this month's borrow in the first future month
            // where paying it back won't breach the threshold.
            if (needed > 0) {
                for (int j = i + 1; j < n; j++) {
                    ForecastDanger later = rows.get(j);
                    // Approximate future offset as current offset (before any future borrows)
                    if (later.minBalance + offset - needed >= DANGER_THRESHOLD) {
                        pendingRepay[j] += needed;
                        break;
                    }
                }
            }
        }
    }

    private void updateBanner(List<ForecastDanger> rows, boolean simulating) {
        String[] months = new DateFormatSymbols().getMonths();
        if (simulating) {
            // In simulation mode show total borrows needed and whether plan holds
            double totalBorrow = 0;
            boolean planFails = false;
            for (ForecastDanger d : rows) {
                totalBorrow += d.borrowNeeded;
                if (d.simMin < DANGER_THRESHOLD) planFails = true;
            }
            if (planFails) {
                bannerText.setText("Warning: plan doesn't fully resolve danger within 6 months");
                bannerText.setTextColor(requireContext().getColor(R.color.colorSecondary));
            } else if (totalBorrow > 0) {
                bannerText.setText(String.format(Locale.UK,
                        "Plan: borrow £%.0f from Marcos — all months safe", totalBorrow));
                bannerText.setTextColor(requireContext().getColor(R.color.incurred));
            } else {
                bannerText.setText("All clear — no borrowing needed");
                bannerText.setTextColor(requireContext().getColor(R.color.incurred));
            }
            bannerText.setVisibility(View.VISIBLE);
            return;
        }

        for (ForecastDanger d : rows) {
            if (d.minBalance < DANGER_THRESHOLD) {
                double needed = Math.ceil(DANGER_THRESHOLD - d.minBalance);
                String label = months[d.periodMonth - 1] + " " + d.periodYear;
                bannerText.setText(String.format(Locale.UK,
                        "Danger: %s dips to £%.0f  •  Borrow £%.0f from Marcos",
                        label, d.minBalance, needed));
                bannerText.setTextColor(requireContext().getColor(R.color.negative));
                bannerText.setVisibility(View.VISIBLE);
                return;
            }
        }
        for (ForecastDanger d : rows) {
            if (d.minBalance < 0) {
                String label = months[d.periodMonth - 1] + " " + d.periodYear;
                bannerText.setText(String.format(Locale.UK,
                        "Warning: %s goes negative (£%.0f)", label, d.minBalance));
                bannerText.setTextColor(requireContext().getColor(R.color.colorSecondary));
                bannerText.setVisibility(View.VISIBLE);
                return;
            }
        }
        bannerText.setText("All clear — 6 months look safe");
        bannerText.setTextColor(requireContext().getColor(R.color.incurred));
        bannerText.setVisibility(View.VISIBLE);
    }
}
