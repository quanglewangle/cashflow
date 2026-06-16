package com.quanglewangle.peter.cashflow;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private final OnItemClick onClick;

    public CreditCardAdapter(List<CreditCardEntity> items, OnItemClick onClick) {
        this.items = items;
        this.onClick = onClick;
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
        holder.dates.setText("Statement " + Util.ordinal(card.statementDay) + " · Due " + Util.ordinal(card.paymentDueDay));
        holder.itemView.setOnClickListener(v -> onClick.onClick(card));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, dates;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            dates = itemView.findViewById(R.id.dates);
        }
    }
}
