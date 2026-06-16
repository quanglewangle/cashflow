package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.RecurringItemEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.util.ArrayList;
import java.util.List;

/** Manage the recurring-item templates (monthly / annual / irregular) that generate entries. */
public class ItemsFragment extends Fragment {

    private static final String[] ITEM_TYPES = {"income", "expense", "savings"};
    private static final String[] FREQUENCIES = {"monthly", "four_weekly", "annual", "irregular"};

    private SwipeRefreshLayout swipeRefresh;
    private RecurringItemAdapter adapter;
    private Repository repo;

    private List<CategoryEntity> categories = new ArrayList<>();
    private List<CreditCardEntity> creditCards = new ArrayList<>();

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

        adapter = new RecurringItemAdapter(new ArrayList<>(), this::showEditDialog);
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.fabAdd).setOnClickListener(v -> showEditDialog(null));
        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
    }

    private void loadAll() {
        swipeRefresh.setRefreshing(true);
        repo.getCategories((cats, fromCache) -> {
            categories = cats;
            if (!fromCache) maybeStopRefresh();
        });
        repo.getCreditCards((cards, fromCache) -> {
            creditCards = cards;
            adapter.setCreditCards(cards);
            if (!fromCache) maybeStopRefresh();
        });
        repo.getRecurringItems((items, fromCache) -> {
            adapter.setItems(items);
            if (!fromCache) maybeStopRefresh();
        });
    }

    private void maybeStopRefresh() {
        swipeRefresh.setRefreshing(false);
    }

    private void showEditDialog(@Nullable RecurringItemEntity existing) {
        if (categories.isEmpty()) {
            Toast.makeText(getContext(), "Still loading categories, try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }

        View formView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_recurring_item, null);
        EditText inputName = formView.findViewById(R.id.inputName);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        Spinner spinnerItemType = formView.findViewById(R.id.spinnerItemType);
        Spinner spinnerFrequency = formView.findViewById(R.id.spinnerFrequency);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDueDay = formView.findViewById(R.id.inputDueDay);
        EditText inputTargetMonth = formView.findViewById(R.id.inputTargetMonth);
        Spinner spinnerCreditCard = formView.findViewById(R.id.spinnerCreditCard);
        CheckBox checkActive = formView.findViewById(R.id.checkActive);

        List<String> categoryNames = new ArrayList<>();
        for (CategoryEntity c : categories) categoryNames.add(c.name);
        spinnerCategory.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryNames));

        spinnerItemType.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, ITEM_TYPES));
        spinnerFrequency.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, FREQUENCIES));

        List<String> cardNames = new ArrayList<>();
        cardNames.add("(none)");
        for (CreditCardEntity c : creditCards) cardNames.add(c.name);
        spinnerCreditCard.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, cardNames));

        if (existing != null) {
            inputName.setText(existing.name);
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).id == existing.categoryId) spinnerCategory.setSelection(i);
            }
            for (int i = 0; i < ITEM_TYPES.length; i++) {
                if (ITEM_TYPES[i].equals(existing.itemType)) spinnerItemType.setSelection(i);
            }
            for (int i = 0; i < FREQUENCIES.length; i++) {
                if (FREQUENCIES[i].equals(existing.frequency)) spinnerFrequency.setSelection(i);
            }
            if (existing.defaultAmount != null) inputAmount.setText(String.valueOf(existing.defaultAmount));
            if (existing.dueDay != null) inputDueDay.setText(String.valueOf(existing.dueDay));
            if (existing.targetMonth != null) inputTargetMonth.setText(String.valueOf(existing.targetMonth));
            if (existing.creditCardId != null) {
                for (int i = 0; i < creditCards.size(); i++) {
                    if (creditCards.get(i).id == existing.creditCardId) spinnerCreditCard.setSelection(i + 1);
                }
            }
            checkActive.setChecked(existing.active);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add item" : "Edit item")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    RecurringItemEntity item = existing != null ? existing : new RecurringItemEntity();
                    item.name = inputName.getText().toString().trim();
                    item.categoryId = categories.get(spinnerCategory.getSelectedItemPosition()).id;
                    item.itemType = ITEM_TYPES[spinnerItemType.getSelectedItemPosition()];
                    item.frequency = FREQUENCIES[spinnerFrequency.getSelectedItemPosition()];
                    item.defaultAmount = parseDoubleOrNull(inputAmount.getText().toString());
                    item.dueDay = parseIntOrNull(inputDueDay.getText().toString());
                    item.targetMonth = parseIntOrNull(inputTargetMonth.getText().toString());
                    int cardPos = spinnerCreditCard.getSelectedItemPosition();
                    item.creditCardId = cardPos > 0 ? creditCards.get(cardPos - 1).id : null;
                    item.active = checkActive.isChecked();

                    if (item.name.isEmpty()) {
                        Toast.makeText(getContext(), "Name required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (existing == null) {
                        repo.addRecurringItem(item, this::loadAll, this::showError);
                    } else {
                        repo.updateRecurringItem(item, this::loadAll, this::showError);
                    }
                });

        if (existing != null) {
            builder.setNeutralButton("Delete", (dialog, which) ->
                    repo.deleteRecurringItem(existing.id, this::loadAll, this::showError));
        }

        builder.show();
    }

    private void showError(String error) {
        if (getContext() != null) Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
    }

    @Nullable
    private Double parseDoubleOrNull(String s) {
        try {
            return s.trim().isEmpty() ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Integer parseIntOrNull(String s) {
        try {
            return s.trim().isEmpty() ? null : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
