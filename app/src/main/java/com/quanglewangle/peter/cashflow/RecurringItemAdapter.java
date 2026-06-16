package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.RecurringItemEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RecurringItemAdapter extends RecyclerView.Adapter<RecurringItemAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(RecurringItemEntity item);
    }

    private List<RecurringItemEntity> items;
    private List<CreditCardEntity> creditCards = new ArrayList<>();
    private final OnItemClick onClick;

    public RecurringItemAdapter(List<RecurringItemEntity> items, OnItemClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    public void setCreditCards(List<CreditCardEntity> creditCards) {
        this.creditCards = creditCards;
        notifyDataSetChanged();
    }

    public void setItems(List<RecurringItemEntity> items) {
        this.items = new ArrayList<>(items);
        this.items.sort(Comparator.comparingInt(i -> i.dueDay != null ? i.dueDay : Integer.MAX_VALUE));
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

        holder.dueDay.setText(item.dueDay != null ? Util.ordinal(item.dueDay) : "—");

        StringBuilder subtitle = new StringBuilder(item.frequency);
        if ("annual".equals(item.frequency) && item.targetMonth != null) {
            subtitle.append(" · ").append(monthName(item.targetMonth));
        }
        holder.subtitle.setText(subtitle.toString());

        holder.amount.setText(item.defaultAmount != null
                ? String.format(Locale.UK, "£%.2f", item.defaultAmount) : "—");
        boolean paidByCard = Util.isChargedToCard(item.creditCardId, item.name, creditCards);
        holder.amount.setTextColor(Util.colorForAmount(holder.itemView.getContext(), item.itemType, paidByCard));
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
        TextView dueDay, name, subtitle, amount;

        ViewHolder(View itemView) {
            super(itemView);
            dueDay = itemView.findViewById(R.id.dueDay);
            name = itemView.findViewById(R.id.name);
            subtitle = itemView.findViewById(R.id.subtitle);
            amount = itemView.findViewById(R.id.amount);
        }
    }
}
