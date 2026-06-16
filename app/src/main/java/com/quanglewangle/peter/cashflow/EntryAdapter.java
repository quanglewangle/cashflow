package com.quanglewangle.peter.cashflow;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.EntryEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.ViewHolder> {

    public interface OnMarkIncurred {
        void onClick(EntryEntity entry);
    }

    private List<EntryEntity> items = new ArrayList<>();
    private double[] runningBalances = new double[0];
    private double broughtForward = Double.NaN;
    private final OnMarkIncurred onMarkIncurred;

    public EntryAdapter(List<EntryEntity> items, OnMarkIncurred onMarkIncurred) {
        this.onMarkIncurred = onMarkIncurred;
        setItems(items);
    }

    public void setBroughtForward(double broughtForward) {
        this.broughtForward = broughtForward;
        recomputeRunningBalances();
        notifyDataSetChanged();
    }

    public void setItems(List<EntryEntity> items) {
        this.items = new ArrayList<>(items);
        this.items.sort(Comparator.comparingInt(e -> e.dueDay != null ? e.dueDay : Integer.MAX_VALUE));
        recomputeRunningBalances();
        notifyDataSetChanged();
    }

    private void recomputeRunningBalances() {
        runningBalances = new double[items.size()];
        double balance = Double.isNaN(broughtForward) ? Double.NaN : broughtForward;
        for (int i = 0; i < items.size(); i++) {
            if (!Double.isNaN(balance)) {
                EntryEntity e = items.get(i);
                double amount = e.actualAmount != null && "incurred".equals(e.status) ? e.actualAmount : e.plannedAmount;
                if ("income".equals(e.itemType)) balance += amount;
                else balance -= amount;
            }
            runningBalances[i] = balance;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EntryEntity e = items.get(position);
        holder.name.setText(e.name);

        holder.dueDay.setText(e.dueDay != null ? Util.ordinal(e.dueDay) : "—");

        boolean incurred = "incurred".equals(e.status);
        boolean isIncome = "income".equals(e.itemType);
        double amount = incurred && e.actualAmount != null ? e.actualAmount : e.plannedAmount;
        String prefix = incurred ? (isIncome ? "Received" : "Paid") : "Planned";
        holder.status.setText(String.format(Locale.UK, "%s: £%.2f", prefix, amount));
        holder.status.setTextColor(Util.colorForItemType(holder.itemView.getContext(), e.itemType));
        holder.status.setTypeface(null, incurred ? Typeface.BOLD : Typeface.NORMAL);

        double bal = runningBalances.length > position ? runningBalances[position] : Double.NaN;
        if (!Double.isNaN(bal)) {
            holder.runningBalance.setVisibility(View.VISIBLE);
            holder.runningBalance.setText(String.format(Locale.UK, "Balance: £%.2f", bal));
        } else {
            holder.runningBalance.setVisibility(View.GONE);
        }

        holder.markIncurredButton.setText(incurred ? "Edit" : (isIncome ? "Mark received" : "Mark paid"));
        holder.markIncurredButton.setOnClickListener(v -> onMarkIncurred.onClick(e));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView dueDay, name, status, runningBalance;
        Button markIncurredButton;

        ViewHolder(View itemView) {
            super(itemView);
            dueDay = itemView.findViewById(R.id.dueDay);
            name = itemView.findViewById(R.id.name);
            status = itemView.findViewById(R.id.status);
            runningBalance = itemView.findViewById(R.id.runningBalance);
            markIncurredButton = itemView.findViewById(R.id.markIncurredButton);
        }
    }
}
