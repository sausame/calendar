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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarDatabase;
import com.android.calendar.DeleteEventHelper;
import com.android.calendar.Log;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.event.EventColorCache;
import com.android.calendar.event.EventColorPickerDialog;
import com.android.colorpicker.ColorPickerSwatch.OnColorSelectedListener;
import com.android.colorpicker.HsvColorComparator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

@SuppressLint("ValidFragment")
public class EditTherapyFragment extends Fragment implements EventHandler {
    private static final String TAG = "EditEventActivity";
    private static final String COLOR_PICKER_DIALOG_TAG = "ColorPickerDialog";

    private static final String BUNDLE_KEY_MODEL = "key_therapy";
    private static final String BUNDLE_KEY_EDIT_STATE = "key_edit_state";
    private static final String BUNDLE_KEY_EVENT = "key_event";
    private static final String BUNDLE_KEY_READ_ONLY = "key_read_only";
    private static final String BUNDLE_KEY_EDIT_ON_LAUNCH = "key_edit_on_launch";
    private static final String BUNDLE_KEY_SHOW_COLOR_PALETTE = "show_color_palette";

    private static final String BUNDLE_KEY_DATE_BUTTON_CLICKED = "date_button_clicked";

    private static final boolean DEBUG = true;

    private static final int TOKEN_EVENT = 1;
//    private static final int TOKEN_ATTENDEES = 1 << 1;
//    private static final int TOKEN_REMINDERS = 1 << 2;
//    private static final int TOKEN_CALENDARS = 1 << 3;
//    private static final int TOKEN_COLORS = 1 << 4;
//
//    private static final int TOKEN_ALL = TOKEN_EVENT | TOKEN_ATTENDEES | TOKEN_REMINDERS
//            | TOKEN_CALENDARS | TOKEN_COLORS;
    private static final int TOKEN_UNITIALIZED = 1 << 31;

    /**
     * A bitfield of TOKEN_* to keep track which query hasn't been completed
     * yet. Once all queries have returned, the therapy can be applied to the
     * view.
     */
    private int mOutstandingQueries = TOKEN_UNITIALIZED;

    Therapy mTherapy;
    Therapy mOriginalTherapy = null;
    Therapy mRestoreTherapy;
    EditTherapyView mView;
    QueryHandler mHandler;

    private AlertDialog mModifyDialog;
    int mModification = Utils.MODIFY_UNINITIALIZED;

    private final EventInfo mEvent;
    private EventBundle mEventBundle;
    private Uri mUri;

    private Activity mActivity;
    private final Done mOnDone = new Done();

    private boolean mSaveOnDetach = true;
    private boolean mIsReadOnly = false;
    public boolean mShowModifyDialogOnLaunch = false;

    private boolean mTimeSelectedWasStartTime;
    private boolean mDateSelectedWasStartDate;

    private InputMethodManager mInputMethodManager;

    private final Intent mIntent;

    private boolean mUseCustomActionBar;

