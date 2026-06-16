package com.quanglewangle.peter.cashflow;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> {

    private List<GridRow> rows;

    public GridAdapter(List<GridRow> rows) {
        this.rows = rows;
    }

    public void setRows(List<GridRow> rows) {
        this.rows = rows;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GridRow row = rows.get(position);
        holder.label.setText(row.label);
        holder.label.setTypeface(null, row.bold ? Typeface.BOLD : Typeface.NORMAL);
        if (row.paidByCard) {
            holder.label.setTextColor(Util.colorForAmount(holder.itemView.getContext(), row.itemType, true));
        } else {
            holder.label.setTextColor(Util.colorForItemType(holder.itemView.getContext(), null));
        }

        holder.cellsContainer.removeAllViews();
        for (String cell : row.cells) {
            TextView tv = new TextView(holder.itemView.getContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tv.setText(cell);
            tv.setTextSize(13);
            tv.setTypeface(null, row.bold ? Typeface.BOLD : Typeface.NORMAL);
            tv.setTextColor(Util.colorForAmount(holder.itemView.getContext(), row.itemType, row.paidByCard));
            holder.cellsContainer.addView(tv);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView label;
        LinearLayout cellsContainer;

        ViewHolder(View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.label);
            cellsContainer = itemView.findViewById(R.id.cellsContainer);
        }
    }
}
