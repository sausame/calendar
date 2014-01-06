/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Debug;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import com.android.calendar.infor.DailyStatus;
import com.android.calendar.therapy.Therapy;

// TODO: should Event be Parcelable so it can be passed via Intents?
public class Event implements Cloneable {

    private static final String TAG = "CalEvent";
    private static final boolean PROFILE = false;

    /**
     * The sort order is:
     * 1) events with an earlier start (begin for normal events, startday for allday)
     * 2) events with a later end (end for normal events, endday for allday)
     * 3) the title (unnecessary, but nice)
     *
     * The start and end day is sorted first so that all day events are
     * sorted correctly with respect to events that are >24 hours (and
     * therefore show up in the allday area).
     */
    private static final String SORT_EVENTS_BY =
            "begin ASC, end DESC, title ASC";
    private static final String SORT_ALLDAY_BY =
            "startDay ASC, endDay DESC, title ASC";
    private static final String DISPLAY_AS_ALLDAY = "dispAllday";

    private static final String EVENTS_WHERE = DISPLAY_AS_ALLDAY + "=0";
    private static final String ALLDAY_WHERE = DISPLAY_AS_ALLDAY + "=1";

    // The projection to use when querying instances to build a list of events
    // TODO Only used in CalendarDatabase.
    public static final String[] EVENT_PROJECTION = new String[] {
            Instances.TITLE,                 // 0
            Instances.EVENT_LOCATION,        // 1
            Instances.ALL_DAY,               // 2
            Instances.DISPLAY_COLOR,         // 3 If SDK < 16, set to Instances.CALENDAR_COLOR.
            Instances.EVENT_TIMEZONE,        // 4
            Instances.EVENT_ID,              // 5
            Instances.BEGIN,                 // 6
            Instances.END,                   // 7
            Instances._ID,                   // 8
            Instances.START_DAY,             // 9
            Instances.END_DAY,               // 10
            Instances.START_MINUTE,          // 11
            Instances.END_MINUTE,            // 12
            Instances.HAS_ALARM,             // 13
            Instances.RRULE,                 // 14
            Instances.RDATE,                 // 15
            Instances.SELF_ATTENDEE_STATUS,  // 16
            Events.ORGANIZER,                // 17
            Events.GUESTS_CAN_MODIFY,        // 18
            Instances.ALL_DAY + "=1 OR (" + Instances.END + "-" + Instances.BEGIN + ")>="
                    + DateUtils.DAY_IN_MILLIS + " AS " + DISPLAY_AS_ALLDAY, // 19
    };

    // The indices for the projection array above.
    private static final int PROJECTION_TITLE_INDEX = 0;
    private static final int PROJECTION_LOCATION_INDEX = 1;
    private static final int PROJECTION_ALL_DAY_INDEX = 2;
    private static final int PROJECTION_COLOR_INDEX = 3;
    private static final int PROJECTION_TIMEZONE_INDEX = 4;
    private static final int PROJECTION_EVENT_ID_INDEX = 5;
    private static final int PROJECTION_BEGIN_INDEX = 6;
    private static final int PROJECTION_END_INDEX = 7;
    private static final int PROJECTION_START_DAY_INDEX = 9;
    private static final int PROJECTION_END_DAY_INDEX = 10;
    private static final int PROJECTION_START_MINUTE_INDEX = 11;
    private static final int PROJECTION_END_MINUTE_INDEX = 12;
    private static final int PROJECTION_HAS_ALARM_INDEX = 13;
    private static final int PROJECTION_RRULE_INDEX = 14;
    private static final int PROJECTION_RDATE_INDEX = 15;
    private static final int PROJECTION_SELF_ATTENDEE_STATUS_INDEX = 16;
    private static final int PROJECTION_ORGANIZER_INDEX = 17;
    private static final int PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX = 18;
    private static final int PROJECTION_DISPLAY_AS_ALLDAY = 19;

