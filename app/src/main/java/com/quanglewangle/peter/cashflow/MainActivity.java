package com.quanglewangle.peter.cashflow;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("Cashflow v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE
                + " " + BuildConfig.GIT_HASH + ")");

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_cards) {
                fragment = new CardsFragment();
            } else if (id == R.id.nav_grid) {
                fragment = new DangerFragment();
            } else {
                fragment = new ItemsFragment();
            }
            showFragment(fragment);
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_forecast);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureNotificationListeners();
    }

    /** Android can silently stop running (or revoke) the notification
     *  listeners -- e.g. after an app update -- which kills Google Pay and
     *  Lloyds payment detection with no visible sign. On every open, ask the
     *  system to rebind them if access is granted; if it isn't, offer to jump
     *  to the notification-access settings screen. */
    private void ensureNotificationListeners() {
        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())) {
            if (accessDialogShown) return;
            accessDialogShown = true;
            new AlertDialog.Builder(this)
                    .setTitle("Payment detection is off")
                    .setMessage("Cashflow no longer has notification access, so Google Pay and bank "
                            + "payments won't be detected. Turn it back on?")
                    .setPositiveButton("Open settings", (d, w) ->
                            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNegativeButton("Not now", null)
                    .show();
            return;
        }
        for (Class<?> cls : new Class<?>[]{GooglePayListenerService.class, BankPaymentListenerService.class}) {
            try {
                NotificationListenerService.requestRebind(new ComponentName(this, cls));
            } catch (RuntimeException ignored) {
                // Best effort -- never let a rebind hiccup stop the app opening.
            }
        }
    }

    // Once per activity instance, so it doesn't nag on every return from another screen.
    private boolean accessDialogShown = false;

    private void showFragment(Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.replace(R.id.fragmentContainer, fragment);
        tx.commit();
    }
}
