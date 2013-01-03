/**
 * Copyright 2012 Kevin Law
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
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

public class CameraActivity extends Activity implements PictureCallback {
  private static final String LOG_PREFIX = "CameraActivity";

  private LayoutInflater inflater;

  private Countdown countdown;

  private CameraOrientationListener orientationListener;

  private CameraView cameraView;

  private Camera camera;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

    orientationListener = new CameraOrientationListener(this);
    setupCameraView();
  }
  
  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    
    // The camera is already open, so close the camera.
    finish();
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
      camera = Camera.open();
      cameraView.setCamera(camera);
      setContentView(cameraView);
    }

    // Show a toast with instructions on how to cancel.
    Toast.makeText(this, R.string.shake_cancel, Toast.LENGTH_LONG).show();

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
      String path = Environment.getExternalStoragePublicDirectory(
          Environment.DIRECTORY_PICTURES).toString();
      
      // Make sure photo directory exists.
      File photoDir = new File(path + "/SkiPhone");
      photoDir.mkdir();

      // Write the image to the sdcard.
      File photoFile = new File(String.format(photoDir.toString() + "/skiphone-%d.jpg",
          System.currentTimeMillis()));
      FileOutputStream fos = new FileOutputStream(photoFile);
      fos.write(data);
      fos.close();

      // Notify the media store, so it shows up in the gallery.
      MediaStore.Images.Media.insertImage(getContentResolver(), photoFile.getAbsolutePath(),
          photoFile.getName(), photoFile.getName());

      // Show a toast with instructions on how to exit.
      Toast.makeText(this, R.string.shake_exit, Toast.LENGTH_LONG).show();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
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
