package com.rbk.testapp;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;

public class CIFSbrowser extends ListActivity {

	private int currSmbLevel;
	private String currDir;
	private List currDirContent;
	private String smbuser, smbpasswd, smbservername, smbshare;
	private static NtlmPasswordAuthentication auth = null;
	private static boolean PicSyncBound = false;
	private final Context contextCIFS = this;
	private static Toolbar mActionBarToolbar;
	private static ProgressBar progressBar;
	private static String servername = "";
	private static String sharename = "";

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				if (currDirContent == null)
					currDirContent = new ArrayList();
				else
					currDirContent.clear();
				Collections.addAll(currDirContent, bundle.getStringArray("cifsList"));
				currSmbLevel = bundle.getInt("smbType");
				currDir = bundle.getString("smbCanonicalPath");
				servername = bundle.getString("servername");
				sharename = bundle.getString("sharename");
				dirListToCIFSListView();
			}
		}

	};

	public void dirListToCIFSListView() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ArrayAdapter adapter = new ArrayAdapter(contextCIFS, android.R.layout.simple_list_item_1, android.R.id.text1, currDirContent);
				setListAdapter(adapter);
				mActionBarToolbar.setTitle(currDir);
				mActionBarToolbar.setSubtitle(currDir);
				progressBar.setVisibility(ProgressBar.INVISIBLE);
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		Intent intentPicSync = new Intent(this, PicSync.class);
		LocalBroadcastManager.getInstance(getBaseContext()).registerReceiver(
				mMessageReceiver, new IntentFilter("CIFSList"));
		intentPicSync.setAction(PicSync.ACTION_BROWSE_CIFS);
		intentPicSync.putExtra("path", currDir);
		startService(intentPicSync);

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cifsbrowser);
		mActionBarToolbar = (Toolbar) findViewById(R.id.toolbar);

		currSmbLevel = 0;
		currDir = "smb://";
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		smbuser = settings.getString("prefsSMBUSER", "");
		smbpasswd = settings.getString("prefsSMBPWD", "");
		smbservername = settings.getString("prefsSMBSRV", "");
		smbshare = settings.getString("prefsTGTURI", "");
		if (smbshare.startsWith("smb://"))
			currDir = smbshare;
		else
			currDir = "smb://" + smbservername + "/";
		mActionBarToolbar.setTitle(currDir);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
        String filename = (String) getListAdapter().getItem(position);
		if (currSmbLevel == SmbFile.TYPE_WORKGROUP || currSmbLevel == SmbFile.TYPE_SERVER)
			currDir = "smb://" + filename + File.separator;
		else {
			if (currDir.endsWith(File.separator)) {
				currDir = currDir + filename;
			} else {
				currDir = currDir + File.separator + filename;
			}
		}
		Intent intentPicSync = new Intent(this, PicSync.class);
		intentPicSync.setAction(PicSync.ACTION_BROWSE_CIFS);
		intentPicSync.putExtra("path", currDir);
		startService(intentPicSync);
		mActionBarToolbar.setTitle("Discovering...");
		progressBar.setVisibility(ProgressBar.VISIBLE);
	}

    public void onBtnSelectClick(View v) {
        Intent returnIntent = new Intent();
        try {
            returnIntent.putExtra("path", new SmbFile(currDir).getCanonicalPath());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
		returnIntent.putExtra("servername", servername);
		returnIntent.putExtra("sharename", sharename);
		setResult(0, returnIntent);
        finish();
    }
}
