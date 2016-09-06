package com.rbk.testapp;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SyncedFoldersList extends ListActivity {

	private final Context MyContext = this;
	private static SharedPreferences prefs;
	private static SharedPreferences.Editor editor;
	private List folderList;
	private static ListView mediaFolderListView;
	private static Set<String> prefFolderList;
	private static ProgressBar progressBar;
	private static boolean mMessageReceiverRegistered = false;

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {

				prefFolderList = prefs.getStringSet("prefFolderList", null);
/*
				if (prefFolderList == null)
					prefFolderList = new HashSet<String>();
				else
					prefFolderList.clear();
*/
				String [] mediaFolderList = bundle.getStringArray("MediaFoldersList");
				if ((mediaFolderList != null) && (prefFolderList != null))
					Collections.addAll(prefFolderList, mediaFolderList);
				if (editor == null)
					editor = prefs.edit();
				editor.putStringSet("prefFolderList", prefFolderList);
				editor.commit();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						populateList();
					}
				});
			}
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_synced_folders_list);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefFolderList = new HashSet<String>();
		Intent myIntent = getIntent();
		if (myIntent.hasExtra("action")) {
			if (myIntent.getStringExtra("action").equals(PicSync.ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS)) {
				progressBar.setVisibility(ProgressBar.VISIBLE);
				callForAddMediaFoldersToSettings();
			}
		} else
			populateList();


/*
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
*/

		FloatingActionButton fabAddNewFolder = (FloatingActionButton) findViewById(R.id.fabAddNewFolder);
		if (fabAddNewFolder != null) {
			fabAddNewFolder.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intentFolderPicker = new Intent(MyContext, FolderPicker.class);
					intentFolderPicker.setAction("android.intent.action.VIEW");
					startActivityForResult(intentFolderPicker, 2000);
	/*
					Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
							.setAction("Action", null).show();
	*/
				}
			});
		}
/*
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
*/
	}

	private void callForAddMediaFoldersToSettings() {
		Intent intentPicSync = new Intent(this, PicSync.class);
		if (!mMessageReceiverRegistered) {
			LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(
					mMessageReceiver, new IntentFilter("getMediaFoldersList"));
			mMessageReceiverRegistered = true;
		}
		intentPicSync.setAction(PicSync.ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS);

		startService(intentPicSync);
	}

	@Override
	protected void onActivityResult(int reqCode, int resCode, Intent data) {
		if ((reqCode == 2000) & (resCode == 0) & (data != null)) {
			prefFolderList.clear();
			prefFolderList = prefs.getStringSet("prefFolderList", null);
			prefFolderList.add(data.getStringExtra("path"));

			SharedPreferences.Editor editor = prefs.edit();
			editor.putStringSet("prefFolderList", prefFolderList);
			editor.commit();
			finish();
			startActivity(getIntent());
		}
	}

	private void populateList() {
/*		runOnUiThread(new Runnable() {
			@Override
			public void run() {*/
		progressBar.setVisibility(ProgressBar.INVISIBLE);
		Set<String> folderListArray = prefs.getStringSet("prefFolderList",null);
		final List folderList = new ArrayList();
		folderList.addAll(folderListArray);
		Collections.sort(folderList);
		final ArrayAdapter adapter = new ArrayAdapter(MyContext, android.R.layout.simple_list_item_1, android.R.id.text1, folderList);
		setListAdapter(adapter);
		mediaFolderListView = getListView();
		mediaFolderListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
				final String selectedValue = (String) mediaFolderListView.getItemAtPosition(position);
				AlertDialog.Builder confirmDeletionDialog = new AlertDialog.Builder(MyContext);
				confirmDeletionDialog.setTitle("Delete?");
				confirmDeletionDialog.setMessage(selectedValue);
				confirmDeletionDialog.setNegativeButton("Delete", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						folderList.remove(position);
						adapter.notifyDataSetChanged();
						adapter.notifyDataSetInvalidated();
						SharedPreferences.Editor editor = prefs.edit();
						editor.putStringSet("prefFolderList",new HashSet<String>(folderList));
						editor.commit();
					}
				});
				confirmDeletionDialog.setPositiveButton("Keep",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {

					}
				});
				confirmDeletionDialog.show();
				return false;
			}
		});
/*
			}
		});
*/
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mMessageReceiverRegistered)
			LocalBroadcastManager.getInstance(getBaseContext()).unregisterReceiver(mMessageReceiver);
		mMessageReceiverRegistered = false;
	}
}
