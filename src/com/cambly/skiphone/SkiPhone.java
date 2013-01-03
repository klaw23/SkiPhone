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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

/**
 * The SkiPhone App. Allows the user to enable to disable SkiPhone.
 * 
 * TODO(kevin): The main UI and the camera should be separate activities.
 * 
 * @author kevin@intercambly.com (Kevin Law)
 */
public class SkiPhone extends Activity {
  /* Shared preferences filename. */
  public final static String PREF_FILENAME = "com.cambly.skiphone";

  /* Preference key to see whether SkiPhone is enabled. */
  public final static String IS_ENABLED_PREF = "is_enabled";

  /* Whether SkiPhone is enabled. */
  private boolean isEnabled;

  /* The button that enables/disables SkiPhone. */
  private Button toggleButton;

  /* The button to rate the app. */
  private Button rateButton;

  /* The button to send feedback. */
  private Button feedbackButton;

  private SharedPreferences prefs;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = getSharedPreferences(PREF_FILENAME, MODE_PRIVATE);    
    setContentView(R.layout.main);
  }

  @Override
  public void onResume() {
    super.onResume();
    
    // Set the button states.
    isEnabled = prefs.getBoolean(IS_ENABLED_PREF, false);
    toggleButton = (Button) findViewById(R.id.toggle_button);
    toggleButton.setText(isEnabled ? R.string.disable_button : R.string.enable_button);
    rateButton = (Button) findViewById(R.id.rate_button);
    rateButton.setVisibility(isEnabled ? View.INVISIBLE : View.VISIBLE);
    feedbackButton = (Button) findViewById(R.id.feedback_button);
    feedbackButton.setVisibility(isEnabled ? View.INVISIBLE : View.VISIBLE);
  }

  /**
   * Use volume buttons to simulate shakes.
   */
//  @Override
//  public boolean onKeyDown(int keyCode, KeyEvent event) {
//    if (!isEnabled) {
//      return false;
//    }
//
//    Intent intent;
//
//    switch (keyCode) {
//    case KeyEvent.KEYCODE_VOLUME_UP:
//      // Start the voice actions prompt.
//      intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
//      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//      startActivity(intent);
//
//      // Show a toast with instructions on how to cancel.
//      Toast.makeText(this, R.string.screen_cancel, Toast.LENGTH_LONG).show();
//
//      return true;
//      
//    case KeyEvent.KEYCODE_VOLUME_DOWN:
//      // Open the app in camera mode.
//      intent = new Intent(this, CameraActivity.class);
//      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//      startActivity(intent);
//
//      return true;
//    }
//    
//    return false;
//  }

  /** Turn SkiPhone on and off. */
  public void toggle(View view) {
    // Intent used to update the SkiPhone service.
    Intent serviceIntent = new Intent(this, SkiPhoneService.class);

    // The user pressed the button, so the screen must be on.
    serviceIntent.putExtra(SkiPhoneService.IS_SCREEN_ON_EXTRA, true);

    if (!isEnabled) {
      // Turn SkiPhone on.
      toggleButton.setText(R.string.disable_button);
      isEnabled = true;
    } else {
      // Turn SkiPhone off.
      toggleButton.setText(R.string.enable_button);
      isEnabled = false;
    }

    // The rating and feedback buttons should only be clickable if SkiPhone is disabled.
    rateButton.setVisibility(isEnabled ? View.INVISIBLE : View.VISIBLE);
    feedbackButton.setVisibility(isEnabled ? View.INVISIBLE : View.VISIBLE);

    // Update the service with the current settings.
    serviceIntent.putExtra(SkiPhoneService.IS_ENABLED_EXTRA, isEnabled);
    startService(serviceIntent);
  }

  /**
   * Goto the market page.
   */
  public void gotoMarket(View view) {
    Intent marketIntent = new Intent(Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.cambly.skiphone"));
    startActivity(marketIntent);
  }

  /**
   * Open e-mail to send feedback.
   */
  public void sendFeedback(View view) {
    Intent feedbackIntent = new Intent(Intent.ACTION_SEND);
    feedbackIntent.setType("plain/text");
    feedbackIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { "kevin@intercambly.com" });
    feedbackIntent.putExtra(Intent.EXTRA_SUBJECT, "SkiPhone Feedback");
    startActivity(Intent.createChooser(feedbackIntent, null));
  }
}
