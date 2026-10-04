package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.Holiday;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Holidays on the Cards tab, below the cards: a header row whose + adds one,
 *  then one row per holiday. Reuses the card row layout, like the savings rows. */
public class HolidayAdapter extends RecyclerView.Adapter<HolidayAdapter.ViewHolder> {

    public interface OnHolidayClick {
        /** null = add a new holiday. */
        void onClick(Holiday holiday);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_HOLIDAY = 1;

    private List<Holiday> items = new ArrayList<>();
    private Map<Long, String> cardNames = new HashMap<>();
    private final OnHolidayClick onClick;

    public HolidayAdapter(OnHolidayClick onClick) {
        this.onClick = onClick;
    }

    public void setItems(List<Holiday> items, Map<Long, String> cardNames) {
        this.items = items;
        this.cardNames = cardNames;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_HOLIDAY;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_credit_card, parent, false);
        return new ViewHolder(v, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position == 0) {
            holder.name.setText("Holidays");
            holder.dates.setText(items.isEmpty()
                    ? "None planned · tap + to add one"
                    : "Spending per day, on top of card sundries");
            holder.itemView.setOnClickListener(v -> onClick.onClick(null));
            holder.editButton.setOnClickListener(v -> onClick.onClick(null));
            return;
        }
        Holiday h = items.get(position - 1);
        holder.name.setText(h.name + "  £" + String.format(Locale.UK, "%,.0f", h.total));
        String card = cardNames.containsKey(h.creditCardId) ? cardNames.get(h.creditCardId) : "card";
        String status;
        if (h.remaining <= 0.005) status = " · over";
        else if (h.remaining < h.total - 0.005) status = String.format(Locale.UK, " · £%,.0f left", h.remaining);
        else status = "";
        holder.dates.setText(dateRange(h.startDate, h.endDate)
                + String.format(Locale.UK, " · £%,.0f/day on ", h.perDay) + card + status);
        holder.itemView.setOnClickListener(v -> onClick.onClick(h));
        holder.editButton.setOnClickListener(v -> onClick.onClick(h));
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    /** "10–14 Oct", or "28 Oct–3 Nov" across a month end. */
    static String dateRange(String start, String end) {
        String[] months = new DateFormatSymbols(Locale.UK).getShortMonths();
        try {
            int sm = Integer.parseInt(start.substring(5, 7)), sd = Integer.parseInt(start.substring(8, 10));
            int em = Integer.parseInt(end.substring(5, 7)), ed = Integer.parseInt(end.substring(8, 10));
            if (start.equals(end)) return sd + " " + months[sm - 1];
            if (sm == em) return sd + "–" + ed + " " + months[em - 1];
            return sd + " " + months[sm - 1] + "–" + ed + " " + months[em - 1];
        } catch (RuntimeException e) {
            return start + " – " + end;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, dates;
        ImageButton editButton;

        ViewHolder(View itemView, int viewType) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            dates = itemView.findViewById(R.id.dates);
            editButton = itemView.findViewById(R.id.editButton);
            if (viewType == TYPE_HEADER) {
                editButton.setImageResource(R.drawable.ic_add);
                editButton.setContentDescription("Add holiday");
            } else {
                editButton.setContentDescription("Edit holiday");
            }
            itemView.findViewById(R.id.checkpointsButton).setVisibility(View.GONE);
            itemView.findViewById(R.id.purchasesButton).setVisibility(View.GONE);
            itemView.findViewById(R.id.subscriptionsButton).setVisibility(View.GONE);
        }
    }
}
