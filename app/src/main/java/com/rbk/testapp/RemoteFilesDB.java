package com.rbk.testapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by biel on 7.2.2017.
 */

public class RemoteFilesDB extends SQLiteOpenHelper {
	static final int DATABASE_VERSION = 1;
	static final String DATABASE_NAME = "RemoteFilesDB.db";
	private static volatile int dbOpened = 0;
	private static SQLiteDatabase db;

	public void addFile(String fileName, Long fileSize) {
		db = getWritableDatabase();
		ContentValues newRemoteFile = new ContentValues();
		newRemoteFile.put(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE, fileName);
		newRemoteFile.put(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE, fileSize);
		try {
			db.insert(Constants.MediaFilesDBEntry.TABLE_NAME, null, newRemoteFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (--dbOpened == 0)
			db.close();
	}
	void updateFileFingerprint(String fileName, String fingerPrint){
		db = getWritableDatabase();
		ContentValues newFingerprint=new ContentValues();;
		newFingerprint.put(Constants.RemoteFilesDBEntry.COLUMN_NAME_FINGERPRINT, fingerPrint);
		String where = Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE + "=\"" +  fileName + "\"";
		db.update(Constants.RemoteFilesDBEntry.TABLE_NAME, newFingerprint, where, null);
		if (--dbOpened == 0)
			db.close();
	}
	public Cursor getFilesBySize(Long fileSize, Long sizeDiff){
		db = getWritableDatabase();
		Cursor cFilesBySize = db.rawQuery("select * from "
												  + Constants.RemoteFilesDBEntry.TABLE_NAME
												  + "where"
												  + Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE
												  + " > "
												  + Long.toString(fileSize - sizeDiff)
												  + " and "
												  + Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE
												  + " < "
												  + Long.toString(fileSize + sizeDiff)

				, null);
		if (--dbOpened == 0)
			db.close();
		return cFilesBySize;
	}
	RemoteFilesDB(Context ctx) {
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
	}
	public void onCreate(SQLiteDatabase db) {
		Log.d("SQL","Creating db "+DATABASE_NAME);
		db.execSQL("drop table if exists " + Constants.RemoteFilesDBEntry.TABLE_NAME);
		final String TABLE_CREATE = "create table " + Constants.RemoteFilesDBEntry.TABLE_NAME + "("
											+ Constants.RemoteFilesDBEntry.TABLE_NAME
											+ "_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
/*
											+ Constants.RemoteFilesDBEntry.COLUMN_NAME_PATH + " TEXT, "
*/
											+ Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE + " TEXT, "
											+ Constants.RemoteFilesDBEntry.COLUMN_NAME_FINGERPRINT + " TEXT, "
											+ Constants.RemoteFilesDBEntry.COLUMN_NAME_TS + " INTEGER, "
											+ Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE + " INTEGER, "
											+ "CONSTRAINT srcFile_unique UNIQUE ("
											+ "," + Constants.RemoteFilesDBEntry.COLUMN_NAME_PATH
											+ "," + Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE + ")"
											+ ")";
		db.execSQL(TABLE_CREATE);
	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onCreate(db);
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	public int size(){
		db = getWritableDatabase();
		Cursor c = db.rawQuery("select count(*) from "+ Constants.RemoteFilesDBEntry.TABLE_NAME,null);
		c.moveToFirst();
		if (--dbOpened == 0)
			db.close();
		return (c.getInt(0));
	}
	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
		synchronized (this) {
			dbOpened++;
		}
		Log.d("onOpen "+db.toString(),Integer.toString(dbOpened));
	}

}
