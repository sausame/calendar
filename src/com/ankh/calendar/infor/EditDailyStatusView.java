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

package com.ankh.calendar.infor;

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
import android.provider.CalendarContract.Calendars;
import android.provider.Settings;
import android.text.TextUtils;
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

import com.android.calendarcommon2.EventRecurrence;
import com.android.datetimepicker.date.DatePickerDialog;
import com.android.datetimepicker.date.DatePickerDialog.OnDateSetListener;
import com.android.ex.chips.AccountSpecifier;
import com.ankh.calendar.EmailAddressAdapter;
import com.ankh.calendar.GeneralPreferences;
import com.ankh.calendar.Log;
import com.ankh.calendar.R;
import com.ankh.calendar.Utils;
import com.ankh.calendar.event.EditEventHelper;
import com.ankh.calendar.infor.DailyStatus.BodyStatus;
import com.ankh.calendar.infor.EditDailyStatusHelper.EditDoneRunnable;

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

	private Spinner mLevelSpinner;
	private LevelAdapter mLevelAdapter;

	TextView mLoadingMessage;
	ScrollView mScrollView;
	Button mWhenButton;
	TextView mTitleTextView;
	TextView mDescriptionTextView;
	LinearLayout mBodyStatusesContainer;
	View mDescriptionGroup;
	View mBodyStatusesGroup;
	private Spinner mPrivacySpinner;
	private Spinner mPartSpinner;

	private int[] mOriginalPadding = new int[4];

	public boolean mIsMultipane;
	private ProgressDialog mLoadingCalendarsDialog;
	private AlertDialog mNoCalendarsDialog;
	private Activity mActivity;
	private EditDoneRunnable mDone;
	private View mView;
	private DailyStatus mDailyStatus;
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
	private ArrayList<String> mBodyStatusTypeValues;
	private ArrayList<String> mBodyStatusTypeLabels;
	private ArrayList<String> mBodyStatusTypeDefaultValues;

	private int mDefaultBodyStatusMinutes;

	private Time mWhenTime;

	private int mModification = EditDailyStatusHelper.MODIFY_UNINITIALIZED;

	private EventRecurrence mEventRecurrence = new EventRecurrence();

	private ArrayList<LinearLayout> mBodyStatusItems = new ArrayList<LinearLayout>(
			0);
	private ArrayList<BodyStatus> mUnsupportedBodyStatuss = new ArrayList<BodyStatus>();


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
	 * ready to be saved. An event is ready to be saved so long as a dailyStatus
	 * exists and has a calendar it can be associated with, either because it's
	 * an existing event or we've finished querying.
	 * 
	 * @return false if there is no dailyStatus or no calendar had been loaded yet,
	 *         true otherwise.
	 */
	public boolean prepareForSave() {
		if (mDailyStatus == null) {
			return false;
		}
		return fillDailyStatusFromUI();
	}

	public boolean fillDailyStatusFromReadOnlyUi() {
		if (mDailyStatus == null) {
			return false;
		}
/*		mDailyStatus.mBodyStatuss = InforViewUtils.reminderItemsToBodyStatuss(
				mBodyStatusItems, mBodyStatusTypeValues, mBodyStatusTypeDefaultValues);
		mDailyStatus.mBodyStatuss.addAll(mUnsupportedBodyStatuss);
		mDailyStatus.normalizeBodyStatuss();*/
		return true;
	}

	// This is called if the user clicks on one of the buttons: "Save",
	// "Discard", or "Delete". This is also called if the user clicks
	// on the "remove reminder" button.
	@Override
	public void onClick(View view) {
        if (view.getId() == R.id.body_status_remove) {
			// This must be a click on one of the "remove reminder" buttons
			LinearLayout reminderItem = (LinearLayout) view.getParent();
			LinearLayout parent = (LinearLayout) reminderItem.getParent();
			parent.removeView(reminderItem);
			mBodyStatusItems.remove(reminderItem);
			updateBodyStatussVisibility(mBodyStatusItems.size());
        }

/*		InforViewUtils.updateAddBodyStatusButton(mView, mBodyStatusItems,
				mDailyStatus.mCalendarMaxBodyStatuss);*/
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
				final String[] array = { "com.ankh.calendar" };
				nextIntent.putExtra(Settings.EXTRA_AUTHORITIES, array);
				nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
						| Intent.FLAG_ACTIVITY_NEW_TASK);
				mActivity.startActivity(nextIntent);
			}
		}
	}

	// Goes through the UI elements and updates the dailyStatus as necessary
	private boolean fillDailyStatusFromUI() {
		if (mDailyStatus == null) {
			return false;
		}

		mDailyStatus.setLevel(mLevelSpinner.getSelectedItemPosition());
		mDailyStatus.setPart("" + mPartSpinner.getSelectedItemPosition());

		mDailyStatus.setPrivacy(mPrivacySpinner.getSelectedItemPosition() == 0);

		mDailyStatus.setName(mTitleTextView.getText().toString());
		mDailyStatus.setDescription(mDescriptionTextView.getText().toString());

		if (TextUtils.isEmpty(mDailyStatus.getDescription())) {
			mDailyStatus.setDescription(null);
		}

		mWhenTime.hour = 0;
		mWhenTime.minute = 0;
		mWhenTime.second = 0;

		mDailyStatus.setDay(mWhenTime.normalize(true));

		mDailyStatus.setBodyStatusesGroup(InforViewUtils
				.bodyStatusItemsToBodyStatuses(mBodyStatusItems,
						mBodyStatusTypeValues));

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

		mLevelSpinner = (Spinner) view.findViewById(R.id.serious);

		mLevelAdapter = new LevelAdapter(activity);
		mLevelSpinner.setAdapter(mLevelAdapter);
		mLevelSpinner.setSelection(0);
		mLevelSpinner
				.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
					public void onItemSelected(AdapterView<?> parent,
							View view, int position, long id) {
					}

					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
					}
				});

		mPrivacySpinner = (Spinner) view.findViewById(R.id.visibility);
		mPartSpinner = (Spinner) view.findViewById(R.id.part);

        mOriginalPadding[0] = mTitleTextView.getPaddingLeft();
        mOriginalPadding[1] = mTitleTextView.getPaddingTop();
        mOriginalPadding[2] = mTitleTextView.getPaddingRight();
        mOriginalPadding[3] = mTitleTextView.getPaddingBottom();

		mEditViewList.add(mTitleTextView);
		mEditViewList.add(mDescriptionTextView);

