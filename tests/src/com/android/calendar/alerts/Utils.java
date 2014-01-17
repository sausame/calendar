/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ankh.calendar.alerts;

import android.text.format.Time;

class Utils {
    public static long createTimeInMillis(int second, int minute, int hour, int monthDay,
            int month, int year, String timezone) {
        Time t = new Time(timezone);
        t.set(second, minute, hour, monthDay, month, year);
        t.normalize(false);
        return t.toMillis(false);
    }
}
