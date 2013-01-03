/**
 * Copyright 2011 Kevin Law
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cambly.skiphone;

import com.cambly.skiphone.ShakeDetector.ShakeListener;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * The SkiPhone service. The service is responsible for listening for shake
 * gestures and starting the appropriate activities.
 * 
 * @author kevin@intercambly.com (Kevin Law)
 */
public class SkiPhoneService extends Service implements ShakeListener {
  /* Extras used in intents sent to the service. */
  public static final String IS_SCREEN_ON_EXTRA = "screen_on";
  public static final String IS_ENABLED_EXTRA = "is_enabled";

  private static final String LOG_PREFIX = "SkiPhoneService";

  /* Used to detect shake gestures. */
  private ShakeDetector shakeDetector;

  /* Used to detect incoming calls. */
  private TelephonyManager telephonyManager;

  /* Used to show a status bar icon. */
  private NotificationManager notificationManager;

  /* Notifies the SkiPhone service when the screen turns on and off. */
  private ScreenReceiver screenReceiver;

  /* A filter to look for screen on/off intents. */
  private IntentFilter screenIntentFilter;

  /* Used to disable the keyguard when SkiPhone is enabled. */
  private KeyguardLock keyguardLock;

  /* Preferences for storing the app state. */
  private SharedPreferences prefs;

  /* Vibrates the phone to provide haptic feedback. */
  private Vibrator vibrator;

  @Override
  public void onCreate() {
    // Get system services.
    telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    keyguardLock = ((KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE))
        .newKeyguardLock("SkiPhone");
    vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

    shakeDetector = new ShakeDetector(this, this);

    screenReceiver = new ScreenReceiver();

    // TODO: Move into screen receiver.
    screenIntentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);

    prefs = getSharedPreferences(SkiPhone.PREF_FILENAME, MODE_PRIVATE);
  }

  @Override
  public void onDestroy() {
    disableSkiPhone();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /**
   * This is the old onStart method that will be called on the pre-2.0 platform.
   * On 2.0 or later we override onStartCommand() so this method will not be
   * called.
   */
  @Override
  public void onStart(Intent intent, int startId) {
    handleCommand(intent);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    handleCommand(intent);
    // We want this service to continue running until it is explicitly
    // stopped, so return sticky.
    return START_STICKY;
  }

  private void handleCommand(Intent intent) {
    if (intent == null) {
      return;
    }
    // Start listening for the screen to turn on and off when SkiPhone is
    // enabled.
    if (intent.hasExtra(SkiPhoneService.IS_ENABLED_EXTRA)) {
      if (intent.getBooleanExtra(IS_ENABLED_EXTRA, false)) {
        // Disable the keyguard.
        keyguardLock.disableKeyguard();

        // Listen for the screen to turn on and off.
        registerReceiver(screenReceiver, screenIntentFilter);

        // Show a status bar notification.
        showNotification();

        // Save shared preferences.
        saveEnabledState(true);
      } else {
        disableSkiPhone();
        return;
      }
    }

    // Only run the shake detector when the screen is on. The ShakeReceiver is
    // only running when SkiPhone is enabled, so receiver this intent implies
    // that SkiPhone is enabled.
    if (intent.hasExtra(SkiPhoneService.IS_SCREEN_ON_EXTRA)) {
      if (intent.getBooleanExtra(IS_SCREEN_ON_EXTRA, false)) {
        shakeDetector.start();

        // Show the SkiPhone activity unless we're on a call.
        if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
          Intent activityIntent = new Intent(this, SkiPhone.class);
          activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(activityIntent);
        }
      } else {
        shakeDetector.stop();
      }
    }
  }

  private void disableSkiPhone() {
    // Re-enable the keyguard.
    keyguardLock.reenableKeyguard();

    // Stop listening for the screen to turn on and off.
    try {
      unregisterReceiver(screenReceiver);
    } catch (IllegalArgumentException e) {
      Log.e(LOG_PREFIX, "Tried to disable SkiPhone, but SkiPhone wasn't " + "enabled.");
    }

    // Remove the status bar icon.
    notificationManager.cancel(R.string.enabled);

    // Save shared preferences.
    saveEnabledState(false);

    // If SkiPhone was disabled, then we don't care if the screen was on or
    // not. Just stop listening for shakes.
    shakeDetector.stop();
  }

  private void saveEnabledState(boolean isEnabled) {
    SharedPreferences.Editor prefsEditor = prefs.edit();
    prefsEditor.putBoolean(SkiPhone.IS_ENABLED_PREF, isEnabled);
    prefsEditor.commit();
  }

  public void onVerticalShake() {
    Log.d(LOG_PREFIX, "Vertical shake.");

    vibrator.vibrate(500);

    switch (telephonyManager.getCallState()) {
    case TelephonyManager.CALL_STATE_RINGING:
      // The phone is ringing. Answer it by simulating a press on a headset
      // button.
      Intent buttonDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
      buttonDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN,
          KeyEvent.KEYCODE_HEADSETHOOK));
      sendOrderedBroadcast(buttonDown, "android.permission.CALL_PRIVILEGED");
      Intent buttonUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
      buttonUp.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP,
          KeyEvent.KEYCODE_HEADSETHOOK));
      sendOrderedBroadcast(buttonUp, "android.permission.CALL_PRIVILEGED");
      break;
    case TelephonyManager.CALL_STATE_OFFHOOK:
      // A call is in progress. Hang up by simulating a long press on a headset
      // button.
      Intent longButtonDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
      longButtonDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
          KeyEvent.KEYCODE_HEADSETHOOK, 0, 0, 0, 0, KeyEvent.FLAG_LONG_PRESS));
      sendOrderedBroadcast(longButtonDown, "android.permission.CALL_PRIVILEGED");
      Intent longButtonUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
      longButtonUp.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(0, 0, KeyEvent.ACTION_UP,
          KeyEvent.KEYCODE_HEADSETHOOK, 0, 0, 0, 0, KeyEvent.FLAG_LONG_PRESS));
      sendOrderedBroadcast(longButtonUp, "android.permission.CALL_PRIVILEGED");
      break;
    default:
      // Start the voice actions prompt.
      Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);

      // Show a toast with instructions on how to cancel.
      Toast.makeText(this, R.string.screen_cancel, Toast.LENGTH_LONG).show();
    }
  }

  public void onHorizontalShake() {
    if (telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
      // Don't do anything if the user is on the phone.
      return;
    }

    vibrator.vibrate(500);

    // Open the app in camera mode.
    Intent intent = new Intent(this, CameraActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  private void showNotification() {
    CharSequence text = getText(R.string.enabled);

    // Set the icon, scrolling text and timestamp
    Notification notification = new Notification(R.drawable.service_icon, text,
        System.currentTimeMillis());
    notification.flags |= Notification.FLAG_ONGOING_EVENT;

    // The PendingIntent to launch our activity if the user selects this
    // notification.
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this,
        SkiPhone.class), 0);

    // Set the info for the views that show in the notification panel.
    notification.setLatestEventInfo(this, text, getText(R.string.notification), contentIntent);

    // Send the notification.
    // We use a layout id because it is a unique number. We use it later to
    // cancel.
    notificationManager.notify(R.string.enabled, notification);
  }
}
