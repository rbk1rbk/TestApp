package com.rbk.testapp;

import android.provider.BaseColumns;

/**
 * Created by biel on 19. 10. 2016.
 */

public final class Constants {
	private Constants() {
	}

	;

	public static class MediaFilesDBEntry implements BaseColumns {
		public static final String TABLE_NAME = "MediaFiles";
		public static final String COLUMN_NAME_SRC_PATH = "srcFilePath";
		public static final String COLUMN_NAME_SRC_FILE = "srcFileName";
		public static final String COLUMN_NAME_SRC_MD5 = "srcFileMD5";
		public static final String COLUMN_NAME_SRC_MD5_LENGTH = "srcFileMD5Length";
		public static final String COLUMN_NAME_MIN_FILE = "minFileName";
		public static final String COLUMN_NAME_TGT = "tgtFileNameFull";
		public static final String COLUMN_NAME_SRC_TS = "timestamp";
		public static final String COLUMN_NAME_FINGERPRINT = "srcFileFingerprint";
		public static final String COLUMN_NAME_FINGERPRINT_TYPE = "fingerprintType";
/*
		public static final String COLUMN_NAME_FILE_HASH = "srcFileHash";
		public static final String COLUMN_NAME_FILE_HASH_SIZE = "srcFileHashSize";
*/
		public static final String COLUMN_NAME_SRC_FILESIZE = "srcFileSize";
	}
	public static class RemoteFilesDBEntry implements BaseColumns {
		public static final String TABLE_NAME = "RemoteFiles";
		public static final String COLUMN_NAME_PATH = "filePath";
		public static final String COLUMN_NAME_FILE = "fileName";
		public static final String COLUMN_NAME_FILESIZE = "fileSize";
		public static final String COLUMN_NAME_TS = "fileTS";
		public static final String COLUMN_NAME_FINGERPRINT = "fileFingerprint";
	}
	public static final Integer MediaFilesDBEntry_FINGERPRINT_TYPE = 2;
	public static int cksumMaxBytes = 256 * 1024;

	public static final String FILE_TYPE_PICTURE="Picture";
	public static final String FILE_TYPE_VIDEO="Video";

}
