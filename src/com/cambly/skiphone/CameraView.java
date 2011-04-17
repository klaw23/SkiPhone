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

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * A view containing a camera preview.
 * 
 * @author kevin@intercambly.com (Kevin Law)
 */
public class CameraView extends RelativeLayout 
    implements SurfaceHolder.Callback {
  private static final String LOG_PREFIX = "CameraView";

  /* Contains the camera preview. */
  private SurfaceView surfaceView;
  
  /* Handles callbacks from the surface. */
  private SurfaceHolder holder;

  /* The camera used by the view. */
  private Camera camera;

  /* Preview sizes supported by the camera. */
  private List<Size> supportedPreviewSizes;
  
  /* Image sizes supported by the camera. */
  private List<Size> supportedPictureSizes;

  private Size previewSize;

  private Size pictureSize;

  public CameraView(Context context) {
    super(context);
    
    // Setup the surface view.
    surfaceView = new SurfaceView(context);
    addView(surfaceView);
    
    // Setup surface callbacks.
    holder = surfaceView.getHolder();
    holder.addCallback(this);
    holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
  }
  
  /**
   * Set the camera used by the view.
   */
  public void setCamera(Camera camera) {
    this.camera = camera;
    if (camera != null) {
      Camera.Parameters parameters = camera.getParameters();
      supportedPreviewSizes = parameters.getSupportedPreviewSizes();
      supportedPictureSizes = parameters.getSupportedPictureSizes();
      requestLayout();
    }
  }
  
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // Disregard child measurements.
    final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int height =
      resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);
    
    if (supportedPictureSizes != null) {
      pictureSize = getOptimalPictureSize(supportedPictureSizes, width, height);
      if (supportedPreviewSizes != null) {
        previewSize = getOptimalPreviewSize(supportedPreviewSizes, width,
            height, (double) pictureSize.width / (double) pictureSize.height);
      }
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right,
      int bottom) {
    if (changed && getChildCount() > 0) {
      final View child = getChildAt(0);
      
      final int width = right - left;
      final int height = bottom - top;
      
      int previewWidth = width;
      int previewHeight = height;
      if (previewSize != null) {
        previewWidth = previewSize.width;
        previewHeight = previewSize.height;
      }
      
      // Center the child surface view within the parent.
      if (width * previewHeight > height * previewWidth) {
        final int scaledChildWidth = previewWidth * height / previewHeight;
        child.layout((width - scaledChildWidth) / 2, 0,
            (width + scaledChildWidth) / 2, height);
      } else {
        final int scaledChildHeight = previewHeight * width / previewWidth;
        child.layout(0, (height - scaledChildHeight) / 2, width,
            (height + scaledChildHeight) / 2);
      }
    }

  }

  public void surfaceCreated(SurfaceHolder holder) {
    // Setup the camera preview.  Wait for the size parameters before starting
    // it.
    try {
      if (camera != null) {
        camera.setPreviewDisplay(holder);
      }
    } catch (IOException exception) {
      Log.e(LOG_PREFIX, "Error setting setting up camera preview.");
    }
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    // Stop the camera preview.
    if (camera != null) {
      camera.stopPreview();
    }
  }

  private Size getOptimalPreviewSize(List<Size> sizes, int width, int height,
      double targetRatio) {
    final double ASPECT_TOLERANCE = 0.1;
    if (sizes == null) {
      return null;
    }

    Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;

    int targetHeight = height;

    // Try to find a size match for the aspect ratio and size
    for (Size size : sizes) {
      double ratio = (double) size.width / size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if (Math.abs(size.height - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - targetHeight);
      }
    }

    // Cannot find a match for the aspect ratio, ignore the requirement
    if (optimalSize == null) {
      minDiff = Double.MAX_VALUE;
      for (Size size : sizes) {
        if (Math.abs(size.height - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - targetHeight);
        }
      }
    }
    return optimalSize;  
  }
  
  private Size getOptimalPictureSize(List<Size> sizes, int width, int height) {
    final double ASPECT_TOLERANCE = 0.1;
    double targetRatio = (double) width / height;
    if (sizes == null) {
      return null;
    }

    Size optimalSize = null;
    int maxHeight = 0;

    // Try to find the largest match for the aspect ratio.
    for (Size size : sizes) {
      double ratio = (double) size.width / size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if (size.height  > maxHeight) {
        optimalSize = size;
        maxHeight = size.height;
      }
    }

    // Cannot find a match for the aspect ratio, ignore the requirement
    if (optimalSize == null) {
      maxHeight = 0;
      for (Size size : sizes) {
        if (size.height > maxHeight) {
          optimalSize = size;
          maxHeight = size.height;
        }
      }
    }
    return optimalSize;  
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int width,
      int height) {
    // TODO: camera is null sometimes in 2.0.4.
    if (camera == null) {
      return;
    }
    
    // Start the camera preview.
    Camera.Parameters parameters = camera.getParameters();
    parameters.setPreviewSize(previewSize.width, previewSize.height);
    parameters.setPictureSize(pictureSize.width, pictureSize.height);
    parameters.setJpegQuality(90);
    Log.d(LOG_PREFIX, "Preview: " + previewSize.width  + "," +
        previewSize.height + " Picture: " + pictureSize.width + "," +
        pictureSize.height);
    requestLayout();
    try {
      camera.cancelAutoFocus();
    } catch (RuntimeException e) {
      // No biggie. I guess we weren't focusing.
    }
    // TODO: Fix this.  Sometimes the native camera code throws an exception
    // claiming that autofocus is still running even though we just cancelled
    // it.
    try {
      camera.setParameters(parameters);      
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    camera.startPreview();
    
    // Give the user a couple seconds to aim and then focus the camera.
    DelayedCameraFocuser focuser = new DelayedCameraFocuser(camera);
    focuser.focusIn(2000);
  }
  
  /**
   * 
   */
  private class DelayedCameraFocuser implements Runnable, AutoFocusCallback {
    private Camera camera;
    private Handler handler;

    public DelayedCameraFocuser(Camera camera) {
      this.camera = camera;
      handler = new Handler();
    }
    
    public void onAutoFocus(boolean success, Camera camera) {
      if (!success) {
        // Didn't work.  Try again.
        focus();
      }
    }
    
    public void focusIn(int delayMillis) {
      handler.postDelayed(this, delayMillis);
    }

    public void run() {
      focus();
    }
    
    private void focus() {
      try {
        camera.autoFocus(this);
      } catch (RuntimeException e) {
        // No biggie. The user probably canceled taking a picture.
      }      
    }
  }
}
