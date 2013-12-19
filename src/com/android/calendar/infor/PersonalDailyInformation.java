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

import android.database.Cursor;

public class PersonalDailyInformation implements Serializable {

	private static final String TAG = "PersonalDailyInformation";

	public Date whichDay;
	public String name; // Disease name.
	public int level;

	public List<DetailInformation> detailList;

	public PersonalDailyInformation() {
		whichDay = new Date();
		name = "";
		level = 0;
	}

	public boolean isYesterday(Date whichDay) {
		return isSameDay(getDay(whichDay, -1));
	}

	public boolean isSameDay(Date whichDay) {
		return isSameDay(this.whichDay, whichDay);
	}

	public static boolean isSameDay(Date day1, Date day2) {
		Calendar c1 = new GregorianCalendar();
		Calendar c2 = new GregorianCalendar();

		c1.setTime(day1);
		c2.setTime(day2);

		if (c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR)) {
			return false;
		}
		if (c1.get(Calendar.MONTH) != c2.get(Calendar.MONTH)) {
			return false;
		}
		if (c1.get(Calendar.DAY_OF_MONTH) != c2.get(Calendar.DAY_OF_MONTH)) {
			return false;
		}

		return true;
	}

	public int compare(Date whichDay) {
		return this.whichDay.compareTo(whichDay);
	}

	public int compare(PersonalDailyInformation infor) {
		return compare(infor.whichDay);
	}

	public int getDetailNumber() {
		return detailList == null ? 0 : detailList.size();
	}

	public void addDetail(DetailInformation detailInfor) {
		if (detailList == null) {
			detailList = new ArrayList<DetailInformation>();
		}

		detailList.add(detailInfor);
	}

	public void delDetail(int position) {
		if (detailList == null || position >= detailList.size()) {
			return;
		}

		detailList.remove(position);
	}

	public boolean isDetailExist(int position) {
		return position < getDetailNumber();
	}

	public DetailInformation getDetail(int position) {
		if (!isDetailExist(position)) {
			return null;
		}

		return detailList.get(position);
	}

	public DetailInformation setDetail(int position,
			DetailInformation detailInfor) {
		if (!isDetailExist(position)) {
			return null;
		}

		return detailList.get(position).copy(detailInfor);
	}

	public PersonalDailyInformation copy(PersonalDailyInformation infor) {
		whichDay = infor.whichDay;
		name = infor.name;
		level = infor.level;

		detailList = infor.detailList;
		return this;
	}

    /**
     * A single body status entry.
     *
     * Instances of the class are immutable.
     */
    public static class BodyStatusEntry implements Comparable<BodyStatusEntry>, Serializable {
        private final int mType;
        private final String mValue;

        /**
         * Returns a new BodyStatusEntry, with the specified type and value.
         *
         * @param type Number of type before the start of the event that the alert will fire.
         * @param value Type of alert ({@link BodyStatuss#METHOD_ALERT}, etc).
         */
        public static BodyStatusEntry valueOf(int type, String value) {
            // TODO: cache common instances
            return new BodyStatusEntry(type, value);
        }

        /**
         * Returns a BodyStatusEntry, with the specified number of type and a default alert value.
         *
         * @param type Number of type before the start of the event that the alert will fire.
         */
        public static BodyStatusEntry valueOf(int type) {
            return valueOf(type, "");
        }

        /**
         * Constructs a new BodyStatusEntry.
         *
         * @param type Number of type before the start of the event that the alert will fire.
         * @param value Type of alert ({@link BodyStatuss#METHOD_ALERT}, etc).
         */
        private BodyStatusEntry(int type, String value) {
            // TODO: error-check args
            mType = type;
            mValue = value;
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
            if (!(obj instanceof BodyStatusEntry)) {
                return false;
            }

            BodyStatusEntry re = (BodyStatusEntry) obj;

            if (re.mType != mType) {
                return false;
            }

            return re.mValue.equals(mValue);
        }

        @Override
        public String toString() {
            return "BodyStatusEntry type=" + mType + " value=" + mValue;
        }

        /**
         * Comparison function for a sort ordered primarily descending by type,
         * secondarily ascending by value type.
         */
        @Override
        public int compareTo(BodyStatusEntry re) {
            if (re.mType != mType) {
                return re.mType - mType;
            }
            if (re.mValue.equals(mValue)) {
                return mValue.compareTo(re.mValue);
            }
            return 0;
        }

        /** Returns the type. */
        public int getType() {
            return mType;
        }

        /** Returns the alert value. */
        public String getValue() {
            return mValue;
        }
    }


	public static class DetailInformation implements Serializable {
		public String description;
		public String attachmentPath;

		public DetailInformation() {
			description = "";
			attachmentPath = "";
		}

		public DetailInformation(DetailInformation infor) {
			this.copy(infor);
		}

		public static DetailInformation parseDetailInformation(JSONObject object) {
			try {
				DetailInformation detailInfo = new DetailInformation();

				detailInfo.description = object.getString("description");
				detailInfo.attachmentPath = object.getString("attachmentPath");

				return detailInfo;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		public DetailInformation copy(DetailInformation infor) {
			description = infor.description;
			attachmentPath = infor.attachmentPath;
			return this;
		}

		public JSONObject toJSONObject() {
			try {
				JSONObject object = new JSONObject();
				object.put("description", description);
				object.put("attachmentPath", attachmentPath);
				return object;
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}
		}

		public String toString() {
			String str = "";
			str += "Description: " + description + "\n";
			str += "AttachmentPath: " + attachmentPath + "\n";

			return str;
		}
	}

	public static PersonalDailyInformation parsePersonalDailyInformation(
			JSONObject object) {
		PersonalDailyInformation info;
		try {
			info = new PersonalDailyInformation();

			info.setDay(object.getString("whichDay"));
			info.name = object.getString("name");
			info.level = Integer.parseInt(object.getString("level"));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		try {
			JSONArray jsonArray = object.getJSONArray("detailList");
			int num = jsonArray.length();
			if (num > 0) {
				info.detailList = new ArrayList<DetailInformation>();

				for (int i = 0; i < num; i++) {
					JSONObject obj = jsonArray.getJSONObject(i);
					DetailInformation detailInfo = DetailInformation
							.parseDetailInformation(obj);
					if (detailInfo != null) {
						info.detailList.add(detailInfo);
					}
				}
			}
		} catch (Exception e) {
//			e.printStackTrace();
		}

		return info;
	}
	
	public static PersonalDailyInformation parsePersonalDailyInformation(
			Cursor cEvents) {
		try {
			String jsonBuf = cEvents.getString(0);
			JSONObject object = new JSONObject(jsonBuf);
			return parsePersonalDailyInformation(object);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return null;
	}

	public String toString() {
		String str = "Date: " + whichDay + "\n";
		str += "Name: " + name + "\n";
		str += "Level: " + level + "\n";

		if (detailList != null) {
			for (int i = 0; i < detailList.size(); i++) {
				str += "NO." + i + ": " + detailList.get(i).description + ", "
						+ detailList.get(i).attachmentPath + "\n";
			}
			str += "\n";
		}

		return str;
	}
/*
	private final String getUTCDateTime() {
		try {
			DateFormat df = DateFormat.getDateTimeInstance();
			df.setTimeZone(TimeZone.getTimeZone("UTC"));
			return df.format(whichDay);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private void setUTCDateTime(String dt) {
		try {
			DateFormat df = DateFormat.getDateTimeInstance();
			df.setTimeZone(TimeZone.getTimeZone("UTC"));
			whichDay = df.parse(dt);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
*/
	public String getDay() {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		return formatter.format(whichDay);
	}

	public void setDay(String day) {
		try {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			whichDay = formatter.parse(day);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public JSONObject toJSONObject() {
		try {
			JSONObject object = new JSONObject();
			object.put("whichDay", getDay());
			object.put("name", name);
			object.put("level", level);

			if (detailList != null) {
				JSONArray objectArray = new JSONArray();
				for (int i = 0; i < detailList.size(); i++) {
					objectArray.put(detailList.get(i).toJSONObject());
				}

				object.put("detailList", objectArray);
			}
			return object;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Date getDay(int diff) {
		return getDay(new Date(), diff);
	}

	private static Date getDay(Date date, int diff) {
		Calendar calendar = new GregorianCalendar();
		calendar.setTime(date);
		calendar.add(Calendar.DATE, diff);

		return calendar.getTime();
	}

	public static PersonalDailyInformation createRandomPersonalDailyInformation() {
		Date date = new Date();
		Random random = new Random(date.getTime());

		PersonalDailyInformation infor = new PersonalDailyInformation();
		infor.whichDay = getDay(-1 * (Math.abs(random.nextInt()) % 10));

		final String NAME_GROUP[] = { "AAAA", "BBBB", "CCCC", "DDDD", "EEEE" };

		infor.name = NAME_GROUP[Math.abs(random.nextInt()) % 5];

		int num = Math.abs(random.nextInt()) % 4;

		if (num > 0) {
			infor.detailList = new ArrayList<DetailInformation>();
			for (int i = 0; i < num; i++) {
				DetailInformation detailInfo = new DetailInformation();

				detailInfo.description = "NO." + i + ": description.";
				detailInfo.attachmentPath = "NO." + i + ": attachmentPath.";
				infor.detailList.add(detailInfo);
			}
		}

		// Log.i(TAG, infor.toString());
		return infor;
	}
}