    static {
        if (!Utils.isJellybeanOrLater()) {
            EVENT_PROJECTION[PROJECTION_COLOR_INDEX] = Instances.CALENDAR_COLOR;
        }
    }

    private static String mNoTitleString;
    private static int mNoColorColor;

    public long id;
    public int color;
    public CharSequence title;
    public CharSequence location;
    public boolean allDay;
    public String organizer;
    public boolean guestsCanModify;

    public int startDay;       // start Julian day
    public int endDay;         // end Julian day
    public int startTime;      // Start and end time are in minutes since midnight
    public int endTime;

    public long startMillis;   // UTC milliseconds since the epoch
    public long endMillis;     // UTC milliseconds since the epoch
    private int mColumn;
    private int mMaxColumns;

    public boolean hasAlarm;
    public boolean isRepeating;

    public int selfAttendeeStatus;

    // The coordinates of the event rectangle drawn on the screen.
    public float left;
    public float right;
    public float top;
    public float bottom;

    // These 4 fields are used for navigating among events within the selected
    // hour in the Day and Week view.
    public Event nextRight;
    public Event nextLeft;
    public Event nextUp;
    public Event nextDown;

	// Add for therapy or daily status.
	public int type;

    @Override
    public final Object clone() throws CloneNotSupportedException {
        super.clone();
        Event e = new Event();

        e.title = title;
        e.color = color;
        e.location = location;
        e.allDay = allDay;
        e.startDay = startDay;
        e.endDay = endDay;
        e.startTime = startTime;
        e.endTime = endTime;
        e.startMillis = startMillis;
        e.endMillis = endMillis;
        e.hasAlarm = hasAlarm;
        e.isRepeating = isRepeating;
        e.selfAttendeeStatus = selfAttendeeStatus;
        e.organizer = organizer;
        e.guestsCanModify = guestsCanModify;
		e.type = type;

        return e;
    }

    public final void copyTo(Event dest) {
        dest.id = id;
        dest.title = title;
        dest.color = color;
        dest.location = location;
        dest.allDay = allDay;
        dest.startDay = startDay;
        dest.endDay = endDay;
        dest.startTime = startTime;
        dest.endTime = endTime;
        dest.startMillis = startMillis;
        dest.endMillis = endMillis;
        dest.hasAlarm = hasAlarm;
        dest.isRepeating = isRepeating;
        dest.selfAttendeeStatus = selfAttendeeStatus;
        dest.organizer = organizer;
        dest.guestsCanModify = guestsCanModify;
		dest.type = type;
    }

    public static final Event newInstance() {
        Event e = new Event();

        e.id = 0;
        e.title = null;
        e.color = 0;
        e.location = null;
        e.allDay = false;
        e.startDay = 0;
        e.endDay = 0;
        e.startTime = 0;
        e.endTime = 0;
        e.startMillis = 0;
        e.endMillis = 0;
        e.hasAlarm = false;
        e.isRepeating = false;
        e.selfAttendeeStatus = Attendees.ATTENDEE_STATUS_NONE;
		e.type = 0;

        return e;
    }

