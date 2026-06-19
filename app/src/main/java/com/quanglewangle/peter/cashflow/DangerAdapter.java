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
    private boolean simulating;

    public DangerAdapter(List<ForecastDanger> rows, boolean simulating) {
        this.rows = rows;
        this.simulating = simulating;
    }

    public void setRows(List<ForecastDanger> rows, boolean simulating) {
        this.rows = rows;
        this.simulating = simulating;
        notifyDataSetChanged();
    }

    public void setSimulating(boolean simulating) {
        this.simulating = simulating;
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

        double displayMin = simulating ? d.simMin : d.minBalance;
        double displayCarried = simulating ? d.simCarried : d.carriedForward;

        int dotColor, minColor;
        if (displayMin < DANGER_THRESHOLD) {
            dotColor = minColor = ctx.getColor(R.color.negative);
        } else if (displayMin < WARNING_THRESHOLD) {
            dotColor = minColor = ctx.getColor(R.color.colorSecondary);
        } else {
            dotColor = minColor = ctx.getColor(R.color.incurred);
        }

        vh.statusDot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
        vh.minBalanceLabel.setText(String.format(Locale.UK, "£%.0f", displayMin));
        vh.minBalanceLabel.setTextColor(minColor);

        String dayLabel = d.minBalanceDay > 0 ? "Low on " + Util.ordinal(d.minBalanceDay) : "Low at start";
        vh.detailLabel.setText(dayLabel);
        vh.endBalanceLabel.setText(String.format(Locale.UK, "End: £%.0f", displayCarried));

        if (simulating) {
            bindSimulatedActions(vh, d, ctx, months, position);
        } else {
            bindRawDanger(vh, d, ctx, months, position);
        }
    }

    private void bindRawDanger(ViewHolder vh, ForecastDanger d, android.content.Context ctx,
                                String[] months, int position) {
        if (d.minBalance < DANGER_THRESHOLD) {
            double needed = Math.ceil(DANGER_THRESHOLD - d.minBalance);
            double endAfterBorrow = d.carriedForward + needed;
            String borrowDate = d.minBalanceDay > 0
                    ? "by " + Util.ordinal(d.minBalanceDay) + " " + months[d.periodMonth - 1]
                    : "at start of " + months[d.periodMonth - 1];
            vh.borrowLabel.setVisibility(View.VISIBLE);
            vh.borrowLabel.setText(String.format(Locale.UK,
                    "Borrow £%.0f from Marcos %s → end £%.0f", needed, borrowDate, endAfterBorrow));
            vh.borrowLabel.setTextColor(ctx.getColor(R.color.negative));

            String repayText = null;
            double balanceAfterRepay = Double.NaN;
            for (int j = position + 1; j < rows.size(); j++) {
                ForecastDanger later = rows.get(j);
                if (later.minBalance - needed >= DANGER_THRESHOLD) {
                    String monthName = months[later.periodMonth - 1];
                    repayText = later.minBalanceDay > 0
                            ? "Repay from " + Util.ordinal(later.minBalanceDay) + " " + monthName
                            : "Repay from start of " + monthName;
                    balanceAfterRepay = later.carriedForward - needed;
                    break;
                }
            }
            vh.repayLabel.setVisibility(View.VISIBLE);
            vh.repayBalanceLabel.setVisibility(View.VISIBLE);
            if (repayText != null) {
                vh.repayLabel.setText(repayText);
                vh.repayLabel.setTextColor(ctx.getColor(R.color.incurred));
                vh.repayBalanceLabel.setText(String.format(Locale.UK,
                        "Balance after repay: £%.0f", balanceAfterRepay));
                vh.repayBalanceLabel.setTextColor(balanceAfterRepay >= 0
                        ? ctx.getColor(R.color.incurred)
                        : ctx.getColor(R.color.colorSecondary));
            } else {
                vh.repayLabel.setText("No safe repayment date within 6 months");
                vh.repayLabel.setTextColor(ctx.getColor(R.color.colorSecondary));
                vh.repayBalanceLabel.setVisibility(View.GONE);
            }
        } else {
            vh.borrowLabel.setVisibility(View.GONE);
            vh.repayLabel.setVisibility(View.GONE);
            vh.repayBalanceLabel.setVisibility(View.GONE);
        }
    }

    private void bindSimulatedActions(ViewHolder vh, ForecastDanger d, android.content.Context ctx,
                                      String[] months, int position) {
        boolean hasAction = d.borrowNeeded > 0 || d.repayAmount > 0;
        if (!hasAction) {
            vh.borrowLabel.setVisibility(View.GONE);
            vh.repayLabel.setVisibility(View.GONE);
            vh.repayBalanceLabel.setVisibility(View.GONE);
            return;
        }

        if (d.borrowNeeded > 0) {
            String borrowDate = d.minBalanceDay > 0
                    ? "by " + Util.ordinal(d.minBalanceDay) + " " + months[d.periodMonth - 1]
                    : "at start of " + months[d.periodMonth - 1];
            vh.borrowLabel.setVisibility(View.VISIBLE);
            vh.borrowLabel.setText(String.format(Locale.UK,
                    "Borrow £%.0f from Marcos %s", d.borrowNeeded, borrowDate));
            vh.borrowLabel.setTextColor(ctx.getColor(R.color.negative));
        } else {
            vh.borrowLabel.setVisibility(View.GONE);
        }

        if (d.repayAmount > 0) {
            String repayDate = d.minBalanceDay > 0
                    ? "after " + Util.ordinal(d.minBalanceDay) + " " + months[d.periodMonth - 1]
                    : "end of " + months[d.periodMonth - 1];
            double balAfterRepay = d.simCarried; // already reflects repayment
            vh.repayLabel.setVisibility(View.VISIBLE);
            vh.repayLabel.setText(String.format(Locale.UK,
                    "Repay £%.0f to Marcos %s", d.repayAmount, repayDate));
            vh.repayLabel.setTextColor(ctx.getColor(R.color.incurred));
            vh.repayBalanceLabel.setVisibility(View.VISIBLE);
            vh.repayBalanceLabel.setText(String.format(Locale.UK,
                    "Balance after repay: £%.0f", balAfterRepay));
            vh.repayBalanceLabel.setTextColor(balAfterRepay >= 0
                    ? ctx.getColor(R.color.incurred)
                    : ctx.getColor(R.color.colorSecondary));
        } else {
            vh.repayLabel.setVisibility(View.GONE);
            vh.repayBalanceLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View statusDot;
        TextView monthLabel, minBalanceLabel, detailLabel, endBalanceLabel,
                borrowLabel, repayLabel, repayBalanceLabel;

        ViewHolder(View v) {
            super(v);
            statusDot = v.findViewById(R.id.statusDot);
            monthLabel = v.findViewById(R.id.monthLabel);
            minBalanceLabel = v.findViewById(R.id.minBalanceLabel);
            detailLabel = v.findViewById(R.id.detailLabel);
            endBalanceLabel = v.findViewById(R.id.endBalanceLabel);
            borrowLabel = v.findViewById(R.id.borrowLabel);
            repayLabel = v.findViewById(R.id.repayLabel);
            repayBalanceLabel = v.findViewById(R.id.repayBalanceLabel);
        }
    }
}
