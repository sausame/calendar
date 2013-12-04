package com.android.calendar;

import java.util.ArrayList;
import java.util.Arrays;

import com.android.calendar.infor.PersonalDailyInformationCursor;
import com.android.calendar.month.MonthByWeekAdapter;
import com.android.calendar.month.MonthByWeekFragment;
import com.android.calendar.month.SimpleWeekView;

import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.text.TextUtils;
import android.text.format.Time;

public class CalendarDatabase {

	private final static String TAG = "CalendarDatabase";

	/**
	 * Performs a query to return all visible instances in the given range that
	 * match the given selection. This is a blocking function and should not be
	 * done on the UI thread. This will cause an expansion of recurring events
	 * to fill this time range if they are not already expanded and will slow
	 * down for larger time ranges with many recurring events.
	 * 
	 * @param cr
	 *            The ContentResolver to use for the query
	 * @param projection
	 *            The columns to return
	 * @param begin
	 *            The start of the time range to query in UTC millis since epoch
	 * @param end
	 *            The end of the time range to query in UTC millis since epoch
	 * @param selection
	 *            Filter on the query as an SQL WHERE statement
	 * @param selectionArgs
	 *            Args to replace any '?'s in the selection
	 * @param orderBy
	 *            How to order the rows as an SQL ORDER BY statement
	 * @return A Cursor of instances matching the selection
	 */
	public static final Cursor instancesQuery0(ContentResolver cr,
			String[] projection, int startDay, int endDay, String selection,
			String[] selectionArgs, String orderBy) {
		String WHERE_CALENDARS_SELECTED = Calendars.VISIBLE + "=?";
		String[] WHERE_CALENDARS_ARGS = { "1" };
		String DEFAULT_SORT_ORDER = "begin ASC";

		Uri.Builder builder = Instances.CONTENT_BY_DAY_URI.buildUpon();
		ContentUris.appendId(builder, startDay);
		ContentUris.appendId(builder, endDay);
		if (TextUtils.isEmpty(selection)) {
			selection = WHERE_CALENDARS_SELECTED;
			selectionArgs = WHERE_CALENDARS_ARGS;
		} else {
			selection = "(" + selection + ") AND " + WHERE_CALENDARS_SELECTED;
			if (selectionArgs != null && selectionArgs.length > 0) {
				selectionArgs = Arrays.copyOf(selectionArgs,
						selectionArgs.length + 1);
				selectionArgs[selectionArgs.length - 1] = WHERE_CALENDARS_ARGS[0];
			} else {
				selectionArgs = WHERE_CALENDARS_ARGS;
			}
		}
		Log.v(builder.toString());
		return cr.query(builder.build(), projection, selection, selectionArgs,
				orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
	}

	public interface OnLoaderListener {
		public void onLoadFinished(Uri uri, Cursor data);
	}

	public static class CalendarLoader implements
			LoaderManager.LoaderCallbacks<Cursor> {

		private static final int WEEKS_BUFFER = 1;
		private Context mContext;

		private int mNumWeeks = 6;

		private Uri mEventUri;

		private CursorLoader mLoader;
		private LoaderManager mLoaderManager;

		private int mFirstLoadedJulianDay;

		private boolean mHideDeclined;

		// Selection and selection args for adding event queries
		private static final String WHERE_CALENDARS_VISIBLE = Calendars.VISIBLE
				+ "=1";
		private static final String INSTANCES_SORT_ORDER = Instances.START_DAY
				+ "," + Instances.START_MINUTE + "," + Instances.TITLE;

		// The minimum time between requeries of the data if the db is
		// changing
		private static final int LOADER_THROTTLE_DELAY = 500;

		/*
		 * public static Loader create(LoaderManager lm) { return new
		 * Loader(lm); }
		 */
		public static CalendarLoader create(Context ctx, LoaderManager lm,
				Time day, OnLoaderListener listener) {
			return (new CalendarLoader(ctx, lm)).setSelectDay(day).init(
					listener);
		}

		private CalendarLoader(Context ctx, LoaderManager lm) {
			mContext = ctx;
			mLoaderManager = lm;
		}

		public CalendarLoader init(OnLoaderListener listener) {
			mLoader = (CursorLoader) mLoaderManager.initLoader(0, null, this);
			setOnLoaderListener(listener);
			return this;
		}

		public void uninit() {
			setOnLoaderListener(null);
		}

		public CalendarLoader setSelectDay(Time day) {
			mFirstLoadedJulianDay = Time.getJulianDay(day.toMillis(true),
					day.gmtoff) - (mNumWeeks * 7 / 2);
			return this;
		}

		// XXX The function isn't called.
		public void start(int julianDay, boolean isHide) {
			synchronized (this) {
				if (mLoader == null) {
					return;
				}

				mFirstLoadedJulianDay = julianDay;
				mHideDeclined = isHide;

				// Stop any previous loads while we update the uri
				stop();

				// Start the loader again
				mEventUri = updateUri();

				mLoader.setUri(mEventUri);
				mLoader.startLoading();
				mLoader.onContentChanged();
				if (Log.isLoggable(TAG, Log.DEBUG)) {
					Log.d(TAG, "Started loader with uri: " + mEventUri);
				}
			}
		}

		public void stop() {
			if (mLoader != null) {
				mLoader.stopLoading();
				if (Log.isLoggable(TAG, Log.DEBUG)) {
					Log.d(TAG, "Stopped loader from loading");
				}
			}
		}

		public void forceLoad() {
			if (mLoader != null) {
				mLoader.forceLoad();
			}
		}

		/**
		 * Updates the uri used by the loader according to the current position
		 * of the listview.
		 * 
		 * XXX The value doesn't equal that in the old design.
		 * 
		 * @return The new Uri to use
		 */
		private Uri updateUri() {
			// disposable variable used for time calculations
			Time tempTime = new Time();

			// -1 to ensure we get all day events from any time zone
			tempTime.setJulianDay(mFirstLoadedJulianDay - 1);
			long start = tempTime.toMillis(true);
			int lastLoadedJulianDay = mFirstLoadedJulianDay
					+ (mNumWeeks + 2 * WEEKS_BUFFER) * 7;

			Log.v("Start: " + tempTime.toString());

			// +1 to ensure we get all day events from any time zone
			tempTime.setJulianDay(lastLoadedJulianDay + 1);
			long end = tempTime.toMillis(true);

			Log.v("End: " + tempTime.toString());

			// Create a new uri with the updated times
			Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
			ContentUris.appendId(builder, start);
			ContentUris.appendId(builder, end);
			return builder.build();
		}

		protected String updateWhere() {
			return updateWhere(mHideDeclined);
		}

		protected String updateWhere(boolean isHide) {
			// TODO fix selection/selection args after b/3206641 is fixed
			String where = WHERE_CALENDARS_VISIBLE;
			if (isHide) {
				where += " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
						+ Attendees.ATTENDEE_STATUS_DECLINED;
			}
			return where;
		}

		public void setSelection(boolean isHide) {
			mLoader.setSelection(updateWhere(isHide));
		}

		@Override
		public android.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
			CursorLoader loader;
			synchronized (this) {
				if (mOnLoaderListener != null) {
					return null;
				}

				mEventUri = updateUri();
				String where = updateWhere();

				loader = new CursorLoader(mContext, mEventUri,
						Event.EVENT_PROJECTION, where,
						null /* WHERE_CALENDARS_SELECTED_ARGS */,
						INSTANCES_SORT_ORDER);
				loader.setUpdateThrottle(LOADER_THROTTLE_DELAY);
			}

			if (Log.isLoggable(TAG, Log.DEBUG)) {
				Log.d(TAG, "Returning new loader with uri: " + mEventUri);
			}
			return loader;
		}

		@Override
		public void onLoadFinished(android.content.Loader<Cursor> loader,
				Cursor data) {
			CursorLoader cLoader = (CursorLoader) loader;
			mOnLoaderListener.onLoadFinished(cLoader.getUri(), data);
		}

		@Override
		public void onLoaderReset(android.content.Loader<Cursor> loader) {
		}

		private OnLoaderListener mOnLoaderListener;

		public void setOnLoaderListener(OnLoaderListener listener) {
			mOnLoaderListener = listener;
		}
	}

	// ------------------------------------------------------------------------------
	// For personal daily information.
	// ------------------------------------------------------------------------------
	public static final Cursor instancesQuery(ContentResolver cr,
			String[] projection, int startDay, int endDay, String selection,
			String[] selectionArgs, String orderBy) {

		return null;
	}

	public static class Loader {
		public static Loader create(Context ctx, LoaderManager lm, Time day,
				OnLoaderListener listener) {
			return (new Loader(ctx, lm)).setSelectDay(day).init(listener);
		}

		private Loader(Context ctx, LoaderManager lm) {
		}

		public Loader init(OnLoaderListener listener) {
			listener.onLoadFinished(null, new PersonalDailyInformationCursor());
			return this;
		}

		public void uninit() {
		}

		public Loader setSelectDay(Time day) {
			return this;
		}

		// XXX The function isn't called.
		public void start(int julianDay, boolean isHide) {
		}

		public void stop() {
		}

		public void forceLoad() {
		}

		public void setSelection(boolean isHide) {
		}
	}
}
