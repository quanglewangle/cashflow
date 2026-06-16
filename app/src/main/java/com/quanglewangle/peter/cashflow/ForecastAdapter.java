package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.ForecastSummary;

import java.text.DateFormatSymbols;
import java.util.List;
import java.util.Locale;

public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ViewHolder> {

    public interface OnPeriodClick {
        void onClick(ForecastSummary summary);
    }

    private List<ForecastSummary> items;
    private final OnPeriodClick onClick;

    public ForecastAdapter(List<ForecastSummary> items, OnPeriodClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    public void setItems(List<ForecastSummary> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_forecast_period, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ForecastSummary s = items.get(position);
        String month = new DateFormatSymbols(Locale.UK).getMonths()[s.periodMonth - 1];
        holder.periodLabel.setText(month + " " + s.periodYear);
        holder.broughtForward.setText(String.format(Locale.UK, "Brought fwd: £%.2f", s.broughtForward));
        holder.carriedForward.setText(String.format(Locale.UK, "Carried fwd: £%.2f", s.carriedForward));
        holder.income.setText(String.format(Locale.UK, "Income: £%.2f", s.income));
        holder.expense.setText(String.format(Locale.UK, "Expense: £%.2f", s.expense + s.savings));
        holder.itemView.setOnClickListener(v -> onClick.onClick(s));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView periodLabel, broughtForward, carriedForward, income, expense;

        ViewHolder(View itemView) {
            super(itemView);
            periodLabel = itemView.findViewById(R.id.periodLabel);
            broughtForward = itemView.findViewById(R.id.broughtForward);
            carriedForward = itemView.findViewById(R.id.carriedForward);
            income = itemView.findViewById(R.id.income);
            expense = itemView.findViewById(R.id.expense);
        }
    }
}
