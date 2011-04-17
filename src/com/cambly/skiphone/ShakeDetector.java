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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Listens for shakes using the accelerometer.  This class takes continuous
 * sensor readings over its lifetime, so it should be used sparingly to save
 * battery life.  Shake events are passed to the ShakeListener interface.
 * 
 * The detector remembers recent accelerometer readings projected into the XZ
 * (horizontal when phone is in hand) and XY planes (parallel to face of phone).
 * If the mean of recent readings exceeds the threshold, the callback for
 * larger reading is called and the sensor history is cleared. 
 * 
 * @author kevin@intercambly.com (Kevin Law)
 */
public class ShakeDetector implements SensorEventListener {
  public interface ShakeListener {
    void onVerticalShake();
    void onHorizontalShake();
  }
  
  private final static String LOG_PREFIX = "ShakeDetector";
  
  /* Minimum squared sensor values to trigger shake events. */
  private final static float HORIZONTAL_THRESHOLD = 200.0f;
  private final static float VERTICAL_THRESHOLD = 200.0f;
  
  /* The number of sensor readings to use to compute the mean. */
  private final static int SENSOR_HISTORY = 5;
  
  /* The minimum amount of time to wait between events in ms. */
  private final static long WAIT_TIME = 2000;
  
  private final ShakeListener listener;
  
  /* Used to access the accelerometer. */
  private SensorManager sensorManager;
  
  /* History of recent sensor readings. */
  private LinkedList<Float> previousXYSensorValues = new LinkedList<Float>();
  private LinkedList<Float> previousXZSensorValues = new LinkedList<Float>();
  
  /* The time of the last shake event. */
  private long lastEvent = 0;
  
  public ShakeDetector(Context context, ShakeListener listener) {
    this.listener = listener;    
    sensorManager =
      (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
  }
  
  /**
   * Call to start taking readings from the accelerometer.
   */
  public void start() {
    // Start listening to the accelerometer.
    sensorManager.registerListener(this,
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        SensorManager.SENSOR_DELAY_NORMAL);    
  }
  
  /**
   * Call to stop taking readings from the accelerometer.  This should be called
   * when the object is no longer needed to save battery life.
   */
  public void stop() {
    sensorManager.unregisterListener(this);
  }

  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    Log.d(LOG_PREFIX, "Accuracy: " + accuracy);
  }

  public void onSensorChanged(SensorEvent event) {
    Log.d(LOG_PREFIX, "Sensor changed: " + Arrays.toString(event.values) +
        " : " + event.accuracy);
    
    // Don't trigger shake events if one was triggered recently.
    if (System.currentTimeMillis() - lastEvent < WAIT_TIME) {
      return;
    }
    
    if (previousXYSensorValues.size() == SENSOR_HISTORY) {
      previousXYSensorValues.removeFirst();
      previousXZSensorValues.removeFirst();
    }
    float squaredXSensorValue = sqr(event.values[0]);
    previousXYSensorValues.add(sqr(event.values[1]) + squaredXSensorValue);
    previousXZSensorValues.add(sqr(event.values[2]) + squaredXSensorValue);
    
    float xyMeanSquared = mean(previousXYSensorValues);
    float xzMeanSquared = mean(previousXZSensorValues);

    Log.d(LOG_PREFIX, "Sensor means: XY: " + xyMeanSquared +
        " XZ: " + xzMeanSquared);
    
    // Notify the appropriate listener if the shaking exceeds the threshold in
    // only one plane.  If it exceeds the threshold in multiple planes then wait
    // until the shaking isn't in an ambiguous direction.
    // TODO: Maybe set a lower ambiguity threshold?
    if (xyMeanSquared > xzMeanSquared) {
      if (xyMeanSquared > VERTICAL_THRESHOLD &&
          xzMeanSquared < HORIZONTAL_THRESHOLD) {
        lastEvent = System.currentTimeMillis();
        Log.d(LOG_PREFIX, "Vertical shake: v=" + xyMeanSquared + ", h="
            + xzMeanSquared);
        listener.onVerticalShake();
        clearHistory();
      }
    } else {
      if (xzMeanSquared > HORIZONTAL_THRESHOLD &&
          xyMeanSquared < VERTICAL_THRESHOLD) {
        lastEvent = System.currentTimeMillis();
        Log.d(LOG_PREFIX, "Horizontal shake: v=" + xyMeanSquared + ", h="
            + xzMeanSquared);
        listener.onHorizontalShake();
        clearHistory();
      }
    }
  }
  
  private void clearHistory() {
    previousXYSensorValues.clear();
    previousXZSensorValues.clear();
  }
  
  private static float mean(List<Float> list) {
    float sum = 0;
    for (float item : list) {
      sum += item;
    }
    return sum / list.size();
  }
  
  private static float sqr(float x) {
    return x * x;
  }
}
