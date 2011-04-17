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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * This {@link BroadcastReceiver} is used to notify the {@link SkiPhoneService}
 * when the screen is on and off.
 * 
 * @author kevin@intercambly.com (Kevin Law)
 */
public class ScreenReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    Intent serviceIntent = new Intent(context, SkiPhoneService.class);
    if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
      serviceIntent.putExtra(SkiPhoneService.IS_SCREEN_ON_EXTRA, true);
    } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
      serviceIntent.putExtra(SkiPhoneService.IS_SCREEN_ON_EXTRA, false);
    }
    context.startService(serviceIntent);
  }
}