//		mViewOnlyList.add(view.findViewById(R.id.when_row));

		mEditOnlyList.add(mBodyStatusesGroup);

		mBodyStatusesContainer = (LinearLayout) view
				.findViewById(R.id.body_status_items_container);

		mIsMultipane = activity.getResources().getBoolean(R.bool.tablet_config);
		mWhenTime = new Time();

		// Display loading screen
		setDailyStatus(null);

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
	private void prepareBodyStatuses() {
		Resources r = mActivity.getResources();

		mBodyStatusTypeValues = loadStringArray(r,
				R.array.body_status_values);
		mBodyStatusTypeLabels = loadStringArray(r,
				R.array.body_status_labels);
		mBodyStatusTypeDefaultValues = loadStringArray(r,
				R.array.body_status_default_values);
		
		// XXX Add user-defined items.
	}
	
	public DailyStatus getDailyStatus() {
		return mDailyStatus;
	}

	/**
	 * Fill in the view with the contents of the given event dailyStatus. This allows
	 * an edit view to be initialized before the event has been loaded. Passing
	 * in null for the dailyStatus will display a loading screen. A non-null dailyStatus
	 * will fill in the view's fields with the data contained in the dailyStatus.
	 * 
	 * @param dailyStatus
	 *            The event dailyStatus to pull the data from
	 */
	public void setDailyStatus(DailyStatus dailyStatus) {
		mDailyStatus = dailyStatus;

		// Need to close the autocomplete adapter to prevent leaking cursors.
		if (mAddressAdapter != null
				&& mAddressAdapter instanceof EmailAddressAdapter) {
			((EmailAddressAdapter) mAddressAdapter).close();
			mAddressAdapter = null;
		}

		if (dailyStatus == null) {
			// Display loading screen
			mLoadingMessage.setVisibility(View.VISIBLE);
			mScrollView.setVisibility(View.GONE);
			return;
		}

		long begin = dailyStatus.getDay();

		// Set up the starting times
		if (begin > 0) {
			mWhenTime.set(begin);
			mWhenTime.normalize(true);
		}

		SharedPreferences prefs = GeneralPreferences
				.getSharedPreferences(mActivity);
		String defaultBodyStatusString = prefs.getString(
				GeneralPreferences.KEY_DEFAULT_REMINDER,
				GeneralPreferences.NO_REMINDER_STRING);
		mDefaultBodyStatusMinutes = Integer.parseInt(defaultBodyStatusString);

		prepareBodyStatuses();

		View bodyStatusAddButton = mView.findViewById(R.id.body_status_add);
		View.OnClickListener addBodyStatusOnClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addBodyStatus();
			}
		};
		bodyStatusAddButton.setOnClickListener(addBodyStatusOnClickListener);

		if (dailyStatus.getName() != null) {
			mTitleTextView.setTextKeepState(dailyStatus.getName());
		}

		if (dailyStatus.getDescription() != null) {
			mDescriptionTextView.setTextKeepState(dailyStatus.getDescription());
		}

		populateWhen();

		updateView();
		mScrollView.setVisibility(View.VISIBLE);
		mLoadingMessage.setVisibility(View.GONE);

	}

	/**
	 * Updates the view based on {@link #mModification} and {@link #mDailyStatus}
	 */
	public void updateView() {
		if (mDailyStatus == null) {
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

			mBodyStatusesGroup.setVisibility(View.VISIBLE);
			mDescriptionGroup.setVisibility(View.VISIBLE);
		}
	}

	public void setModification(int modifyWhich) {
		mModification = modifyWhich;
		updateView();
	}

	private void updateBodyStatussVisibility(int numBodyStatuss) {
		if (numBodyStatuss == 0) {
			mBodyStatusesContainer.setVisibility(View.GONE);
		} else {
			mBodyStatusesContainer.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Add a new reminder when the user hits the "add reminder" button. We use
	 * the default reminder time and method.
	 */
	private void addBodyStatus() {
		
		// TODO: when adding a new reminder, make it different from the
		// last one in the list (if any).
		InforViewUtils.addBodyStatus(mActivity, mScrollView,
				mBodyStatusItems,
				mBodyStatusTypeValues,
				mBodyStatusTypeLabels,
				mBodyStatusTypeDefaultValues,
				new BodyStatus(),
				Integer.MAX_VALUE, 
				this, this, this);

		updateBodyStatussVisibility(mBodyStatusItems.size());
		InforViewUtils.updateAddBodyStatusButton(mView, mBodyStatusItems, Integer.MAX_VALUE);
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
		if (parent.getId() == R.id.body_status_type) {
			InforViewUtils.setBodyStatusValueButton(parent.getParent(),
					position, mBodyStatusTypeDefaultValues, this);
			return;
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
	}

	// ====================================================================
	public class LevelAdapter extends BaseAdapter {
		private Context mContext;

		public LevelAdapter(Context context) {
			mContext = context;
		}

		// ====================================================================
		@Override
		public int getCount() {
			return mResIDGroupOfBackground.length;
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
							R.array.serious_level_labels);
				}
			}

			ItemViewGroup viewGroup = null;

			if (null == convertView) {
				LayoutInflater factory = LayoutInflater.from(mContext);
				viewGroup = new ItemViewGroup();

				convertView = factory.inflate(R.layout.edit_serious_level_item,
						null);

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
			viewGroup.mTextView
					.setBackgroundResource(mResIDGroupOfBackground[position]);
		}

		// ====================================================================
		private final int mResIDGroupOfBackground[] = {
				R.drawable.list_selector_holo_green,
				R.drawable.list_selector_holo_blue,
				R.drawable.list_selector_holo_orange,
				R.drawable.list_selector_holo_purple,
				R.drawable.list_selector_holo_red };

		private String mStringGroup[] = null;

		// ====================================================================
		private class ItemViewGroup {
			/* Item */
			private TextView mTextView;
		}

	}

}
