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

import com.android.calendar.Log;
import com.android.calendar.R;
import com.android.calendar.infor.DailyStatus.BodyStatus;

import android.app.Activity;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import java.util.ArrayList;

public class InforViewUtils {
    private static final String TAG = "InforViewUtils";

    private InforViewUtils() {
    }

    /**
     * Finds the index of the given "type" in the "values" list.
     *
     * @param values the list of type corresponding to the spinner choices
     * @param type the type to search for in the values list
     * @return the index of "type" in the "values" list
     */
    public static int findTypeInBodyStatusList(ArrayList<String> values, String type) {
        int index = values.indexOf(type);
        if (index == -1) {
            // This should never happen.
            Log.e(TAG, "Cannot find type (" + type + ") in list");
            return 0;
        }
        return index;
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
     * Sort bodyStatus.
     *
     * @param bodyStatusItems UI elements (layouts with spinners) that hold array indices.
     */
	public static void sortBodyStatusItems(Activity activity,
			ArrayList<LinearLayout> bodyStatusItems) {
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
    public static boolean addBodyStatus(Activity activity, View view,
            ArrayList<LinearLayout> items,
			ArrayList<String> typeValues,
            ArrayList<String> typeLabels,
            ArrayList<String> defaultValues,
			BodyStatus newBodyStatus,
			int maxBodyStatuses,
			OnItemSelectedListener onItemSelected,
			View.OnClickListener removeListener,
			View.OnClickListener setValueListener) {

        if (items.size() >= maxBodyStatuses) {
            return false;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        LinearLayout parent = (LinearLayout) view.findViewById(R.id.body_status_items_container);
        LinearLayout bodyStatusItem = (LinearLayout) inflater.inflate(R.layout.edit_body_status_item,
                null);
        parent.addView(bodyStatusItem);

        ImageButton bodyStatusRemoveButton;
        bodyStatusRemoveButton = (ImageButton) bodyStatusItem.findViewById(R.id.body_status_remove);
        bodyStatusRemoveButton.setOnClickListener(removeListener);

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

		setBodyStatusValueButton(bodyStatusItem, index, defaultValues, setValueListener);

        items.add(bodyStatusItem);

        sortBodyStatusItems(activity, items);

        return true;
    }

    /**
     * Enables/disables the 'add body status' button depending on the current number of
     * body statuses.
     */
    public static void updateAddBodyStatusButton(View view, ArrayList<LinearLayout> bodyStatuses,
            int maxBodyStatuses) {
        View button = view.findViewById(R.id.body_status_add);
        if (button != null) {
            if (bodyStatuses.size() >= maxBodyStatuses) {
                button.setEnabled(false);
                button.setVisibility(View.GONE);
            } else {
                button.setEnabled(true);
                button.setVisibility(View.VISIBLE);
            }
        }
    }

	public static void setBodyStatusValueButton(ViewParent parent, int index,
            ArrayList<String> defaultValues, View.OnClickListener setValueListener) {
		Button setValueButton;
        setValueButton = (Button) ((View) parent).findViewById(R.id.body_status_value);
		setValueButton.setTag(index);
        setValueButton.setOnClickListener(setValueListener);
        setValueButton.setText(defaultValues.get(index));
	}

}
