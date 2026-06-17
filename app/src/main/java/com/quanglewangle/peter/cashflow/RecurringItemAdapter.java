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
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RecurringItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClick {
        void onClick(RecurringItemEntity item);
    }

    public interface OnTodayClick {
        void onTodayClick(int day, double balance);
    }

    private static final int TYPE_ITEM  = 0;
    private static final int TYPE_TODAY = 1;

    private static class TodayMarker {
        final int day;
        final double balance;
        TodayMarker(int day, double balance) { this.day = day; this.balance = balance; }
    }

    private List<RecurringItemEntity> items = new ArrayList<>();
    private List<Object> displayRows = new ArrayList<>(); // RecurringItemEntity or TodayMarker
    private double[] runningBalances = new double[0];    // parallel to items, not displayRows
    private double broughtForward = Double.NaN;
    private int checkpointDay = 0;
    private double checkpointBalance = Double.NaN;
    private List<CreditCardEntity> creditCards = new ArrayList<>();
    private final OnItemClick onClick;
    private final OnTodayClick onTodayClick;
    private int displayYear;
    private int displayMonth;

    public RecurringItemAdapter(List<RecurringItemEntity> items, OnItemClick onClick,
                                OnTodayClick onTodayClick, int displayYear, int displayMonth) {
        this.onClick = onClick;
        this.onTodayClick = onTodayClick;
        this.displayYear = displayYear;
        this.displayMonth = displayMonth;
        setItems(items);
    }

    public void setCreditCards(List<CreditCardEntity> creditCards) {
        this.creditCards = creditCards;
        notifyDataSetChanged();
    }

    public void setBroughtForward(double broughtForward) {
        this.broughtForward = broughtForward;
        recomputeRunningBalances();
        buildDisplayRows();
        notifyDataSetChanged();
    }

    public void setCheckpoint(int day, double balance) {
        this.checkpointDay = day;
        this.checkpointBalance = balance;
        recomputeRunningBalances();
        buildDisplayRows();
        notifyDataSetChanged();
    }

    public void setMonth(int year, int month) {
        this.displayYear = year;
        this.displayMonth = month;
        this.broughtForward = Double.NaN;
        this.checkpointDay = 0;
        this.checkpointBalance = Double.NaN;
        setItems(items);
    }

    public void setItems(List<RecurringItemEntity> items) {
        this.items = new ArrayList<>(items);
        this.items.sort(Comparator.comparingInt(i -> {
            int d = effectiveDay(i);
            return d > 0 ? d : Integer.MAX_VALUE;
        }));
        recomputeRunningBalances();
        buildDisplayRows();
        notifyDataSetChanged();
    }

    private void recomputeRunningBalances() {
        runningBalances = new double[items.size()];
        // Seed from checkpoint balance if there is one, otherwise from brought-forward
        double seed = (checkpointDay > 0 && !Double.isNaN(checkpointBalance))
                ? checkpointBalance : broughtForward;
        double balance = Double.isNaN(seed) ? Double.NaN : seed;
        for (int i = 0; i < items.size(); i++) {
            RecurringItemEntity item = items.get(i);
            int day = effectiveDay(item);
            boolean afterCheckpoint = day > 0 && (checkpointDay == 0 || day >= checkpointDay);
            if (afterCheckpoint && !Double.isNaN(balance) && item.defaultAmount != null) {
                if ("income".equals(item.itemType)) balance += item.defaultAmount;
                else balance -= item.defaultAmount;
            }
            runningBalances[i] = afterCheckpoint ? balance : Double.NaN;
        }
    }

    private void buildDisplayRows() {
        displayRows = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        boolean isCurrentMonth = displayYear == now.get(Calendar.YEAR)
                && displayMonth == now.get(Calendar.MONTH) + 1;
        int todayDay = now.get(Calendar.DAY_OF_MONTH);
        boolean todayInserted = false;

        for (int i = 0; i < items.size(); i++) {
            RecurringItemEntity item = items.get(i);
            int day = effectiveDay(item);

            if (isCurrentMonth && !todayInserted && (day < 0 || day >= todayDay)) {
                displayRows.add(new TodayMarker(todayDay, todayBalance(todayDay)));
                todayInserted = true;
            }
            displayRows.add(item);
        }
        if (isCurrentMonth && !todayInserted) {
            displayRows.add(new TodayMarker(todayDay, todayBalance(todayDay)));
        }
    }

    /** Running balance after all items with effectiveDay in [1, todayDay]. */
    private double todayBalance(int todayDay) {
        double result = Double.isNaN(broughtForward) ? Double.NaN : broughtForward;
        for (int i = 0; i < items.size(); i++) {
            int day = effectiveDay(items.get(i));
            if (day > 0 && day < todayDay && !Double.isNaN(runningBalances[i])) {
                result = runningBalances[i];
            }
        }
        return result;
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
                if (i > 0) sb.append(", ");
                sb.append(Util.ordinal(days[i]));
            }
            return sb.toString();
        }
        int d = effectiveDay(item);
        return d > 0 ? Util.ordinal(d) : "—";
    }

    @Override
    public int getItemViewType(int position) {
        return displayRows.get(position) instanceof TodayMarker ? TYPE_TODAY : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TODAY) {
            View v = inf.inflate(R.layout.item_today_divider, parent, false);
            return new TodayViewHolder(v);
        }
        View v = inf.inflate(R.layout.item_recurring_item, parent, false);
        return new ItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TodayViewHolder) {
            TodayMarker marker = (TodayMarker) displayRows.get(position);
            TodayViewHolder tvh = (TodayViewHolder) holder;
            tvh.todayDay.setText(Util.ordinal(marker.day));
            if (!Double.isNaN(marker.balance)) {
                tvh.todayBalance.setText(String.format(Locale.UK, "£%.2f", marker.balance));
            } else {
                tvh.todayBalance.setText("");
            }
            tvh.itemView.setOnClickListener(v -> onTodayClick.onTodayClick(marker.day, marker.balance));
            return;
        }

        RecurringItemEntity item = (RecurringItemEntity) displayRows.get(position);
        // find index in items list to look up running balance
        int itemIndex = items.indexOf(item);

        ItemViewHolder ivh = (ItemViewHolder) holder;
        ivh.name.setText(item.name + (item.active ? "" : " (inactive)"));
        ivh.dueDay.setText(effectiveDayLabel(item));

        StringBuilder subtitle = new StringBuilder(item.frequency);
        if ("annual".equals(item.frequency) && item.targetMonth != null) {
            subtitle.append(" · ").append(monthName(item.targetMonth));
        }
        ivh.subtitle.setText(subtitle.toString());

        ivh.amount.setText(item.defaultAmount != null
                ? String.format(Locale.UK, "£%.2f", item.defaultAmount) : "—");
        boolean paidByCard = Util.isChargedToCard(item.creditCardId, item.name, creditCards);
        ivh.amount.setTextColor(Util.colorForAmount(ivh.itemView.getContext(), item.itemType, paidByCard));

        double bal = (itemIndex >= 0 && itemIndex < runningBalances.length)
                ? runningBalances[itemIndex] : Double.NaN;
        if (!Double.isNaN(bal) && effectiveDay(item) > 0) {
            ivh.runningBalance.setVisibility(View.VISIBLE);
            ivh.runningBalance.setText(String.format(Locale.UK, "Balance: £%.2f", bal));
        } else {
            ivh.runningBalance.setVisibility(View.GONE);
        }

        ivh.itemView.setOnClickListener(v -> onClick.onClick(item));
    }

    private String monthName(int month) {
        String[] names = new java.text.DateFormatSymbols(Locale.UK).getMonths();
        return month >= 1 && month <= 12 ? names[month - 1] : "";
    }

    @Override
    public int getItemCount() {
        return displayRows.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView dueDay, name, subtitle, amount, runningBalance;

        ItemViewHolder(View itemView) {
            super(itemView);
            dueDay = itemView.findViewById(R.id.dueDay);
            name = itemView.findViewById(R.id.name);
            subtitle = itemView.findViewById(R.id.subtitle);
            amount = itemView.findViewById(R.id.amount);
            runningBalance = itemView.findViewById(R.id.runningBalance);
        }
    }

    static class TodayViewHolder extends RecyclerView.ViewHolder {
        TextView todayDay, todayBalance;

        TodayViewHolder(View itemView) {
            super(itemView);
            todayDay = itemView.findViewById(R.id.todayDay);
            todayBalance = itemView.findViewById(R.id.todayBalance);
        }
    }
}
