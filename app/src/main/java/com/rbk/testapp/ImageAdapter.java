package com.rbk.testapp;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.File;

/**
 * Created by biel on 13.3.2017.
 */

public class ImageAdapter extends CursorAdapter {
	private static Context mContext;
	private static Cursor mCursor;
	private static int colSRCPATH;
	private static int colSRCFILE;
	private static int colTGTFILE;

	public ImageAdapter(Context ctx, Cursor cursor, int flags) {
		super(ctx, cursor, 0);
		mContext = ctx;
		mCursor = cursor;
		colSRCPATH = mCursor.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
		colSRCFILE = mCursor.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
		colTGTFILE = mCursor.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_TGT);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
//      return LayoutInflater.from(context).inflate(R.layout.activity_media_browser, parent, false);
		return new ImageView(context);
	}


	public int getCount() {
		return mCursor.getCount();
	}

	public Object getItem(int position) {
		return null;
	}

	public long getItemId(int position) {
		return 0;
	}

	public void bindView(View convertView, Context context, Cursor cursor) {
		ImageView imageView;
		int picsize=240;
		imageView = (ImageView) convertView;
		String filePath, fileName, fileFullPath, fileNameTGT;
		filePath = mCursor.getString(colSRCPATH);
		fileName = mCursor.getString(colSRCFILE);
		fileNameTGT = mCursor.getString(colTGTFILE);
		fileFullPath = filePath + File.separator + fileName;
		imageView.setLayoutParams(new GridView.LayoutParams(picsize, picsize));
		imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
		imageView.setPadding(8, 8, 8, 8);
		imageView.setCropToPadding(true);
		if (fileNameTGT == null || fileNameTGT.equals(""))
			imageView.setBackgroundColor(android.R.color.holo_green_light);
		else
			imageView.setBackgroundColor(android.R.color.holo_red_light);
//		imageView.setImageURI(Uri.fromFile(new File(fileFullPath)));
		BitmapFactory.Options bo = new BitmapFactory.Options();
		bo.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(fileFullPath,bo);
		int w = bo.outWidth;
		int h = bo.outHeight;
		String imageType = bo.outMimeType;
		bo.inSampleSize=1;
		while (w/bo.inSampleSize>picsize || h/bo.inSampleSize>picsize)
			bo.inSampleSize*=2;
		Log.d("bindView: showing ",fileName);
		bo.inJustDecodeBounds = false;
		Bitmap bf=BitmapFactory.decodeFile(fileFullPath,bo);
		imageView.setImageBitmap(bf);
	}
}
