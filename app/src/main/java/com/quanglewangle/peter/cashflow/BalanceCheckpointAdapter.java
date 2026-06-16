package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.BalanceCheckpoint;

import java.text.DateFormatSymbols;
import java.util.List;
import java.util.Locale;

public class BalanceCheckpointAdapter extends RecyclerView.Adapter<BalanceCheckpointAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(BalanceCheckpoint checkpoint);
    }

    private List<BalanceCheckpoint> items;
    private final OnItemClick onClick;

    public BalanceCheckpointAdapter(List<BalanceCheckpoint> items, OnItemClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    public void setItems(List<BalanceCheckpoint> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_balance_checkpoint, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BalanceCheckpoint c = items.get(position);
        String month = new DateFormatSymbols(Locale.UK).getMonths()[c.periodMonth - 1];
        holder.period.setText(month + " " + c.periodYear);
        holder.balance.setText(String.format(Locale.UK, "£%.2f", c.balance));
        holder.itemView.setOnClickListener(v -> onClick.onClick(c));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView period, balance;

        ViewHolder(View itemView) {
            super(itemView);
            period = itemView.findViewById(R.id.period);
            balance = itemView.findViewById(R.id.balance);
        }
    }
}
