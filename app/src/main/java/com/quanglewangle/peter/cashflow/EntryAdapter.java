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
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class EntryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnMarkIncurred {
        void onClick(EntryEntity entry);
    }

    public interface OnDelete {
        void onDelete(EntryEntity entry);
    }

    private static final int TYPE_ENTRY      = 0;
    private static final int TYPE_CHECKPOINT = 1;

    private static class CheckpointMarker {
        final int day;
        final double balance;
        CheckpointMarker(int day, double balance) { this.day = day; this.balance = balance; }
    }

    private List<EntryEntity> items = new ArrayList<>();
    private List<Object> displayRows = new ArrayList<>(); // EntryEntity | CheckpointMarker
    private double[] runningBalances = new double[0];
    private double broughtForward = Double.NaN;
    private int checkpointDay = 0;
    private double checkpointBalance = Double.NaN;
    private final OnMarkIncurred onMarkIncurred;
    private final OnDelete onDelete;
    private int displayYear;
    private int displayMonth;

    public EntryAdapter(List<EntryEntity> items, OnMarkIncurred onMarkIncurred, OnDelete onDelete,
                        int displayYear, int displayMonth) {
        this.displayYear = displayYear;
        this.displayMonth = displayMonth;
        this.onMarkIncurred = onMarkIncurred;
        this.onDelete = onDelete;
        setItems(items);

    }

    public void setBroughtForward(double broughtForward) {
        this.broughtForward = broughtForward;
        recomputeRunningBalances();
        notifyDataSetChanged();
    }

    public void setCheckpoint(int day, double balance) {
        this.checkpointDay = day;
        this.checkpointBalance = balance;
        recomputeRunningBalances();
        buildDisplayRows();
        notifyDataSetChanged();
    }

    public void setItems(List<EntryEntity> items) {
        this.items = new ArrayList<>(items);
        this.items.sort(Comparator.comparingInt(e -> e.dueDay != null ? e.dueDay : Integer.MAX_VALUE));
        recomputeRunningBalances();
        buildDisplayRows();
        notifyDataSetChanged();
    }

    private void recomputeRunningBalances() {
        runningBalances = new double[items.size()];
        boolean hasCheckpoint = checkpointDay > 0 && !Double.isNaN(checkpointBalance);
        // Anchor to the real recorded balance from the checkpoint; fall back to
        // server-projected brought-forward when no checkpoint exists for this month.
        double balance = hasCheckpoint ? checkpointBalance
                : (Double.isNaN(broughtForward) ? Double.NaN : broughtForward);
        for (int i = 0; i < items.size(); i++) {
            EntryEntity e = items.get(i);
            int day = e.dueDay != null ? e.dueDay : Integer.MAX_VALUE;
            // Mirrors the server's periodNetFrom: an entry due exactly on the
            // checkpoint day but already incurred is baked into the checkpoint
            // balance already, so it must not be added again here.
            boolean isPaid = "incurred".equals(e.status);
            boolean show = !hasCheckpoint || day > checkpointDay || (day == checkpointDay && !isPaid);
            if (show && !Double.isNaN(balance)) {
                double amount = e.actualAmount != null && "incurred".equals(e.status)
                        ? e.actualAmount : e.plannedAmount;
                if ("income".equals(e.itemType)) balance += amount;
                else balance -= amount;
            }
            runningBalances[i] = show ? balance : Double.NaN;
        }
    }

    private void buildDisplayRows() {
        displayRows = new ArrayList<>();
        boolean checkpointInserted = checkpointDay <= 0 || Double.isNaN(checkpointBalance);
        for (EntryEntity e : items) {
            int day = e.dueDay != null ? e.dueDay : Integer.MAX_VALUE;
            boolean isPaid = "incurred".equals(e.status);
            // Insert checkpoint before the first entry that is either past the checkpoint day,
            // or on the checkpoint day but not yet paid.
            if (!checkpointInserted && (day > checkpointDay || (day == checkpointDay && !isPaid))) {
                displayRows.add(new CheckpointMarker(checkpointDay, checkpointBalance));
                checkpointInserted = true;
            }
            displayRows.add(e);
        }
        if (!checkpointInserted) {
            displayRows.add(new CheckpointMarker(checkpointDay, checkpointBalance));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return displayRows.get(position) instanceof CheckpointMarker ? TYPE_CHECKPOINT : TYPE_ENTRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_CHECKPOINT) {
            View v = inf.inflate(R.layout.item_checkpoint_divider, parent, false);
            return new CheckpointViewHolder(v);
        }
        View v = inf.inflate(R.layout.item_entry, parent, false);
        return new EntryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = displayRows.get(position);

        if (holder instanceof CheckpointViewHolder) {
            CheckpointMarker marker = (CheckpointMarker) row;
            CheckpointViewHolder cvh = (CheckpointViewHolder) holder;
            cvh.checkpointDay.setText(Util.ordinal(marker.day));
            cvh.checkpointBalance.setText(String.format(Locale.UK, "£%.2f", marker.balance));
            return;
        }

        EntryEntity e = (EntryEntity) row;
        EntryViewHolder vh = (EntryViewHolder) holder;

        vh.name.setText(e.name);
        vh.dueDay.setText(e.dueDay != null ? Util.ordinal(e.dueDay) : "—");

        boolean incurred = "incurred".equals(e.status);
        boolean isIncome = "income".equals(e.itemType);
        double amount = incurred && e.actualAmount != null ? e.actualAmount : e.plannedAmount;
        String prefix = incurred ? (isIncome ? "Received" : "Paid") : "Planned";
        vh.status.setText(String.format(Locale.UK, "%s: £%.2f", prefix, amount));
        vh.status.setTextColor(Util.colorForItemType(vh.itemView.getContext(), e.itemType));
        vh.status.setTypeface(null, incurred ? Typeface.BOLD : Typeface.NORMAL);

        int idx = items.indexOf(e);
        double bal = (idx >= 0 && idx < runningBalances.length) ? runningBalances[idx] : Double.NaN;
        if (!Double.isNaN(bal)) {
            vh.runningBalance.setVisibility(View.VISIBLE);
            vh.runningBalance.setText(String.format(Locale.UK, "Balance: £%.2f", bal));
        } else {
            vh.runningBalance.setVisibility(View.GONE);
        }

        vh.markIncurredButton.setText(incurred ? "Edit" : (isIncome ? "Mark received" : "Mark paid"));
        vh.markIncurredButton.setOnClickListener(v -> onMarkIncurred.onClick(e));
        vh.itemView.setOnLongClickListener(v -> { onDelete.onDelete(e); return true; });

        if (e.creditCardId != null) {
            vh.cardIcon.setVisibility(View.VISIBLE);
            android.graphics.drawable.Drawable d =
                    androidx.core.graphics.drawable.DrawableCompat.wrap(
                            vh.itemView.getContext().getDrawable(R.drawable.ic_cards)).mutate();
            androidx.core.graphics.drawable.DrawableCompat.setTint(
                    d, vh.itemView.getContext().getColor(R.color.incurred));
            vh.cardIcon.setImageDrawable(d);
        } else {
            vh.cardIcon.setVisibility(View.GONE);
        }

        // Dim paid past entries; highlight overdue ones in red
        Calendar now = Calendar.getInstance();
        boolean isCurrentMonth = displayYear == now.get(Calendar.YEAR)
                && displayMonth == now.get(Calendar.MONTH) + 1;
        int todayDay = now.get(Calendar.DAY_OF_MONTH);
        int itemDay = e.dueDay != null ? e.dueDay : Integer.MAX_VALUE;
        boolean isAtOrBefore = isCurrentMonth && itemDay <= todayDay;
        boolean isBeforeToday = isCurrentMonth && itemDay < todayDay;
        if (isAtOrBefore && incurred) {
            vh.itemView.setAlpha(0.35f);
        } else if (isBeforeToday) {
            vh.itemView.setAlpha(1f);
            vh.name.setTextColor(vh.itemView.getContext().getColor(R.color.negative));
        } else {
            vh.itemView.setAlpha(1f);
        }
    }

    @Override
    public int getItemCount() {
        return displayRows.size();
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView dueDay, name, status, runningBalance;
        Button markIncurredButton;
        android.widget.ImageView cardIcon;

        EntryViewHolder(View itemView) {
            super(itemView);
            dueDay = itemView.findViewById(R.id.dueDay);
            name = itemView.findViewById(R.id.name);
            status = itemView.findViewById(R.id.status);
            runningBalance = itemView.findViewById(R.id.runningBalance);
            markIncurredButton = itemView.findViewById(R.id.markIncurredButton);
            cardIcon = itemView.findViewById(R.id.cardIcon);
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
