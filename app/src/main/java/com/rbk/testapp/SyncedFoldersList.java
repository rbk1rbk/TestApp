package com.rbk.testapp;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SyncedFoldersList extends AppCompatActivity {

	private final Context MyContext = this;
	private static SharedPreferences prefs;
	private static SharedPreferences.Editor editor;
	private List folderList;
	private static ListView mediaFolderListView;
	private static Set<String> prefFolderList;
	private static ProgressBar progressBar;
	private static boolean mMessageReceiverRegistered = false;
	private static final int READ_REQUEST_CODE = 42;


	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
			final String action = intent.getAction();
			if (action.toString().equals("getMediaFoldersList")) {
				if (bundle != null) {
					prefFolderList = prefs.getStringSet("prefFolderList", null);
					if (prefFolderList == null)
						prefFolderList = new HashSet<String>();
					String[] mediaFolderList = bundle.getStringArray("MediaFoldersList");
					if ((mediaFolderList != null) && (prefFolderList != null))
						Collections.addAll(prefFolderList, mediaFolderList);
					if (editor == null)
						editor = prefs.edit();
					editor.putStringSet("prefFolderList", prefFolderList);
					editor.commit();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(MyContext, "Scanning done", Toast.LENGTH_LONG).show();
							populateList();
						}
					});
				}
			}
			if (action.equals(PicSync.ACTION_GET_STORAGE_PATHS_EACCESS)) {
				if (bundle != null) {
					String[] storagePathsEACCESS = bundle.getStringArray("storagePathsEACCESS");
					for (String storagePathEACCESS : storagePathsEACCESS){
						Log.d("storagePathsEACCESS",storagePathEACCESS);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
							requestPersistentPermission(null);
						}
					}
				}
			}
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_synced_folders_list);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		mediaFolderListView = (ListView) findViewById(R.id.syncedFolderList);

		if (!mMessageReceiverRegistered) {
			LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(
					mMessageReceiver, new IntentFilter(PicSync.ACTION_GET_STORAGE_PATHS_EACCESS));
			LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(
					mMessageReceiver, new IntentFilter("getMediaFoldersList"));
			mMessageReceiverRegistered = true;
		}

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Intent myIntent = getIntent();
		populateList();

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
	}

	private void callForPathsWithAccessError() {
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		Intent intentPicSync = new Intent(this, PicSync.class);
		intentPicSync.setAction(PicSync.ACTION_GET_STORAGE_PATHS_EACCESS);
		startService(intentPicSync);
	}

	private void callForAddMediaFoldersToSettings() {
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		Intent intentPicSync = new Intent(this, PicSync.class);
		intentPicSync.setAction(PicSync.ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS);
		startService(intentPicSync);
	}

	@Override
	protected void onActivityResult(int reqCode, int resCode, Intent data) {
		if ((reqCode == 2000) & (resCode == 0) & (data != null)) {
			prefFolderList = prefs.getStringSet("prefFolderList", null);
			if (prefFolderList == null)
				prefFolderList = new HashSet<String>();
			prefFolderList.add(data.getStringExtra("path"));
			SharedPreferences.Editor editor = prefs.edit();
			editor.putStringSet("prefFolderList", prefFolderList);
			editor.commit();
			finish();
			startActivity(getIntent());
		}
		if (reqCode == READ_REQUEST_CODE && data != null) {
			// && resCode == Activity.RESULT_OK
			// The document selected by the user won't be returned in the intent.
			// Instead, a URI to that document will be contained in the return intent
			// provided to this method as a parameter.
			// Pull that URI using resultData.getData().
			Uri uri = null;
			uri = data.getData();
/*
			int takeFlags = 0;
			takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION) & data.getFlags();
*/
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			}
			Log.i("onActivityResult", "Uri: " + uri.toString());
		}

	}

	private void populateList() {
		progressBar.setVisibility(ProgressBar.INVISIBLE);
		Set<String> folderListArray = prefs.getStringSet("prefFolderList",null);
		final List folderList = new ArrayList();
		if (folderListArray != null) {
			folderList.addAll(folderListArray);
			Collections.sort(folderList);
		}
		final ArrayAdapter adapter = new ArrayAdapter(MyContext, android.R.layout.simple_list_item_1, android.R.id.text1, folderList);
		mediaFolderListView.setAdapter(adapter);
		mediaFolderListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
				final String selectedValue = (String) mediaFolderListView.getItemAtPosition(position);
				AlertDialog.Builder confirmDeletionDialog = new AlertDialog.Builder(MyContext);
				confirmDeletionDialog.setTitle("Delete?");
				confirmDeletionDialog.setMessage(selectedValue);
				confirmDeletionDialog.setNegativeButton("Delete", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String intentParamLocalFolder=folderList.get(position).toString();
						folderList.remove(position);
						//TODO: vymaz z DB vsetky nezosynchronizovane obrazky z tejto cesty
						Intent intentPicSync = new Intent(MyContext, PicSync.class);
						intentPicSync.setAction(PicSync.ACTION_DEL_MEDIA_FOLDERS_W_PICS);
						intentPicSync.putExtra(PicSync.INTENT_PARAM_LOCAL_FOLDER,intentParamLocalFolder);
						startService(intentPicSync);

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
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_synced_folders, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_add_folder:
				Intent intentFolderPicker = new Intent(MyContext, FolderPicker.class);
				intentFolderPicker.setAction("android.intent.action.VIEW");
				startActivityForResult(intentFolderPicker, 2000);
				return true;

			case R.id.action_detect_folders:
				progressBar.setVisibility(ProgressBar.VISIBLE);
				callForPathsWithAccessError();
				callForAddMediaFoldersToSettings();
				progressBar.setVisibility(ProgressBar.INVISIBLE);
				return true;

			default:
				// If we got here, the user's action was not recognized.
				// Invoke the superclass to handle it.
				return super.onOptionsItemSelected(item);

		}
	}

	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
	private void requestPersistentPermission(String pathToAskPermissionFor){
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
/*
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("image/*");
*/
		startActivityForResult(intent, READ_REQUEST_CODE);

	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mMessageReceiverRegistered)
			LocalBroadcastManager.getInstance(getBaseContext()).unregisterReceiver(mMessageReceiver);
		mMessageReceiverRegistered = false;
	}
}
