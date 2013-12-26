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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import com.android.calendar.EmailAddressAdapter;
import com.android.calendar.EventRecurrenceFormatter;
import com.android.calendar.GeneralPreferences;
import com.android.calendar.Log;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.infor.EditDailyStatusHelper;
import com.android.calendar.recurrencepicker.RecurrencePickerDialog;
import com.android.calendarcommon2.EventRecurrence;
import com.android.datetimepicker.date.DatePickerDialog;
import com.android.datetimepicker.date.DatePickerDialog.OnDateSetListener;
import com.android.datetimepicker.time.RadialPickerLayout;
import com.android.datetimepicker.time.TimePickerDialog;
import com.android.datetimepicker.time.TimePickerDialog.OnTimeSetListener;
import com.android.ex.chips.AccountSpecifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimeZone;

public class EditTherapyView implements View.OnClickListener,
		DialogInterface.OnCancelListener, DialogInterface.OnClickListener,
        RecurrencePickerDialog.OnRecurrenceSetListener,
		OnItemSelectedListener {

	private static final String TAG = "EditEvent";
	private static final String FRAG_TAG_DATE_PICKER = "datePickerDialogFragment";
    private static final String FRAG_TAG_TIME_PICKER = "timePickerDialogFragment";
    private static final String FRAG_TAG_RECUR_PICKER = "recurrencePickerDialogFragment";

	ArrayList<View> mEditOnlyList = new ArrayList<View>();
	ArrayList<View> mEditViewList = new ArrayList<View>();
	ArrayList<View> mViewOnlyList = new ArrayList<View>();

	private Spinner mTherapyTypeSpinner;
	private TherapyTypeAdapter mTherapyTypeAdapter;

	TextView mLoadingMessage;
	ScrollView mScrollView;
	Button mWhenButton;
	TextView mTitleTextView;
	TextView mDescriptionTextView;
	LinearLayout mReminderesContainer;
	View mDescriptionGroup;
	View mReminderesGroup;
    Button mUsageRuleButton;
	private TextView mNumberInEveryTimeTextView;
	private Spinner mUsageTypeInEveryTimeSpinner;
	private Spinner mPrivacySpinner;

	private int[] mOriginalPadding = new int[4];

	public boolean mIsMultipane;
	private ProgressDialog mLoadingCalendarsDialog;
	private AlertDialog mNoCalendarsDialog;
	private Activity mActivity;
	private EditDoneRunnable mDone;
	private View mView;
	private Therapy mTherapy;
	private Cursor mCalendarsCursor;
	private AccountSpecifier mAddressAdapter;
	public boolean mTimeSelectedWasStartTime;
	public boolean mDateSelectedWasStartDate;
	private DatePickerDialog mDatePickerDialog;

	/**
	 * Contents of the "body status" spinner. This has default values from the XML
	 * file, augmented with any additional values that were already associated
	 * with the event.
	 */
	private ArrayList<Integer> mReminderTypeValues;
	private ArrayList<String> mReminderTypeLabels;
	private ArrayList<String> mReminderTypeDefaultValues;

	private int mDefaultBodyStatusMinutes;

	private Time mWhenTime;

	private int mModification = EditDailyStatusHelper.MODIFY_UNINITIALIZED;

	private EventRecurrence mEventRecurrence = new EventRecurrence();

	private ArrayList<LinearLayout> mReminderItems = new ArrayList<LinearLayout>(
			0);

	private String mRrule;

    /* This class is used to update the time buttons. */
    private class TimeListener implements OnTimeSetListener {
        private View mView;
        private Time mTime;

        public TimeListener(View view, Time time) {
            mView = view;
            mTime = time;
        }

		@Override
		public void onTimeSet(RadialPickerLayout view, int hourOfDay, int minute) {
            // Cache the member variables locally to avoid inner class overhead.
            Time tm = mTime;

			tm.hour = hourOfDay;
			tm.minute = minute;

            // Cache the millis so that we limit the number of calls to 
			// normalize() and toMillis(), which are fairly expensive.
            long millis = tm.normalize(true);
            setTime((Button) mView, millis);				
		}
    }

    private class TimeClickListener implements View.OnClickListener {
        private Time mTime;

        public TimeClickListener(Time time) {
            mTime = time;
        }

        @Override
        public void onClick(View v) {
            final FragmentManager fm = mActivity.getFragmentManager();
            fm.executePendingTransactions();

            TimePickerDialog dialog = TimePickerDialog.newInstance(new TimeListener(v, mTime),
                            mTime.hour, mTime.minute, DateFormat.is24HourFormat(mActivity));
			
            if (dialog != null && !dialog.isAdded()) {
                dialog.show(fm, FRAG_TAG_TIME_PICKER);
            }
        }
    }

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

				mWhenTime = whenTime;

				setDate(mWhenButton, startMillis);
				populateUsage();
			}
		}
	}

	// Fills in the date and time fields
	private void populateWhen() {
		long startMillis = mWhenTime.toMillis(false /* use isDst */);
		setDate(mWhenButton, startMillis);

		mWhenButton.setOnClickListener(new DateClickListener(mWhenTime));
	}

    private void populateUsage() {
        Resources r = mActivity.getResources();
        String usageString;
        boolean enabled;
        if (!TextUtils.isEmpty(mRrule)) {
            usageString = EventRecurrenceFormatter.getRepeatString(mActivity, r,
                    mEventRecurrence, true);

            if (usageString == null) {
                usageString = r.getString(R.string.custom);
                Log.e(TAG, "Can't generate display string for " + mRrule);
                enabled = false;
            } else {
                // TODO Should give option to clear/reset rrule
                enabled = RecurrencePickerDialog.canHandleRecurrenceRule(mEventRecurrence);
                if (!enabled) {
                    Log.e(TAG, "UI can't handle " + mRrule);
                }
            }
        } else {
            usageString = r.getString(R.string.does_not_repeat);
            enabled = true;
        }

        mUsageRuleButton.setText(usageString);

        mUsageRuleButton.setOnClickListener(this);
        mUsageRuleButton.setEnabled(true);
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
	 * ready to be saved. An event is ready to be saved so long as a therapy
	 * exists and has a calendar it can be associated with, either because it's
	 * an existing event or we've finished querying.
	 * 
	 * @return false if there is no therapy or no calendar had been loaded yet,
	 *         true otherwise.
	 */
	public boolean prepareForSave() {
		if (mTherapy == null) {
			return false;
		}
		return fillTherapyFromUI();
	}

	public boolean fillTherapyFromReadOnlyUi() {
		if (mTherapy == null) {
			return false;
		}
/*		mTherapy.mReminders = TherapyViewUtils.reminderItemsToBodyStatuss(
				mReminderItems, mReminderTypeValues, mReminderTypeDefaultValues);
		mTherapy.mReminders.addAll(mUnsupportedBodyStatuss);
		mTherapy.normalizeBodyStatuss();*/
		return true;
	}

	// This is called if the user clicks on one of the buttons: "Save",
	// "Discard", or "Delete". This is also called if the user clicks
	// on the "remove reminder" button.
	@Override
	public void onClick(View view) {
        if (view == mUsageRuleButton) {
            Bundle b = new Bundle();
            b.putLong(RecurrencePickerDialog.BUNDLE_START_TIME_MILLIS,
                    mWhenTime.toMillis(false));
            b.putString(RecurrencePickerDialog.BUNDLE_TIME_ZONE, mWhenTime.timezone);

            // TODO may be more efficient to serialize and pass in EventRecurrence
            b.putString(RecurrencePickerDialog.BUNDLE_RRULE, mRrule);

            FragmentManager fm = mActivity.getFragmentManager();
            RecurrencePickerDialog rpd = (RecurrencePickerDialog) fm
                    .findFragmentByTag(FRAG_TAG_RECUR_PICKER);
            if (rpd != null) {
                rpd.dismiss();
            }
            rpd = new RecurrencePickerDialog();
            rpd.setArguments(b);
            rpd.setOnRecurrenceSetListener(EditTherapyView.this);
            rpd.show(fm, FRAG_TAG_RECUR_PICKER);
            return;
        }

		// This must be a click on one of the "remove reminder" buttons
		LinearLayout reminderItem = (LinearLayout) view.getParent();
		LinearLayout parent = (LinearLayout) reminderItem.getParent();
		parent.removeView(reminderItem);
		mReminderItems.remove(reminderItem);
		updateBodyStatussVisibility(mReminderItems.size());
/*		TherapyViewUtils.updateAddBodyStatusButton(mView, mReminderItems,
				mTherapy.mCalendarMaxBodyStatuss);*/
	}

    @Override
    public void onRecurrenceSet(String rrule) {
        Log.d(TAG, "Old rrule:" + mRrule);
        Log.d(TAG, "New rrule:" + rrule);
        mRrule = rrule;
        if (mRrule != null) {
            mEventRecurrence.parse(mRrule);
        }
        populateUsage();
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

	// Goes through the UI elements and updates the therapy as necessary
	private boolean fillTherapyFromUI() {
		if (mTherapy == null) {
			return false;
		}
		
		mTherapy.setType(mTherapyTypeSpinner.getSelectedItemPosition());

		mTherapy.setName(mTitleTextView.getText().toString());
		mTherapy.setDescription(mDescriptionTextView.getText().toString());

		mTherapy.setUsageRule(mRrule);
		mTherapy.setNumberInEveryTime(Integer.parseInt(mNumberInEveryTimeTextView.getText().toString()));
		mTherapy.setUsageTypeInEveryTime(mUsageTypeInEveryTimeSpinner.getSelectedItemPosition());

		mTherapy.setPrivacy(mPrivacySpinner.getSelectedItemPosition() == 0);

		mTherapy.setHasAlarm(mReminderItems.size() > 0);
		mTherapy.setRemindersGroup(TherapyViewUtils.reminderItemsToReminders(mReminderItems));

		mWhenTime.hour = 0;
		mWhenTime.minute = 0;
		mWhenTime.second = 0;

		mTherapy.setDay(mWhenTime.normalize(true));

		return true;
	}

	public EditTherapyView(Activity activity, View view,
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
		mReminderesGroup = view.findViewById(R.id.reminders_row);
		mDescriptionGroup = view.findViewById(R.id.description_row);
		mTitleTextView.setTag(mTitleTextView.getBackground());
		mDescriptionTextView.setTag(mDescriptionTextView.getBackground());
        mUsageRuleButton = (Button) view.findViewById(R.id.usage_button);

		mTherapyTypeSpinner = (Spinner) view.findViewById(R.id.therapy_type);
		mTherapyTypeAdapter = new TherapyTypeAdapter(activity);
		mTherapyTypeSpinner.setAdapter(mTherapyTypeAdapter);
		mTherapyTypeSpinner.setSelection(0);
		mTherapyTypeSpinner
				.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
					public void onItemSelected(AdapterView<?> parent,
							View view, int position, long id) {
					}

					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
					}
				});


		mNumberInEveryTimeTextView = (TextView) view.findViewById(R.id.number_in_every_time);
		mUsageTypeInEveryTimeSpinner = (Spinner) view.findViewById(R.id.usage_type_in_every_time);
		mPrivacySpinner = (Spinner) view.findViewById(R.id.visibility);

        mOriginalPadding[0] = mTitleTextView.getPaddingLeft();
        mOriginalPadding[1] = mTitleTextView.getPaddingTop();
        mOriginalPadding[2] = mTitleTextView.getPaddingRight();
        mOriginalPadding[3] = mTitleTextView.getPaddingBottom();

		mEditViewList.add(mTitleTextView);
		mEditViewList.add(mDescriptionTextView);

