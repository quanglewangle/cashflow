package com.quanglewangle.peter.cashflow;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.quanglewangle.peter.cashflow.data.ForecastDanger;

import java.text.DateFormatSymbols;
import java.util.List;
import java.util.Locale;

public class DangerAdapter extends RecyclerView.Adapter<DangerAdapter.ViewHolder> {

    private static final double DANGER_THRESHOLD = -1000.0;
    private static final double WARNING_THRESHOLD = 0.0;

    private List<ForecastDanger> rows;

    public DangerAdapter(List<ForecastDanger> rows) {
        this.rows = rows;
    }

    public void setRows(List<ForecastDanger> rows) {
        this.rows = rows;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_danger_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder vh, int position) {
        ForecastDanger d = rows.get(position);
        android.content.Context ctx = vh.itemView.getContext();

        String[] months = new DateFormatSymbols().getMonths();
        vh.monthLabel.setText(months[d.periodMonth - 1] + " " + d.periodYear);
        // months[] is also used in the repayment line below

        int dotColor;
        int minColor;
        if (d.minBalance < DANGER_THRESHOLD) {
            dotColor = ctx.getColor(R.color.negative);
            minColor = ctx.getColor(R.color.negative);
        } else if (d.minBalance < WARNING_THRESHOLD) {
            dotColor = ctx.getColor(R.color.colorSecondary);
            minColor = ctx.getColor(R.color.colorSecondary);
        } else {
            dotColor = ctx.getColor(R.color.incurred);
            minColor = ctx.getColor(R.color.incurred);
        }

        vh.statusDot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
        vh.minBalanceLabel.setText(String.format(Locale.UK, "£%.0f", d.minBalance));
        vh.minBalanceLabel.setTextColor(minColor);

        String dayLabel = d.minBalanceDay > 0 ? "Low on " + Util.ordinal(d.minBalanceDay) : "Low at start";
        vh.detailLabel.setText(dayLabel);
        vh.endBalanceLabel.setText(String.format(Locale.UK, "End: £%.0f", d.carriedForward));

        if (d.minBalance < DANGER_THRESHOLD) {
            double needed = Math.ceil(DANGER_THRESHOLD - d.minBalance);
            double endAfterBorrow = d.carriedForward + needed;
            vh.borrowLabel.setVisibility(View.VISIBLE);
            vh.borrowLabel.setText(String.format(Locale.UK,
                    "Borrow £%.0f from Marcos → end balance £%.0f", needed, endAfterBorrow));
            vh.borrowLabel.setTextColor(ctx.getColor(R.color.negative));

            // Find the first later month where repaying the loan won't breach -£1,000.
            // We use min_balance of that month minus the loan as the worst-case post-repay dip.
            String repayMonth = null;
            for (int j = position + 1; j < rows.size(); j++) {
                ForecastDanger later = rows.get(j);
                if (later.minBalance - needed >= DANGER_THRESHOLD) {
                    repayMonth = months[later.periodMonth - 1];
                    break;
                }
            }
            vh.repayLabel.setVisibility(View.VISIBLE);
            if (repayMonth != null) {
                vh.repayLabel.setText("Repay in: " + repayMonth);
                vh.repayLabel.setTextColor(ctx.getColor(R.color.incurred));
            } else {
                vh.repayLabel.setText("No safe repayment month within 6 months");
                vh.repayLabel.setTextColor(ctx.getColor(R.color.colorSecondary));
            }
        } else {
            vh.borrowLabel.setVisibility(View.GONE);
            vh.repayLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View statusDot;
        TextView monthLabel, minBalanceLabel, detailLabel, endBalanceLabel, borrowLabel, repayLabel;

        ViewHolder(View v) {
            super(v);
            statusDot = v.findViewById(R.id.statusDot);
            monthLabel = v.findViewById(R.id.monthLabel);
            minBalanceLabel = v.findViewById(R.id.minBalanceLabel);
            detailLabel = v.findViewById(R.id.detailLabel);
            endBalanceLabel = v.findViewById(R.id.endBalanceLabel);
            borrowLabel = v.findViewById(R.id.borrowLabel);
            repayLabel = v.findViewById(R.id.repayLabel);
        }
    }
}
