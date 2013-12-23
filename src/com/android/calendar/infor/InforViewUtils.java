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
package com.android.calendar.infor;

import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.Log;
import com.android.calendar.R;
import com.android.calendar.infor.PersonalDailyInformation.BodyStatusEntry;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;

public class InforViewUtils {
    private static final String TAG = "InforViewUtils";

    private InforViewUtils() {
    }

    // Constructs a label given an arbitrary number of minutes. For example,
    // if the given minutes is 63, then this returns the string "63 minutes".
    // As another example, if the given minutes is 120, then this returns
    // "2 hours".
    public static String constructReminderLabel(Context context, int minutes, boolean abbrev) {
        Resources resources = context.getResources();
        int value, resId;

        if (minutes % 60 != 0) {
            value = minutes;
            if (abbrev) {
                resId = R.plurals.Nmins;
            } else {
                resId = R.plurals.Nminutes;
            }
        } else if (minutes % (24 * 60) != 0) {
            value = minutes / 60;
            resId = R.plurals.Nhours;
        } else {
            value = minutes / (24 * 60);
            resId = R.plurals.Ndays;
        }

        String format = resources.getQuantityString(resId, value);
        return String.format(format, value);
    }

    /**
     * Finds the index of the given "type" in the "values" list.
     *
     * @param values the list of type corresponding to the spinner choices
     * @param type the type to search for in the values list
     * @return the index of "type" in the "values" list
     */
    public static int findTypeInBodyStatusList(ArrayList<Integer> values, int type) {
        int index = values.indexOf(type);
        if (index == -1) {
            // This should never happen.
            Log.e(TAG, "Cannot find type (" + type + ") in list");
            return 0;
        }
        return index;
    }

    /**
     * Finds the index of the given method in the "methods" list.  If the method isn't present
     * (perhaps because we don't think it's allowed for this calendar), we return zero (the
     * first item in the list).
     * <p>
     * With the current definitions, this effectively converts DEFAULT and unsupported method
     * types to ALERT.
     *
     * @param values the list of minutes corresponding to the spinner choices
     * @param method the method to search for in the values list
     * @return the index of the method in the "values" list
     */
    public static int findMethodInReminderList(ArrayList<Integer> values, int method) {
        int index = values.indexOf(method);
        if (index == -1) {
            // If not allowed, or undefined, just use the first entry in the list.
            //Log.d(TAG, "Cannot find method (" + method + ") in allowed list");
            index = 0;
        }
        return index;
    }

    /**
     * Extracts reminder minutes info from UI elements.
     *
     * @param reminderItems UI elements (layouts with spinners) that hold array indices.
     * @param reminderMinuteValues Maps array index to time in minutes.
     * @param reminderMethodValues Maps array index to alert method constant.
     * @return Array with reminder data.
     */
    public static ArrayList<ReminderEntry> reminderItemsToReminders(
            ArrayList<LinearLayout> reminderItems, ArrayList<Integer> reminderMinuteValues,
            ArrayList<Integer> reminderMethodValues) {
        int len = reminderItems.size();
        ArrayList<ReminderEntry> reminders = new ArrayList<ReminderEntry>(len);
        for (int index = 0; index < len; index++) {
            LinearLayout layout = reminderItems.get(index);
            Spinner minuteSpinner = (Spinner) layout.findViewById(R.id.reminder_minutes_value);
            Spinner methodSpinner = (Spinner) layout.findViewById(R.id.reminder_method_value);
            int minutes = reminderMinuteValues.get(minuteSpinner.getSelectedItemPosition());
            int method = reminderMethodValues.get(methodSpinner.getSelectedItemPosition());
            reminders.add(ReminderEntry.valueOf(minutes, method));
        }
        return reminders;
    }

    /**
     * If "minutes" is not currently present in "values", we add an appropriate new entry
     * to values and labels.
     */
    public static void addMinutesToList(Context context, ArrayList<Integer> values,
            ArrayList<String> labels, int minutes) {
        int index = values.indexOf(minutes);
        if (index != -1) {
            return;
        }

        // The requested "minutes" does not exist in the list, so insert it
        // into the list.

        String label = constructReminderLabel(context, minutes, false);
        int len = values.size();
        for (int i = 0; i < len; i++) {
            if (minutes < values.get(i)) {
                values.add(i, minutes);
                labels.add(i, label);
                return;
            }
        }

        values.add(minutes);
        labels.add(len, label);
    }

