package com.quanglewangle.peter.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.cashflow.data.ForecastSummary;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Rolling 12-month forecast starting this month, tap a period to see/edit its entries. */
public class ForecastFragment extends Fragment {

    private static final int MONTHS_AHEAD = 12;

    private SwipeRefreshLayout swipeRefresh;
    private ForecastAdapter adapter;
    private Repository repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forecast, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = Repository.getInstance(requireContext());

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new ForecastAdapter(new ArrayList<>(), this::openPeriod);
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.fabCheckpoint).setOnClickListener(v ->
                startActivity(new Intent(getContext(), BalanceCheckpointsActivity.class)));
        swipeRefresh.setOnRefreshListener(this::loadForecast);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Also covers the initial load, and catches balance checkpoints
        // added/edited in BalanceCheckpointsActivity on the way back.
        loadForecast();
    }

    private void openPeriod(ForecastSummary summary) {
        Intent intent = new Intent(getContext(), EntriesActivity.class);
        intent.putExtra(EntriesActivity.EXTRA_YEAR, summary.periodYear);
        intent.putExtra(EntriesActivity.EXTRA_MONTH, summary.periodMonth);
        startActivity(intent);
    }

    private void loadForecast() {
        swipeRefresh.setRefreshing(true);
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;

        repo.getForecastRange(year, month, MONTHS_AHEAD,
                new com.quanglewangle.peter.cashflow.api.ApiService.Callback<List<ForecastSummary>>() {
                    @Override
                    public void onSuccess(List<ForecastSummary> result) {
                        swipeRefresh.setRefreshing(false);
                        adapter.setItems(result);
                    }

                    @Override
                    public void onError(String error) {
                        swipeRefresh.setRefreshing(false);
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Couldn't load forecast: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
