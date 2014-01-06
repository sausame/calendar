/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.calendar.therapy;

import java.util.ArrayList;
import java.util.TimeZone;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.calendar.R;

public class TherapyViewUtils {
    private static final String TAG = "TherapyViewUtils";

    private TherapyViewUtils() {
    }
    
    public static long getTime(Time tm,	int hourOfDay, int minute) {
		// Cache the member variables locally to avoid inner class overhead.
		tm.hour = hourOfDay;
		tm.minute = minute;

		// Cache the millis so that we limit the number of calls to
		// normalize() and toMillis(), which are fairly expensive.
		long millis = tm.normalize(true);
		return millis;
    }
    
	public static void setTime(Activity activity, TextView view, Time tm,
			int hourOfDay, int minute) {
		setTime(activity, view, getTime(tm, hourOfDay, minute));
	}
    
    public static void setTime(Activity activity, TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        flags |= DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
        if (DateFormat.is24HourFormat(activity)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }

        // Unfortunately, DateUtils doesn't support a timezone other than the
        // default timezone provided by the system, so we have this ugly hack
        // here to trick it into formatting our time correctly. In order to
        // prevent all sorts of craziness, we synchronize on the TimeZone class
        // to prevent other threads from reading an incorrect timezone from
        // calls to TimeZone#getDefault()
        // TODO fix this if/when DateUtils allows for passing in a timezone
        String timeString;
        synchronized (TimeZone.class) {
            timeString = DateUtils.formatDateTime(activity, millis, flags);
            TimeZone.setDefault(null);
        }
        
        view.setTag(millis);
        view.setText(timeString);
    }

    /**
     * Sort reminder.
     *
     * @param reminderItems UI elements (layouts with spinners) that hold array indices.
     */
	public static void sortReminderItems(Activity activity,
			ArrayList<LinearLayout> reminderItems) {
		int len = reminderItems.size();

		for (int index = 0; index < len; index++) {
			LinearLayout layout = reminderItems.get(index);
			TextView labelTextView = (TextView) layout
					.findViewById(R.id.reminder_number_label);
			labelTextView.setText(activity.getString(
					R.string.reminder_number_label, index + 1));
		}

	}
    
    /**
     * Extracts reminder minutes info from UI elements.
     *
     * @param reminderItems UI elements (layouts with spinners) that hold array indices.
     * @param reminderMinuteValues Maps array index to time in minutes.
     * @param reminderMethodValues Maps array index to alert method constant.
     * @return Array with reminder data.
     */
    public static long[] reminderItemsToReminders(ArrayList<LinearLayout> reminderItems) {
        int len = reminderItems.size();
        long reminders[] = new long[len];
        for (int index = 0; index < len; index++) {
            LinearLayout layout = reminderItems.get(index);
            Button button = (Button) layout.findViewById(R.id.therapy_reminder_time);
            reminders[index] = (Long) button.getTag();
        }
        return reminders;
    }

    /**
     * Adds a reminder to the displayed list of reminders. The values/labels
     * arrays must not change after calling here, or the spinners we created
     * might index into the wrong entry. Returns true if successfully added
     * reminder, false if no reminders can be added.
     *
     * onItemSelected allows a listener to be set for any changes to the
     * spinners in the reminder. If a listener is set it will store the
     * initial position of the spinner into the spinner's tag for comparison
     * with any new position setting.
     */
    public static boolean addTherapyReminder(Activity activity, View view,
				View.OnClickListener removeListener,
            ArrayList<LinearLayout> items,
			int maxTherapyReminders, View.OnClickListener setTimeListener, long millis) {

        if (items.size() >= maxTherapyReminders) {
            return false;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        LinearLayout parent = (LinearLayout) view.findViewById(R.id.reminder_items_container);
        LinearLayout reminderItem = (LinearLayout) inflater.inflate(R.layout.edit_therapy_reminder_item,
                null);
        parent.addView(reminderItem);

        ImageButton reminderRemoveButton;
        reminderRemoveButton = (ImageButton) reminderItem.findViewById(R.id.reminder_remove);
        reminderRemoveButton.setOnClickListener(removeListener);

		Button setTimeButton;
        setTimeButton = (Button) reminderItem.findViewById(R.id.therapy_reminder_time);
        setTimeButton.setOnClickListener(setTimeListener);
        setTime(activity, setTimeButton, millis);

        items.add(reminderItem);
        
        sortReminderItems(activity, items);

        return true;
    }
    
	public static boolean addTherapyReminder(Activity activity, View view,
			View.OnClickListener removeListener, ArrayList<LinearLayout> items,
			int maxTherapyReminders, View.OnClickListener setTimeListener,
			int hourOfDay, int minute) {
		return addTherapyReminder(activity, view, removeListener, items,
				maxTherapyReminders, setTimeListener,
				getTime(new Time(), hourOfDay, minute));
	}

    /**
     * Enables/disables the 'add reminder' button depending on the current number of
     * reminders.
     */
    public static void updateAddTherapyReminderButton(View view, ArrayList<LinearLayout> reminders,
            int maxTherapyReminders) {
        View reminderAddButton = view.findViewById(R.id.reminder_add);
        if (reminderAddButton != null) {
            if (reminders.size() >= maxTherapyReminders) {
                reminderAddButton.setEnabled(false);
                reminderAddButton.setVisibility(View.GONE);
            } else {
                reminderAddButton.setEnabled(true);
                reminderAddButton.setVisibility(View.VISIBLE);
            }
        }
    }

	private final static int THERAPY_TYPE_ICON_RES_ID_GROUP[] = {
			R.drawable.ic_drug_small, R.drawable.ic_injection_small };

	public static void drawTherapyTypeIcon(Canvas canvas, Context context,
			int type, Rect dst) {
		Rect src = new Rect(0, 0, dst.width(), dst.height());
		int resId = THERAPY_TYPE_ICON_RES_ID_GROUP[type
				% THERAPY_TYPE_ICON_RES_ID_GROUP.length];
		BitmapDrawable drawable = (BitmapDrawable) context.getResources()
				.getDrawable(resId);
		Bitmap bitmap = drawable.getBitmap();
		canvas.drawBitmap(bitmap, src, dst, null);
	}
}
