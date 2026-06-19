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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_danger, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repo = Repository.getInstance(requireContext());

        bannerText = view.findViewById(R.id.bannerText);

        adapter = new DangerAdapter(new ArrayList<>());
        RecyclerView rv = view.findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        rv.setAdapter(adapter);

        load();
    }

    private void load() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;

        repo.getForecastDanger(year, month, MONTHS_AHEAD, new com.quanglewangle.peter.cashflow.api.ApiService.Callback<List<ForecastDanger>>() {
            @Override
            public void onSuccess(List<ForecastDanger> result) {
                adapter.setRows(result);
                updateBanner(result);
            }

            @Override
            public void onError(String error) {
                if (getContext() != null)
                    Toast.makeText(getContext(), "Forecast error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateBanner(List<ForecastDanger> rows) {
        String[] months = new DateFormatSymbols().getMonths();
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
        // Check for amber (negative but above threshold)
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
