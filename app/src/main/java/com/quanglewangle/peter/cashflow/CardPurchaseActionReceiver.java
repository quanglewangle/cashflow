package com.quanglewangle.peter.cashflow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

/** Handles the "Ignore" quick action on a detected-purchase notification. */
public class CardPurchaseActionReceiver extends BroadcastReceiver {
    static final String ACTION_DISMISS = "com.quanglewangle.peter.cashflow.ACTION_DISMISS_CARD_PURCHASE";
    static final String EXTRA_NOTIF_ID = "notif_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_DISMISS.equals(intent.getAction())) return;
        int notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1);
        if (notifId != -1) NotificationManagerCompat.from(context).cancel(notifId);
    }
}
