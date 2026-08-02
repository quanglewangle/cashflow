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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Watches for Lloyds current-account payment notifications (e.g. "You sent
 *  a payment of £36.25 to BENJAMIN KEW from your account ending 2560.") and
 *  offers to log them as a one-off cash entry -- or, if the amount/date
 *  plausibly match an already-planned entry, mark that entry paid/received
 *  instead (see ConfirmBankPaymentActivity). Never adds anything silently --
 *  always posts a confirm/ignore notification first, same as
 *  GooglePayListenerService.
 *
 *  RECEIVED_PATTERN is a best guess mirrored off the confirmed SENT_PATTERN
 *  wording -- it hasn't been checked against a real Lloyds credit
 *  notification yet, so it may need adjusting once one's actually seen. */
public class BankPaymentListenerService extends NotificationListenerService {
    private static final String TAG = "BankListener";
    private static final String PKG = "com.grppl.android.shell.CMBlloydsTSB73";
    private static final String CHANNEL_ID = "bank_payment_detected";

    private static final Pattern SENT_PATTERN = Pattern.compile(
            "You sent a payment of £([0-9]+(?:\\.[0-9]{2})?) to (.+?) from your account ending (\\d{4})");
    private static final Pattern RECEIVED_PATTERN = Pattern.compile(
            "You received a payment of £([0-9]+(?:\\.[0-9]{2})?) from (.+?) into your account ending (\\d{4})");

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
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        Log.i(TAG, "lloyds notification text=" + textCs);
        if (textCs == null) return;
        String text = textCs.toString();

        String description;
        String itemType;
        double amount;

        Matcher sent = SENT_PATTERN.matcher(text);
        Matcher received = RECEIVED_PATTERN.matcher(text);
        if (sent.find()) {
            description = sent.group(2).trim();
            itemType = "expense";
            amount = parseAmountOrNaN(sent.group(1));
        } else if (received.find()) {
            description = received.group(2).trim();
            itemType = "income";
            amount = parseAmountOrNaN(received.group(1));
        } else {
            Log.i(TAG, "text did not match a known payment pattern");
            return;
        }
        if (Double.isNaN(amount)) return;

        String dateIso = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(new Date(sbn.getPostTime()));
        Log.i(TAG, "parsed description=" + description + " amount=" + amount + " itemType=" + itemType + " date=" + dateIso);
        showConfirmNotification(description, amount, itemType, dateIso);
    }

    private double parseAmountOrNaN(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // Offset well clear of GooglePayListenerService's own counter (which also
    // starts near zero) so the two services' notification/request ids never collide.
    private static final AtomicInteger NEXT_NOTIF_ID = new AtomicInteger(1000);

    private void showConfirmNotification(String description, double amount, String itemType, String dateIso) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Bank payments detected", NotificationManager.IMPORTANCE_DEFAULT));
        }

        int notifId = NEXT_NOTIF_ID.incrementAndGet();
        boolean isIncome = "income".equals(itemType);

        Intent confirmIntent = new Intent(this, ConfirmBankPaymentActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ConfirmBankPaymentActivity.EXTRA_DESCRIPTION, description)
                .putExtra(ConfirmBankPaymentActivity.EXTRA_AMOUNT, amount)
                .putExtra(ConfirmBankPaymentActivity.EXTRA_ITEM_TYPE, itemType)
                .putExtra(ConfirmBankPaymentActivity.EXTRA_DATE, dateIso)
                .putExtra(ConfirmBankPaymentActivity.EXTRA_NOTIF_ID, notifId);
        PendingIntent confirmPending = PendingIntent.getActivity(this, notifId, confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(this, CardPurchaseActionReceiver.class)
                .setAction(CardPurchaseActionReceiver.ACTION_DISMISS)
                .putExtra(CardPurchaseActionReceiver.EXTRA_NOTIF_ID, notifId);
        PendingIntent dismissPending = PendingIntent.getBroadcast(this, notifId + 1, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle((isIncome ? "Log payment received from " : "Log payment sent to ") + description + "?")
                .setContentText("£" + String.format(Locale.UK, "%.2f", amount))
                .setContentIntent(confirmPending)
                .addAction(0, "Ignore", dismissPending)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(this).notify(notifId, n);
    }
}
