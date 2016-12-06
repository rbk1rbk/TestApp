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
		public static final String COLUMN_NAME_MIN_FILE = "minFileName";
		public static final String COLUMN_NAME_TGT = "tgtFileNameFull";
		public static final String COLUMN_NAME_SRC_TS = "timestamp";
	}


}
