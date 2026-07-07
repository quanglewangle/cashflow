package com.quanglewangle.peter.cashflow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.quanglewangle.peter.cashflow.data.CreditCardEntity;
import com.quanglewangle.peter.cashflow.data.Repository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Watches for Google Pay tap-to-pay transaction notifications (e.g. title
 *  "SPAR BOSCASWELL STORES", text "£6.10 with Lloyds Platinum ••7159") and
 *  offers to log them as a card purchase. Never adds anything silently --
 *  always posts a confirm/ignore notification first. */
public class GooglePayListenerService extends NotificationListenerService {
    private static final String TAG = "GPayListener";
    private static final String PKG = "com.google.android.apps.walletnfcrel";
    private static final String CHANNEL_ID = "gpay_purchase_detected";
    private static final Pattern TX_PATTERN = Pattern.compile("£([0-9]+(?:\\.[0-9]{2})?) with .*••(\\d{4})");

    // Last 4 digits shown in the Google Pay notification -> this app's credit card name.
    // Add an entry here if another card gets added to Google Pay.
    private static final Map<String, String> CARD_LAST_FOUR_TO_NAME = new HashMap<>();
    static {
        CARD_LAST_FOUR_TO_NAME.put("7159", "Visacard");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "listener connected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "onNotificationPosted pkg=" + sbn.getPackageName());
        if (!PKG.equals(sbn.getPackageName())) return;

        Bundle extras = sbn.getNotification().extras;
        String description = extras.getString(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        Log.i(TAG, "gpay notification title=" + description + " text=" + textCs);
        if (description == null || textCs == null) return;

        Matcher m = TX_PATTERN.matcher(textCs.toString());
        if (!m.find()) {
            Log.i(TAG, "text did not match TX_PATTERN");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            Log.i(TAG, "amount parse failed for " + m.group(1));
            return;
        }
        String lastFour = m.group(2);
        String cardName = CARD_LAST_FOUR_TO_NAME.get(lastFour);
        Log.i(TAG, "parsed amount=" + amount + " lastFour=" + lastFour + " -> cardName=" + cardName);
        if (cardName == null) return; // unrecognised card -- don't guess

        AtomicBoolean alreadyNotified = new AtomicBoolean(false);
        Repository repo = Repository.getInstance(getApplicationContext());
        repo.getCreditCards((cards, fromCache) -> {
            Log.i(TAG, "got " + cards.size() + " credit cards, fromCache=" + fromCache);
            if (!alreadyNotified.compareAndSet(false, true)) return;
            for (CreditCardEntity c : cards) {
                if (c.name.equalsIgnoreCase(cardName)) {
                    Log.i(TAG, "matched card id=" + c.id + " name=" + c.name + " -- showing confirm notification");
                    showConfirmNotification(description, amount, c.id, c.name);
                    return;
                }
            }
            Log.i(TAG, "no credit card named " + cardName + " found");
        });
    }

    private void showConfirmNotification(String description, double amount, long cardId, String cardName) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Card purchases detected", NotificationManager.IMPORTANCE_DEFAULT));
        }

        int notifId = (int) System.currentTimeMillis();

        Intent confirmIntent = new Intent(this, ConfirmCardPurchaseActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ConfirmCardPurchaseActivity.EXTRA_DESCRIPTION, description)
                .putExtra(ConfirmCardPurchaseActivity.EXTRA_AMOUNT, amount)
                .putExtra(ConfirmCardPurchaseActivity.EXTRA_CARD_ID, cardId)
                .putExtra(ConfirmCardPurchaseActivity.EXTRA_NOTIF_ID, notifId);
        PendingIntent confirmPending = PendingIntent.getActivity(this, notifId, confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(this, CardPurchaseActionReceiver.class)
                .setAction(CardPurchaseActionReceiver.ACTION_DISMISS)
                .putExtra(CardPurchaseActionReceiver.EXTRA_NOTIF_ID, notifId);
        PendingIntent dismissPending = PendingIntent.getBroadcast(this, notifId + 1, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Log purchase on " + cardName + "?")
                .setContentText(description + " — £" + String.format(Locale.UK, "%.2f", amount))
                .setContentIntent(confirmPending)
                .addAction(0, "Ignore", dismissPending)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(this).notify(notifId, n);
    }
}
