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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Reminders;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.EmailAddressAdapter;
import com.android.calendar.GeneralPreferences;
import com.android.calendar.Log;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.event.EventViewUtils;
import com.android.calendar.infor.EditDailyStatusHelper.EditDoneRunnable;
import com.android.calendarcommon2.EventRecurrence;
import com.android.datetimepicker.date.DatePickerDialog;
import com.android.datetimepicker.date.DatePickerDialog.OnDateSetListener;
import com.android.ex.chips.AccountSpecifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimeZone;

public class EditDailyStatusView implements View.OnClickListener,
		DialogInterface.OnCancelListener, DialogInterface.OnClickListener,
		OnItemSelectedListener {

	private static final String TAG = "EditEvent";
	private static final String FRAG_TAG_DATE_PICKER = "datePickerDialogFragment";
	ArrayList<View> mEditOnlyList = new ArrayList<View>();
	ArrayList<View> mEditViewList = new ArrayList<View>();
	ArrayList<View> mViewOnlyList = new ArrayList<View>();

	TextView mLoadingMessage;
	ScrollView mScrollView;
	Button mWhenButton;
	TextView mTitleTextView;
	TextView mDescriptionTextView;
	LinearLayout mBodyStatusesContainer;
	View mDescriptionGroup;
	View mBodyStatusesGroup;

	private int[] mOriginalPadding = new int[4];

	public boolean mIsMultipane;
	private ProgressDialog mLoadingCalendarsDialog;
	private AlertDialog mNoCalendarsDialog;
	private Activity mActivity;
	private EditDoneRunnable mDone;
	private View mView;
	private CalendarEventModel mModel;
	private Cursor mCalendarsCursor;
	private AccountSpecifier mAddressAdapter;
	public boolean mTimeSelectedWasStartTime;
	public boolean mDateSelectedWasStartDate;
	private DatePickerDialog mDatePickerDialog;

	/**
	 * Contents of the "minutes" spinner. This has default values from the XML
	 * file, augmented with any additional values that were already associated
	 * with the event.
	 */
	private ArrayList<Integer> mReminderMinuteValues;
	private ArrayList<String> mReminderMinuteLabels;

	/**
	 * Contents of the "methods" spinner. The "values" list specifies the method
	 * constant (e.g. {@link Reminders#METHOD_ALERT}) associated with the
	 * labels. Any methods that aren't allowed by the Calendar will be removed.
	 */
	private ArrayList<Integer> mReminderMethodValues;
	private ArrayList<String> mReminderMethodLabels;

	private int mDefaultReminderMinutes;

	private Time mWhenTime;

	private int mModification = EditDailyStatusHelper.MODIFY_UNINITIALIZED;

	private EventRecurrence mEventRecurrence = new EventRecurrence();

	private ArrayList<LinearLayout> mReminderItems = new ArrayList<LinearLayout>(
			0);
	private ArrayList<ReminderEntry> mUnsupportedReminders = new ArrayList<ReminderEntry>();
	private String mRrule;

	private class DateListener implements OnDateSetListener {
		View mView;

		public DateListener(View view) {
			mView = view;
		}

		@Override
		public void onDateSet(DatePickerDialog view, int year, int month,
				int monthDay) {
			Log.d(TAG, "onDateSet: " + year + " " + month + " " + monthDay);
			// Cache the member variables locally to avoid inner class overhead.
			Time whenTime = mWhenTime;

			// Cache the start so that we limit the number of calls to
			// normalize() and toMillis(), which are fairly expensive.
			long startMillis = 0;
			if (mView == mWhenButton) {
				// The when date was changed.
				whenTime.year = year;
				whenTime.month = month;
				whenTime.monthDay = monthDay;
				startMillis = whenTime.normalize(true);

				setDate(mWhenButton, startMillis);
			}
		}
	}

	// Fills in the date and time fields
	private void populateWhen() {
		long startMillis = mWhenTime.toMillis(false /* use isDst */);
		setDate(mWhenButton, startMillis);

		mWhenButton.setOnClickListener(new DateClickListener(mWhenTime));
	}

	private class DateClickListener implements View.OnClickListener {
		private Time mTime;

		public DateClickListener(Time time) {
			mTime = time;
		}

		@Override
		public void onClick(View v) {
			if (!mView.hasWindowFocus()) {
				// Don't do anything if the activity if paused. Since Activity
				// doesn't
				// have a built in way to do this, we would have to implement
				// one ourselves and
				// either cast our Activity to a specialized activity base class
				// or implement some
				// generic interface that tells us if an activity is paused.
				// hasWindowFocus() is
				// close enough if not quite perfect.
				return;
			}
			if (v == mWhenButton) {
				mDateSelectedWasStartDate = true;
			} else {
				mDateSelectedWasStartDate = false;
			}

			final DateListener listener = new DateListener(v);
			if (mDatePickerDialog != null) {
				mDatePickerDialog.dismiss();
			}
			mDatePickerDialog = DatePickerDialog.newInstance(listener,
					mTime.year, mTime.month, mTime.monthDay);
			mDatePickerDialog.setFirstDayOfWeek(Utils
					.getFirstDayOfWeekAsCalendar(mActivity));
			mDatePickerDialog.setYearRange(Utils.YEAR_MIN, Utils.YEAR_MAX);
			mDatePickerDialog.show(mActivity.getFragmentManager(),
					FRAG_TAG_DATE_PICKER);
		}
	}

	/**
	 * Does prep steps for saving a calendar event.
	 * 
	 * This triggers a parse of the attendees list and checks if the event is
	 * ready to be saved. An event is ready to be saved so long as a model
	 * exists and has a calendar it can be associated with, either because it's
	 * an existing event or we've finished querying.
	 * 
	 * @return false if there is no model or no calendar had been loaded yet,
	 *         true otherwise.
	 */
	public boolean prepareForSave() {
		if (mModel == null || (mCalendarsCursor == null && mModel.mUri == null)) {
			return false;
		}
		return fillModelFromUI();
	}

	public boolean fillModelFromReadOnlyUi() {
		if (mModel == null || (mCalendarsCursor == null && mModel.mUri == null)) {
			return false;
		}
		mModel.mReminders = EventViewUtils.reminderItemsToReminders(
				mReminderItems, mReminderMinuteValues, mReminderMethodValues);
		mModel.mReminders.addAll(mUnsupportedReminders);
		mModel.normalizeReminders();
		return true;
	}

	// This is called if the user clicks on one of the buttons: "Save",
	// "Discard", or "Delete". This is also called if the user clicks
	// on the "remove reminder" button.
	@Override
	public void onClick(View view) {
		// This must be a click on one of the "remove reminder" buttons
		LinearLayout reminderItem = (LinearLayout) view.getParent();
		LinearLayout parent = (LinearLayout) reminderItem.getParent();
		parent.removeView(reminderItem);
		mReminderItems.remove(reminderItem);
		updateRemindersVisibility(mReminderItems.size());
		EventViewUtils.updateAddReminderButton(mView, mReminderItems,
				mModel.mCalendarMaxReminders);
	}

	// This is called if the user cancels the "No calendars" dialog.
	// The "No calendars" dialog is shown if there are no syncable calendars.
	@Override
	public void onCancel(DialogInterface dialog) {
		if (dialog == mLoadingCalendarsDialog) {
			mLoadingCalendarsDialog = null;
		} else if (dialog == mNoCalendarsDialog) {
			mDone.setDoneCode(Utils.DONE_REVERT);
			mDone.run();
			return;
		}
	}

	// This is called if the user clicks on a dialog button.
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (dialog == mNoCalendarsDialog) {
			mDone.setDoneCode(Utils.DONE_REVERT);
			mDone.run();
			if (which == DialogInterface.BUTTON_POSITIVE) {
				Intent nextIntent = new Intent(Settings.ACTION_ADD_ACCOUNT);
				final String[] array = { "com.android.calendar" };
				nextIntent.putExtra(Settings.EXTRA_AUTHORITIES, array);
				nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
						| Intent.FLAG_ACTIVITY_NEW_TASK);
				mActivity.startActivity(nextIntent);
			}
		}
	}

	// Goes through the UI elements and updates the model as necessary
	private boolean fillModelFromUI() {
		if (mModel == null) {
			return false;
		}
		mModel.mReminders = EventViewUtils.reminderItemsToReminders(
				mReminderItems, mReminderMinuteValues, mReminderMethodValues);
		mModel.mReminders.addAll(mUnsupportedReminders);
		mModel.normalizeReminders();
		mModel.mHasAlarm = mReminderItems.size() > 0;
		mModel.mTitle = mTitleTextView.getText().toString();
		mModel.mDescription = mDescriptionTextView.getText().toString();
		if (TextUtils.isEmpty(mModel.mLocation)) {
			mModel.mLocation = null;
		}
		if (TextUtils.isEmpty(mModel.mDescription)) {
			mModel.mDescription = null;
		}

		mWhenTime.hour = 0;
		mWhenTime.minute = 0;
		mWhenTime.second = 0;
		mModel.mStart = mWhenTime.normalize(true);

		return true;
	}

	public EditDailyStatusView(Activity activity, View view,
			EditDoneRunnable done, boolean timeSelectedWasStartTime,
			boolean dateSelectedWasStartDate) {

		mActivity = activity;
		mView = view;
		mDone = done;

		// cache top level view elements
		mLoadingMessage = (TextView) view.findViewById(R.id.loading_message);
		mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);

		// cache all the widgets
		mTitleTextView = (TextView) view.findViewById(R.id.title);
		mDescriptionTextView = (TextView) view.findViewById(R.id.description);
		mWhenButton = (Button) view.findViewById(R.id.when_button);
		mBodyStatusesGroup = view.findViewById(R.id.body_statuses_row);
		mDescriptionGroup = view.findViewById(R.id.description_row);
		mTitleTextView.setTag(mTitleTextView.getBackground());
		mDescriptionTextView.setTag(mDescriptionTextView.getBackground());

        mOriginalPadding[0] = mTitleTextView.getPaddingLeft();
        mOriginalPadding[1] = mTitleTextView.getPaddingTop();
        mOriginalPadding[2] = mTitleTextView.getPaddingRight();
        mOriginalPadding[3] = mTitleTextView.getPaddingBottom();

		mEditViewList.add(mTitleTextView);
		mEditViewList.add(mDescriptionTextView);

