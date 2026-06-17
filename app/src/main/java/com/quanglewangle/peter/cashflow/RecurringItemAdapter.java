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
    private int displayYear;
    private int displayMonth;

    public RecurringItemAdapter(List<RecurringItemEntity> items, OnItemClick onClick,
                                int displayYear, int displayMonth) {
        this.onClick = onClick;
        this.displayYear = displayYear;
        this.displayMonth = displayMonth;
        setItems(items);
    }

    public void setCreditCards(List<CreditCardEntity> creditCards) {
        this.creditCards = creditCards;
        notifyDataSetChanged();
    }

    public void setMonth(int year, int month) {
        this.displayYear = year;
        this.displayMonth = month;
        setItems(items); // re-sort for new month
    }

    public void setItems(List<RecurringItemEntity> items) {
        this.items = new ArrayList<>(items);
        this.items.sort(Comparator.comparingInt(i -> {
            int d = effectiveDay(i);
            return d > 0 ? d : Integer.MAX_VALUE;
        }));
        notifyDataSetChanged();
    }

    /** Returns the day-of-month this item falls on in (displayYear, displayMonth),
     *  or -1 if it doesn't apply / has no date. */
    private int effectiveDay(RecurringItemEntity item) {
        switch (item.frequency == null ? "" : item.frequency) {
            case "monthly": {
                if (item.anchorDate != null && item.anchorDate.length() >= 7) {
                    try {
                        int ay = Integer.parseInt(item.anchorDate.substring(0, 4));
                        int am = Integer.parseInt(item.anchorDate.substring(5, 7));
                        if (ay * 12 + am > displayYear * 12 + displayMonth) return -1;
                    } catch (NumberFormatException ignored) {}
                }
                return item.dueDay != null ? item.dueDay : -1;
            }
            case "four_weekly": {
                if (item.anchorDate != null) {
                    int[] days = Util.fourWeeklyDaysInMonth(item.anchorDate, displayYear, displayMonth);
                    return days.length > 0 ? days[0] : -1;
                }
                return item.dueDay != null ? item.dueDay : -1;
            }
            case "annual": {
                if (item.targetMonth != null && item.targetMonth == displayMonth) {
                    return item.dueDay != null ? item.dueDay : -1;
                }
                return -1;
            }
            default:
                return -1;
        }
    }

    private String effectiveDayLabel(RecurringItemEntity item) {
        if ("four_weekly".equals(item.frequency) && item.anchorDate != null) {
            int[] days = Util.fourWeeklyDaysInMonth(item.anchorDate, displayYear, displayMonth);
            if (days.length == 0) return "—";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < days.length; i++) {
                if (i > 0) sb.append(", ") ;
                sb.append(Util.ordinal(days[i]));
            }
            return sb.toString();
        }
        int d = effectiveDay(item);
        return d > 0 ? Util.ordinal(d) : "—";
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
        holder.dueDay.setText(effectiveDayLabel(item));

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