    /**
     * Loads <i>days</i> days worth of instances starting at <i>startDay</i>.
     */
    public static void loadEvents0(Context context, ArrayList<Event> events, int startDay, int days,
            int requestId, AtomicInteger sequenceNumber) {

        if (PROFILE) {
            Debug.startMethodTracing("loadEvents");
        }

        Cursor cEvents = null;
        Cursor cAllday = null;

        events.clear();
        try {
            int endDay = startDay + days - 1;

            // We use the byDay instances query to get a list of all events for
            // the days we're interested in.
            // The sort order is: events with an earlier start time occur
            // first and if the start times are the same, then events with
            // a later end time occur first. The later end time is ordered
            // first so that long rectangles in the calendar views appear on
            // the left side.  If the start and end times of two events are
            // the same then we sort alphabetically on the title.  This isn't
            // required for correctness, it just adds a nice touch.

            // Respect the preference to show/hide declined events
            SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
            boolean hideDeclined = prefs.getBoolean(GeneralPreferences.KEY_HIDE_DECLINED,
                    false);

            String where = EVENTS_WHERE;
            String whereAllday = ALLDAY_WHERE;
            if (hideDeclined) {
                String hideString = " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
                        + Attendees.ATTENDEE_STATUS_DECLINED;
                where += hideString;
                whereAllday += hideString;
            }

//            cEvents = CalendarDatabase.instancesQuery(context.getContentResolver(), EVENT_PROJECTION, startDay,
//                    endDay, where, null, SORT_EVENTS_BY);
            cAllday = CalendarDatabase.instancesQueryDailyStatus(context, EVENT_PROJECTION, startDay,
                    endDay, whereAllday, null, SORT_ALLDAY_BY);

            // Check if we should return early because there are more recent
            // load requests waiting.
            if (requestId != sequenceNumber.get()) {
                return;
            }

            buildEventsFromCursor(events, cEvents, context, startDay, endDay);
            buildEventsFromCursor(events, cAllday, context, startDay, endDay);

        } finally {
            if (cEvents != null) {
                cEvents.close();
            }
            if (cAllday != null) {
                cAllday.close();
            }
            if (PROFILE) {
                Debug.stopMethodTracing();
            }
        }
    }

    /**
     * Adds all the events from the cursors to the events list.
     *
     * @param events The list of events
     * @param cEvents Events to add to the list
     * @param context
     * @param startDay
     * @param endDay
     */
    public static void buildEventsFromCursor(
            ArrayList<Event> events, Cursor cEvents, Context context, int startDay, int endDay) {
        if (cEvents == null || events == null) {
            Log.e(TAG, "buildEventsFromCursor: null cursor or null events list!");
            return;
        }

        int count = cEvents.getCount();

        if (count == 0) {
            return;
        }

        Resources res = context.getResources();
        mNoTitleString = res.getString(R.string.no_title_label);
        mNoColorColor = res.getColor(R.color.event_center);
        // Sort events in two passes so we ensure the allday and standard events
        // get sorted in the correct order
        cEvents.moveToPosition(-1);
        while (cEvents.moveToNext()) {
        	Event e = generateEventFromCursor(cEvents); // Calendar design
            Log.v("" + startDay + " < " + e.startDay + " <= " + e.endDay + " < " + endDay);
            if (e.startDay > endDay || e.endDay < startDay) {
                continue;
            }
            events.add(e);
        }
    }

    /**
     * @param cEvents Cursor pointing at event
     * @return An event created from the cursor
     */
    private static Event generateEventFromCursor(Cursor cEvents) {
        Event e = new Event();

        e.id = cEvents.getLong(PROJECTION_EVENT_ID_INDEX);
        e.title = cEvents.getString(PROJECTION_TITLE_INDEX);
        e.location = cEvents.getString(PROJECTION_LOCATION_INDEX);
        e.allDay = cEvents.getInt(PROJECTION_ALL_DAY_INDEX) != 0;
        e.organizer = cEvents.getString(PROJECTION_ORGANIZER_INDEX);
        e.guestsCanModify = cEvents.getInt(PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX) != 0;

        if (e.title == null || e.title.length() == 0) {
            e.title = mNoTitleString;
        }

        if (!cEvents.isNull(PROJECTION_COLOR_INDEX)) {
            // Read the color from the database
            e.color = Utils.getDisplayColorFromColor(cEvents.getInt(PROJECTION_COLOR_INDEX));
        } else {
            e.color = mNoColorColor;
        }

        long eStart = cEvents.getLong(PROJECTION_BEGIN_INDEX);
        long eEnd = cEvents.getLong(PROJECTION_END_INDEX);

        e.startMillis = eStart;
        e.startTime = cEvents.getInt(PROJECTION_START_MINUTE_INDEX);
        e.startDay = cEvents.getInt(PROJECTION_START_DAY_INDEX);

        e.endMillis = eEnd;
        e.endTime = cEvents.getInt(PROJECTION_END_MINUTE_INDEX);
        e.endDay = cEvents.getInt(PROJECTION_END_DAY_INDEX);

        e.hasAlarm = cEvents.getInt(PROJECTION_HAS_ALARM_INDEX) != 0;

        // Check if this is a repeating event
        String rrule = cEvents.getString(PROJECTION_RRULE_INDEX);
        String rdate = cEvents.getString(PROJECTION_RDATE_INDEX);
        if (!TextUtils.isEmpty(rrule) || !TextUtils.isEmpty(rdate)) {
            e.isRepeating = true;
        } else {
            e.isRepeating = false;
        }

        e.selfAttendeeStatus = cEvents.getInt(PROJECTION_SELF_ATTENDEE_STATUS_INDEX);
        return e;
    }

