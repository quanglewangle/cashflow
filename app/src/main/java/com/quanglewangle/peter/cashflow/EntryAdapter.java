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

import java.util.List;
import java.util.Locale;

public class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.ViewHolder> {

    public interface OnMarkIncurred {
        void onClick(EntryEntity entry);
    }

    private List<EntryEntity> items;
    private final OnMarkIncurred onMarkIncurred;

    public EntryAdapter(List<EntryEntity> items, OnMarkIncurred onMarkIncurred) {
        this.items = items;
        this.onMarkIncurred = onMarkIncurred;
    }

    public void setItems(List<EntryEntity> items) {
        this.items = items;
        notifyDataSetChanged();
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

        boolean incurred = "incurred".equals(e.status);
        boolean isIncome = "income".equals(e.itemType);
        double amount = incurred && e.actualAmount != null ? e.actualAmount : e.plannedAmount;
        String incurredWord = isIncome ? "Received" : "Paid";
        String prefix = incurred ? incurredWord : "Planned";
        holder.status.setText(String.format(Locale.UK, "%s: £%.2f", prefix, amount));
        // Color always reflects income/expense; bold marks it as settled (paid/received).
        holder.status.setTextColor(Util.colorForItemType(holder.itemView.getContext(), e.itemType));
        holder.status.setTypeface(null, incurred ? Typeface.BOLD : Typeface.NORMAL);

        holder.markIncurredButton.setText(incurred ? "Edit" : (isIncome ? "Mark received" : "Mark paid"));
        holder.markIncurredButton.setOnClickListener(v -> onMarkIncurred.onClick(e));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, status;
        Button markIncurredButton;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            status = itemView.findViewById(R.id.status);
            markIncurredButton = itemView.findViewById(R.id.markIncurredButton);
        }
    }
}
