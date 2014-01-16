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

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.CalendarController.EVENT_EDIT_ON_LAUNCH;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.util.Rfc822Token;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.DeleteEventHelper;
import com.android.calendar.Event;
import com.android.calendar.EventRecurrenceFormatter;
import com.android.calendar.ExpandableTextView;
import com.android.calendar.GeneralPreferences;
import com.android.calendar.Log;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.alerts.QuickResponseActivity;
import com.android.calendar.event.AttendeesView;
import com.android.calendar.event.EditEventActivity;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.event.EventColorPickerDialog;
import com.android.calendar.event.EventViewUtils;
import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.Duration;
import com.android.calendarcommon2.EventRecurrence;
import com.android.colorpicker.ColorPickerSwatch.OnColorSelectedListener;
import com.android.colorpicker.HsvColorComparator;

@SuppressLint("ValidFragment")
public class DailyStatusInfoFragment extends DialogFragment implements OnCheckedChangeListener,
        CalendarController.EventHandler, OnClickListener, DeleteEventHelper.DeleteNotifyListener,
        OnColorSelectedListener {

    public static final boolean DEBUG = false;

    public static final String TAG = "DailyStatusInfoFragment";
    public static final String COLOR_PICKER_DIALOG_TAG = "EventColorPickerDialog";

    protected static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    protected static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";
    protected static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";
    protected static final String BUNDLE_KEY_IS_DIALOG = "key_fragment_is_dialog";
    protected static final String BUNDLE_KEY_DELETE_DIALOG_VISIBLE = "key_delete_dialog_visible";
    protected static final String BUNDLE_KEY_WINDOW_STYLE = "key_window_style";
    protected static final String BUNDLE_KEY_CALENDAR_COLOR = "key_calendar_color";
    protected static final String BUNDLE_KEY_CALENDAR_COLOR_INIT = "key_calendar_color_init";
    protected static final String BUNDLE_KEY_CURRENT_COLOR = "key_current_color";
    protected static final String BUNDLE_KEY_CURRENT_COLOR_KEY = "key_current_color_key";
    protected static final String BUNDLE_KEY_CURRENT_COLOR_INIT = "key_current_color_init";
    protected static final String BUNDLE_KEY_ORIGINAL_COLOR = "key_original_color";
    protected static final String BUNDLE_KEY_ORIGINAL_COLOR_INIT = "key_original_color_init";
    protected static final String BUNDLE_KEY_ATTENDEE_RESPONSE = "key_attendee_response";
    protected static final String BUNDLE_KEY_USER_SET_ATTENDEE_RESPONSE =
            "key_user_set_attendee_response";
    protected static final String BUNDLE_KEY_TENTATIVE_USER_RESPONSE =
            "key_tentative_user_response";
    protected static final String BUNDLE_KEY_RESPONSE_WHICH_EVENTS = "key_response_which_events";
    protected static final String BUNDLE_KEY_REMINDER_MINUTES = "key_reminder_minutes";
    protected static final String BUNDLE_KEY_REMINDER_METHODS = "key_reminder_methods";


    private static final String PERIOD_SPACE = ". ";

    private static final String NO_EVENT_COLOR = "";

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;

    // Style of view
    public static final int FULL_WINDOW_STYLE = 0;
    public static final int DIALOG_WINDOW_STYLE = 1;

    private int mWindowStyle = DIALOG_WINDOW_STYLE;

    // Query tokens for QueryHandler
    private static final int TOKEN_QUERY_EVENT = 1 << 0;
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_ATTENDEES = 1 << 2;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 1 << 3;
    private static final int TOKEN_QUERY_REMINDERS = 1 << 4;
    private static final int TOKEN_QUERY_VISIBLE_CALENDARS = 1 << 5;
    private static final int TOKEN_QUERY_COLORS = 1 << 6;

    private static final int TOKEN_QUERY_ALL = TOKEN_QUERY_DUPLICATE_CALENDARS
            | TOKEN_QUERY_ATTENDEES | TOKEN_QUERY_CALENDARS | TOKEN_QUERY_EVENT
            | TOKEN_QUERY_REMINDERS | TOKEN_QUERY_VISIBLE_CALENDARS | TOKEN_QUERY_COLORS;

    private int mCurrentQuery = 0;

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,                  // 0  do not remove; used in DeleteEventHelper
        Events.TITLE,                // 1  do not remove; used in DeleteEventHelper
        Events.RRULE,                // 2  do not remove; used in DeleteEventHelper
        Events.ALL_DAY,              // 3  do not remove; used in DeleteEventHelper
        Events.CALENDAR_ID,          // 4  do not remove; used in DeleteEventHelper
        Events.DTSTART,              // 5  do not remove; used in DeleteEventHelper
        Events._SYNC_ID,             // 6  do not remove; used in DeleteEventHelper
        Events.EVENT_TIMEZONE,       // 7  do not remove; used in DeleteEventHelper
        Events.DESCRIPTION,          // 8
        Events.EVENT_LOCATION,       // 9
        Calendars.CALENDAR_ACCESS_LEVEL, // 10
        Events.CALENDAR_COLOR,       // 11
        Events.EVENT_COLOR,          // 12
        Events.HAS_ATTENDEE_DATA,    // 13
        Events.ORGANIZER,            // 14
        Events.HAS_ALARM,            // 15
        Calendars.MAX_REMINDERS,     // 16
        Calendars.ALLOWED_REMINDERS, // 17
        Events.CUSTOM_APP_PACKAGE,   // 18
        Events.CUSTOM_APP_URI,       // 19
        Events.DTEND,                // 20
        Events.DURATION,             // 21
        Events.ORIGINAL_SYNC_ID      // 22 do not remove; used in DeleteEventHelper
    };
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_TITLE = 1;
    private static final int EVENT_INDEX_RRULE = 2;
    private static final int EVENT_INDEX_ALL_DAY = 3;
    private static final int EVENT_INDEX_CALENDAR_ID = 4;
    private static final int EVENT_INDEX_DTSTART = 5;
    private static final int EVENT_INDEX_SYNC_ID = 6;
    private static final int EVENT_INDEX_EVENT_TIMEZONE = 7;
    private static final int EVENT_INDEX_DESCRIPTION = 8;
    private static final int EVENT_INDEX_EVENT_LOCATION = 9;
    private static final int EVENT_INDEX_ACCESS_LEVEL = 10;
    private static final int EVENT_INDEX_CALENDAR_COLOR = 11;
    private static final int EVENT_INDEX_EVENT_COLOR = 12;
    private static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 13;
    private static final int EVENT_INDEX_ORGANIZER = 14;
    private static final int EVENT_INDEX_HAS_ALARM = 15;
    private static final int EVENT_INDEX_MAX_REMINDERS = 16;
    private static final int EVENT_INDEX_ALLOWED_REMINDERS = 17;
    private static final int EVENT_INDEX_CUSTOM_APP_PACKAGE = 18;
    private static final int EVENT_INDEX_CUSTOM_APP_URI = 19;
    private static final int EVENT_INDEX_DTEND = 20;
    private static final int EVENT_INDEX_DURATION = 21;

    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_NAME,            // 1
        Attendees.ATTENDEE_EMAIL,           // 2
        Attendees.ATTENDEE_RELATIONSHIP,    // 3
        Attendees.ATTENDEE_STATUS,          // 4
        Attendees.ATTENDEE_IDENTITY,        // 5
        Attendees.ATTENDEE_ID_NAMESPACE     // 6
    };

    static {
        if (!Utils.isJellybeanOrLater()) {
            EVENT_PROJECTION[EVENT_INDEX_CUSTOM_APP_PACKAGE] = Events._ID; // dummy value
            EVENT_PROJECTION[EVENT_INDEX_CUSTOM_APP_URI] = Events._ID; // dummy value
        }
    }

    static final String[] CALENDARS_PROJECTION = new String[] {
        Calendars._ID,           // 0
        Calendars.CALENDAR_DISPLAY_NAME,  // 1
        Calendars.OWNER_ACCOUNT, // 2
        Calendars.CAN_ORGANIZER_RESPOND, // 3
        Calendars.ACCOUNT_NAME, // 4
        Calendars.ACCOUNT_TYPE  // 5
    };
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_OWNER_CAN_RESPOND = 3;
    static final int CALENDARS_INDEX_ACCOUNT_NAME = 4;
    static final int CALENDARS_INDEX_ACCOUNT_TYPE = 5;

    static final String CALENDARS_WHERE = Calendars._ID + "=?";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.CALENDAR_DISPLAY_NAME + "=?";
    static final String CALENDARS_VISIBLE_WHERE = Calendars.VISIBLE + "=?";

    static final String[] COLORS_PROJECTION = new String[] {
        Colors._ID, // 0
        Colors.COLOR, // 1
        Colors.COLOR_KEY // 2
    };

    static final String COLORS_WHERE = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE +
        "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;

    public static final int COLORS_INDEX_COLOR = 1;
    public static final int COLORS_INDEX_COLOR_KEY = 2;

    private View mView;

	private DailyStatus mDailyStatus;

    private static float mScale = 0; // Used for supporting different screen densities

    private static int mCustomAppIconSize = 32;

    private long mStartMillis;
    private long mEndMillis;
    private boolean mAllDay;

    private String mSyncAccountName;
    private String mCalendarOwnerAccount;
    private boolean mCanModifyCalendar;
    private boolean mCanModifyEvent;
    private boolean mIsBusyFreeCalendar;
    private boolean mDeleteDialogVisible = false;
    private DeleteEventHelper mDeleteHelper;

    // Used to prevent saving changes in event if it is being deleted.
    private boolean mEventDeletionStarted = false;

    private TextView mTitle;
    private TextView mWhenDateTime;
    private TextView mWhere;
    private ExpandableTextView mDesc;
    private Menu mMenu = null;
    private View mHeadlines;
    private ScrollView mScrollView;
    private View mLoadingMsgView;
    private View mErrorMsgView;
    private ObjectAnimator mAnimateAlpha;

    private EventColorPickerDialog mColorPickerDialog;
    private SparseIntArray mDisplayColorKeyMap = new SparseIntArray();
    private int[] mColors;
    private int mOriginalColor = -1;
    private boolean mOriginalColorInitialized = false;
    private int mCalendarColor = -1;
    private boolean mCalendarColorInitialized = false;
    private int mCurrentColor = -1;
    private boolean mCurrentColorInitialized = false;
    private int mCurrentColorKey = -1;

    private static final int FADE_IN_TIME = 300;   // in milliseconds
    private static final int LOADING_MSG_DELAY = 600;   // in milliseconds
    private static final int LOADING_MSG_MIN_DISPLAY_TIME = 600;
    private boolean mNoCrossFade = false;  // Used to prevent repeated cross-fade

    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            updateEvent(mView);
        }
    };

    private static int mDialogWidth = 500;
    private static int mDialogHeight = 600;
    private static int DIALOG_TOP_MARGIN = 8;
    private boolean mIsDialog = false;
    private boolean mIsPaused = true;
    private boolean mDismissOnResume = false;
    private int mX = -1;
    private int mY = -1;
    private int mMinTop;         // Dialog cannot be above this location
    private boolean mIsTabletConfig;
    private Activity mActivity;
    private Context mContext;

    private CalendarController mController;

    private void sendAccessibilityEventIfQueryDone(int token) {
        mCurrentQuery |= token;
        if (mCurrentQuery == TOKEN_QUERY_ALL) {
            sendAccessibilityEvent();
        }
    }

    public DailyStatusInfoFragment(Context context, DailyStatus dailyStatus, boolean isDialog,
				int windowStyle) {

        Resources r = context.getResources();
        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                mCustomAppIconSize *= mScale;
                if (isDialog) {
                    DIALOG_TOP_MARGIN *= mScale;
                }
            }
        }
        if (isDialog) {
            setDialogSize(r);
        }
        mIsDialog = isDialog;

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
		mDailyStatus = dailyStatus;
        mWindowStyle = windowStyle;
    }

    // This is currently required by the fragment manager.
    public DailyStatusInfoFragment() {
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
            mWindowStyle = savedInstanceState.getInt(BUNDLE_KEY_WINDOW_STYLE,
                    DIALOG_WINDOW_STYLE);
        }

        if (mIsDialog) {
            applyDialogParams();
        }

        final Activity activity = getActivity();
        mContext = activity;
        mColorPickerDialog = (EventColorPickerDialog) activity.getFragmentManager()
                .findFragmentByTag(COLOR_PICKER_DIALOG_TAG);
        if (mColorPickerDialog != null) {
            mColorPickerDialog.setOnColorSelectedListener(this);
        }
    }

    private void applyDialogParams() {
        Dialog dialog = getDialog();
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams a = window.getAttributes();
        a.dimAmount = .4f;

        a.width = mDialogWidth;
        a.height = mDialogHeight;


        // On tablets , do smart positioning of dialog
        // On phones , use the whole screen

        if (mX != -1 || mY != -1) {
            a.x = mX - mDialogWidth / 2;
            a.y = mY - mDialogHeight / 2;
            if (a.y < mMinTop) {
                a.y = mMinTop + DIALOG_TOP_MARGIN;
            }
            a.gravity = Gravity.LEFT | Gravity.TOP;
        }
        window.setAttributes(a);
    }

    public void setDialogParams(int x, int y, int minTop) {
        mX = x;
        mY = y;
        mMinTop = minTop;
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mController.deregisterEventHandler(R.layout.event_info);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        // Ensure that mIsTabletConfig is set before creating the menu.
        mIsTabletConfig = Utils.getConfigBool(mActivity, R.bool.tablet_config);
        mController = CalendarController.getInstance(mActivity);
        mController.registerEventHandler(R.layout.event_info, this);
        if (!mIsDialog) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
            mWindowStyle = savedInstanceState.getInt(BUNDLE_KEY_WINDOW_STYLE,
                    DIALOG_WINDOW_STYLE);
            mDeleteDialogVisible =
                savedInstanceState.getBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE,false);
            mCalendarColor = savedInstanceState.getInt(BUNDLE_KEY_CALENDAR_COLOR);
            mCalendarColorInitialized =
                    savedInstanceState.getBoolean(BUNDLE_KEY_CALENDAR_COLOR_INIT);
            mOriginalColor = savedInstanceState.getInt(BUNDLE_KEY_ORIGINAL_COLOR);
            mOriginalColorInitialized = savedInstanceState.getBoolean(
                    BUNDLE_KEY_ORIGINAL_COLOR_INIT);
            mCurrentColor = savedInstanceState.getInt(BUNDLE_KEY_CURRENT_COLOR);
            mCurrentColorInitialized = savedInstanceState.getBoolean(
                    BUNDLE_KEY_CURRENT_COLOR_INIT);
            mCurrentColorKey = savedInstanceState.getInt(BUNDLE_KEY_CURRENT_COLOR_KEY);

        }

        if (mWindowStyle == DIALOG_WINDOW_STYLE) {
            mView = inflater.inflate(R.layout.daily_status_info_dialog, container, false);
        } else {
            mView = inflater.inflate(R.layout.event_info, container, false);
        }
        mScrollView = (ScrollView) mView.findViewById(R.id.event_info_scroll_view);
        mLoadingMsgView = mView.findViewById(R.id.event_info_loading_msg);
        mErrorMsgView = mView.findViewById(R.id.event_info_error_msg);
        mTitle = (TextView) mView.findViewById(R.id.title);
        mWhenDateTime = (TextView) mView.findViewById(R.id.when_datetime);
        mWhere = (TextView) mView.findViewById(R.id.where);
        mDesc = (ExpandableTextView) mView.findViewById(R.id.description);
        mHeadlines = mView.findViewById(R.id.event_info_headline);

		if (savedInstanceState != null) {
			mStartMillis = savedInstanceState.getLong(BUNDLE_KEY_START_MILLIS);
			mEndMillis = savedInstanceState.getLong(BUNDLE_KEY_END_MILLIS);
		}

        mAnimateAlpha = ObjectAnimator.ofFloat(mScrollView, "Alpha", 0, 1);
        mAnimateAlpha.setDuration(FADE_IN_TIME);
        mAnimateAlpha.addListener(new AnimatorListenerAdapter() {
            int defLayerType;

            @Override
            public void onAnimationStart(Animator animation) {
                // Use hardware layer for better performance during animation
                defLayerType = mScrollView.getLayerType();
                mScrollView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                // Ensure that the loading message is gone before showing the
                // event info
                mLoadingMsgView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
                // Do not cross fade after the first time
                mNoCrossFade = true;
            }
        });

        mLoadingMsgView.setAlpha(0);
        mScrollView.setAlpha(0);
        mErrorMsgView.setVisibility(View.INVISIBLE);

        View b = mView.findViewById(R.id.delete);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCanModifyCalendar) {
                    return;
                }
                mDeleteHelper =
                        new DeleteEventHelper(mContext, mActivity, !mIsDialog && !mIsTabletConfig /* exitWhenDone */);
                mDeleteHelper.setDeleteNotificationListener(DailyStatusInfoFragment.this);
                mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
                mDeleteDialogVisible = true;
                mDeleteHelper.delete(mStartMillis, mEndMillis, 0, -1, onDeleteRunnable);
            }
        });

        b = mView.findViewById(R.id.change_color);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCanModifyCalendar) {
                    return;
                }
                showEventColorPickerDialog();
            }
        });

        // Hide Edit/Delete buttons if in full screen mode on a phone
        if (!mIsDialog && !mIsTabletConfig || mWindowStyle == DailyStatusInfoFragment.FULL_WINDOW_STYLE) {
            mView.findViewById(R.id.event_info_buttons_container).setVisibility(View.GONE);
        }

        return mView;
    }

    private final Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (DailyStatusInfoFragment.this.mIsPaused) {
                mDismissOnResume = true;
                return;
            }
            if (DailyStatusInfoFragment.this.isVisible()) {
                DailyStatusInfoFragment.this.dismiss();
            }
        }
    };

	private Handler mHandler = new Handler();

    private void updateTitle() {
        Resources res = getActivity().getResources();
        if (mCanModifyCalendar) {
            getActivity().setTitle(res.getString(R.string.event_info_title_invite));
        } else {
            getActivity().setTitle(res.getString(R.string.event_info_title));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_EVENT_ID, 0);
        outState.putLong(BUNDLE_KEY_START_MILLIS, mStartMillis);
        outState.putLong(BUNDLE_KEY_END_MILLIS, mEndMillis);
        outState.putBoolean(BUNDLE_KEY_IS_DIALOG, mIsDialog);
        outState.putInt(BUNDLE_KEY_WINDOW_STYLE, mWindowStyle);
        outState.putBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE, mDeleteDialogVisible);
        outState.putInt(BUNDLE_KEY_CALENDAR_COLOR, mCalendarColor);
        outState.putBoolean(BUNDLE_KEY_CALENDAR_COLOR_INIT, mCalendarColorInitialized);
        outState.putInt(BUNDLE_KEY_ORIGINAL_COLOR, mOriginalColor);
        outState.putBoolean(BUNDLE_KEY_ORIGINAL_COLOR_INIT, mOriginalColorInitialized);
        outState.putInt(BUNDLE_KEY_CURRENT_COLOR, mCurrentColor);
        outState.putBoolean(BUNDLE_KEY_CURRENT_COLOR_INIT, mCurrentColorInitialized);
        outState.putInt(BUNDLE_KEY_CURRENT_COLOR_KEY, mCurrentColorKey);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Show color/edit/delete buttons only in non-dialog configuration
        if (!mIsDialog && !mIsTabletConfig || mWindowStyle == DailyStatusInfoFragment.FULL_WINDOW_STYLE) {
            inflater.inflate(R.menu.event_info_title_bar, menu);
            mMenu = menu;
            updateMenu();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // If we're a dialog we don't want to handle menu buttons
        if (mIsDialog) {
            return false;
        }
        // Handles option menu selections:
        // Home button - close event info activity and start the main calendar
        // one
        // Edit button - start the event edit activity and close the info
        // activity
        // Delete button - start a delete query that calls a runnable that close
        // the info activity

        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            Utils.returnToCalendarHome(mContext);
            mActivity.finish();
            return true;
        } else if (itemId == R.id.info_action_edit) {
            doEdit();
            mActivity.finish();
        } else if (itemId == R.id.info_action_delete) {
            mDeleteHelper =
                    new DeleteEventHelper(mActivity, mActivity, true /* exitWhenDone */);
            mDeleteHelper.setDeleteNotificationListener(DailyStatusInfoFragment.this);
            mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
            mDeleteDialogVisible = true;
            mDeleteHelper.delete(mStartMillis, mEndMillis, 0, -1, onDeleteRunnable);
        } else if (itemId == R.id.info_action_change_color) {
            showEventColorPickerDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    private void showEventColorPickerDialog() {
        if (mColorPickerDialog == null) {
            mColorPickerDialog = EventColorPickerDialog.newInstance(mColors, mCurrentColor,
                    mCalendarColor, mIsTabletConfig);
            mColorPickerDialog.setOnColorSelectedListener(this);
        }
        final FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.executePendingTransactions();
        if (!mColorPickerDialog.isAdded()) {
            mColorPickerDialog.show(fragmentManager, COLOR_PICKER_DIALOG_TAG);
        }
    }

    private boolean saveEventColor() {
        if (mCurrentColor == mOriginalColor) {
            return false;
        }

        ContentValues values = new ContentValues();
        if (mCurrentColor != mCalendarColor) {
            values.put(Events.EVENT_COLOR_KEY, mCurrentColorKey);
        } else {
            values.put(Events.EVENT_COLOR_KEY, NO_EVENT_COLOR);
        }

        return true;
    }

    private void doEdit() {
        Context c = getActivity();
        // This ensures that we aren't in the process of closing and have been
        // unattached already
        if (c != null) {
            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, 0);
            Intent intent = new Intent(Intent.ACTION_EDIT, uri);
            intent.setClass(mActivity, EditDailyStatusActivity.class);
            intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);
            intent.putExtra(EXTRA_EVENT_END_TIME, mEndMillis);
            intent.putExtra(EXTRA_EVENT_ALL_DAY, mAllDay);
            intent.putExtra(EditDailyStatusActivity.EXTRA_EVENT_COLOR, mCurrentColor);
            intent.putExtra(EVENT_EDIT_ON_LAUNCH, true);
            startActivity(intent);
        }
    }

    private void updateEvent(View view) {
        if (view == null) {
            return;
        }
        
        Event event = Event.generateEventFromDailyStatus(mContext, 0, mDailyStatus);

        Context context = view.getContext();
        if (context == null) {
            return;
        }

        String eventName = mDailyStatus.getName();
        if (eventName == null || eventName.length() == 0) {
            eventName = getActivity().getString(R.string.no_title_label);
        }

        // 3rd parties might not have specified the start/end time when firing the
        // Events.CONTENT_URI intent.  Update these with values read from the db.
        if (mStartMillis == 0 && mEndMillis == 0) {
            mStartMillis = event.startMillis;
            mEndMillis = event.endMillis;
            if (mEndMillis == 0) {
                mEndMillis = mStartMillis;
            }
        }

        mAllDay = event.allDay;
        String location = event.location.toString();
        String description = mDailyStatus.getDescription();
        String rRule = "";
        String eventTimezone = "";

		mCurrentColor = event.color;
		mCurrentColorInitialized = true;

        mHeadlines.setBackgroundColor(mCurrentColor);

        // What
        if (eventName != null) {
            setTextCommon(view, R.id.title, eventName);
        }

        // When
        // Set the date and repeats (if any)
        String localTimezone = Utils.getTimeZone(mActivity, mTZUpdater);

        Resources resources = context.getResources();
        String displayedDatetime = Utils.getDisplayedDatetime(mStartMillis, mEndMillis,
                System.currentTimeMillis(), localTimezone, mAllDay, context);

        String displayedTimezone = null;
        if (!mAllDay) {
            displayedTimezone = Utils.getDisplayedTimezone(mStartMillis, localTimezone,
                    eventTimezone);
        }
        // Display the datetime.  Make the timezone (if any) transparent.
        if (displayedTimezone == null) {
            setTextCommon(view, R.id.when_datetime, displayedDatetime);
        } else {
            int timezoneIndex = displayedDatetime.length();
            displayedDatetime += "  " + displayedTimezone;
            SpannableStringBuilder sb = new SpannableStringBuilder(displayedDatetime);
            ForegroundColorSpan transparentColorSpan = new ForegroundColorSpan(
                    resources.getColor(R.color.event_info_headline_transparent_color));
            sb.setSpan(transparentColorSpan, timezoneIndex, displayedDatetime.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            setTextCommon(view, R.id.when_datetime, sb);
        }

        // Display the repeat string (if any)
        String repeatString = null;
        if (!TextUtils.isEmpty(rRule)) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time(localTimezone);
            date.set(mStartMillis);
            if (mAllDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            eventRecurrence.setStartDate(date);
            repeatString = EventRecurrenceFormatter.getRepeatString(mContext, resources,
                    eventRecurrence, true);
        }
        if (repeatString == null) {
            view.findViewById(R.id.when_repeat).setVisibility(View.GONE);
        } else {
            setTextCommon(view, R.id.when_repeat, repeatString);
        }

        // Organizer view is setup in the updateCalendar method


        // Where
        if (location == null || location.trim().length() == 0) {
            setVisibilityCommon(view, R.id.where, View.GONE);
        } else {
            final TextView textView = mWhere;
            if (textView != null) {
                textView.setAutoLinkMask(0);
                textView.setText(location.trim());
                try {
                    textView.setText(Utils.extendedLinkify(textView.getText().toString(), true));

                    // Linkify.addLinks() sets the TextView movement method if it finds any links.
                    // We must do the same here, in case linkify by itself did not find any.
                    // (This is cloned from Linkify.addLinkMovementMethod().)
                    MovementMethod mm = textView.getMovementMethod();
                    if ((mm == null) || !(mm instanceof LinkMovementMethod)) {
                        if (textView.getLinksClickable()) {
                            textView.setMovementMethod(LinkMovementMethod.getInstance());
                        }
                    }
                } catch (Exception ex) {
                    // unexpected
                    Log.e(TAG, "Linkification failed", ex);
                }

                textView.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        try {
                            return v.onTouchEvent(event);
                        } catch (ActivityNotFoundException e) {
                            // ignore
                            return true;
                        }
                    }
                });
            }
        }

        // Description
        if (description != null && description.length() != 0) {
            mDesc.setText(description);
        }

        // Launch Custom App
        if (Utils.isJellybeanOrLater()) {
            updateCustomAppButton();
        }

        mLoadingMsgView.setAlpha(0);
        mScrollView.setAlpha(1);
    }

    private void updateCustomAppButton() {
        buttonSetup: {
            final Button launchButton = (Button) mView.findViewById(R.id.launch_custom_app_button);
            if (launchButton == null)
                break buttonSetup;

            final String customAppPackage = "";
            final String customAppUri = "";

            if (TextUtils.isEmpty(customAppPackage) || TextUtils.isEmpty(customAppUri))
                break buttonSetup;

            PackageManager pm = mContext.getPackageManager();
            if (pm == null)
                break buttonSetup;

            ApplicationInfo info;
            try {
                info = pm.getApplicationInfo(customAppPackage, 0);
                if (info == null)
                    break buttonSetup;
            } catch (NameNotFoundException e) {
                break buttonSetup;
            }

            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, 0);
            final Intent intent = new Intent(CalendarContract.ACTION_HANDLE_CUSTOM_EVENT, uri);
            intent.setPackage(customAppPackage);
            intent.putExtra(CalendarContract.EXTRA_CUSTOM_APP_URI, customAppUri);
            intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);

            // See if we have a taker for our intent
            if (pm.resolveActivity(intent, 0) == null)
                break buttonSetup;

            Drawable icon = pm.getApplicationIcon(info);
            if (icon != null) {

                Drawable[] d = launchButton.getCompoundDrawables();
                icon.setBounds(0, 0, mCustomAppIconSize, mCustomAppIconSize);
                launchButton.setCompoundDrawables(icon, d[1], d[2], d[3]);
            }

            CharSequence label = pm.getApplicationLabel(info);
            if (label != null && label.length() != 0) {
                launchButton.setText(label);
            } else if (icon == null) {
                // No icon && no label. Hide button?
                break buttonSetup;
            }

            // Launch custom app
            launchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivityForResult(intent, 0);
                    } catch (ActivityNotFoundException e) {
                        // Shouldn't happen as we checked it already
                        setVisibilityCommon(mView, R.id.launch_custom_app_container, View.GONE);
                    }
                }
            });

            setVisibilityCommon(mView, R.id.launch_custom_app_container, View.VISIBLE);
            return;

        }

        setVisibilityCommon(mView, R.id.launch_custom_app_container, View.GONE);
        return;
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am =
            (AccessibilityManager) getActivity().getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (!am.isEnabled()) {
            return;
        }

        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(DailyStatusInfoFragment.class.getName());
        event.setPackageName(getActivity().getPackageName());
        List<CharSequence> text = event.getText();

        addFieldToAccessibilityEvent(text, mTitle, null);
        addFieldToAccessibilityEvent(text, mWhenDateTime, null);
        addFieldToAccessibilityEvent(text, mWhere, null);
        addFieldToAccessibilityEvent(text, null, mDesc);

        am.sendAccessibilityEvent(event);
    }

    private void addFieldToAccessibilityEvent(List<CharSequence> text, TextView tv,
            ExpandableTextView etv) {
        CharSequence cs;
        if (tv != null) {
            cs = tv.getText();
        } else if (etv != null) {
            cs = etv.getText();
        } else {
            return;
        }

        if (!TextUtils.isEmpty(cs)) {
            cs = cs.toString().trim();
            if (cs.length() > 0) {
                text.add(cs);
                text.add(PERIOD_SPACE);
            }
        }
    }

	private void updateCalendar(View view) {

		setVisibilityCommon(view, R.id.organizer_container, View.GONE);

		mCanModifyCalendar = true;
		// TODO add "|| guestCanModify" after b/1299071 is fixed
		mCanModifyEvent = true;

		View b = mView.findViewById(R.id.edit);
		b.setEnabled(true);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				doEdit();
				// For dialogs, just close the fragment
				// For full screen, close activity on phone, leave it for tablet
				if (mIsDialog) {
					DailyStatusInfoFragment.this.dismiss();
				} else if (!mIsTabletConfig) {
					getActivity().finish();
				}
			}
		});

		View button;
		if (mCanModifyCalendar) {
			button = mView.findViewById(R.id.delete);
			if (button != null) {
				button.setEnabled(true);
				button.setVisibility(View.VISIBLE);
			}
		}
		if (mCanModifyEvent) {
			button = mView.findViewById(R.id.edit);
			if (button != null) {
				button.setEnabled(true);
				button.setVisibility(View.VISIBLE);
			}
		}
		if ((!mIsDialog && !mIsTabletConfig || mWindowStyle == DailyStatusInfoFragment.FULL_WINDOW_STYLE)
				&& mMenu != null) {
			mActivity.invalidateOptionsMenu();
		}

	}

    /**
     *
     */
    private void updateMenu() {
        if (mMenu == null) {
            return;
        }
        MenuItem delete = mMenu.findItem(R.id.info_action_delete);
        MenuItem edit = mMenu.findItem(R.id.info_action_edit);
        MenuItem changeColor = mMenu.findItem(R.id.info_action_change_color);
        if (delete != null) {
            delete.setVisible(mCanModifyCalendar);
            delete.setEnabled(mCanModifyCalendar);
        }
        if (edit != null) {
            edit.setVisible(mCanModifyEvent);
            edit.setEnabled(mCanModifyEvent);
        }
        if (changeColor != null && mColors != null && mColors.length > 0) {
            changeColor.setVisible(mCanModifyCalendar);
            changeColor.setEnabled(mCanModifyCalendar);
        }
    }

    private void setTextCommon(View view, int id, CharSequence text) {
        TextView textView = (TextView) view.findViewById(id);
        if (textView == null)
            return;
        textView.setText(text);
    }

    private void setVisibilityCommon(View view, int id, int visibility) {
        View v = view.findViewById(id);
        if (v != null) {
            v.setVisibility(visibility);
        }
        return;
    }

    /**
     * Taken from com.google.android.gm.HtmlConversationActivity
     *
     * Send the intent that shows the Contact info corresponding to the email address.
     */
    public void showContactInfo(Attendee attendee, Rect rect) {
        // First perform lookup query to find existing contact
        final ContentResolver resolver = getActivity().getContentResolver();
        final String address = attendee.mEmail;
        final Uri dataUri = Uri.withAppendedPath(CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        final Uri lookupUri = ContactsContract.Data.getContactLookupUri(resolver, dataUri);

        if (lookupUri != null) {
            // Found matching contact, trigger QuickContact
            QuickContact.showQuickContact(getActivity(), rect, lookupUri,
                    QuickContact.MODE_MEDIUM, null);
        } else {
            // No matching contact, ask user to create one
            final Uri mailUri = Uri.fromParts("mailto", address, null);
            final Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT, mailUri);

            // Pass along full E-mail string for possible create dialog
            Rfc822Token sender = new Rfc822Token(attendee.mName, attendee.mEmail, null);
            intent.putExtra(Intents.EXTRA_CREATE_DESCRIPTION, sender.toString());

            // Only provide personal name hint if we have one
            final String senderPersonal = attendee.mName;
            if (!TextUtils.isEmpty(senderPersonal)) {
                intent.putExtra(Intents.Insert.NAME, senderPersonal);
            }

            startActivity(intent);
        }
    }

    @Override
	public void onStart() {
        super.onStart();
		updateEvent(mView);
		updateCalendar(mView);
	}

    @Override
	public void onStop() {
        super.onStop();
	}

    @Override
    public void onPause() {
        mIsPaused = true;
        mHandler .removeCallbacks(onDeleteRunnable);
        super.onPause();
        // Remove event deletion alert box since it is being rebuild in the OnResume
        // This is done to get the same behavior on OnResume since the AlertDialog is gone on
        // rotation but not if you press the HOME key
        if (mDeleteDialogVisible && mDeleteHelper != null) {
            mDeleteHelper.dismissAlertDialog();
            mDeleteHelper = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsDialog) {
            setDialogSize(getActivity().getResources());
            applyDialogParams();
        }
        mIsPaused = false;
        if (mDismissOnResume) {
            mHandler.post(onDeleteRunnable);
        }
        // Display the "delete confirmation" or "edit response helper" dialog if needed
        if (mDeleteDialogVisible) {
            mDeleteHelper = new DeleteEventHelper(
                    mContext, mActivity,
                    !mIsDialog && !mIsTabletConfig /* exitWhenDone */);
            mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
            mDeleteHelper.delete(mStartMillis, mEndMillis, 0, -1, onDeleteRunnable);
        }
    }

    @Override
    public void eventsChanged() {
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.EVENTS_CHANGED;
    }

    @Override
    public void handleEvent(EventInfo event) {
    }

    @Override
    public void onClick(View view) {
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

    @Override
    public void onDeleteStarted() {
        mEventDeletionStarted = true;
    }

    private Dialog.OnDismissListener createDeleteOnDismissListener() {
        return new Dialog.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // Since OnPause will force the dialog to dismiss , do
                        // not change the dialog status
                        if (!mIsPaused) {
                            mDeleteDialogVisible = false;
                        }
                    }
                };
    }

    public long getEventId() {
        return 0;
    }

    public long getStartMillis() {
        return mStartMillis;
    }
    public long getEndMillis() {
        return mEndMillis;
    }
    private void setDialogSize(Resources r) {
        mDialogWidth = (int)r.getDimension(R.dimen.event_info_dialog_width);
        mDialogHeight = (int)r.getDimension(R.dimen.event_info_dialog_height);
    }

    @Override
    public void onColorSelected(int color) {
        mCurrentColor = color;
        mCurrentColorKey = mDisplayColorKeyMap.get(color);
        mHeadlines.setBackgroundColor(color);
    }

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		// TODO Auto-generated method stub
		
	}
}