//		mViewOnlyList.add(view.findViewById(R.id.when_row));

		mEditOnlyList.add(mBodyStatusesGroup);

		mBodyStatusesContainer = (LinearLayout) view
				.findViewById(R.id.reminder_items_container);

		mIsMultipane = activity.getResources().getBoolean(R.bool.tablet_config);
		mWhenTime = new Time();

		// Display loading screen
		setModel(null);

		FragmentManager fm = activity.getFragmentManager();
		mDatePickerDialog = (DatePickerDialog) fm
				.findFragmentByTag(FRAG_TAG_DATE_PICKER);
		if (mDatePickerDialog != null) {
			mDatePickerDialog
					.setOnDateSetListener(new DateListener(mWhenButton));
		}
	}

	/**
	 * Loads an integer array asset into a list.
	 */
	private static ArrayList<Integer> loadIntegerArray(Resources r, int resNum) {
		int[] vals = r.getIntArray(resNum);
		int size = vals.length;
		ArrayList<Integer> list = new ArrayList<Integer>(size);

		for (int i = 0; i < size; i++) {
			list.add(vals[i]);
		}

		return list;
	}

	/**
	 * Loads a String array asset into a list.
	 */
	private static ArrayList<String> loadStringArray(Resources r, int resNum) {
		String[] labels = r.getStringArray(resNum);
		ArrayList<String> list = new ArrayList<String>(Arrays.asList(labels));
		return list;
	}

	/**
	 * Prepares the reminder UI elements.
	 * <p>
	 * (Re-)loads the minutes / methods lists from the XML assets, adds/removes
	 * items as needed for the current set of reminders and calendar properties,
	 * and then creates UI elements.
	 */
	private void prepareReminders() {
		CalendarEventModel model = mModel;
		Resources r = mActivity.getResources();

		// Load the labels and corresponding numeric values for the minutes and
		// methods lists
		// from the assets. If we're switching calendars, we need to clear and
		// re-populate the
		// lists (which may have elements added and removed based on calendar
		// properties). This
		// is mostly relevant for "methods", since we shouldn't have any
		// "minutes" values in a
		// new event that aren't in the default set.
		mReminderMinuteValues = loadIntegerArray(r,
				R.array.reminder_minutes_values);
		mReminderMinuteLabels = loadStringArray(r,
				R.array.reminder_minutes_labels);
		mReminderMethodValues = loadIntegerArray(r,
				R.array.reminder_methods_values);
		mReminderMethodLabels = loadStringArray(r,
				R.array.reminder_methods_labels);

		// Remove any reminder methods that aren't allowed for this calendar. If
		// this is
		// a new event, mCalendarAllowedReminders may not be set the first time
		// we're called.
		if (mModel.mCalendarAllowedReminders != null) {
			EventViewUtils.reduceMethodList(mReminderMethodValues,
					mReminderMethodLabels, mModel.mCalendarAllowedReminders);
		}

		int numReminders = 0;
		if (model.mHasAlarm) {
			ArrayList<ReminderEntry> reminders = model.mReminders;
			numReminders = reminders.size();
			// Insert any minute values that aren't represented in the minutes
			// list.
			for (ReminderEntry re : reminders) {
				if (mReminderMethodValues.contains(re.getMethod())) {
					EventViewUtils.addMinutesToList(mActivity,
							mReminderMinuteValues, mReminderMinuteLabels,
							re.getMinutes());
				}
			}

			// Create a UI element for each reminder. We display all of the
			// reminders we get
			// from the provider, even if the count exceeds the calendar
			// maximum. (Also, for
			// a new event, we won't have a maxReminders value available.)
			mUnsupportedReminders.clear();
			for (ReminderEntry re : reminders) {
				if (mReminderMethodValues.contains(re.getMethod())
						|| re.getMethod() == Reminders.METHOD_DEFAULT) {
					EventViewUtils.addReminder(mActivity, mScrollView, this,
							mReminderItems, mReminderMinuteValues,
							mReminderMinuteLabels, mReminderMethodValues,
							mReminderMethodLabels, re, Integer.MAX_VALUE, null);
				} else {
					// TODO figure out a way to display unsupported reminders
					mUnsupportedReminders.add(re);
				}
			}
		}

		updateRemindersVisibility(numReminders);
		EventViewUtils.updateAddReminderButton(mView, mReminderItems,
				mModel.mCalendarMaxReminders);
	}

	/**
	 * Fill in the view with the contents of the given event model. This allows
	 * an edit view to be initialized before the event has been loaded. Passing
	 * in null for the model will display a loading screen. A non-null model
	 * will fill in the view's fields with the data contained in the model.
	 * 
	 * @param model
	 *            The event model to pull the data from
	 */
	public void setModel(CalendarEventModel model) {
		mModel = model;

		// Need to close the autocomplete adapter to prevent leaking cursors.
		if (mAddressAdapter != null
				&& mAddressAdapter instanceof EmailAddressAdapter) {
			((EmailAddressAdapter) mAddressAdapter).close();
			mAddressAdapter = null;
		}

		if (model == null) {
			// Display loading screen
			mLoadingMessage.setVisibility(View.VISIBLE);
			mScrollView.setVisibility(View.GONE);
			return;
		}

		long begin = model.mStart;

		// Set up the starting times
		if (begin > 0) {
			mWhenTime.set(begin);
			mWhenTime.normalize(true);
		}

		mRrule = model.mRrule;
		if (!TextUtils.isEmpty(mRrule)) {
			mEventRecurrence.parse(mRrule);
		}

		if (mEventRecurrence.startDate == null) {
			mEventRecurrence.startDate = mWhenTime;
		}

		SharedPreferences prefs = GeneralPreferences
				.getSharedPreferences(mActivity);
		String defaultReminderString = prefs.getString(
				GeneralPreferences.KEY_DEFAULT_REMINDER,
				GeneralPreferences.NO_REMINDER_STRING);
		mDefaultReminderMinutes = Integer.parseInt(defaultReminderString);

//XXX		prepareReminders();

		View bodyStatusAddButton = mView.findViewById(R.id.body_status_add);
		View.OnClickListener addBodyStatusOnClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addReminder();
			}
		};
		bodyStatusAddButton.setOnClickListener(addBodyStatusOnClickListener);

		if (model.mTitle != null) {
			mTitleTextView.setTextKeepState(model.mTitle);
		}

		if (model.mDescription != null) {
			mDescriptionTextView.setTextKeepState(model.mDescription);
		}

		populateWhen();

		updateView();
		mScrollView.setVisibility(View.VISIBLE);
		mLoadingMessage.setVisibility(View.GONE);

	}

	/**
	 * Updates the view based on {@link #mModification} and {@link #mModel}
	 */
	public void updateView() {
		if (mModel == null) {
			return;
		}
		if (EditDailyStatusHelper.canModifyEvent(mModel)) {
			setViewStates(mModification);
		} else {
			setViewStates(Utils.MODIFY_UNINITIALIZED);
		}
	}

	private void setViewStates(int mode) {
		// Extra canModify check just in case
		if (mode == Utils.MODIFY_UNINITIALIZED
				|| !EditEventHelper.canModifyEvent(mModel)) {

			for (View v : mViewOnlyList) {
				v.setVisibility(View.VISIBLE);
			}
			for (View v : mEditOnlyList) {
				v.setVisibility(View.GONE);
			}
			for (View v : mEditViewList) {
				v.setEnabled(false);
				v.setBackgroundDrawable(null);
			}

			if (EditEventHelper.canAddReminders(mModel)) {
				mBodyStatusesGroup.setVisibility(View.VISIBLE);
			} else {
				mBodyStatusesGroup.setVisibility(View.GONE);
			}

			if (TextUtils.isEmpty(mDescriptionTextView.getText())) {
				mDescriptionGroup.setVisibility(View.GONE);
			}
		} else {
			for (View v : mViewOnlyList) {
				v.setVisibility(View.GONE);
			}
			for (View v : mEditOnlyList) {
				v.setVisibility(View.VISIBLE);
			}
			for (View v : mEditViewList) {
				v.setEnabled(true);
				if (v.getTag() != null) {
					v.setBackgroundDrawable((Drawable) v.getTag());
					v.setPadding(mOriginalPadding[0], mOriginalPadding[1],
							mOriginalPadding[2], mOriginalPadding[3]);
				}
			}

			mBodyStatusesGroup.setVisibility(View.VISIBLE);
			mDescriptionGroup.setVisibility(View.VISIBLE);
		}
	}

	public void setModification(int modifyWhich) {
		mModification = modifyWhich;
		updateView();
	}

	private void updateRemindersVisibility(int numReminders) {
		if (numReminders == 0) {
			mBodyStatusesContainer.setVisibility(View.GONE);
		} else {
			mBodyStatusesContainer.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Add a new reminder when the user hits the "add reminder" button. We use
	 * the default reminder time and method.
	 */
	private void addReminder() {
		// TODO: when adding a new reminder, make it different from the
		// last one in the list (if any).
		if (mDefaultReminderMinutes == GeneralPreferences.NO_REMINDER) {
			EventViewUtils.addReminder(mActivity, mScrollView, this,
					mReminderItems, mReminderMinuteValues,
					mReminderMinuteLabels, mReminderMethodValues,
					mReminderMethodLabels, ReminderEntry
							.valueOf(GeneralPreferences.REMINDER_DEFAULT_TIME),
					mModel.mCalendarMaxReminders, null);
		} else {
			EventViewUtils.addReminder(mActivity, mScrollView, this,
					mReminderItems, mReminderMinuteValues,
					mReminderMinuteLabels, mReminderMethodValues,
					mReminderMethodLabels,
					ReminderEntry.valueOf(mDefaultReminderMinutes),
					mModel.mCalendarMaxReminders, null);
		}
		updateRemindersVisibility(mReminderItems.size());
		EventViewUtils.updateAddReminderButton(mView, mReminderItems,
				mModel.mCalendarMaxReminders);
	}

	private void setDate(TextView view, long millis) {
		int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
				| DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH
				| DateUtils.FORMAT_ABBREV_WEEKDAY;

		// Unfortunately, DateUtils doesn't support a timezone other than the
		// default timezone provided by the system, so we have this ugly hack
		// here to trick it into formatting our time correctly. In order to
		// prevent all sorts of craziness, we synchronize on the TimeZone class
		// to prevent other threads from reading an incorrect timezone from
		// calls to TimeZone#getDefault()
		// TODO fix this if/when DateUtils allows for passing in a timezone
		String dateString;
		synchronized (TimeZone.class) {
			dateString = DateUtils.formatDateTime(mActivity, millis, flags);
			// setting the default back to null restores the correct behavior
			TimeZone.setDefault(null);
		}
		view.setText(dateString);
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		// This is only used for the Calendar spinner in new events, and only
		// fires when the
		// calendar selection changes or on screen rotation
		Cursor c = (Cursor) parent.getItemAtPosition(position);
		if (c == null) {
			// TODO: can this happen? should we drop this check?
			Log.w(TAG, "Cursor not set on calendar item");
			return;
		}

		// Do nothing if the selection didn't change so that reminders will not
		// get lost
		int idColumn = c.getColumnIndexOrThrow(Calendars._ID);
		long calendarId = c.getLong(idColumn);
		int colorColumn = c.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
		int color = c.getInt(colorColumn);
		int displayColor = Utils.getDisplayColorFromColor(color);

		// Prevents resetting of data (reminders, etc.) on orientation change.
		if (calendarId == mModel.mCalendarId
				&& mModel.isCalendarColorInitialized()
				&& displayColor == mModel.getCalendarColor()) {
			return;
		}

		mModel.mCalendarId = calendarId;
		mModel.setCalendarColor(displayColor);
		mModel.mCalendarAccountName = c
				.getString(EditDailyStatusHelper.CALENDARS_INDEX_ACCOUNT_NAME);
		mModel.mCalendarAccountType = c
				.getString(EditDailyStatusHelper.CALENDARS_INDEX_ACCOUNT_TYPE);
		mModel.setEventColor(mModel.getCalendarColor());

		// Update the max/allowed reminders with the new calendar properties.
		int maxRemindersColumn = c
				.getColumnIndexOrThrow(Calendars.MAX_REMINDERS);
		mModel.mCalendarMaxReminders = c.getInt(maxRemindersColumn);
		int allowedRemindersColumn = c
				.getColumnIndexOrThrow(Calendars.ALLOWED_REMINDERS);
		mModel.mCalendarAllowedReminders = c.getString(allowedRemindersColumn);
		int allowedAttendeeTypesColumn = c
				.getColumnIndexOrThrow(Calendars.ALLOWED_ATTENDEE_TYPES);
		mModel.mCalendarAllowedAttendeeTypes = c
				.getString(allowedAttendeeTypesColumn);
		int allowedAvailabilityColumn = c
				.getColumnIndexOrThrow(Calendars.ALLOWED_AVAILABILITY);
		mModel.mCalendarAllowedAvailability = c
				.getString(allowedAvailabilityColumn);

		// Discard the current reminders and replace them with the model's
		// default reminder set.
		// We could attempt to save & restore the reminders that have been
		// added, but that's
		// probably more trouble than it's worth.
		mModel.mReminders.clear();
		mModel.mReminders.addAll(mModel.mDefaultReminders);
		mModel.mHasAlarm = mModel.mReminders.size() != 0;

		// Update the UI elements.
		mReminderItems.clear();
		LinearLayout reminderLayout = (LinearLayout) mScrollView
				.findViewById(R.id.reminder_items_container);
		reminderLayout.removeAllViews();
		prepareReminders();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
	}
}
