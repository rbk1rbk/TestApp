package com.rbk.testapp;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

public class MediaBrowser extends AppCompatActivity {

	private Context myContext = null;
	private static MediaFilesDB MediaFilesDB;
	private static Cursor cAllFiles;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		myContext = this;
		setContentView(R.layout.activity_media_browser);

		GridView gridview = (GridView) findViewById(R.id.gridviewMediaBrowser);
		MediaFilesDB = new MediaFilesDB(myContext);
		cAllFiles = MediaFilesDB.getUnsyncedFiles(); //getAllFiles();
		cAllFiles.moveToFirst();
		if (cAllFiles.isAfterLast())
			return;
		gridview.setAdapter(new ImageAdapter(this, cAllFiles, 0));

		gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v,
									int position, long id) {
				int currPosition = cAllFiles.getPosition();
				cAllFiles.moveToPosition(position);
				int colSRCPATH = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
				int colSRCFILE = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
				String filePath, fileName, fileFullPath, fileNameTGT;
				filePath = cAllFiles.getString(colSRCPATH);
				fileName = cAllFiles.getString(colSRCFILE);
				fileFullPath=filePath+"/"+fileName;
				cAllFiles.moveToPosition(currPosition);
				Toast.makeText(MediaBrowser.this, fileFullPath,
						Toast.LENGTH_SHORT).show();
			}
		});

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (cAllFiles != null)
			cAllFiles.close();
	}
}