    /**
     * Remove entries from the method list that aren't allowed for this calendar.
     *
     * @param values List of known method values.
     * @param labels List of known method labels.
     * @param allowedMethods Has the form "0,1,3", indicating method constants from Reminders.
     */
    public static void reduceMethodList(ArrayList<Integer> values, ArrayList<String> labels,
            String allowedMethods)
    {
        // Parse "allowedMethods".
        String[] allowedStrings = allowedMethods.split(",");
        int[] allowedValues = new int[allowedStrings.length];

        for (int i = 0; i < allowedValues.length; i++) {
            try {
                allowedValues[i] = Integer.parseInt(allowedStrings[i], 10);
            } catch (NumberFormatException nfe) {
                Log.w(TAG, "Bad allowed-strings list: '" + allowedStrings[i] +
                        "' in '" + allowedMethods + "'");
                return;
            }
        }

        // Walk through the method list, removing entries that aren't in the allowed list.
        for (int i = values.size() - 1; i >= 0; i--) {
            int val = values.get(i);
            int j;

            for (j = allowedValues.length - 1; j >= 0; j--) {
                if (val == allowedValues[j]) {
                    break;
                }
            }
            if (j < 0) {
                values.remove(i);
                labels.remove(i);
            }
        }
    }

    /**
     * Set the list of labels on a body status spinner.
     */
    private static void setBodyStatusSpinnerLabels(Activity activity, Spinner spinner,
            ArrayList<String> labels) {
        Resources res = activity.getResources();
        spinner.setPrompt(res.getString(R.string.body_statuses_label));
        int resource = android.R.layout.simple_spinner_item;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, resource, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    /**
     * Adds a body status to the displayed list of body statuss. The values/labels
     * arrays must not change after calling here, or the spinners we created
     * might index into the wrong entry. Returns true if successfully added
     * body status, false if no body status can be added.
     *
     * onItemSelected allows a listener to be set for any changes to the
     * spinners in the body status. If a listener is set it will store the
     * initial position of the spinner into the spinner's tag for comparison
     * with any new position setting.
     */
    public static boolean addBodyStatus(Activity activity, View view, View.OnClickListener listener,
            ArrayList<LinearLayout> items, ArrayList<Integer> typeValues,
            ArrayList<String> typeLabels, ArrayList<String> defaultValues,
			BodyStatusEntry newBodyStatus, int maxBodyStatuss,
            OnItemSelectedListener onItemSelected) {

        if (items.size() >= maxBodyStatuss) {
            return false;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        LinearLayout parent = (LinearLayout) view.findViewById(R.id.body_status_items_container);
        LinearLayout bodyStatusItem = (LinearLayout) inflater.inflate(R.layout.edit_body_status_item,
                null);
        parent.addView(bodyStatusItem);

        ImageButton bodyStatusRemoveButton;
        bodyStatusRemoveButton = (ImageButton) bodyStatusItem.findViewById(R.id.body_status_remove);
        bodyStatusRemoveButton.setOnClickListener(listener);

        /*
         * The spinner has the default set of labels from the string resource file, but we
         * want to drop in our custom set of labels because it may have additional entries.
         */
        Spinner spinner = (Spinner) bodyStatusItem.findViewById(R.id.body_status_type);
        setBodyStatusSpinnerLabels(activity, spinner, typeLabels);

        int index = findTypeInBodyStatusList(typeValues, newBodyStatus.getType());
        spinner.setSelection(index);

        if (onItemSelected != null) {
            spinner.setTag(index);
            spinner.setOnItemSelectedListener(onItemSelected);
        }

		EditText editText = (EditText) bodyStatusItem.findViewById(R.id.body_status_value);
		
		editText.setText(newBodyStatus.getValue());		
		editText.setHint(defaultValues.get(index));
		
        if (onItemSelected != null) {
            spinner.setTag(index);
            spinner.setOnItemSelectedListener(onItemSelected);
        }

        items.add(bodyStatusItem);

        return true;
    }

    /**
     * Enables/disables the 'add body status' button depending on the current number of
     * body statuses.
     */
    public static void updateAddBodyStatusButton(View view, ArrayList<LinearLayout> bodyStatuses,
            int maxBodyStatuss) {
        View button = view.findViewById(R.id.body_status_add);
        if (button != null) {
            if (bodyStatuses.size() >= maxBodyStatuss) {
                button.setEnabled(false);
                button.setVisibility(View.GONE);
            } else {
                button.setEnabled(true);
                button.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Set the list of labels on a reminder spinner.
     */
    private static void setTherapyReminderSpinnerLabels(Activity activity, Spinner spinner,
            ArrayList<String> labels) {
        Resources res = activity.getResources();
        spinner.setPrompt(res.getString(R.string.reminders_label));
        int resource = android.R.layout.simple_spinner_item;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, resource, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
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
