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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The SkiPhone App. Allows the user to enable to disable SkiPhone.
 * 
 * TODO(kevin): The main UI and the camera should be separate activities.
 * 
 * @author kevin@intercambly.com (Kevin Law)
 */
public class SkiPhone extends Activity implements PictureCallback {
  /* Shared preferences filename. */
  public final static String PREF_FILENAME = "com.cambly.skiphone";

  /* Preference key to see whether SkiPhone is enabled. */
  public final static String IS_ENABLED_PREF = "is_enabled";

  /* Name of intent extra that specifies special modes. */
  public static final String MODE_EXTRA = "mode";

  /* Modes of operation specified in intent. */
  public static final int DEFAULT_MODE = 0;
  public static final int CAMERA_MODE = 1;

  private static final String LOG_PREFIX = "SkiPhone";

  /* Whether SkiPhone is enabled. */
  private boolean isEnabled;

  /* The button that enables/disables SkiPhone. */
  private Button toggleButton;

  /* The button to rate the app. */
  private Button rateButton;

  /* The button to send feedback. */
  private Button feedbackButton;

  private SharedPreferences prefs;

  private CameraView cameraView;

  private Camera camera;

  private LayoutInflater inflater;

  private Countdown countdown;

  private CameraOrientationListener orientationListener;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prefs = getSharedPreferences(PREF_FILENAME, MODE_PRIVATE);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

