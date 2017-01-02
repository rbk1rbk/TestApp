package com.rbk.testapp;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/*
public class FolderPicker extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folderPicker);
    }

}
*/

public class FolderPicker extends ListActivity {

    private String path;
    private final Context MyContext = this;
	private static boolean mMessageReceiverRegistered = false;
	private static long cmdTimestamp=0;

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("FolderPicker onReceive",intent.getAction());
			Bundle bundle = intent.getExtras();
			long retCmdTimestamp = intent.getLongExtra("cmdTimestamp",0);
			if ( retCmdTimestamp != cmdTimestamp) {
				Log.d("FolderPicker onReceive","Received answer with different timestamp than current request");
				return;
			}
			List values = new ArrayList();
			if (bundle != null) {
				Collections.addAll(values, bundle.getStringArray("storagePaths"));
				Collections.sort(values);
				final ArrayAdapter adapter = new ArrayAdapter(MyContext, android.R.layout.simple_list_item_1, android.R.id.text1, values);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setListAdapter(adapter);
					}
				});
			}
		}

	};

	private void dirListToFOLDERListView() {
		List values = new ArrayList();
		File dir = null;
		String pathCanonical;
		if (TextUtils.equals(path, "getStoragePaths")) {
			path = "";
			Intent intentPicSync = new Intent(this, PicSync.class);
			intentPicSync.setAction(PicSync.ACTION_GET_STORAGE_PATHS);
			cmdTimestamp=new Date().getTime();
			startService(intentPicSync);
		}
		try {
			pathCanonical = new File(path).getCanonicalPath();
		} catch (IOException e) {
			e.printStackTrace();
			values.add("../");
			return;
		}
		if (!pathCanonical.endsWith(File.separator))
			pathCanonical += File.separator;
		dir = new File(pathCanonical);
		if (!dir.canRead()) {
			setTitle(getTitle() + " (inaccessible)");
        }
        String[] list = dir.list();
        if (list != null) {
            for (String file : list) {
                File file2verify = new File(path + "/" + file);
                boolean canRead = file2verify.canExecute();
                boolean isDirectory = file2verify.isDirectory();
                boolean isFile = file2verify.isFile();
                if ((!file.startsWith(".")) && (canRead) && (isDirectory)) {
                    values.add(file);
                }
            }
        }

        try {
			if (!TextUtils.equals(new File(path).getCanonicalPath(), new File(path + "/../").getCanonicalPath()))
				values.add("../");
		} catch (IOException e) {
            e.printStackTrace();
        }
        Collections.sort(values);

        // Put the data into the list
        ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, values);
        setListAdapter(adapter);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
/*
		// Funguje, ale chce to tuning layoutu a namiesto FAB pouzit OK a CANCEL
		// Podobne, ako v SyncedFolderList populateList()
        setTheme(android.R.style.Theme_Dialog);
*/
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_picker);

        // Use the current directory as title
		//Todo: implementovat korenovy zoznam adresarov z PicSync.getStoragePaths()
		path = "getStoragePaths";
		if (getIntent().hasExtra("path")) {
            path = getIntent().getStringExtra("path");
        }
        setTitle(path);

		LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(
				mMessageReceiver, new IntentFilter("storagePaths"));
		mMessageReceiverRegistered = true;

        FloatingActionButton fabAddNewFolder = (FloatingActionButton) findViewById(R.id.fabAddNewFolder);
        if (fabAddNewFolder != null) {
            fabAddNewFolder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("path", path);
                    setResult(0, returnIntent);
                    finish();
                }
            });
        }

        dirListToFOLDERListView();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String filename = (String) getListAdapter().getItem(position);
        if (path.endsWith(File.separator)) {
            filename = path + filename;
        } else {
            filename = path + File.separator + filename;
        }
        if (new File(filename).isDirectory()) {
/*
            Intent intent = new Intent(this, FolderPicker.class);
            intent.putExtra("path", filename);
            startActivity(intent);
*/
            path = filename;
            dirListToFOLDERListView();
        } else {
            Toast.makeText(this, filename + " is not a directory", Toast.LENGTH_LONG).show();
        }
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mMessageReceiverRegistered)
			LocalBroadcastManager.getInstance(getBaseContext()).unregisterReceiver(mMessageReceiver);
		mMessageReceiverRegistered = false;
	}
}
