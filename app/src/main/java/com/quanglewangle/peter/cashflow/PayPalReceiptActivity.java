package com.quanglewangle.peter.cashflow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handles "Share" on a PayPal receipt email (Gmail's share sheet sends the
 *  email body as EXTRA_TEXT) -- parses the amount, merchant, transaction
 *  date, and card last-4, then opens the same confirm dialog Google Pay
 *  taps use. Never adds anything silently; a bad or unrecognised share just
 *  shows an error and does nothing. */
public class PayPalReceiptActivity extends AppCompatActivity {
    private static final String TAG = "PayPalReceipt";

    private static final Pattern MERCHANT_PATTERN =
            Pattern.compile("You paid\\s+\\S+\\s+[A-Z]{3}\\s+to (.+)");
    // The "You paid" line can be in a foreign currency (PayPal converts at
    // checkout) -- the amount actually charged to the card is always quoted
    // in GBP separately, whether that's the same line (domestic) or a later
    // "£X GBP" line next to the card details (foreign-currency purchase).
    private static final Pattern GBP_AMOUNT_PATTERN =
            Pattern.compile("£([0-9]+(?:\\.[0-9]{2})?)\\s*GBP");
    private static final Pattern LAST_FOUR_PATTERN = Pattern.compile("••(\\d{4})");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("Transaction date\\s+(\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4})");

    // Guards against the same receipt being shared (or the share intent firing)
    // twice in quick succession opening two confirm dialogs for one purchase.
    private static final long DEDUP_WINDOW_MS = 5000;
    private static String lastKey = null;
    private static long lastKeyAtMs = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            fail("Nothing to read");
            return;
        }

        Matcher merchantMatcher = MERCHANT_PATTERN.matcher(text);
        if (!merchantMatcher.find()) {
            fail("Couldn't find a PayPal payment in this text");
            return;
        }
        String merchant = merchantMatcher.group(1).trim();

        Matcher amountMatcher = GBP_AMOUNT_PATTERN.matcher(text);
        if (!amountMatcher.find()) {
            fail("Couldn't find the GBP amount charged to your card");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountMatcher.group(1));
        } catch (NumberFormatException e) {
            fail("Couldn't parse the amount");
            return;
        }

        Matcher lastFourMatcher = LAST_FOUR_PATTERN.matcher(text);
        if (!lastFourMatcher.find()) {
            fail("Couldn't find which card this was paid with");
            return;
        }
        String cardName = Util.cardNameForLastFour(lastFourMatcher.group(1));
        if (cardName == null) {
            fail("Unrecognised card ••" + lastFourMatcher.group(1));
            return;
        }

        String date = null;
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (dateMatcher.find()) {
            try {
                Date parsed = new SimpleDateFormat("d MMM yyyy", Locale.UK).parse(dateMatcher.group(1));
                if (parsed != null) date = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(parsed);
            } catch (ParseException e) {
                Log.i(TAG, "date parse failed for " + dateMatcher.group(1));
            }
        }

        Log.i(TAG, "parsed amount=" + amount + " merchant=" + merchant + " cardName=" + cardName + " date=" + date);

        String key = cardName + "|" + amount + "|" + merchant;
        long now = System.currentTimeMillis();
        synchronized (PayPalReceiptActivity.class) {
            if (key.equals(lastKey) && now - lastKeyAtMs < DEDUP_WINDOW_MS) {
                Log.i(TAG, "duplicate share ignored: " + key);
                finish();
                return;
            }
            lastKey = key;
            lastKeyAtMs = now;
        }

        String finalDate = date;
        Repository repo = Repository.getInstance(getApplicationContext());
        repo.getCreditCards((cards, fromCache) -> {
            for (CreditCardEntity c : cards) {
                if (c.name.equalsIgnoreCase(cardName)) {
                    openConfirmDialog(merchant, amount, c.id, finalDate);
                    return;
                }
            }
            fail("No credit card named " + cardName + " found");
        });
    }

    private void openConfirmDialog(String description, double amount, long cardId, @Nullable String date) {
        Intent intent = new Intent(this, ConfirmCardPurchaseActivity.class)
                .putExtra(ConfirmCardPurchaseActivity.EXTRA_DESCRIPTION, description)
                .putExtra(ConfirmCardPurchaseActivity.EXTRA_AMOUNT, amount)
                .putExtra(ConfirmCardPurchaseActivity.EXTRA_CARD_ID, cardId);
        if (date != null) intent.putExtra(ConfirmCardPurchaseActivity.EXTRA_DATE, date);
        startActivity(intent);
        finish();
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }
}