    orientationListener = new CameraOrientationListener(this);
  }

  /**
   * This activity only runs a single task, so we need to update the intent when
   * this activity is resumed by the service.
   */
  @Override
  protected void onNewIntent(Intent intent) {
    setIntent(intent);
  }

  /**
   * Setup the appropriate view based on the intent.
   */
  @Override
  public void onResume() {
    super.onResume();

    // Setup the camera again if we have a cameraView.
    if (cameraView != null) {
      setupCamera();
    }

    if (getIntent().hasExtra(MODE_EXTRA)) {
      switch (getIntent().getIntExtra(MODE_EXTRA, 0)) {
      case CAMERA_MODE:
        if (cameraView == null) {
          // Start the camera if it isn't already running.
          setupCameraView();
        } else {
          // Cancel the camera if it's already running.
          if (countdown != null) {
            countdown.cancel();
          }
          setupDefaultView();
        }
        return;
      }
    }

    // Run the activity with the default home screen.
    setupDefaultView();
  }

  /**
   * Let go of the camera resource while we're not in the foreground.
   */
  @Override
  protected void onPause() {
    super.onPause();

    // Let go of the camera while we're in the background.
    releaseCamera();
  }

  /**
   * Use volume buttons to simulate shakes.
   */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (!isEnabled) {
      return false;
    }

    Intent intent;

    switch (keyCode) {
    case KeyEvent.KEYCODE_VOLUME_UP:
      // Start the voice actions prompt.
      intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);

      // Show a toast with instructions on how to cancel.
      Toast.makeText(this, R.string.screen_cancel, 5000).show();

      return true;
      
    case KeyEvent.KEYCODE_VOLUME_DOWN:
      // Open the app in camera mode.
      intent = new Intent(this, SkiPhone.class);
      intent.putExtra(SkiPhone.MODE_EXTRA, SkiPhone.CAMERA_MODE);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);

      return true;
    }
    
    return false;
  }

  private void setupCamera() {
    camera = Camera.open();
    cameraView.setCamera(camera);
  }

  private void releaseCamera() {
    if (cameraView != null && camera != null) {
      cameraView.setCamera(null);
      cancelAutoFocus();
      camera.release();
      camera = null;
    }
  }

  private void cancelAutoFocus() {
    try {
      camera.cancelAutoFocus();
    } catch (RuntimeException e) {
      // Not focusing.
    }
  }

  /**
   * Setup the default view containing instructions and app settings.
   */
  private void setupDefaultView() {
    // Show the window title.
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    // Don't set the screen orientation.
    if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
      // This will trigger onResume to be called again, so let's setup the
      // view the next time around. Make sure the intent specifies the
      // right
      // mode.
      Intent intent = new Intent(this, SkiPhone.class);
      intent.putExtra(SkiPhone.MODE_EXTRA, SkiPhone.DEFAULT_MODE);
      setIntent(intent);
      return;
    }
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

    setContentView(R.layout.main);

    // Clean up the camera view.
    releaseCamera();
    cameraView = null;

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
   * Setup the camera view used for taking pictures.
   */
  private void setupCameraView() {
    // Hide the window title.
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    // Use landscape mode.
    if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      // This will trigger onResume to be called again, so let's setup the
      // view the next time around.
      return;
    }

    // Setup the camera and camera view.
    if (cameraView == null) {
      cameraView = new CameraView(this);
      setupCamera();
      setContentView(cameraView);
    }

    // Show a toast with instructions on how to cancel.
    Toast.makeText(this, R.string.shake_cancel, 5000).show();

    // Display countdown.
    // Setup overlay text view.
    addContentView(inflater.inflate(R.layout.camera_overlay, null), new LayoutParams(
        LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
    TextView textView = (TextView) findViewById(R.id.text_overlay);
    countdown = new Countdown(textView, 5, new Runnable() {
      public void run() {
        takePicture();
      }
    });
    countdown.run();
    orientationListener.enable();
  }

  private void takePicture() {
    if (camera != null) {
      orientationListener.disable();
      if (orientationListener.hasOrientation()) {
        Camera.Parameters params = camera.getParameters();
        params.setRotation(orientationListener.getOrientation());
        try {
          camera.setParameters(params);
        } catch (RuntimeException e) {
          // Oops, picture might not be oriented right, but at least
          // it didn't
          // crash.
          Log.e(LOG_PREFIX, "Runtime exception while setting orientation!");
        }
      }
      camera.takePicture(null, null, this);
    }
  }

  /**
   * Write the image to the sdcard.
   */
  public void onPictureTaken(byte[] data, Camera camera) {
    // Make sure autofocus is stopped.
    cancelAutoFocus();

    try {
      // Make sure photo directory exists.
      File photoDir = new File("/sdcard/DCIM/SkiPhone");
      photoDir.mkdir();

      // Write the image to the sdcard.
      File photoFile = new File(String.format("/sdcard/DCIM/SkiPhone/skiphone-%d.jpg",
          System.currentTimeMillis()));
      FileOutputStream fos = new FileOutputStream(photoFile);
      fos.write(data);
      fos.close();

      // Notify the media store, so it shows up in the gallery.
      MediaStore.Images.Media.insertImage(getContentResolver(), photoFile.getAbsolutePath(),
          photoFile.getName(), photoFile.getName());

      // Show a toast with instructions on how to exit.
      Toast.makeText(this, R.string.shake_exit, 5000).show();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

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

    // The rating and feedback buttons should only be clickable if SkiPhone
    // is
    // disabled.
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

  private final class CameraOrientationListener extends OrientationEventListener {
    private int orientation = ORIENTATION_UNKNOWN;

    private CameraOrientationListener(Context context) {
      super(context);
    }

    @Override
    public void onOrientationChanged(int orientation) {
      if (orientation == ORIENTATION_UNKNOWN)
        return;
      // Round to the nearest right angle.
      this.orientation = (((orientation + 115) / 90) * 90) % 360;
    }

    public int getOrientation() {
      return orientation;
    }

    public boolean hasOrientation() {
      return orientation != ORIENTATION_UNKNOWN;
    }
  }

  private class Countdown implements Runnable {
    private TextView textView;
    private int count;
    private Handler handler;
    private Runnable callback;

    public Countdown(TextView textView, int count, Runnable callback) {
      this.textView = textView;
      this.count = count;
      this.callback = callback;
      handler = new Handler();
    }

    public void run() {
      textView.setText(Integer.toString(count));
      count--;
      if (count >= 0) {
        handler.postDelayed(this, 1000);
      } else {
        callback.run();
      }
    }

    public void cancel() {
      handler.removeCallbacks(this);
    }
  }
}
