package com.rbk.testapp;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class WiFiPicker extends ListActivity {
	WifiWatchdogService wifiWatchdogServiceService;
	boolean WifiWatchdogServiceBound = false;

	private ServiceConnection wifiWatchdogServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			WifiWatchdogService.LocalBinder binder = (WifiWatchdogService.LocalBinder) service;
			wifiWatchdogServiceService = binder.getService();
			WifiWatchdogServiceBound = true;
			String[] wifiList;
			if (wifiWatchdogServiceService!=null && WifiWatchdogServiceBound) {
				wifiList = wifiWatchdogServiceService.getWifiList();
/*
		List values = new ArrayList();
		Collections.addAll(values, wifiList);
*/
				ArrayAdapter adapter = new ArrayAdapter(WiFiPicker.this, android.R.layout.simple_list_item_1, android.R.id.text1, wifiList);
				setListAdapter(adapter);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			WifiWatchdogServiceBound = false;
		}
	};

	@Override
	protected void onStart() {
		super.onStart();

		Intent wifiWatchdogServiceIntent = new Intent(this, WifiWatchdogService.class);
		bindService(wifiWatchdogServiceIntent, wifiWatchdogServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wifipicker);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Intent returnIntent = new Intent();
		returnIntent.putExtra("wifiName",(String)getListAdapter().getItem(position));
		setResult(0, returnIntent);
		finish();
	}
}
