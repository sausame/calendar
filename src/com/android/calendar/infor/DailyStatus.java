package com.android.calendar.infor;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.calendar.Log;

import android.content.Intent;
import android.database.Cursor;
import android.text.format.Time;

/**
 * A single daily status.
 *
 * Instances of the class are immutable.
 */
public class DailyStatus implements Comparable<DailyStatus>, Serializable { 

	public final static String DAILY_STATUS = "daily_status";

	private static String TAG = "DailyStatus";

	private int mId;

	private int mLevel;
	private String mName;
	private String mPart;
	private long mDay;
	private String mDescription;
	private boolean mPrivacy;
	private BodyStatus mBodyStatusesGroup[];

	public static class BodyStatus {
        private String mType;
        private String mValue;

		/** Returns the type. */
		public String getType() {
			return mType;
		}

		/** Set the type. */
		public void setType(String type) {
			mType = type;
		}

		/** Returns the value. */
		public String getValue() {
			return mValue;
		}

		/** Set the value. */
		public void setValue(String value) {
			mValue = value;
		}
	}

	/**
	 * Constructs a new DailyStatus.
	 *
	 */
	public DailyStatus() {
		// TODO: error-check args
	}

	@Override
	public int hashCode() {
		return (int) mDay;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DailyStatus)) {
			return false;
		}

		DailyStatus re = (DailyStatus) obj;
		return this.mName.equals(re.getName())
			&& re.getDay() == this.mDay;
	}

	/**
	 * Comparison function for a sort ordered primarily descending by part,
	 * secondarily ascending by value part.
	 */
	@Override
	public int compareTo(DailyStatus re) {
		int diff;
		
		if ((diff = (int) (re.getDay() - this.mDay)) != 0) {
			return diff;
		}
	
		return this.mName.compareTo(re.getName());
	}

	public int compareTo(long milliseconds) {
		return (int) (milliseconds - this.mDay);
	}

	@Override
	public String toString() {
		String str = "";

		str += "\nID=" + mId;

		str += "\nLevel=" + mLevel;
		str += "\nName=" + mName;
		str += "\nPart=" + mPart;
		str += "\nDay=" + mDay;
		str += "\nDescription=" + mDescription;
		str += "\nPrivacy=" + mPrivacy;

		if (mBodyStatusesGroup != null) {

			for (BodyStatus status: mBodyStatusesGroup) {
				str += "\n" + status.mType + "=" + status.mValue;
			}
		}
		return str;
	}

	/** Returns the id. */
	public int getId() {
		return mId;
	}

	/** Set the id. */
	public void setId(int id) {
		mId = id;
	}

	/** Returns the part. */
	public String getPart() {
		return mPart;
	}

	/** Set the part. */
	public void setPart(String part) {
		mPart = part;
	}

	/** Returns the name. */
	public String getName() {
		return mName;
	}

	/** Set the name. */
	public void setName(String name) {
		mName = name;
	}

	/** Returns the level. */
	public int getLevel() {
		return mLevel;
	}

	/** Set the level. */
	public void setLevel(int level) {
		mLevel = level;
	}

	/** Returns the day. */
	public long getDay() {
		return mDay;
	}

	/** Set the day. */
	public void setDay(long day) {
		mDay = day;
	}

	/** Returns the body statuses group. */
	public BodyStatus[] getBodyStatusesGroup() {
		return mBodyStatusesGroup;
	}

	/** Set the body statuses group. */
	public void setBodyStatusesGroup(BodyStatus[] bodyStatusesGroup) {
		mBodyStatusesGroup = bodyStatusesGroup;
	}

	/** Returns the description. */
	public String getDescription() {
		return mDescription;
	}

	/** Set the description. */
	public void setDescription(String description) {
		mDescription = description;
	}

	/** Returns the privacy. */
	public boolean getPrivacy() {
		return mPrivacy;
	}

	/** Set the privacy. */
	public void setPrivacy(boolean privacy) {
		mPrivacy = privacy;
	}

	public static String getStringValue(JSONObject object, String name) {
		try {
			return object.getString(name);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			return null;
		}
	}

	public static int getIntegerValue(JSONObject object, String name) {
		try {
			return Integer.parseInt(getStringValue(object, name));
		} catch (Exception e) {
			Log.e(TAG , e.toString());
			return 0;
		}
	}

	public static long getLongValue(JSONObject object, String name) {
		try {
			return Long.parseLong(getStringValue(object, name));
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			return 0;
		}
	}

	public static DailyStatus parse(JSONObject object) {
		DailyStatus dailyStatus = new DailyStatus();

		dailyStatus.setDay(getLongValue(object, "day"));
		dailyStatus.setDescription(getStringValue(object, "description"));
		dailyStatus.setLevel(getIntegerValue(object, "level"));
		dailyStatus.setName(getStringValue(object, "name"));
		dailyStatus.setPart(getStringValue(object, "part"));
		dailyStatus.setPrivacy(getIntegerValue(object, "privacy") != 0);
		
		try {
			JSONArray jsonArray = object.getJSONArray("body_status");
			int num = jsonArray.length();
			if (num > 0) {
				BodyStatus group[] = new BodyStatus[num];
				for (int i = 0; i < num; i++) {
					JSONObject obj = jsonArray.getJSONObject(i);
					group[i].mType = getStringValue(obj, "type");
					group[i].mValue = getStringValue(obj, "value");
				}
				dailyStatus.setBodyStatusesGroup(group);
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		
		return dailyStatus;
	}
	
	public static DailyStatus parse(Cursor cEvents) {
		return parse(cEvents.getString(0));
	}

	public static DailyStatus parse(String jsonBuf) {
		if (jsonBuf == null || jsonBuf.isEmpty()) {
			Log.v("Empty string: " + jsonBuf);
			return null;
		}
		
		try {
			JSONObject object = new JSONObject(jsonBuf);
			return parse(object);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}

	public JSONObject toJSONObject() {
		try {
			JSONObject object = new JSONObject();

			object.put("day", Long.toString(this.getDay()));
			object.put("description", this.getDescription());
			object.put("level", this.getLevel());
			object.put("name", this.getName());
			object.put("part", this.getPart());
			object.put("privacy", this.getPrivacy() ? 1 : 0);

			if (this.getBodyStatusesGroup() != null) {
				JSONArray objectArray = new JSONArray();
				for (BodyStatus status : this.getBodyStatusesGroup()) {
					JSONObject obj = new JSONObject();
					obj.put("type", status.mType);
					obj.put("value", status.mValue);
					objectArray.put(obj);
				}

				object.put("body_status", objectArray);
			}

			Log.v(this.toString());
			Log.v(object.toString(2));
			return object;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static DailyStatus from(Intent intent) {
        if (intent == null) {
            return null;
        }

		return parse(intent.getStringExtra(DAILY_STATUS));
    }

	public boolean isEmpty() {
		return this.mName == null || this.mName.isEmpty();
	}
}
