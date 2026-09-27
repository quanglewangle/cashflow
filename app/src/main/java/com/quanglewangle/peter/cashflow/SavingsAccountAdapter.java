package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.SavingsAccount;

import java.util.List;
import java.util.Locale;

/** Savings accounts on the Cards tab, above the cards -- reuses the card row
 *  layout with only the edit icon kept. */
public class SavingsAccountAdapter extends RecyclerView.Adapter<SavingsAccountAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(SavingsAccount account);
    }

    private List<SavingsAccount> items;
    /** Tapping the row shows the month-by-month projection. */
    private final OnItemClick onViewProjection;
    /** The edit icon re-anchors the balance and changes rate/interest day. */
    private final OnItemClick onEdit;

    public SavingsAccountAdapter(List<SavingsAccount> items, OnItemClick onViewProjection, OnItemClick onEdit) {
        this.items = items;
        this.onViewProjection = onViewProjection;
        this.onEdit = onEdit;
    }

    public void setItems(List<SavingsAccount> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_credit_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavingsAccount a = items.get(position);
        holder.name.setText(a.name + " savings  £" + String.format(Locale.UK, "%,.2f", a.currentBalance));
        holder.dates.setText(String.format(Locale.UK, "%.2f", a.interestRate) + "% AER · interest on the "
                + Util.ordinal(a.interestDay));
        holder.itemView.setOnClickListener(v -> onViewProjection.onClick(a));
        holder.editButton.setOnClickListener(v -> onEdit.onClick(a));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, dates;
        ImageButton editButton;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            dates = itemView.findViewById(R.id.dates);
            editButton = itemView.findViewById(R.id.editButton);
            editButton.setContentDescription("Edit savings account");
            itemView.findViewById(R.id.checkpointsButton).setVisibility(View.GONE);
            itemView.findViewById(R.id.purchasesButton).setVisibility(View.GONE);
            itemView.findViewById(R.id.subscriptionsButton).setVisibility(View.GONE);
        }
    }
}
