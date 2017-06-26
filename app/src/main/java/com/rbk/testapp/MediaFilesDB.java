package com.rbk.testapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;

/**
 * Created by biel on 14.3.2017.
 */

class MediaFilesDB extends SQLiteOpenHelper {
	static final int DATABASE_VERSION = 10;
	static final String DATABASE_NAME = "PicSync.db";
	private static volatile int dbOpened = 0;
	private static SQLiteDatabase db;
	private MediaFilesCallback callBack;

	MediaFilesDB(Context ctx) {
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		callBack = null;
	}

	//TODO: https://guides.codepath.com/android/Creating-Custom-Listeners
	public interface MediaFilesCallback {
		public void onDBStatisticsChange(String statistic, int data);
	}

	public void setMediaFilesCallback(MediaFilesCallback cb){
		this.callBack = cb;
	}
	/*		public void updateFileHashOfAllFiles(){
				db = getReadableDatabase();
				Cursor cChkFile;
				if (db == null)
					return;
				int count = 1;

				try {
					cChkFile = db.rawQuery("select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
									+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
									+ " where "
									+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5 + "=\"" + 0 + "\""
									+ " or "
									+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5 + " is null "
									+ " or "
									+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH + "!=\"" + cksumMaxBytes + "\""
									+ " or "
									+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH + " is null "

							, null);
					cChkFile.moveToFirst();
					while (!cChkFile.isAfterLast()) {
						int colSRCPATH = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
						int colSRCFILE = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
						String fileNameFull = cChkFile.getString(colSRCPATH) + File.separator + cChkFile.getString(colSRCFILE);
						String md5sum = Utils.makeFileFingerprint(fileNameFull);
						ContentValues newHashValues = new ContentValues();
						newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5, md5sum);
						newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH, cksumMaxBytes);
						String where = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" +  cChkFile.getString(colSRCPATH) + "\""
								+ " and "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + cChkFile.getString(colSRCFILE) + "\"";
						db.update(Constants.MediaFilesDBEntry.TABLE_NAME, newHashValues, where, null);
						Log.d("updateFileHash", fileNameFull + " md5sum: " + md5sum);
						cChkFile.moveToNext();
					}
					cChkFile.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				finally {
					if (--dbOpened == 0)
						db.close();
				}
			}
	*/
	public void updateMetadataHashOfFile(String filePath, String fileName, String fileMetadataHash){
		String fileNameFull = filePath + File.separator + fileName;
		ContentValues newHashValues = new ContentValues();
		newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT, fileMetadataHash);
		newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE, Constants.MediaFilesDBEntry_FINGERPRINT_TYPE);
		String where = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" +  filePath + "\""
							   + " and "
							   + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + fileName + "\"";
		db.update(Constants.MediaFilesDBEntry.TABLE_NAME, newHashValues, where, null);
		Log.d("updateFileHash", fileNameFull + " exifHash: " + fileMetadataHash);
	}
	public void updateMetadataHashOfAllFiles(){
		db = getReadableDatabase();
		Cursor cChkFile;
		if (db == null)
			return;
		int count = 1;

		try {
			cChkFile = db.rawQuery("select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
										   + " from " + Constants.MediaFilesDBEntry.TABLE_NAME
										   + " where "
										   + Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT + "=\"" + 0 + "\""
										   + " or "
										   + Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT + " is null "
										   + " or "
										   + Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE + "!=\"" + Constants.MediaFilesDBEntry_FINGERPRINT_TYPE + "\""
					, null);
			cChkFile.moveToFirst();
			while (!cChkFile.isAfterLast()) {
				int colSRCPATH = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
				int colSRCFILE = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
				String filePath = cChkFile.getString(colSRCPATH);
				String fileName = cChkFile.getString(colSRCFILE);
				String fileNameFull = filePath + File.separator + fileName;
				String fileType = Utils.getFileType(fileNameFull);
				if (fileType==null)
					throw new Exception("Unknown filetype for "+fileNameFull);
				String fileMetadataHash = null;
				if ( fileType.equals(Constants.FILE_TYPE_PICTURE))
					fileMetadataHash = Utils.makeEXIFHash(fileNameFull);
				else if ( fileType.equals(Constants.FILE_TYPE_VIDEO))
					fileMetadataHash = Utils.makeFileFingerprint(fileNameFull);
				updateMetadataHashOfFile(filePath, fileName, fileMetadataHash);
				cChkFile.moveToNext();
			}
			cChkFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (--dbOpened == 0)
				db.close();
		}
	}
	boolean insertSrcFile(String srcMediaFileNameFull) {
		final String TAG="String";
		db = getWritableDatabase();
		File mediaFile = new File(srcMediaFileNameFull);
		String srcMediaFileName = mediaFile.getName();
		String srcMediaFilePath = mediaFile.getParent();
		Long srcMediaFileSize = mediaFile.length();
		ContentValues newMediaFile=null;
		boolean containsSrcFile=containsSrcFile(srcMediaFilePath,srcMediaFileName);
		boolean fileInserted=false;
		if (!containsSrcFile) {
			Long lastModified = mediaFile.lastModified();
			newMediaFile = new ContentValues();
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH, srcMediaFilePath);
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE, srcMediaFileName);
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS, lastModified);
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS, lastModified);
/*
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT, exifHash);
*/
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE, srcMediaFileSize);
			//TODO: ziskat z nastaveni
			boolean cksumEnabled = true;
			if (cksumEnabled){
				String fileType = Utils.getFileType(srcMediaFileNameFull);
				if (fileType == null)
					return false;
				String fileMetadataHash=null;
				if ( fileType.equals(Constants.FILE_TYPE_PICTURE))
					fileMetadataHash = Utils.makeEXIFHash(srcMediaFileNameFull);
				else if ( fileType.equals(Constants.FILE_TYPE_VIDEO))
					fileMetadataHash = Utils.makeFileFingerprint(srcMediaFileNameFull);

				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT, fileMetadataHash);
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE, Constants.MediaFilesDBEntry_FINGERPRINT_TYPE);
/*
					newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5, Utils.makeFileFingerprint(srcMediaFileNameFull));
					newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH, cksumMaxBytes);
*/
			}
			try {
				db.insert(Constants.MediaFilesDBEntry.TABLE_NAME, null, newMediaFile);
				fileInserted=true;
			} catch (Exception e) {
				Log.d("insertSrcFile","dbOpened: "+dbOpened);
				if (e.getMessage().contains("attempt to re-open an already-closed object")) {
					Log.d("insertSrcFile","Hacking 'already closed object' exception.");
					db = getWritableDatabase();
					db.insert(Constants.MediaFilesDBEntry.TABLE_NAME, null, newMediaFile);
				}
				else
					e.printStackTrace();
			}
		}
		if (--dbOpened == 0)
			db.close();
		return fileInserted;
	}

	void insertSrcFile(String[] srcMediaFileNameFullList) {
		for (String srcMediaFileNameFull : srcMediaFileNameFullList)
			insertSrcFile(srcMediaFileNameFull);
	}

	void updateTgtFileName(String srcMediaFilePath, String srcMediaFileName, String tgtMediaFileNameFull){
		updateTgtFileName(srcMediaFilePath, srcMediaFileName, tgtMediaFileNameFull,null);
	}
	void updateTgtFileName(String srcMediaFilePath, String srcMediaFileName, String tgtMediaFileNameFull, String fingerPrint){
		//update tgtMediaFileName where  srcMediaFilePath + srcMediaFileName
		ContentValues newvalues = new ContentValues();
		newvalues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_TGT, tgtMediaFileNameFull);
		if (fingerPrint != null) {
			newvalues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT, fingerPrint);
			newvalues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE, Constants.MediaFilesDBEntry_FINGERPRINT_TYPE);
		}
		String where = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
							   + " and "
							   + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\"";
		db.update(Constants.MediaFilesDBEntry.TABLE_NAME, newvalues, where, null);
	}
	void removeLocalPathWithUnsyncedPics(String srcMediaFilePath) {
		db = getWritableDatabase();
		String WHERE = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + " like \"" + srcMediaFilePath + "%\""
							   + " and ("
							   + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
							   + " or "
							   + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null )";

		try {
			Cursor c = db.rawQuery("select count(*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
										   + " where "
										   + WHERE
					, null);
			c.moveToFirst();
			Integer picsToRemove = c.getInt(0);
			c.close();
			Log.d("removePath","Removing "+ picsToRemove+" pictures from path "+srcMediaFilePath);
			db.delete(Constants.MediaFilesDBEntry.TABLE_NAME, WHERE, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (--dbOpened == 0)
			db.close();

	}
	boolean removeSrcFile(String srcMediaFileNameFull) {
		db = getWritableDatabase();
		boolean rc=true;
		File mediaFile = new File(srcMediaFileNameFull);
		String srcMediaFilePath = mediaFile.getParent();
		String srcMediaFileName = mediaFile.getName();
		try {
			db.delete(Constants.MediaFilesDBEntry.TABLE_NAME,
					Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
							+ " and "
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\""
					, null);
		} catch (Exception e) {
			e.printStackTrace();
			rc = false;
		}
		if (--dbOpened == 0)
			db.close();
		return rc;
	}

	boolean containsSrcFile(String srcMediaFileNameFull) {
		File mediaFile = new File(srcMediaFileNameFull);
		String srcMediaFilePath = mediaFile.getParent();
		String srcMediaFileName = mediaFile.getName();
		return containsSrcFile(srcMediaFilePath, srcMediaFileName);
	}
	boolean containsSrcFile(String srcMediaFilePath, String srcMediaFileName) {
		db = getReadableDatabase();
		Cursor cChkFile;
		if (db == null)
			return true;
		int count = 1;
		try {
			cChkFile = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
										   + " where "
										   + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
										   + " and "
										   + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\""
					, null);
			cChkFile.moveToFirst();
			count = cChkFile.getInt(0);
			cChkFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (--dbOpened == 0)
				db.close();
		}
		return (count > 0);
	}
	boolean containsSrcFileOfSize(Long fileSize){
		db = getReadableDatabase();
		Cursor cChkFile;
		if (db == null)
			return true;
		int count = 1;
		try {
			cChkFile = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
										   + " where "
										   + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE + "=\"" + fileSize + "\""
					, null);
			cChkFile.moveToFirst();
			count = cChkFile.getInt(0);
			cChkFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (--dbOpened == 0)
				db.close();
		}
		return (count > 0);
	}
	/*		String getSrcFileOfSizeAndHash(Long fileSize, String fileHash){
				db = getReadableDatabase();
				Cursor cChkFile;
				if (db == null)
					return null;
				int count = 1;
				String fileNameFull=new String();
				try {
					cChkFile = db.rawQuery("select * from " + Constants.MediaFilesDBEntry.TABLE_NAME
									+ " where "
									+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE + "=\"" + fileSize + "\""
									+ " and "
									+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5 + "=\"" + fileHash + "\""
							, null);
					cChkFile.moveToFirst();
					if (cChkFile.isAfterLast())
						return null;
					int colSRCPATH = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
					int colSRCFILE = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
					fileNameFull = cChkFile.getString(colSRCPATH)+File.separator+cChkFile.getString(colSRCFILE);
					cChkFile.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				finally {
					if (--dbOpened == 0)
						db.close();
				}
				return fileNameFull;
			}*/
	Cursor getUnsyncedFiles() {
		db = getWritableDatabase();
		Cursor cToSync;
/*
		SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
*/
		if (db == null)
			return null;
		try {
			cToSync = db.rawQuery("select rowid  _id  "
										  + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH
										  + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
										  + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS
										  + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT
										  + " from " + Constants.MediaFilesDBEntry.TABLE_NAME
										  + " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
										  + " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
										  + " order by " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " desc "
					, null);
			if (cToSync == null)
				return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return cToSync;
	}
	Cursor getAllFiles() {
		db = getWritableDatabase();
		Cursor cAllFiles;
//			SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
		if (db == null)
			return null;
		try {
			cAllFiles = db.rawQuery("select rowid  _id,* "
											+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
/*
											  + " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
											  + " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
*/
											  + " order by " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " desc "
					, null);
			if (cAllFiles == null)
				return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return cAllFiles;
	}
	void getUnsyncedFilesCloseDB() {
		if (--dbOpened == 0)
			db.close();
	}
	/*
	* Function return timestamp of the most recent synced pic
	* where all older pictures are also synced
	* */
	Long getConsistentSyncTimestamp() {
		db = getWritableDatabase();
		String QUERY = "select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS
							   + " from " + Constants.MediaFilesDBEntry.TABLE_NAME
							   + " where ( " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
							   + " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
							   + " ) and "
							   + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " > 0 "
							   + " order by " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " asc"
							   + " limit 1";
		Cursor cOldestUnsyncedImage = db.rawQuery(QUERY, null);
		if (cOldestUnsyncedImage == null)
			return 0L;
		cOldestUnsyncedImage.moveToFirst();
		if (cOldestUnsyncedImage.isAfterLast())
			return 0L;
		Long oldestUnsyncedImage = cOldestUnsyncedImage.getLong(0);
		cOldestUnsyncedImage.close();

		QUERY = "select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS
						+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
						+ " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + "  != \"\" "
						+ " and " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is not null "
						+ " and " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " > 0 "
						+ " and " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " <  " + oldestUnsyncedImage
						+ " order by timestamp desc limit 1";
		Cursor cConsistentSyncTimestamp = db.rawQuery(QUERY, null);
		if (cConsistentSyncTimestamp == null)
			return 0L;
		cConsistentSyncTimestamp.moveToFirst();
		if (cConsistentSyncTimestamp.isAfterLast())
			return oldestUnsyncedImage-1;
		Long ConsistentSyncTimestamp = cConsistentSyncTimestamp.getLong(0);
		cConsistentSyncTimestamp.close();
		if (--dbOpened == 0)
			db.close();
		return ConsistentSyncTimestamp;
	}
	void getDBStatistics() {
		if (callBack == null) {
			return;
		}
			db = getWritableDatabase();
		Cursor c = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME, null);
		c.moveToFirst();
		int mediaFilesCountTotal = c.getInt(0);
		c.close();

		Cursor cToSync = db.rawQuery("select count (*) from "
											 + Constants.MediaFilesDBEntry.TABLE_NAME
											 + " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
											 + " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
				, null);
		cToSync.moveToFirst();
		int mediaFilesCountToSync = cToSync.getInt(0);
		cToSync.close();

/*
			Cursor cHighestScannedTimestamp = db.rawQuery("select max("
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + ")"
							+ " from "
							+ Constants.MediaFilesDBEntry.TABLE_NAME
					, null);
			cHighestScannedTimestamp.moveToFirst();
*/
/*
			lastScannedImageTimestamp = cHighestScannedTimestamp.getLong(0);
*//*

			cHighestScannedTimestamp.close();

*/
		if (callBack != null){
			callBack.onDBStatisticsChange("mediaFilesCountTotal",mediaFilesCountTotal);
			callBack.onDBStatisticsChange("mediaFilesCountToSync",mediaFilesCountToSync);
		}

		if (--dbOpened == 0)
			db.close();
	}

	public void onCreate(SQLiteDatabase db) {
		Log.d("SQL","Creating db "+DATABASE_NAME);
		db.execSQL("drop table if exists " + Constants.MediaFilesDBEntry.TABLE_NAME);
		final String TABLE_CREATE = "create table " + Constants.MediaFilesDBEntry.TABLE_NAME + "("
											+ Constants.MediaFilesDBEntry.TABLE_NAME
											+ "_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + " TEXT, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + " TEXT, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_MIN_FILE + " TEXT, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " INTEGER, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " TEXT, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT + " TEXT, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE + " INTEGER, "
											+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE + " INTEGER, "
											+ "CONSTRAINT srcFile_unique UNIQUE (" + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH
											+ "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
											+ "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + ")"
											+ ")";
		db.execSQL(TABLE_CREATE);

	}

	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
/*
			db.execSQL("backup data");
			db.execSQL("delete");
*/
		onCreate(db);
/*
			db.execSQL("restore data");
*/
	}

	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
		synchronized (this) {
			dbOpened++;
		}
//			Log.d("onOpen "+db.toString(),Integer.toString(dbOpened));
	}

	public void openDBRW() {
		synchronized (this) {
			db = getWritableDatabase();
			dbOpened++;
		}
	}

	public void openDBRO() {
		db = getReadableDatabase();
	}

	public void closeDB() {
		close();
	}
}
