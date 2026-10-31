package com.quanglewangle.peter.cashflow;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.PresentOccasion;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Presents on the Cards tab: a header row whose + adds an occasion, then one row
 *  per occasion showing what's been spent toward its next date out of its
 *  allowance. An overspent occasion's row is pale red, like a "!" entry. */
public class PresentOccasionAdapter extends RecyclerView.Adapter<PresentOccasionAdapter.ViewHolder> {

    public interface OnOccasionClick {
        /** null = add a new occasion. */
        void onClick(PresentOccasion occasion);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_OCCASION = 1;

    private List<PresentOccasion> items = new ArrayList<>();
    private Map<Long, String> cardNames = new HashMap<>();
    private final OnOccasionClick onClick;

    public PresentOccasionAdapter(OnOccasionClick onClick) {
        this.onClick = onClick;
    }

    public void setItems(List<PresentOccasion> items, Map<Long, String> cardNames) {
        this.items = items;
        this.cardNames = cardNames;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_OCCASION;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_credit_card, parent, false);
        return new ViewHolder(v, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.itemView.setBackground(holder.defaultBackground);
        if (position == 0) {
            holder.name.setText("Presents");
            holder.dates.setText(items.isEmpty()
                    ? "No occasions · tap + to add one"
                    : "\"Presents\" purchases count toward the next occasion");
            holder.itemView.setOnClickListener(v -> onClick.onClick(null));
            holder.editButton.setOnClickListener(v -> onClick.onClick(null));
            return;
        }
        PresentOccasion p = items.get(position - 1);
        holder.name.setText(p.name + "  £" + String.format(Locale.UK, "%,.0f", p.allowance));
        String card = cardNames.containsKey(p.creditCardId) ? cardNames.get(p.creditCardId) : "card";
        String status;
        if (p.overspent) status = String.format(Locale.UK, " · over by £%,.0f", p.spent - p.allowance);
        else if (p.spent > 0.005) status = String.format(Locale.UK, " · £%,.0f spent, £%,.0f left", p.spent, p.remaining);
        else status = " · nothing spent yet";
        holder.dates.setText(dayMonth(p.day, p.month) + " on " + card + status);
        if (p.overspent) {
            holder.itemView.setBackgroundColor(holder.itemView.getContext().getColor(R.color.highlightFlagged));
        }
        holder.itemView.setOnClickListener(v -> onClick.onClick(p));
        holder.editButton.setOnClickListener(v -> onClick.onClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    /** "25 Dec". */
    static String dayMonth(int day, int month) {
        return day + " " + new DateFormatSymbols(Locale.UK).getShortMonths()[month - 1];
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, dates;
        ImageButton editButton;
        final Drawable defaultBackground;

        ViewHolder(View itemView, int viewType) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            dates = itemView.findViewById(R.id.dates);
            editButton = itemView.findViewById(R.id.editButton);
            defaultBackground = itemView.getBackground();
            if (viewType == TYPE_HEADER) {
                editButton.setImageResource(R.drawable.ic_add);
                editButton.setContentDescription("Add present occasion");
            } else {
                editButton.setContentDescription("Edit present occasion");
            }
            itemView.findViewById(R.id.checkpointsButton).setVisibility(View.GONE);
            itemView.findViewById(R.id.purchasesButton).setVisibility(View.GONE);
            itemView.findViewById(R.id.subscriptionsButton).setVisibility(View.GONE);
        }
    }
}
