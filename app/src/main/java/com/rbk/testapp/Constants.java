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
		public static final String COLUMN_NAME_SRC = "srcFile";
		public static final String COLUMN_NAME_TGT = "tgtFile";
		public static final String COLUMN_NAME_TS = "timestamp";
		public static final String COLUMN_NAME_SYNC = "synced";
	}


}