    /**
     * Computes a position for each event.  Each event is displayed
     * as a non-overlapping rectangle.  For normal events, these rectangles
     * are displayed in separate columns in the week view and day view.  For
     * all-day events, these rectangles are displayed in separate rows along
     * the top.  In both cases, each event is assigned two numbers: N, and
     * Max, that specify that this event is the Nth event of Max number of
     * events that are displayed in a group. The width and position of each
     * rectangle depend on the maximum number of rectangles that occur at
     * the same time.
     *
     * @param eventsList the list of events, sorted into increasing time order
     * @param minimumDurationMillis minimum duration acceptable as cell height of each event
     * rectangle in millisecond. Should be 0 when it is not determined.
     */
    /* package */ static void computePositions(ArrayList<Event> eventsList,
            long minimumDurationMillis) {
        if (eventsList == null) {
            return;
        }

        // Compute the column positions separately for the all-day events
        doComputePositions(eventsList, minimumDurationMillis, false);
        doComputePositions(eventsList, minimumDurationMillis, true);
    }

	/* XXX: The Column will be ERROR if the events aren't sorted. 
	 * The function should be modified for therapy or daily status.
	 */
    private static void doComputePositions(ArrayList<Event> eventsList,
            long minimumDurationMillis, boolean doAlldayEvents) {
        final ArrayList<Event> activeList = new ArrayList<Event>();
        final ArrayList<Event> groupList = new ArrayList<Event>();

        if (minimumDurationMillis < 0) {
            minimumDurationMillis = 0;
        }

        long colMask = 0;
        int maxCols = 0;
        for (Event event : eventsList) {
            // Process all-day events separately
            if (event.drawAsAllday() != doAlldayEvents)
                continue;

           if (!doAlldayEvents) {
                colMask = removeNonAlldayActiveEvents(
                        event, activeList.iterator(), minimumDurationMillis, colMask);
            } else {
                colMask = removeAlldayActiveEvents(event, activeList.iterator(), colMask);
            }

            // If the active list is empty, then reset the max columns, clear
            // the column bit mask, and empty the groupList.
            if (activeList.isEmpty()) {
                for (Event ev : groupList) {
                    ev.setMaxColumns(maxCols);
                }
                maxCols = 0;
                colMask = 0;
                groupList.clear();
            }

            // Find the first empty column.  Empty columns are represented by
            // zero bits in the column mask "colMask".
            int col = findFirstZeroBit(colMask);
            if (col == 64)
                col = 63;
            colMask |= (1L << col);
            event.setColumn(col);
            activeList.add(event);
            groupList.add(event);
            int len = activeList.size();
            if (maxCols < len)
                maxCols = len;
        }
        for (Event ev : groupList) {
            ev.setMaxColumns(maxCols);
        }
    }

