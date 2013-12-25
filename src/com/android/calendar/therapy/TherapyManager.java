package com.android.calendar.therapy;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.calendar.Log;

public class TherapyManager {

	private static final String TAG = "TherapyManager";

	private JSONArray mJsonArray = null;
	private int mCurrentIndex = 0;
	private String mPathname;

	public void setPathname(String path) {
		mPathname = path;
	}

	public int getSize() {
		return mJsonArray == null ? 0 : mJsonArray.length();
	}

	public void load() {
		try {
			StringBuilder sb = new StringBuilder();
			BufferedReader br = new BufferedReader(new InputStreamReader(
					new FileInputStream(mPathname)));

			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line + "\n");
			}
			br.close();

			setTherapyBuffer(sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void save() {
		if (null == mJsonArray) {
			Log.e(TAG, "Nothing is needed to save.");
			return;
		}

		try {
			String jsonString = mJsonArray.toString();
			OutputStreamWriter osw = new OutputStreamWriter(
					new FileOutputStream(mPathname));

			osw.write(jsonString, 0, jsonString.length());

			osw.flush();
			osw.close();
			
			Log.i(TAG, "Save " + mJsonArray.length() + " records to " + mPathname);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void setTherapyBuffer(String buffer) {
		try {
			mJsonArray = new JSONArray(buffer);
			mCurrentIndex = 0;

			if (0 == mJsonArray.length()) {
				mJsonArray = null;
			}

		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void reset() {
		mCurrentIndex = 0;
	}

	public boolean add(Therapy newTherapy) {
		reset();

		Therapy therapy = null;

		try {
			while (null != (therapy = getTherapy())) {
				int diff = therapy.compareTo(newTherapy);

				if (diff > 0) {
					continue;
				}

				if (diff <= 0) {
					// Append.
					int i = mJsonArray.length() - 1;
					mJsonArray.put(mJsonArray.getJSONObject(i));

					// Move
					for (; i >= mCurrentIndex - 1; i--) {
						mJsonArray.put(i + 1, mJsonArray.getJSONObject(i));
					}

					// Insert before.
				}

				// Move the current.
				mCurrentIndex--;
				break;
			}

			if (mJsonArray == null) {
				mJsonArray = new JSONArray();
			}

//			Log.v(TAG, "Add " + mCurrentIndex + ":" + newTherapy.toJSONObject());
			mJsonArray.put(mCurrentIndex, newTherapy.toJSONObject());

			return true;

		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean del(int id) {
		reset();

		JSONArray array = new JSONArray();

		Therapy therapy = null;

		for (; null != (therapy = getTherapy()); id--) {
			if (0 == id) {
				continue;
			}

			array.put(therapy.toJSONObject());
		}

		mJsonArray = array;
		return true;
	}

	public boolean modify(int id, Therapy newTherapy) {
		if (!del(id)) {
			return false;
		}

		newTherapy.setId(id);
		return add(newTherapy);
	}

	public boolean modify(Therapy oldTherapy, Therapy newTherapy) {
		return modify(oldTherapy.getId(), newTherapy);
	}

	public Therapy getTherapy() {
		if (null == mJsonArray || mCurrentIndex >= mJsonArray.length()) {
			Log.d(TAG, (mJsonArray == null) ? "NO array" : "" + mCurrentIndex
					+ " >= " + mJsonArray.length());
			return null;
		}

		try {
//			Log.v(TAG,
//					"Try to get " + mCurrentIndex + " in "
//							+ mJsonArray.length());
			JSONObject obj = mJsonArray.getJSONObject(mCurrentIndex++);

			Therapy therapy = Therapy.parse(obj);
			therapy.setId(mCurrentIndex);
			return therapy;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	public Therapy getTherapy(Date whichDay) {
		reset();

		Therapy therapy = null;
		while (null != (therapy = getTherapy())) {
			int diff = therapy.compareTo(whichDay.getTime());

			if (0 == diff) {
				return therapy;
			}

			if (diff > 0) {
				break;
			}
		}

		return null;
	}

	public String toString() {

		String str = "\n-------------------------------------------------------------------------------\n";
		str += "Pathname: " + mPathname + "\n";

		try {
			str += mJsonArray.toString(2);
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		str += "\n-------------------------------------------------------------------------------\n";

		return str;
	}

}
