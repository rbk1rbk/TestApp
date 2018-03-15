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
	static final int DATABASE_VERSION = 3;
	static final String DATABASE_NAME = "RemoteFilesDB.db";
	private static volatile int dbOpened = 0;
	private static SQLiteDatabase db;

	Cursor getAllFiles() {
		boolean iOpenedDB=false;
		if ((db==null) || (dbOpened == 0)) {
			db = getWritableDatabase();
			iOpenedDB = true;
		}

		Cursor cAllFiles;
		try {
			cAllFiles = db.rawQuery("select rowid  _id,* "
											+ " from " + Constants.RemoteFilesDBEntry.TABLE_NAME
					, null);
			if (cAllFiles == null)
				return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
// NEZATVARAT DB!
		return cAllFiles;
	}

	boolean containsFile(String fileName) {
	boolean iOpenedDB=false;
		if ((db==null) || (dbOpened == 0)) {
			db = getReadableDatabase();
			iOpenedDB = true;
		}
		Cursor cChkFile;
		if (db == null)
			return true;
		int count = 1;
		try {
			cChkFile = db.rawQuery("select count (*) from " + Constants.RemoteFilesDBEntry.TABLE_NAME
										   + " where "
										   + Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE + "=\"" + fileName + "\""
					, null);
			cChkFile.moveToFirst();
			count = cChkFile.getInt(0);
			cChkFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if ((iOpenedDB) && (--dbOpened==0))
				db.close();
		}
		return (count > 0);
	}

	public void addFile(String fileName, Long fileSize) {
		boolean iOpenedDB=false;
		if ((db==null) || (dbOpened == 0)) {
			db = getWritableDatabase();
			iOpenedDB = true;
		}
		if (!containsFile(fileName)) {
			ContentValues newRemoteFile = new ContentValues();
			newRemoteFile.put(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE, fileName);
			newRemoteFile.put(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE, fileSize);
			try {
				db.insertOrThrow(Constants.RemoteFilesDBEntry.TABLE_NAME, null, newRemoteFile);
			} catch (Exception e) {
				if (e.toString().contains("UNIQUE constraint failed")) {
//				Log.d("RemoteFilesDB.addFile",fileName+" entry exists");
				} else
					e.printStackTrace();
			}
		}
		if ((iOpenedDB) && (--dbOpened==0)) 
			db.close();
	}
	public void delFile(String fileName) {
		boolean iOpenedDB=false;
		if ((db==null) || (dbOpened == 0)) {
			db = getWritableDatabase();
			iOpenedDB = true;
		}
		db.delete(Constants.RemoteFilesDBEntry.TABLE_NAME,Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE+ "=\"" +  fileName + "\"",null);
		if ((iOpenedDB) && (--dbOpened==0)) 
			db.close();
	}
	void updateFileFingerprint(String fileName, String fingerPrint){
		boolean iOpenedDB=false;
		if ((db==null) || (dbOpened == 0)) {
			db = getWritableDatabase();
			iOpenedDB = true;
		}

		ContentValues newFingerprint=new ContentValues();;
		newFingerprint.put(Constants.RemoteFilesDBEntry.COLUMN_NAME_FINGERPRINT, fingerPrint);
		String where = Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE + "=\"" +  fileName + "\"";
		db.update(Constants.RemoteFilesDBEntry.TABLE_NAME, newFingerprint, where, null);
		if ((iOpenedDB) && (--dbOpened==0)) 
			db.close();
	}
	public Cursor getFilesBySize(Long fileSize, Long sizeDiff){
		boolean iOpenedDB=false;
		if ((db==null) || (dbOpened == 0)) {
			db = getWritableDatabase();
			iOpenedDB = true;
		}

		String fileSizeWhere=null;
		if (sizeDiff>0)
			fileSizeWhere=Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE
								  + " >= "
								  + Long.toString(fileSize)
								  + " and "
								  + Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE
								  + " <= "
								  + Long.toString(fileSize + sizeDiff);
		else
			fileSizeWhere=Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE
								  + " == "
								  + Long.toString(fileSize);
		Cursor cFilesBySize = db.rawQuery("select * from "
												  + Constants.RemoteFilesDBEntry.TABLE_NAME
												  + " where "
												  + fileSizeWhere

				, null);
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
											+ "constraint remotefileUnique UNIQUE ("
											+ Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE + ")"
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
		boolean iOpenedDB=false;
		if ((db==null) || (dbOpened == 0)) {
			db = getWritableDatabase();
			iOpenedDB = true;
		}

		Cursor c = db.rawQuery("select count(*) from "+ Constants.RemoteFilesDBEntry.TABLE_NAME,null);
		c.moveToFirst();
		int ret=c.getInt(0);
		c.close();
		if ((iOpenedDB) && (--dbOpened==0)) 
			db.close();
		return (ret);
	}
	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
//		Log.d("onOpen before"+db.toString(),Integer.toString(dbOpened));
//		Log.d("onOpen before",Integer.toString(dbOpened));
		synchronized (this) {
			dbOpened++;
		}
//		Log.d("onOpen after"+db.toString(),Integer.toString(dbOpened));
//		Log.d("onOpen after",Integer.toString(dbOpened));
	}

}
