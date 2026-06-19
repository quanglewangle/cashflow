package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.CardPurchase;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.EntryEntity;
import com.quanglewangle.peter.cashflow.data.RecurringItemEntity;

import java.text.DateFormatSymbols;
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

    public interface OnCardPurchaseClick {
        void onClick(CardPurchase purchase);
    }

    private static final int TYPE_ITEM          = 0;
    private static final int TYPE_TODAY         = 1;
    private static final int TYPE_CARD_PURCHASE = 2;
    private static final int TYPE_CHECKPOINT    = 3;

    private static class TodayMarker {
        final int day;
        final double balance;
        TodayMarker(int day, double balance) { this.day = day; this.balance = balance; }
    }

    private static class CheckpointMarker {
        final int day;
        final double balance;
        CheckpointMarker(int day, double balance) { this.day = day; this.balance = balance; }
    }

    private List<RecurringItemEntity> items = new ArrayList<>();
    private List<EntryEntity> oneOffEntries = new ArrayList<>();
    private List<CardPurchase> cardPurchases = new ArrayList<>();
    // Maps recurringItemId → entry plannedAmount for the displayed month.
    // When present, overrides the recurring item's defaultAmount in the running balance.
    private java.util.Map<Long, Double> entryAmounts = new java.util.HashMap<>();
    // Maps recurringItemId → entry due_day for the displayed month.
    // Entries can have a different due_day than the template (e.g. after a template edit),
    // so we use the entry's day for positioning to match what the server uses.
    private java.util.Map<Long, Integer> entryDueDays = new java.util.HashMap<>();
    // Maps recurringItemId → entry status ("planned" / "incurred") for the displayed month.
    private java.util.Map<Long, String> entryStatuses = new java.util.HashMap<>();

    // sortedContentRows = RecurringItemEntity | EntryEntity, sorted by day
    private List<Object> sortedContentRows = new ArrayList<>();
    private double[] runningBalances = new double[0]; // parallel to sortedContentRows
    private List<Object> displayRows = new ArrayList<>(); // sortedContentRows + TodayMarker

    // Checkpoint: most recent balance snapshot at or before the displayed month
    private int checkpointYear = 0;
    private int checkpointMonth = 0;
    private int checkpointDay = 0;
    private double checkpointBalance = Double.NaN;
    // Server-computed brought-forward used as fallback when no checkpoint chains to this month
    private double serverBroughtForward = Double.NaN;

    private List<CreditCardEntity> creditCards = new ArrayList<>();
    private final OnItemClick onClick;
    private final OnTodayClick onTodayClick;
    private OnCardPurchaseClick onCardPurchaseClick;
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

    public void setOnCardPurchaseClick(OnCardPurchaseClick listener) {
        this.onCardPurchaseClick = listener;
    }

    public void setCheckpoint(int year, int month, int day, double balance) {
        this.checkpointYear = year;
        this.checkpointMonth = month;
        this.checkpointDay = day;
        this.checkpointBalance = balance;
        rebuild();
    }

    public void setBroughtForward(double bf) {
        this.serverBroughtForward = bf;
        rebuild();
    }

    public void setMonth(int year, int month) {
        this.displayYear = year;
        this.displayMonth = month;
        setItems(items);
    }

    public void setItems(List<RecurringItemEntity> items) {
        this.items = new ArrayList<>(items);
        rebuild();
    }

    public void setOneOffEntries(List<EntryEntity> entries) {
        this.oneOffEntries = new ArrayList<>(entries);
        rebuild();
    }

    public void setCardPurchases(List<CardPurchase> purchases) {
        this.cardPurchases = new ArrayList<>(purchases);
        rebuild();
    }

    /** Pass all entries for the displayed month so recurring items use their
     *  actual planned amount (reflecting real purchases) instead of the default. */
    public void setEntryAmounts(List<EntryEntity> allEntries) {
        entryAmounts = new java.util.HashMap<>();
        entryDueDays = new java.util.HashMap<>();
        entryStatuses = new java.util.HashMap<>();
        for (EntryEntity e : allEntries) {
            if (e.recurringItemId != null) {
                double amount = e.actualAmount != null ? e.actualAmount : e.plannedAmount;
                entryAmounts.put(e.recurringItemId, amount);
                if (e.dueDay != null) entryDueDays.put(e.recurringItemId, e.dueDay);
                if (e.status != null) entryStatuses.put(e.recurringItemId, e.status);
            }
        }
        rebuild();
    }

    public double getChainedBroughtForward() {
        return computeChainedBroughtForward();
    }

    public double getFinalRunningBalance() {
        for (int i = runningBalances.length - 1; i >= 0; i--) {
            if (!Double.isNaN(runningBalances[i])) return runningBalances[i];
        }
        return computeChainedBroughtForward();
    }

    // ---- Core rebuild ----

    private void rebuild() {
        buildSortedContentRows();
        recomputeRunningBalances();
        buildDisplayRows();
        notifyDataSetChanged();
    }

    private void buildSortedContentRows() {
        sortedContentRows = new ArrayList<>();
        for (RecurringItemEntity item : items) {
            if (effectiveDay(item) > 0) sortedContentRows.add(item);
        }
        for (EntryEntity e : oneOffEntries) {
            sortedContentRows.add(e);
        }
        for (CardPurchase p : cardPurchases) {
            sortedContentRows.add(p);
        }
        sortedContentRows.sort(Comparator.comparingInt(this::dayOf));
    }

    private int dayOf(Object row) {
        if (row instanceof RecurringItemEntity) return effectiveDay((RecurringItemEntity) row);
        if (row instanceof EntryEntity) {
            EntryEntity e = (EntryEntity) row;
            return e.dueDay != null ? e.dueDay : 32; // after all dated items
        }
        if (row instanceof CardPurchase) {
            CardPurchase p = (CardPurchase) row;
            if (p.purchaseDate != null && p.purchaseDate.length() >= 10) {
                try { return Integer.parseInt(p.purchaseDate.substring(8, 10)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return 32;
    }

    private void recomputeRunningBalances() {
        runningBalances = new double[sortedContentRows.size()];
        double broughtFwd = computeChainedBroughtForward();
        double balance = Double.isNaN(broughtFwd) ? Double.NaN : broughtFwd;

        int suppressBefore = (checkpointYear == displayYear && checkpointMonth == displayMonth)
                ? checkpointDay : 0;

        for (int i = 0; i < sortedContentRows.size(); i++) {
            Object row = sortedContentRows.get(i);
            int day = dayOf(row);
            boolean show = suppressBefore == 0 || day >= suppressBefore;
            if (show && !Double.isNaN(balance)) {
                double amount = effectiveAmount(row);
                if (!Double.isNaN(amount)) {
                    if (isIncome(row)) balance += amount;
                    else balance -= amount;
                }
            }
            runningBalances[i] = show ? balance : Double.NaN;
        }
    }

    private void buildDisplayRows() {
        displayRows = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        boolean isCurrentMonth = displayYear == now.get(Calendar.YEAR)
                && displayMonth == now.get(Calendar.MONTH) + 1;
        int todayDay = now.get(Calendar.DAY_OF_MONTH);
        boolean todayInserted = false;

        boolean hasCheckpointHere = checkpointYear == displayYear && checkpointMonth == displayMonth;
        boolean checkpointInserted = false;

        for (Object row : sortedContentRows) {
            int day = dayOf(row);
            if (hasCheckpointHere && !checkpointInserted && day >= checkpointDay) {
                displayRows.add(new CheckpointMarker(checkpointDay, checkpointBalance));
                checkpointInserted = true;
            }
            if (isCurrentMonth && !todayInserted && day >= todayDay) {
                displayRows.add(new TodayMarker(todayDay, todayBalance(todayDay)));
                todayInserted = true;
            }
            displayRows.add(row);
        }
        if (hasCheckpointHere && !checkpointInserted) {
            displayRows.add(new CheckpointMarker(checkpointDay, checkpointBalance));
        }
        if (isCurrentMonth && !todayInserted) {
            displayRows.add(new TodayMarker(todayDay, todayBalance(todayDay)));
        }
    }

    private double todayBalance(int todayDay) {
        double result = computeChainedBroughtForward();
        for (int i = 0; i < sortedContentRows.size(); i++) {
            if (dayOf(sortedContentRows.get(i)) < todayDay && !Double.isNaN(runningBalances[i])) {
                result = runningBalances[i];
            }
        }
        return result;
    }

    // ---- Chaining ----

    private double computeChainedBroughtForward() {
        if (checkpointYear == 0 || Double.isNaN(checkpointBalance)) return serverBroughtForward;

        int displayPeriod    = displayYear * 12 + displayMonth;
        int checkpointPeriod = checkpointYear * 12 + checkpointMonth;

        if (displayPeriod < checkpointPeriod) return Double.NaN;
        // For the checkpoint's own month, anchor to the recorded balance.
        if (displayPeriod == checkpointPeriod) return checkpointBalance;
        // For any later month, the server's value is authoritative — it has real
        // entry amounts for past periods; client-side monthNet() only knows template
        // defaults and diverges from the carried-forward shown on the previous month.
        return serverBroughtForward;
    }

    /** Net of active recurring items in (year, month) with effectiveDay >= fromDay. */
    private double monthNet(int year, int month, int fromDay) {
        double net = 0;
        for (RecurringItemEntity item : items) {
            if (!item.active || item.defaultAmount == null) continue;
            int day = effectiveDayForMonth(item, year, month);
            if (day > 0 && day >= fromDay) {
                if ("income".equals(item.itemType)) net += item.defaultAmount;
                else net -= item.defaultAmount;
            }
        }
        return net;
    }

    // ---- Row helpers ----

    private double effectiveAmount(Object row) {
        if (row instanceof RecurringItemEntity) {
            RecurringItemEntity item = (RecurringItemEntity) row;
            Double entryAmount = entryAmounts.get(item.id);
            if (entryAmount != null) return entryAmount;
            return item.defaultAmount != null ? item.defaultAmount : Double.NaN;
        }
        if (row instanceof EntryEntity) {
            EntryEntity e = (EntryEntity) row;
            return e.actualAmount != null ? e.actualAmount : e.plannedAmount;
        }
        // CardPurchase: informational only, never affects running balance
        return Double.NaN;
    }

    private boolean isIncome(Object row) {
        if (row instanceof RecurringItemEntity) return "income".equals(((RecurringItemEntity) row).itemType);
        if (row instanceof EntryEntity) return "income".equals(((EntryEntity) row).itemType);
        return false;
    }

    private int effectiveDay(RecurringItemEntity item) {
        // Prefer the entry's actual due_day for the displayed month — it reflects any
        // due-date changes made after entry generation, and matches what the server uses.
        Integer entryDay = entryDueDays.get(item.id);
        if (entryDay != null) return entryDay;
        return effectiveDayForMonth(item, displayYear, displayMonth);
    }

    private int effectiveDayForMonth(RecurringItemEntity item, int year, int month) {
        switch (item.frequency == null ? "" : item.frequency) {
            case "monthly": {
                if (item.anchorDate != null && item.anchorDate.length() >= 7) {
                    try {
                        int ay = Integer.parseInt(item.anchorDate.substring(0, 4));
                        int am = Integer.parseInt(item.anchorDate.substring(5, 7));
                        if (ay * 12 + am > year * 12 + month) return -1;
                    } catch (NumberFormatException ignored) {}
                }
                return item.dueDay != null ? item.dueDay : -1;
            }
            case "four_weekly": {
                if (item.anchorDate != null) {
                    int[] days = Util.fourWeeklyDaysInMonth(item.anchorDate, year, month);
                    return days.length > 0 ? days[0] : -1;
                }
                return item.dueDay != null ? item.dueDay : -1;
            }
            case "annual": {
                if (item.targetMonth != null && item.targetMonth == month) {
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

    // ---- RecyclerView ----

    @Override
    public int getItemViewType(int position) {
        Object row = displayRows.get(position);
        if (row instanceof TodayMarker) return TYPE_TODAY;
        if (row instanceof CheckpointMarker) return TYPE_CHECKPOINT;
        if (row instanceof CardPurchase) return TYPE_CARD_PURCHASE;
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TODAY) {
            View v = inf.inflate(R.layout.item_today_divider, parent, false);
            return new TodayViewHolder(v);
        }
        if (viewType == TYPE_CHECKPOINT) {
            View v = inf.inflate(R.layout.item_checkpoint_divider, parent, false);
            return new CheckpointViewHolder(v);
        }
        // TYPE_ITEM and TYPE_CARD_PURCHASE both use the same layout
        View v = inf.inflate(R.layout.item_recurring_item, parent, false);
        return new ItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = displayRows.get(position);

        if (holder instanceof TodayViewHolder) {
            TodayMarker marker = (TodayMarker) row;
            TodayViewHolder tvh = (TodayViewHolder) holder;
            tvh.todayDay.setText(Util.ordinal(marker.day));
            tvh.todayBalance.setText(!Double.isNaN(marker.balance)
                    ? String.format(Locale.UK, "£%.2f", marker.balance) : "");
            tvh.itemView.setOnClickListener(v -> onTodayClick.onTodayClick(marker.day, marker.balance));
            return;
        }

        if (holder instanceof CheckpointViewHolder) {
            CheckpointMarker marker = (CheckpointMarker) row;
            CheckpointViewHolder cvh = (CheckpointViewHolder) holder;
            cvh.checkpointDay.setText(Util.ordinal(marker.day));
            cvh.checkpointBalance.setText(String.format(Locale.UK, "£%.2f", marker.balance));
            cvh.itemView.setOnClickListener(null);
            return;
        }

        ItemViewHolder ivh = (ItemViewHolder) holder;
        int idx = sortedContentRows.indexOf(row);
        double bal = (idx >= 0 && idx < runningBalances.length) ? runningBalances[idx] : Double.NaN;

        android.content.Context ctx = ivh.itemView.getContext();

        if (row instanceof CardPurchase) {
            CardPurchase purchase = (CardPurchase) row;
            int grey = ctx.getColor(R.color.planned);
            ivh.name.setText(purchase.description);
            ivh.name.setTextColor(grey);
            int day = dayOf(purchase);
            ivh.dueDay.setText(day <= 31 ? Util.ordinal(day) : "—");
            ivh.dueDay.setTextColor(grey);
            String cardName = "";
            for (CreditCardEntity c : creditCards) {
                if (c.id == purchase.creditCardId) { cardName = c.name; break; }
            }
            ivh.subtitle.setText(cardName);
            ivh.subtitle.setTextColor(grey);
            ivh.amount.setText(String.format(Locale.UK, "£%.2f", purchase.amount));
            ivh.amount.setTextColor(grey);
            ivh.runningBalance.setVisibility(View.GONE);
            ivh.itemView.setOnClickListener(onCardPurchaseClick != null
                    ? v -> onCardPurchaseClick.onClick(purchase) : null);
            return;
        }

        // Reset colors that may have been greyed by a recycled card purchase row
        ivh.name.setTextColor(ctx.getColor(android.R.color.black));
        ivh.dueDay.setTextColor(ctx.getColor(R.color.colorPrimary));
        ivh.subtitle.setTextColor(ctx.getColor(R.color.planned));

        boolean paidByCard = false;
        if (row instanceof RecurringItemEntity) {
            RecurringItemEntity item = (RecurringItemEntity) row;
            ivh.name.setText(item.name + (item.active ? "" : " (inactive)"));
            ivh.dueDay.setText(effectiveDayLabel(item));
            String subtitle = "annual".equals(item.frequency)
                    ? (item.targetMonth != null ? monthName(item.targetMonth) : "annual") : "";
            ivh.subtitle.setText(subtitle);
            double dispAmount = effectiveAmount(item);
            ivh.amount.setText(!Double.isNaN(dispAmount)
                    ? String.format(Locale.UK, "£%.2f", dispAmount) : "—");
            paidByCard = Util.isChargedToCard(item.creditCardId, item.name, creditCards);
            ivh.amount.setTextColor(Util.colorForAmount(ctx, item.itemType, paidByCard));
            ivh.cardIcon.setVisibility(paidByCard ? View.VISIBLE : View.GONE);
            ivh.itemView.setOnClickListener(v -> onClick.onClick(item));
        } else if (row instanceof EntryEntity) {
            EntryEntity entry = (EntryEntity) row;
            String status = "incurred".equals(entry.status) ? " ✓" : "";
            ivh.name.setText(entry.name + status);
            ivh.dueDay.setText(entry.dueDay != null ? Util.ordinal(entry.dueDay) : "—");
            ivh.subtitle.setText("one-off");
            double amount = effectiveAmount(entry);
            ivh.amount.setText(!Double.isNaN(amount)
                    ? String.format(Locale.UK, "£%.2f", amount) : "—");
            ivh.amount.setTextColor(Util.colorForAmount(ctx, entry.itemType, false));
            ivh.cardIcon.setVisibility(View.GONE);
            ivh.itemView.setOnClickListener(null);
        }

        if (!Double.isNaN(bal)) {
            ivh.runningBalance.setVisibility(View.VISIBLE);
            ivh.runningBalance.setText(String.format(Locale.UK, "Balance: £%.2f", bal));
        } else {
            ivh.runningBalance.setVisibility(View.GONE);
        }

        // Visual treatment for past items
        Calendar now = Calendar.getInstance();
        boolean isCurrentMonth = displayYear == now.get(Calendar.YEAR)
                && displayMonth == now.get(Calendar.MONTH) + 1;
        int todayDay = now.get(Calendar.DAY_OF_MONTH);
        int itemDay = dayOf(row);
        boolean isPast = isCurrentMonth && itemDay < todayDay && itemDay > 0;
        if (isPast) {
            // Dim if: explicitly paid, billed to a card, or suppressed by a checkpoint (NaN balance)
            if (isIncurred(row) || paidByCard || Double.isNaN(bal)) {
                ivh.itemView.setAlpha(0.35f);
            } else {
                // Overdue — full opacity but name in red
                ivh.itemView.setAlpha(1f);
                ivh.name.setTextColor(ctx.getColor(R.color.negative));
            }
        } else {
            ivh.itemView.setAlpha(1f);
        }
    }

    private String monthName(int month) {
        String[] names = new DateFormatSymbols(Locale.UK).getMonths();
        return month >= 1 && month <= 12 ? names[month - 1] : "";
    }

    /** Balance as of right now: all past items plus only today's items already marked incurred. */
    public double getTodayBalance() {
        Calendar now = Calendar.getInstance();
        if (displayYear != now.get(Calendar.YEAR) || displayMonth != now.get(Calendar.MONTH) + 1)
            return Double.NaN;
        int todayDay = now.get(Calendar.DAY_OF_MONTH);
        // Start from the balance just before today's items
        double result = computeChainedBroughtForward();
        for (int i = 0; i < sortedContentRows.size(); i++) {
            if (dayOf(sortedContentRows.get(i)) < todayDay && !Double.isNaN(runningBalances[i]))
                result = runningBalances[i];
        }
        if (Double.isNaN(result)) return Double.NaN;
        // Add only today's items that have actually been marked paid/received
        for (Object row : sortedContentRows) {
            if (dayOf(row) != todayDay) continue;
            if (!isIncurred(row)) continue;
            double amount = effectiveAmount(row);
            if (!Double.isNaN(amount)) {
                if (isIncome(row)) result += amount;
                else result -= amount;
            }
        }
        return result;
    }

    private boolean isIncurred(Object row) {
        if (row instanceof RecurringItemEntity)
            return "incurred".equals(entryStatuses.get(((RecurringItemEntity) row).id));
        if (row instanceof EntryEntity)
            return "incurred".equals(((EntryEntity) row).status);
        return false;
    }

    @Override
    public int getItemCount() {
        return displayRows.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView dueDay, name, subtitle, amount, runningBalance;
        android.widget.ImageView cardIcon;
        ItemViewHolder(View itemView) {
            super(itemView);
            dueDay = itemView.findViewById(R.id.dueDay);
            name = itemView.findViewById(R.id.name);
            subtitle = itemView.findViewById(R.id.subtitle);
            amount = itemView.findViewById(R.id.amount);
            runningBalance = itemView.findViewById(R.id.runningBalance);
            cardIcon = itemView.findViewById(R.id.cardIcon);
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

    static class CheckpointViewHolder extends RecyclerView.ViewHolder {
        TextView checkpointDay, checkpointBalance;
        CheckpointViewHolder(View itemView) {
            super(itemView);
            checkpointDay = itemView.findViewById(R.id.checkpointDay);
            checkpointBalance = itemView.findViewById(R.id.checkpointBalance);
        }
    }
}