    private static long removeAlldayActiveEvents(Event event, Iterator<Event> iter, long colMask) {
        // Remove the inactive allday events. An event on the active list
        // becomes inactive when the end day is less than the current event's
        // start day. Or the start day is more than the current event's end day.
        while (iter.hasNext()) {
            final Event active = iter.next();
            if (active.endDay < event.startDay || active.startDay > event.endDay) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    private static long removeNonAlldayActiveEvents(
            Event event, Iterator<Event> iter, long minDurationMillis, long colMask) {
        long start = event.getStartMillis();
        // Remove the inactive events. An event on the active list
        // becomes inactive when its end time is less than or equal to
        // the current event's start time.
        while (iter.hasNext()) {
            final Event active = iter.next();

            final long duration = Math.max(
                    active.getEndMillis() - active.getStartMillis(), minDurationMillis);
            if ((active.getStartMillis() + duration) <= start) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    public static int findFirstZeroBit(long val) {
        for (int ii = 0; ii < 64; ++ii) {
            if ((val & (1L << ii)) == 0)
                return ii;
        }
        return 64;
    }

    public final void dump() {
        Log.e("Cal", "+-----------------------------------------+");
        Log.e("Cal", "+        id = " + id);
        Log.e("Cal", "+     color = " + color);
        Log.e("Cal", "+     title = " + title);
        Log.e("Cal", "+  location = " + location);
        Log.e("Cal", "+    allDay = " + allDay);
        Log.e("Cal", "+  startDay = " + startDay);
        Log.e("Cal", "+    endDay = " + endDay);
        Log.e("Cal", "+ startTime = " + startTime);
        Log.e("Cal", "+   endTime = " + endTime);
        Log.e("Cal", "+ organizer = " + organizer);
        Log.e("Cal", "+  guestwrt = " + guestsCanModify);
    }
    
    public final void show() {
    	Log.v("+-----------------------------------------+");
        Log.v("+        id = " + id);
        Log.v("+     title = " + title);
    	Log.v("+  position = [" + left + ", " + top + ", " + right + ", " + bottom + "]");
    	Log.v("+    column = " + mColumn);
    	Log.v("+ MaxColumn = " + mMaxColumns);
    }

    public final boolean intersects(int julianDay, int startMinute,
            int endMinute) {
        if (endDay < julianDay) {
            return false;
        }

        if (startDay > julianDay) {
            return false;
        }

        if (endDay == julianDay) {
            if (endTime < startMinute) {
                return false;
            }
            // An event that ends at the start minute should not be considered
            // as intersecting the given time span, but don't exclude
            // zero-length (or very short) events.
            if (endTime == startMinute
                    && (startTime != endTime || startDay != endDay)) {
                return false;
            }
        }

        if (startDay == julianDay && startTime > endMinute) {
            return false;
        }

        return true;
    }

    /**
     * Returns the event title and location separated by a comma.  If the
     * location is already part of the title (at the end of the title), then
     * just the title is returned.
     *
     * @return the event title and location as a String
     */
    public String getTitleAndLocation() {
        String text = title.toString();

        // Append the location to the title, unless the title ends with the
        // location (for example, "meeting in building 42" ends with the
        // location).
        if (location != null) {
            String locationString = location.toString();
            if (!text.endsWith(locationString)) {
                text += ", " + locationString;
            }
        }
        return text;
    }

    private void setColumn(int column) {
        mColumn = column;
    }

    public int getColumn() {
        return mColumn;
    }

    public void setMaxColumns(int maxColumns) {
        mMaxColumns = maxColumns;
    }

    public int getMaxColumns() {
        return mMaxColumns;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public boolean drawAsAllday() {
        // Use >= so we'll pick up Exchange allday events
        return allDay || endMillis - startMillis >= DateUtils.DAY_IN_MILLIS;
    }
    
	// ------------------------------------------------------------------------------
	// For personal daily information.
	// ------------------------------------------------------------------------------
	private DailyStatus mDailyStatus = null;

	public DailyStatus getDailyStatus() {
		return mDailyStatus;
	}

	public final static int LEVEL_COLOR[] = { android.R.color.holo_green_light,
			android.R.color.holo_blue_light, android.R.color.holo_purple,
			android.R.color.holo_orange_light, android.R.color.holo_red_light };

	private static int getColorFromLevel(int level) {
		return LEVEL_COLOR[level % LEVEL_COLOR.length];
	}

	private static Event generateEventFromDailyStatus(Context context, long id,
			DailyStatus dailyStatus) {
		Event e = new Event();

		e.id = id;
		e.title = dailyStatus.getName() + " (" + id + ")";
		e.location = "";
		e.allDay = true;
		e.organizer = "";
		e.guestsCanModify = false;

		if (e.title == null || e.title.length() == 0) {
			e.title = mNoTitleString;
		}

		e.color = Utils.getDisplayColorFromColor(context.getResources()
				.getColor(getColorFromLevel(dailyStatus.getLevel())));

		long eStart = dailyStatus.getDay();
		long eEnd = eStart;
		
		Time t = new Time(); 
		t.set(eStart);

		int eDate = Time.getJulianDay(eStart, t.gmtoff);;

		e.startMillis = eStart;
		e.startTime = 0;
		e.startDay = eDate;

		e.endMillis = eEnd;
		e.endTime = 1440;
		e.endDay = eDate;

		e.hasAlarm = false;

		// Check if this is a repeating event
		e.isRepeating = false;

		e.selfAttendeeStatus = 0;

		return e;
	}

    /**
     * Adds all the events from the cursors to the events list.
     *
     * @param events The list of events
     * @param cDailyStatuses Events to add to the list
     * @param context
     * @param startDay
     * @param endDay
     */
    public static void buildEventsFromCursor(
            ArrayList<Event> events,
			Cursor cDailyStatuses,
			Cursor cTherapies,
			Context context,
			int startDay,
			int endDay) {

        if (events == null) {
            Log.e(TAG, "buildEventsFromCursor: null events list!");
            return;
        }

		int count = 0;
		if (cDailyStatuses != null) {
			if (cDailyStatuses.getCount() > 0) {
				// Sort events in two passes so we ensure the allday and standard events
				// get sorted in the correct order
				cDailyStatuses.moveToPosition(-1);
				while (cDailyStatuses.moveToNext()) {
					Event e = generateEventFromDailyStatusCursor(context, cDailyStatuses);
					if (e.startDay > endDay || e.endDay < startDay) {
						continue;
					}
					events.add(e);
				}

				count += cDailyStatuses.getCount();
			}
		}

		if (cTherapies != null) {
			if (cTherapies.getCount() > 0) {
				// Sort events in two passes so we ensure the allday and standard events
				// get sorted in the correct order
				cTherapies.moveToPosition(-1);
				while (cTherapies.moveToNext()) {
					Event[] es = generateEventGroupFromTherapyCursor(context, cTherapies);
					if (es[0].startDay > endDay || es[0].endDay < startDay) {
						continue;
					}

					for (Event e : es) {
						events.add(e);
					}
				}

				count += cTherapies.getCount();
			}
		}

		Log.i(TAG, "buildEventsFromCursor: " + count + " events list!");

		Resources res = context.getResources();
		mNoTitleString = res.getString(R.string.no_title_label);
		mNoColorColor = res.getColor(R.color.event_center);
    }

	private static Event generateEventFromDailyStatusCursor(Context context, Cursor cEvents) {
		DailyStatus dailyStatus = DailyStatus.parse(cEvents);
		return generateEventFromDailyStatus(context, cEvents.getPosition(), dailyStatus);
	}

	// ------------------------------------------------------------------------------
	// For therapy.
	// ------------------------------------------------------------------------------
	private static Event[] generateEventGroupFromTherapy(Context context, long id,
			Therapy therapy) {
		Event e = new Event();

		e.id = id;
		e.title = therapy.getName() + " (" + id + ")";
		e.location = "";
		e.allDay = false;
		e.organizer = "";
		e.guestsCanModify = false;

		if (e.title == null || e.title.length() == 0) {
			e.title = mNoTitleString;
		}

		e.color = Utils.getDisplayColorFromColor(context.getResources()
				.getColor(android.R.color.darker_gray));

		long eStart = therapy.getDay();
		long eEnd = eStart;
		
		Time t = new Time(); 
		t.set(eStart);

		int eDate = Time.getJulianDay(eStart, t.gmtoff);;

		e.startMillis = eStart;
		e.startTime = 0;
		e.startDay = eDate;

		e.endMillis = eEnd;
		e.endTime = 1440;
		e.endDay = eDate;

		e.hasAlarm = false;

		// Check if this is a repeating event
		e.isRepeating = false;

		e.selfAttendeeStatus = 0;

		e.type = therapy.getType();
		
		// To event group.
		Event es[]; 

		long reminders[] = therapy.getRemindersGroup();

		if (reminders != null && reminders.length > 0) {
			int num = reminders.length;
			es = new Event[num]; 

			for (int i = 0; i < num; i ++) {
				Event event = null;
				try {
					event = (Event) e.clone();
				} catch (CloneNotSupportedException ex) {
					Log.e(TAG, e.toString());
				}

				Time when = new Time();
				when.set(reminders[i]);

				event.startTime = when.hour * 60 + when.minute;
				event.endTime = event.startTime + 60;

				event.startMillis += reminders[i] + TimeZone.getDefault().getRawOffset();
				event.endMillis = event.startMillis + 60 * 60 * 1000;

				es[i] = event;
			}
		} else {
			es = new Event[1]; 
			es[0] = e;
		}

		return es;
	}

	private static Event[] generateEventGroupFromTherapyCursor(Context context, Cursor cEvents) {
		Therapy therapy = Therapy.parse(cEvents);
		return generateEventGroupFromTherapy(context, cEvents.getPosition(), therapy);
	}

    /**
     * Loads <i>days</i> days worth of instances starting at <i>startDay</i>.
     */
    public static void loadEvents(Context context, ArrayList<Event> events, int startDay, int days,
            int requestId, AtomicInteger sequenceNumber) {

        if (PROFILE) {
            Debug.startMethodTracing("loadEvents");
        }

        Cursor cTherapies = null;
        Cursor cDailyStatus = null;

        events.clear();
        try {
            int endDay = startDay + days - 1;

            // We use the byDay instances query to get a list of all events for
            // the days we're interested in.
            // The sort order is: events with an earlier start time occur
            // first and if the start times are the same, then events with
            // a later end time occur first. The later end time is ordered
            // first so that long rectangles in the calendar views appear on
            // the left side.  If the start and end times of two events are
            // the same then we sort alphabetically on the title.  This isn't
            // required for correctness, it just adds a nice touch.

            // Respect the preference to show/hide declined events
            SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
            boolean hideDeclined = prefs.getBoolean(GeneralPreferences.KEY_HIDE_DECLINED,
                    false);

            String where = EVENTS_WHERE;
            String whereAllday = ALLDAY_WHERE;
            if (hideDeclined) {
                String hideString = " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
                        + Attendees.ATTENDEE_STATUS_DECLINED;
                where += hideString;
                whereAllday += hideString;
            }

            cTherapies = CalendarDatabase.instancesQueryTherapy(context,
						EVENT_PROJECTION, startDay, endDay, where, null, SORT_EVENTS_BY);
            cDailyStatus = CalendarDatabase.instancesQueryDailyStatus(context,
						EVENT_PROJECTION, startDay, endDay, whereAllday, null, SORT_ALLDAY_BY);

            // Check if we should return early because there are more recent
            // load requests waiting.
            if (requestId != sequenceNumber.get()) {
                return;
            }

            buildEventsFromCursor(events, cDailyStatus, cTherapies, context, startDay, endDay);

        } finally {
            if (cTherapies != null) {
                cTherapies.close();
            }
            if (cDailyStatus != null) {
                cDailyStatus.close();
            }
            if (PROFILE) {
                Debug.stopMethodTracing();
            }
        }
    }
}
