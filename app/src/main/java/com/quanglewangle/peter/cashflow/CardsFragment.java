package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.util.ArrayList;

/** Manage credit cards: name + statement closing day + payment due day. */
public class CardsFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private CreditCardAdapter adapter;
    private Repository repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = Repository.getInstance(requireContext());

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Logging a purchase is the everyday action; editing a card's own
        // parameters (name/dates) is rare, so it's tucked behind the edit icon.
        adapter = new CreditCardAdapter(new ArrayList<>(), this::showAddPurchaseDialog, this::showEditDialog);
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.fabAdd).setOnClickListener(v -> showEditDialog(null));
        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
    }

    private void loadAll() {
        swipeRefresh.setRefreshing(true);
        repo.getCreditCards((cards, fromCache) -> {
            adapter.setItems(cards);
            if (!fromCache) swipeRefresh.setRefreshing(false);
        });
    }

    private void showEditDialog(@Nullable CreditCardEntity existing) {
        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_credit_card, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        EditText inputStatementDay = formView.findViewById(R.id.inputStatementDay);
        EditText inputDueDay = formView.findViewById(R.id.inputDueDay);
        EditText inputDueMonthOffset = formView.findViewById(R.id.inputDueMonthOffset);

        if (existing != null) {
            inputName.setText(existing.name);
            inputStatementDay.setText(String.valueOf(existing.statementDay));
            inputDueDay.setText(String.valueOf(existing.paymentDueDay));
            inputDueMonthOffset.setText(String.valueOf(existing.paymentDueMonthOffset));
        } else {
            inputDueMonthOffset.setText("1");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add card" : "Edit card")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    Integer statementDay = parseDay(inputStatementDay.getText().toString());
                    Integer dueDay = parseDay(inputDueDay.getText().toString());
                    Integer dueMonthOffset = parseOffset(inputDueMonthOffset.getText().toString());
                    if (name.isEmpty() || statementDay == null || dueDay == null || dueMonthOffset == null) {
                        Toast.makeText(getContext(), "Name, both days, and the month offset are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CreditCardEntity card = existing != null ? existing : new CreditCardEntity();
                    card.name = name;
                    card.statementDay = statementDay;
                    card.paymentDueDay = dueDay;
                    card.paymentDueMonthOffset = dueMonthOffset;

                    if (existing == null) {
                        repo.addCreditCard(card, this::loadAll, this::showError);
                    } else {
                        repo.updateCreditCard(card, this::loadAll, this::showError);
                    }
                })
                .show();
    }

    /** Logs a real purchase against a card; the server works out which payment
     *  period (which month's bill) it lands on from the statement/due dates. */
    private void showAddPurchaseDialog(CreditCardEntity card) {
        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_card_purchase, null);
        EditText inputDescription = formView.findViewById(R.id.inputDescription);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDate = formView.findViewById(R.id.inputDate);
        inputDate.setText(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK).format(new java.util.Date()));

        new AlertDialog.Builder(requireContext())
                .setTitle("Log purchase on " + card.name)
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String description = inputDescription.getText().toString().trim();
                    Double amount = parseDoubleOrNull(inputAmount.getText().toString());
                    String date = inputDate.getText().toString().trim();
                    if (description.isEmpty() || amount == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Toast.makeText(getContext(), "Description, amount, and a YYYY-MM-DD date are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repo.addCardPurchase(card.id, description, amount, date,
                            () -> Toast.makeText(getContext(), "Logged", Toast.LENGTH_SHORT).show(),
                            this::showError);
                })
                .show();
    }

    @Nullable
    private Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showError(String error) {
        if (getContext() != null) Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
    }

    @Nullable
    private Integer parseDay(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return (v >= 1 && v <= 31) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Integer parseOffset(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return (v >= 1 && v <= 3) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
