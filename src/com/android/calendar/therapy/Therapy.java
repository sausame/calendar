package com.android.calendar.therapy;

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
 * A single therapy.
 *
 * Instances of the class are immutable.
 */
public class Therapy implements Comparable<Therapy>, Serializable { 

	public final static String THERAPY = "therapy";

	private static String TAG = "Therapy";

	private int mId;

	// In every time.
	public final int USAGE_TYPE_NUMBER = 0;
	public final int USAGE_TYPE_MILLILITER = 1;
	public final int USAGE_TYPE_TIME_SECONDS = 2;
	public final int USAGE_TYPE_TIME_MINUTES = 3;
	public final int USAGE_TYPE_TIME_HOURS = 4;

	private int mType = 0;
	private String mName;

	// XXX: We should put usages together.
	// They will be replaced with one or two rule in the future.
	private String mUsageRule;
	private int mNumberInEveryTime = 1;
	private int mUsageTypeInEveryTime = USAGE_TYPE_NUMBER;

	private long mDay;
	private boolean mHasAlarm;
	private long mRemindersGroup[];
	private String mDescription;
	private boolean mPrivacy;

	/**
	 * Constructs a new Therapy.
	 *
	 */
	public Therapy() {
		// TODO: error-check args
	}

	@Override
	public int hashCode() {
		return mType * 10;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Therapy)) {
			return false;
		}

		Therapy re = (Therapy) obj;
		return re.getType() == this.mType
			&& this.mName.equals(re.getName())
			&& re.getDay() == this.mDay;
	}

	/**
	 * Comparison function for a sort ordered primarily descending by type,
	 * secondarily ascending by value type.
	 */
	@Override
	public int compareTo(Therapy re) {
		int diff;
		
		if ((diff = (int) (re.getDay() - this.mDay)) != 0) {
			return diff;
		}

		if ((diff = re.getType() - this.mType) != 0) {
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

		str += "\nType=" + mType;
		str += "\nName=" + mName;
		str += "\nUsageRule=" + mUsageRule;
		str += "\nNumberInEveryTime=" + mNumberInEveryTime;
		str += "\nUsageTypeInEveryTime=" + mUsageTypeInEveryTime;
		str += "\nDay=" + mDay;
		str += "\nHasAlarm=" + mHasAlarm;

		if (mRemindersGroup != null) {

			for (int i = 0; i < mRemindersGroup.length; i++) {
				str += "\nNO." + i + ":" + mRemindersGroup[i];
				;
			}
		}
		str += "\nDescription=" + mDescription;
		str += "\nPrivacy=" + mPrivacy;

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

	/** Returns the type. */
	public int getType() {
		return mType;
	}

	/** Set the type. */
	public void setType(int type) {
		mType = type;
	}

	/** Returns the name. */
	public String getName() {
		return mName;
	}

	/** Set the name. */
	public void setName(String name) {
		mName = name;
	}

	/** Returns the usageRule. */
	public String getUsageRule() {
		return mUsageRule;
	}

	/** Set the usageRule. */
	public void setUsageRule(String rule) {
		mUsageRule = rule;
	}

	/** Returns the number. */
	public int getNumberInEveryTime() {
		return mNumberInEveryTime;
	}

	/** Set the number. */
	public void setNumberInEveryTime(int number) {
		mNumberInEveryTime = number;
	}

	/** Returns the usage. */
	public int getUsageTypeInEveryTime() {
		return mUsageTypeInEveryTime;
	}

	/** Set the usage. */
	public void setUsageTypeInEveryTime(int type) {
		mUsageTypeInEveryTime = type;
	}

	/** Returns the day. */
	public long getDay() {
		return mDay;
	}

	/** Set the day. */
	public void setDay(long day) {
		mDay = day;
	}

	/** Returns the hasAlarm. */
	public boolean getHasAlarm() {
		return mHasAlarm;
	}

	/** Set the hasAlarm. */
	public void setHasAlarm(boolean hasAlarm) {
		mHasAlarm = hasAlarm;
	}

	/** Returns the reminders group. */
	public long[] getRemindersGroup() {
		return mRemindersGroup;
	}

   /** Set the reminders group. */
	public void setRemindersGroup(long[] remindersGroup) {
		mRemindersGroup = remindersGroup;
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
			Log.e(TAG, e.toString());
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
	
	public static Therapy parse(JSONObject object) {
		Therapy therapy = new Therapy();

		therapy.setDay(getLongValue(object, "day"));
		therapy.setDescription(getStringValue(object, "description"));
		therapy.setHasAlarm(getIntegerValue(object, "hasAlarm") != 0);
		therapy.setName(getStringValue(object, "name"));
		therapy.setNumberInEveryTime(getIntegerValue(object, "numberInEveryTime"));
		therapy.setPrivacy(getIntegerValue(object, "privacy") != 0);
		therapy.setType(getIntegerValue(object, "type"));
		therapy.setUsageRule(getStringValue(object, "usageRule"));
		therapy.setUsageTypeInEveryTime(getIntegerValue(object, "usageTypeInEveryTime"));
		
		try {
			JSONArray jsonArray = object.getJSONArray("reminder");
			int num = jsonArray.length();
			if (num > 0) {
				long group[] = new long[num];
				for (int i = 0; i < num; i++) {
					JSONObject obj = jsonArray.getJSONObject(i);
					group[i] = getLongValue(obj, "time");
				}
				therapy.setRemindersGroup(group);
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		
		return therapy;
	}
	
	public static Therapy parse(Cursor cEvents) {
		return parse(cEvents.getString(0));
	}

	public static Therapy parse(String jsonBuf) {
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
			object.put("hasAlarm", this.getHasAlarm() ? 1 : 0);
			object.put("name", this.getName());
			object.put("numberInEveryTime", this.getNumberInEveryTime());
			object.put("privacy", this.getPrivacy() ? 1 : 0);
			object.put("type", this.getType());
			object.put("usageRule", this.getUsageRule());
			object.put("usageTypeInEveryTime", this.getUsageTypeInEveryTime());

			if (this.getRemindersGroup() != null) {
				JSONArray objectArray = new JSONArray();
				for (int i = 0; i < this.getRemindersGroup().length; i++) {
					JSONObject obj = new JSONObject();
					obj.put("time", Long.toString(this.getRemindersGroup()[i]));
					objectArray.put(obj);
				}

				object.put("reminder", objectArray);
			}
			Log.v(this.toString());
			Log.v(object.toString(2));
			return object;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Therapy from(Intent intent) {
        if (intent == null) {
            return null;
        }

		return parse(intent.getStringExtra(THERAPY));
    }

	public boolean isEmpty() {
		return this.mName == null || this.mName.isEmpty();
	}
}
