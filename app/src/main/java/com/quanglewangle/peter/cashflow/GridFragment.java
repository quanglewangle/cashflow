package com.quanglewangle.peter.cashflow;

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

import com.quanglewangle.peter.cashflow.api.ApiService;
import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.EntryEntity;
import com.quanglewangle.peter.cashflow.data.ForecastSummary;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spreadsheet-style overview: rows are items grouped by category (Income /
 * Committed / Savings, matching the original budget spreadsheet's
 * sections), columns are the next few months -- brought-forward / item /
 * subtotal / carried-forward, same shape as the sheet had.
 */
public class GridFragment extends Fragment {

    private static final int MONTHS = 3;

    private SwipeRefreshLayout swipeRefresh;
    private GridAdapter adapter;
    private Repository repo;

    private int[] years = new int[MONTHS];
    private int[] months = new int[MONTHS];

    private List<CategoryEntity> categories;
    private List<CreditCardEntity> creditCards;
    private ForecastSummary[] forecasts = new ForecastSummary[MONTHS];
    private final Map<Integer, List<EntryEntity>> entriesByMonthIndex = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_grid, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = Repository.getInstance(requireContext());

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GridAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadAll);

        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;
        for (int i = 0; i < MONTHS; i++) {
            years[i] = year;
            months[i] = month;
            month++;
            if (month > 12) { month = 1; year++; }
        }

        loadAll();
    }

    private void loadAll() {
        swipeRefresh.setRefreshing(true);
        entriesByMonthIndex.clear();

        repo.getCategories((cats, fromCache) -> {
            categories = cats;
            maybeBuildGrid();
        });

        repo.getCreditCards((cards, fromCache) -> {
            creditCards = cards;
            maybeBuildGrid();
        });

        repo.getForecastRange(years[0], months[0], MONTHS, new ApiService.Callback<List<ForecastSummary>>() {
            @Override public void onSuccess(List<ForecastSummary> result) {
                for (int i = 0; i < MONTHS && i < result.size(); i++) forecasts[i] = result.get(i);
                maybeBuildGrid();
            }
            @Override public void onError(String error) {
                swipeRefresh.setRefreshing(false);
                if (getContext() != null) Toast.makeText(getContext(), "Couldn't load forecast: " + error, Toast.LENGTH_LONG).show();
            }
        });

        for (int i = 0; i < MONTHS; i++) {
            final int idx = i;
            repo.loadPeriod(years[i], months[i], (entries, fromCache) -> {
                entriesByMonthIndex.put(idx, entries);
                maybeBuildGrid();
            });
        }
    }

    private void maybeBuildGrid() {
        if (categories == null || creditCards == null || entriesByMonthIndex.size() < MONTHS) return;
        for (ForecastSummary f : forecasts) if (f == null) return;

        adapter.setRows(buildRows());
        swipeRefresh.setRefreshing(false);
    }

    private List<GridRow> buildRows() {
        List<GridRow> rows = new ArrayList<>();
        String[] monthHeaders = new String[MONTHS];
        String[] monthNames = new DateFormatSymbols(Locale.UK).getShortMonths();
        for (int i = 0; i < MONTHS; i++) {
            monthHeaders[i] = monthNames[months[i] - 1] + " " + String.valueOf(years[i]).substring(2);
        }
        rows.add(new GridRow("", monthHeaders, null, true));

        rows.add(new GridRow("Brought fwd", formatAll(i -> forecasts[i].broughtForward), null, true));

        for (CategoryEntity category : categories) {
            rows.add(new GridRow(category.name, new String[MONTHS], null, true));

            // Union of item rows across all months, keyed so a recurring item
            // is one row (it repeats every month it's generated for) while a
            // one-off keeps its own row instead of merging with same-named ones.
            Map<String, GridRowBuilder> byKey = new LinkedHashMap<>();
            for (int i = 0; i < MONTHS; i++) {
                for (EntryEntity e : entriesByMonthIndex.get(i)) {
                    if (e.categoryId != category.id) continue;
                    String key = e.recurringItemId != null ? "ri-" + e.recurringItemId : "oneoff-" + e.id;
                    boolean chargedToCard = Util.isChargedToCard(e.creditCardId, e.name, creditCards);
                    GridRowBuilder b = byKey.computeIfAbsent(key, k -> new GridRowBuilder(e.name, e.itemType, chargedToCard));
                    boolean incurred = "incurred".equals(e.status);
                    b.amounts[i] = incurred && e.actualAmount != null ? e.actualAmount : e.plannedAmount;
                }
            }
            for (GridRowBuilder b : byKey.values()) {
                // Greyed out for card-charged items: they're billed to the card, not
                // cash, so they don't move the brought/carried-forward balance --
                // the category total below (from the server's cash forecast)
                // already excludes them. A card's own statement payment is a real
                // cash expense though, so it's excluded from this and stays normal.
                rows.add(new GridRow(b.name, formatAmounts(b.amounts), b.itemType, false, b.paidByCard));
            }

            String[] subtotal = formatAll(i -> {
                switch (category.itemType) {
                    case "income": return forecasts[i].income;
                    case "savings": return forecasts[i].savings;
                    default: return forecasts[i].expense;
                }
            });
            rows.add(new GridRow("Total", subtotal, category.itemType, true));
        }

        rows.add(new GridRow("Carried fwd", formatAll(i -> forecasts[i].carriedForward), null, true));
        return rows;
    }

    private interface AmountAt { double get(int monthIndex); }

    private String[] formatAll(AmountAt fn) {
        String[] out = new String[MONTHS];
        for (int i = 0; i < MONTHS; i++) out[i] = String.format(Locale.UK, "£%.2f", fn.get(i));
        return out;
    }

    private String[] formatAmounts(Double[] amounts) {
        String[] out = new String[MONTHS];
        for (int i = 0; i < MONTHS; i++) out[i] = amounts[i] == null ? "" : String.format(Locale.UK, "£%.2f", amounts[i]);
        return out;
    }

    /** Mutable accumulator while merging entries across months into rows. */
    private static class GridRowBuilder {
        final String name;
        final String itemType;
        final boolean paidByCard;
        final Double[] amounts = new Double[MONTHS];

        GridRowBuilder(String name, String itemType, boolean paidByCard) {
            this.name = name;
            this.itemType = itemType;
            this.paidByCard = paidByCard;
        }
    }
}
