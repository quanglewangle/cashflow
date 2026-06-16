package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.RecurringItemEntity;

import java.util.List;
import java.util.Locale;

public class RecurringItemAdapter extends RecyclerView.Adapter<RecurringItemAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(RecurringItemEntity item);
    }

    private List<RecurringItemEntity> items;
    private final OnItemClick onClick;

    public RecurringItemAdapter(List<RecurringItemEntity> items, OnItemClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    public void setItems(List<RecurringItemEntity> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recurring_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecurringItemEntity item = items.get(position);
        holder.name.setText(item.name + (item.active ? "" : " (inactive)"));

        StringBuilder subtitle = new StringBuilder(item.frequency);
        if (item.dueDay != null) subtitle.append(" · due ").append(Util.ordinal(item.dueDay));
        if ("annual".equals(item.frequency) && item.targetMonth != null) {
            subtitle.append(" · ").append(monthName(item.targetMonth));
        }
        holder.subtitle.setText(subtitle.toString());

        holder.amount.setText(item.defaultAmount != null
                ? String.format(Locale.UK, "£%.2f", item.defaultAmount) : "—");
        holder.amount.setTextColor(Util.colorForItemType(holder.itemView.getContext(), item.itemType));
        holder.itemView.setOnClickListener(v -> onClick.onClick(item));
    }

    private String monthName(int month) {
        String[] names = new java.text.DateFormatSymbols(Locale.UK).getMonths();
        return month >= 1 && month <= 12 ? names[month - 1] : "";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, subtitle, amount;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            subtitle = itemView.findViewById(R.id.subtitle);
            amount = itemView.findViewById(R.id.amount);
        }
    }
}
