package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;

import java.util.List;

public class CreditCardAdapter extends RecyclerView.Adapter<CreditCardAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(CreditCardEntity card);
    }

    private List<CreditCardEntity> items;
    /** Tapping the row logs a purchase -- the common case, day to day. */
    private final OnItemClick onLogPurchase;
    /** The subscriptions icon manages recurring charges on this card. */
    private final OnItemClick onSubscriptions;
    /** The edit icon is the rare-case path to changing the card's own parameters. */
    private final OnItemClick onEdit;

    public CreditCardAdapter(List<CreditCardEntity> items, OnItemClick onLogPurchase,
                             OnItemClick onSubscriptions, OnItemClick onEdit) {
        this.items = items;
        this.onLogPurchase = onLogPurchase;
        this.onSubscriptions = onSubscriptions;
        this.onEdit = onEdit;
    }

    public void setItems(List<CreditCardEntity> items) {
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
        CreditCardEntity card = items.get(position);
        holder.name.setText(card.name);
        String monthsLabel = card.paymentDueMonthOffset == 1 ? "1 month later" : card.paymentDueMonthOffset + " months later";
        holder.dates.setText("Statement " + Util.ordinal(card.statementDay) + " · Due " + Util.ordinal(card.paymentDueDay) + " (" + monthsLabel + ")");
        holder.itemView.setOnClickListener(v -> onLogPurchase.onClick(card));
        holder.subscriptionsButton.setOnClickListener(v -> onSubscriptions.onClick(card));
        holder.editButton.setOnClickListener(v -> onEdit.onClick(card));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, dates;
        ImageButton subscriptionsButton, editButton;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            dates = itemView.findViewById(R.id.dates);
            subscriptionsButton = itemView.findViewById(R.id.subscriptionsButton);
            editButton = itemView.findViewById(R.id.editButton);
        }
    }
}