//		mViewOnlyList.add(view.findViewById(R.id.when_row));

		mEditOnlyList.add(mReminderesGroup);

		mReminderesContainer = (LinearLayout) view
				.findViewById(R.id.reminder_items_container);

		mIsMultipane = activity.getResources().getBoolean(R.bool.tablet_config);
		mWhenTime = new Time();

		// Display loading screen
		setTherapy(null);

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
		Resources r = mActivity.getResources();

		mReminderTypeValues = loadIntegerArray(r,
				R.array.body_status_values);
		mReminderTypeLabels = loadStringArray(r,
				R.array.body_status_labels);
		mReminderTypeDefaultValues = loadStringArray(r,
				R.array.body_status_default_values);
		
		// XXX Add user-defined items.
	}

	/**
	 * Fill in the view with the contents of the given event therapy. This allows
	 * an edit view to be initialized before the event has been loaded. Passing
	 * in null for the therapy will display a loading screen. A non-null therapy
	 * will fill in the view's fields with the data contained in the therapy.
	 * 
	 * @param therapy
	 *            The event therapy to pull the data from
	 */
	public void setTherapy(Therapy therapy) {
		mTherapy = therapy;

		// Need to close the autocomplete adapter to prevent leaking cursors.
		if (mAddressAdapter != null
				&& mAddressAdapter instanceof EmailAddressAdapter) {
			((EmailAddressAdapter) mAddressAdapter).close();
			mAddressAdapter = null;
		}

		if (therapy == null) {
			// Display loading screen
			mLoadingMessage.setVisibility(View.VISIBLE);
			mScrollView.setVisibility(View.GONE);
			return;
		}

		mTherapyTypeSpinner.setSelection(therapy.getType());

		long begin = therapy.getDay();

		// Set up the starting times
		if (begin > 0) {
			mWhenTime.set(begin);
			mWhenTime.normalize(true);
		}

		mRrule = therapy.getUsageRule();
		if (!TextUtils.isEmpty(mRrule)) {
			mEventRecurrence.parse(mRrule);
		}

		if (mEventRecurrence.startDate == null) {
			mEventRecurrence.startDate = mWhenTime;
		}

		mNumberInEveryTimeTextView.setTextKeepState("" + therapy.getNumberInEveryTime());
		mUsageTypeInEveryTimeSpinner.setSelection(therapy.getUsageTypeInEveryTime());
		mPrivacySpinner.setSelection(therapy.getPrivacy() ? 1 : 0);

		SharedPreferences prefs = GeneralPreferences
				.getSharedPreferences(mActivity);
		String defaultBodyStatusString = prefs.getString(
				GeneralPreferences.KEY_DEFAULT_REMINDER,
				GeneralPreferences.NO_REMINDER_STRING);
		mDefaultBodyStatusMinutes = Integer.parseInt(defaultBodyStatusString);

		prepareReminders();

		View button = mView.findViewById(R.id.reminder_add);
		View.OnClickListener listener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addReminder();
			}
		};
		button.setOnClickListener(listener);

		if (therapy.getName() != null) {
			mTitleTextView.setTextKeepState(therapy.getName());
		}

		if (therapy.getDescription() != null) {
			mDescriptionTextView.setTextKeepState(therapy.getDescription());
		}

		populateWhen();
        populateUsage();

		updateView();
		mScrollView.setVisibility(View.VISIBLE);
		mLoadingMessage.setVisibility(View.GONE);

	}

	/**
	 * Updates the view based on {@link #mModification} and {@link #mTherapy}
	 */
	public void updateView() {
		if (mTherapy == null) {
			return;
		}

		setViewStates(mModification);
	}

	private void setViewStates(int mode) {
		// Extra canModify check just in case
		if (mode == Utils.MODIFY_UNINITIALIZED) {

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

			mReminderesGroup.setVisibility(View.VISIBLE);
			mDescriptionGroup.setVisibility(View.VISIBLE);
		}
	}

	public void setModification(int modifyWhich) {
		mModification = modifyWhich;
		updateView();
	}

	private void updateBodyStatussVisibility(int numReminders) {
		if (numReminders == 0) {
			mReminderesContainer.setVisibility(View.GONE);
		} else {
			mReminderesContainer.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Add a new reminder when the user hits the "add reminder" button. We use
	 * the default reminder time and method.
	 */
	private void addReminder() {
		// TODO: when adding a new reminder, make it different from the
		// last one in the list (if any).
		if (mDefaultBodyStatusMinutes == GeneralPreferences.NO_REMINDER) {
			TherapyViewUtils.addTherapyReminder(mActivity, mScrollView, this,
					mReminderItems, mReminderTypeValues,
					mReminderTypeLabels, 
					1,
					Integer.MAX_VALUE, new TimeClickListener(new Time()));
		} else {
			TherapyViewUtils.addTherapyReminder(mActivity, mScrollView, this,
					mReminderItems, mReminderTypeValues,
					mReminderTypeLabels, 					
					1,
					Integer.MAX_VALUE, new TimeClickListener(new Time()));
		}
		updateBodyStatussVisibility(mReminderItems.size());
		TherapyViewUtils.updateAddTherapyReminderButton(mView, mReminderItems, Integer.MAX_VALUE);
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

    private void setTime(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        flags |= DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
        if (DateFormat.is24HourFormat(mActivity)) {
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
            timeString = DateUtils.formatDateTime(mActivity, millis, flags);
            TimeZone.setDefault(null);
        }
        
        view.setTag(millis);
        view.setText(timeString);
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

	// ====================================================================
	public class TherapyTypeAdapter extends BaseAdapter {
		private Context mContext;

		public TherapyTypeAdapter(Context context) {
			mContext = context;
		}

		// ====================================================================
		@Override
		public int getCount() {
			return mResIDGroupOfIcon.length;
		}

		@Override
		public Object getItem(int id) {
			return null;
		}

		@Override
		public long getItemId(int id) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (position < 0 || position >= getCount()) {
				return null;
			}

			synchronized (this) {
				if (mStringGroup == null) {
					mStringGroup = mContext.getResources().getStringArray(
							R.array.therapy_type_labels);
				}
			}

			ItemViewGroup viewGroup = null;

			if (null == convertView) {
				LayoutInflater factory = LayoutInflater.from(mContext);
				viewGroup = new ItemViewGroup();

				convertView = factory.inflate(R.layout.edit_therapy_type_item,
						null);

				viewGroup.mImageView = (ImageView) convertView
						.findViewById(R.id.image);
				viewGroup.mTextView = (TextView) convertView
						.findViewById(R.id.text);

				convertView.setTag(viewGroup);
			} else {
				viewGroup = (ItemViewGroup) convertView.getTag();
			}

			showItem(position, viewGroup);

			return convertView;
		}

		private void showItem(final int position, ItemViewGroup viewGroup) {
			viewGroup.mTextView.setText(mStringGroup[position]);
			viewGroup.mImageView.setImageResource(mResIDGroupOfIcon[position]);
		}

		// ====================================================================
		private final int mResIDGroupOfIcon[] = {
				R.drawable.ic_drug,
				R.drawable.ic_injection,
				0 };

		private String mStringGroup[] = null;

		// ====================================================================
		private class ItemViewGroup {
			/* Item */
			private ImageView mImageView;
			private TextView mTextView;
		}

	}

    public interface EditDoneRunnable extends Runnable {
        public void setDoneCode(int code);
    }

	public Therapy getTherapy() {
		return mTherapy;
	}
}
