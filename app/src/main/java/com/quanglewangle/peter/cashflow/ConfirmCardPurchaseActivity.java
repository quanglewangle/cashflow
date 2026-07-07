package com.quanglewangle.peter.cashflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.quanglewangle.peter.cashflow.data.CategoryEntity;
import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Floating confirm dialog opened by tapping a detected Google Pay purchase
 *  notification -- lets the description/amount/date/card be corrected and a
 *  category assigned before anything is actually logged. */
public class ConfirmCardPurchaseActivity extends AppCompatActivity {
    static final String EXTRA_DESCRIPTION = "description";
    static final String EXTRA_AMOUNT = "amount";
    static final String EXTRA_CARD_ID = "card_id";
    static final String EXTRA_NOTIF_ID = "notif_id";

    private Repository repo;
    private List<CategoryEntity> categories = new ArrayList<>();
    private List<CreditCardEntity> creditCards = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = Repository.getInstance(this);

        String description = getIntent().getStringExtra(EXTRA_DESCRIPTION);
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        long cardId = getIntent().getLongExtra(EXTRA_CARD_ID, -1);
        int notifId = getIntent().getIntExtra(EXTRA_NOTIF_ID, -1);
        if (notifId != -1) NotificationManagerCompat.from(this).cancel(notifId);

        repo.getCategories((cats, fromCache) -> categories = cats);
        repo.getCreditCards((cards, fromCache) -> {
            creditCards = cards;
            showDialog(description, amount, cardId);
        });
    }

    private void showDialog(String description, double amount, long cardId) {
        if (creditCards.isEmpty()) {
            Toast.makeText(this, "No credit cards configured", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        List<String> catNames = new ArrayList<>();
        List<Long> catIds = new ArrayList<>();
        catNames.add("No category");
        catIds.add(null);
        for (CategoryEntity c : categories) {
            if ("expense".equals(c.itemType)) { catNames.add(c.name); catIds.add(c.id); }
        }

        View formView = LayoutInflater.from(this).inflate(R.layout.dialog_quick_add_purchase, null);
        Spinner spinnerCard = formView.findViewById(R.id.spinnerCard);
        Spinner spinnerCategory = formView.findViewById(R.id.spinnerCategory);
        EditText inputDescription = formView.findViewById(R.id.inputDescription);
        EditText inputAmount = formView.findViewById(R.id.inputAmount);
        EditText inputDate = formView.findViewById(R.id.inputDate);

        List<String> cardNames = new ArrayList<>();
        int cardSelIdx = 0;
        for (int i = 0; i < creditCards.size(); i++) {
            cardNames.add(creditCards.get(i).name);
            if (creditCards.get(i).id == cardId) cardSelIdx = i;
        }
        spinnerCard.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cardNames));
        spinnerCard.setSelection(cardSelIdx);
        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catNames));

        inputDescription.setText(description);
        inputAmount.setText(String.format(Locale.UK, "%.2f", amount));
        Calendar now = Calendar.getInstance();
        inputDate.setText(String.format(Locale.UK, "%04d-%02d-%02d",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)));

        new AlertDialog.Builder(this)
                .setTitle("Add card purchase")
                .setView(formView)
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .setPositiveButton("Add", (d, w) -> {
                    String desc = inputDescription.getText().toString().trim();
                    Double amt = parseDoubleOrNull(inputAmount.getText().toString());
                    String date = inputDate.getText().toString().trim();
                    if (desc.isEmpty() || amt == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Toast.makeText(this, "Description, amount, and date are required", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    CreditCardEntity card = creditCards.get(spinnerCard.getSelectedItemPosition());
                    Long categoryId = catIds.get(spinnerCategory.getSelectedItemPosition());
                    repo.addCardPurchase(card.id, desc, amt, date, categoryId,
                            () -> { Toast.makeText(this, "Added " + desc, Toast.LENGTH_SHORT).show(); finish(); },
                            err -> { Toast.makeText(this, "Failed to add: " + err, Toast.LENGTH_LONG).show(); finish(); });
                })
                .show();
    }

    @Nullable
    private Double parseDoubleOrNull(String s) {
        try {
            return s.trim().isEmpty() ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
