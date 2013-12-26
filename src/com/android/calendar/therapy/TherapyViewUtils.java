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

import android.app.Activity;
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
            ArrayList<LinearLayout> items, ArrayList<Integer> values,
            ArrayList<String> labels, int number,
			int maxTherapyReminders, View.OnClickListener setTimeListener) {

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

		TextView labelTextView = (TextView) reminderItem.findViewById(R.id.reminder_number_label);
		labelTextView.setText(activity.getString(R.string.reminder_number_label, number));

		Button setTimeButton;
        setTimeButton = (Button) reminderItem.findViewById(R.id.therapy_reminder_time);
        setTimeButton.setOnClickListener(setTimeListener);

        items.add(reminderItem);

        return true;
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

}
