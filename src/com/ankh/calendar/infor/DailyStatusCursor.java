package com.ankh.calendar.infor;

import android.content.ContentResolver;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

import com.ankh.calendar.R;

public class DailyStatusCursor implements Cursor {

	private static final String TAG = "DailyStatusCursor";

	private DailyStatusManager mManager = new DailyStatusManager();

	private DailyStatus mCurrentInfor = null;

	public DailyStatusCursor(Context ctx) {
		mManager.setPathname(ctx.getResources().getString(
				R.string.infor_filename));
		mManager.load();
	}

	private int mPosition = 0;

	public int getColumnCount() {
		return COLUMN_NAMES.length;
	}

	public int getColumnIndex(String columnName) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public int getColumnIndexOrThrow(String columnName) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public String getColumnName(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	final static String COLUMN_NAMES[] = {"JSON_CODE"};
	public String[] getColumnNames() {
		return COLUMN_NAMES;
	}

	public int getCount() {
		return mManager.getSize();
	}

	public boolean isNull(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public int getInt(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public long getLong(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public short getShort(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public float getFloat(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public double getDouble(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public byte[] getBlob(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public String getString(int columnIndex) {
		return mCurrentInfor.toJSONObject().toString();
	}

	public Bundle getExtras() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public int getPosition() {
		return mPosition;
	}

	public boolean isAfterLast() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean isBeforeFirst() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean isFirst() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean isLast() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean move(int offset) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean moveToFirst() {
		return moveToPosition(-1);
	}

	public boolean moveToLast() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean moveToNext() {
		mPosition ++;
		mCurrentInfor = mManager.getDailyStatus();
		return (mCurrentInfor != null);
	}

	public boolean moveToPrevious() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean moveToPosition(int position) {
		if (position != -1 && position != 0) {
			throw new UnsupportedOperationException("Unsupport " + position);
		}

		mManager.reset();
		mPosition = position;

		return true;
	}

	public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public void deactivate() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public void close() {
	}

	public boolean isClosed() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean requery() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public void registerContentObserver(ContentObserver observer) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public void registerDataSetObserver(DataSetObserver observer) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public Bundle respond(Bundle extras) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public boolean getWantsAllOnMoveCalls() {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public void setNotificationUri(ContentResolver cr, Uri uri) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public void unregisterContentObserver(ContentObserver observer) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public void unregisterDataSetObserver(DataSetObserver observer) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	public int getType(int columnIndex) {
		throw new UnsupportedOperationException("unimplemented mock method");
	}

	@Override
	public Uri getNotificationUri() {
		return null;
	}

}