    private final View.OnClickListener mActionBarListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onActionBarItemSelected(v.getId());
        }
    };

    // TODO turn this into a helper function in EditDailyStatusHelper for building the
    // therapy
    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // If the query didn't return a cursor for some reason return
            if (cursor == null) {
                return;
            }

            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            final Activity activity = EditTherapyFragment.this.getActivity();
            if (activity == null || activity.isFinishing()) {
                cursor.close();
                return;
            }
            long eventId;
            switch (token) {
                case TOKEN_EVENT:
                    if (cursor.getCount() == 0) {
                        // The cursor is empty. This can happen if the event
                        // was deleted.
                        cursor.close();
                        mOnDone.setDoneCode(Utils.DONE_EXIT);
                        mSaveOnDetach = false;
                        mOnDone.run();
                        return;
                    }
                    mOriginalTherapy = Therapy.parse(cursor);
                    mTherapy = Therapy.parse(cursor);
                    
                    cursor.close();

                    setTherapyIfDone(TOKEN_EVENT);
                    break;
                default:
                    cursor.close();
                    break;
            }
        }
    }

    private void setTherapyIfDone(int queryType) {
        synchronized (this) {
            mOutstandingQueries &= ~queryType;
            if (mOutstandingQueries == 0) {
                if (mRestoreTherapy != null) {
                    mTherapy = mRestoreTherapy;
                }
                if (mShowModifyDialogOnLaunch && mModification == Utils.MODIFY_UNINITIALIZED) {
                        mModification = Utils.MODIFY_ALL;
                }
                
                mView.setTherapy(new Therapy());
                mView.setModification(mModification);
            }
        }
    }

    public EditTherapyFragment() {
        this(null, false, null);
    }

    public EditTherapyFragment(EventInfo event, boolean readOnly, Intent intent) {
        mEvent = event;
        mIsReadOnly = readOnly;
        mIntent = intent;

        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void startQuery() {
        mUri = null;
 
        // Kick off the query for the event
        boolean newEvent = mUri == null;
        if (!newEvent) {
            mOutstandingQueries = TOKEN_EVENT;
            if (DEBUG) {
                Log.d(TAG, "startQuery: uri for therapy is " + mUri.toString());
            }
            
            // Query.
        } else {
            mOutstandingQueries = TOKEN_EVENT;
            if (DEBUG) {
                Log.d(TAG, "startQuery: Editing a new therapy.");
            }

            mModification = Utils.MODIFY_ALL;
            mView.setModification(mModification);
        }
        
        setTherapyIfDone(TOKEN_EVENT);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;

        mHandler = new QueryHandler(activity.getContentResolver());
        mTherapy = Therapy.from(mIntent);
        mInputMethodManager = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        mUseCustomActionBar = !Utils.getConfigBool(mActivity, R.bool.multiple_pane_config);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
//        mContext.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        View view;
        if (mIsReadOnly) {
            view = inflater.inflate(R.layout.edit_daily_status_single_column, null);
        } else {
            view = inflater.inflate(R.layout.edit_therapy, null);
        }
        mView = new EditTherapyView(mActivity, view, mOnDone, mTimeSelectedWasStartTime,
                mDateSelectedWasStartDate);
        
        startQuery();

        if (mUseCustomActionBar) {
            View actionBarButtons = inflater.inflate(R.layout.edit_event_custom_actionbar,
                    new LinearLayout(mActivity), false);
            View cancelActionView = actionBarButtons.findViewById(R.id.action_cancel);
            cancelActionView.setOnClickListener(mActionBarListener);
            View doneActionView = actionBarButtons.findViewById(R.id.action_done);
            doneActionView.setOnClickListener(mActionBarListener);

            mActivity.getActionBar().setCustomView(actionBarButtons);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mUseCustomActionBar) {
            mActivity.getActionBar().setCustomView(null);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BUNDLE_KEY_MODEL)) {
                mRestoreTherapy = (Therapy) savedInstanceState.getSerializable(
                        BUNDLE_KEY_MODEL);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_EDIT_STATE)) {
                mModification = savedInstanceState.getInt(BUNDLE_KEY_EDIT_STATE);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_EDIT_ON_LAUNCH)) {
                mShowModifyDialogOnLaunch = savedInstanceState
                        .getBoolean(BUNDLE_KEY_EDIT_ON_LAUNCH);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_EVENT)) {
                mEventBundle = (EventBundle) savedInstanceState.getSerializable(BUNDLE_KEY_EVENT);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_READ_ONLY)) {
                mIsReadOnly = savedInstanceState.getBoolean(BUNDLE_KEY_READ_ONLY);
            }
            if (savedInstanceState.containsKey("EditEventView_timebuttonclicked")) {
                mTimeSelectedWasStartTime = savedInstanceState.getBoolean(
                        "EditEventView_timebuttonclicked");
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_DATE_BUTTON_CLICKED)) {
                mDateSelectedWasStartDate = savedInstanceState.getBoolean(
                        BUNDLE_KEY_DATE_BUTTON_CLICKED);
            }

        }
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (!mUseCustomActionBar) {
            inflater.inflate(R.menu.edit_event_title_bar, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onActionBarItemSelected(item.getItemId());
    }

    /**
     * Handles menu item selections, whether they come from our custom action bar buttons or from
     * the standard menu items. Depends on the menu item ids matching the custom action bar button
     * ids.
     *
     * @param itemId the button or menu item id
     * @return whether the event was handled here
     */
	private boolean onActionBarItemSelected(int itemId) {
		if (itemId == R.id.action_done) {
			if (mView != null && mView.prepareForSave()) {
				if (mModification == Utils.MODIFY_UNINITIALIZED) {
					mModification = Utils.MODIFY_ALL;
				}
				mOnDone.setDoneCode(Utils.DONE_SAVE | Utils.DONE_EXIT);
				mOnDone.run();
			} else {
				mOnDone.setDoneCode(Utils.DONE_REVERT);
				mOnDone.run();
			}

		} else if (itemId == R.id.action_cancel) {
			mOnDone.setDoneCode(Utils.DONE_REVERT);
			mOnDone.run();
		}
		return true;
	}

    protected void displayEditWhichDialog() {
        if (mModification == Utils.MODIFY_UNINITIALIZED) {
            int itemIndex = 0;
            CharSequence[] items;
			items = new CharSequence[2];

			items[itemIndex++] = mActivity.getText(R.string.modify_event);
            items[itemIndex++] = mActivity.getText(R.string.modify_all);

            // Display the modification dialog.
            if (mModifyDialog != null) {
                mModifyDialog.dismiss();
                mModifyDialog = null;
            }
            mModifyDialog = new AlertDialog.Builder(mActivity).setTitle(R.string.edit_event_label)
                    .setItems(items, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                // Update this if we start allowing exceptions
                                // to unsynced events in the app
                                mModification = Utils.MODIFY_ALL;
                            } else if (which == 1) {
                                mModification =Utils.MODIFY_ALL_FOLLOWING;
                            } else if (which == 2) {
                                mModification = Utils.MODIFY_ALL_FOLLOWING;
                            }

                            mView.setModification(mModification);
                        }
                    }).show();

            mModifyDialog.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Activity a = EditTherapyFragment.this.getActivity();
                    if (a != null) {
                        a.finish();
                    }
                }
            });
        }
    }

    class Done implements EditTherapyView.EditDoneRunnable {
        private int mCode = -1;

        @Override
        public void setDoneCode(int code) {
            mCode = code;
        }

        @Override
        public void run() {
            // We only want this to get called once, either because the user
            // pressed back/home or one of the buttons on screen
            mSaveOnDetach = false;
            if (mModification == Utils.MODIFY_UNINITIALIZED) {
                // If this is uninitialized the user hit back, the only
                // changeable item is response to default to all events.
                mModification = Utils.MODIFY_ALL;
            }

			if ((mCode & Utils.DONE_SAVE) != 0
					&& mTherapy != null
					&& mView.prepareForSave()
					&& !isEmptyNewTherapy()
					&& CalendarDatabase.saveTherapy(mActivity, mTherapy,
							mOriginalTherapy, mModification)) {
				int stringResource;
				if (mOriginalTherapy != null) {
					stringResource = R.string.saving_event;
				} else {
					stringResource = R.string.creating_event;
				}

				Toast.makeText(mActivity, stringResource, Toast.LENGTH_SHORT)
						.show();
            } else if ((mCode & Utils.DONE_SAVE) != 0 && mTherapy != null && isEmptyNewTherapy()) {
                Toast.makeText(mActivity, R.string.empty_event, Toast.LENGTH_SHORT).show();
            }

            if ((mCode & Utils.DONE_EXIT) != 0) {
                // This will exit the edit event screen, should be called
                // when we want to return to the main calendar views
				if ((mCode & Utils.DONE_SAVE) != 0) {
					if (mActivity != null) {
						long start = mTherapy.getDay();
						long end = start;

						// For allday events we want to go to the day in the
						// user's current tz
						String tz = Utils.getTimeZone(mActivity, null);
						Time t = new Time(Time.TIMEZONE_UTC);
						t.set(start);
						t.timezone = tz;
						start = t.toMillis(true);

						t.timezone = Time.TIMEZONE_UTC;
						t.set(end);
						t.timezone = tz;
						end = t.toMillis(true);

						CalendarController.getInstance(mActivity)
								.launchViewEvent(-1, start, end,
										Attendees.ATTENDEE_STATUS_NONE);
					}
                }
                Activity a = EditTherapyFragment.this.getActivity();
                if (a != null) {
                    a.finish();
                }
            }

            // Hide a software keyboard so that user won't see it even after this Fragment's
            // disappearing.
            final View focusedView = mActivity.getCurrentFocus();
            if (focusedView != null) {
                mInputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
                focusedView.clearFocus();
            }
        }
    }

    boolean isEmptyNewTherapy() {
        return mTherapy.isEmpty();
    }

    @Override
    public void onPause() {
        Activity act = getActivity();
        if (mSaveOnDetach && act != null && !mIsReadOnly && !act.isChangingConfigurations()
                && mView.prepareForSave()) {
            mOnDone.setDoneCode(Utils.DONE_SAVE);
            mOnDone.run();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mView != null) {
            mView.setTherapy(null);
        }
        if (mModifyDialog != null) {
            mModifyDialog.dismiss();
            mModifyDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public void eventsChanged() {
        // TODO Requery to see if event has changed
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mView.prepareForSave();
        outState.putSerializable(BUNDLE_KEY_MODEL, mTherapy);
        outState.putInt(BUNDLE_KEY_EDIT_STATE, mModification);
        if (mEventBundle == null && mEvent != null) {
            mEventBundle = new EventBundle();
            mEventBundle.id = mEvent.id;
            if (mEvent.startTime != null) {
                mEventBundle.start = mEvent.startTime.toMillis(true);
            }
            if (mEvent.endTime != null) {
                mEventBundle.end = mEvent.startTime.toMillis(true);
            }
        }
        outState.putBoolean(BUNDLE_KEY_EDIT_ON_LAUNCH, mShowModifyDialogOnLaunch);
        outState.putSerializable(BUNDLE_KEY_EVENT, mEventBundle);
        outState.putBoolean(BUNDLE_KEY_READ_ONLY, mIsReadOnly);

        outState.putBoolean("EditEventView_timebuttonclicked", mView.mTimeSelectedWasStartTime);
        outState.putBoolean(BUNDLE_KEY_DATE_BUTTON_CLICKED, mView.mDateSelectedWasStartDate);
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.USER_HOME;
    }

    @Override
    public void handleEvent(EventInfo event) {
        // It's currently unclear if we want to save the event or not when home
        // is pressed. When creating a new event we shouldn't save since we
        // can't get the id of the new event easily.
        if ((false && event.eventType == EventType.USER_HOME) || (event.eventType == EventType.GO_TO
                && mSaveOnDetach)) {
            if (mView != null && mView.prepareForSave()) {
                mOnDone.setDoneCode(Utils.DONE_SAVE);
                mOnDone.run();
            }
        }
    }

    private static class EventBundle implements Serializable {
        private static final long serialVersionUID = 1L;
        long id = -1;
        long start = -1;
        long end = -1;
    }

}
