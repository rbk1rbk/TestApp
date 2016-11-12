package com.rbk.testapp;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WiFiPicker extends ListActivity {
	WifiWatchdogService wifiWatchdogServiceGW;
	boolean WifiWatchdogServiceBound = false;

	private ServiceConnection wifiWatchdogServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			WifiWatchdogService.LocalBinder binder = (WifiWatchdogService.LocalBinder) service;
			wifiWatchdogServiceGW = binder.getService();
			WifiWatchdogServiceBound = true;
			String[] wifiList;
			Set<String> wifiSet = new HashSet<String>();
			if (wifiWatchdogServiceGW != null && WifiWatchdogServiceBound) {
/*
				wifiWatchdogServiceGW.startScan();
*/
				wifiList = wifiWatchdogServiceGW.getWifiList();
				String currentSSID=wifiWatchdogServiceGW.getCurrentSsid();
				if (currentSSID != null)
					wifiSet.add(wifiWatchdogServiceGW.getCurrentSsid());
				for (String wifiName : wifiList) {
					wifiSet.add(wifiName);
				}
/*
		List values = new ArrayList();
		Collections.addAll(values, wifiList);
*/
				ArrayAdapter adapter = new ArrayAdapter(WiFiPicker.this, android.R.layout.simple_list_item_1, android.R.id.text1, wifiSet.toArray(new String[wifiSet.size()]));
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

/*
		Intent wifiWatchdogServiceIntent = new Intent(this, WifiWatchdogService.class);
		boolean bindOK = bindService(wifiWatchdogServiceIntent, wifiWatchdogServiceConnection, Context.BIND_AUTO_CREATE);
		startService(wifiWatchdogServiceIntent);
		Log.d("Wifi picker bindService", new Boolean(bindOK).toString());*/
		WifiManager wifiManager;
		wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		List<WifiConfiguration> wifiList = wifiManager.getConfiguredNetworks();
		List<String> wifiSSIDList =new ArrayList<String>();
		if (wifiList != null) {
			wifiSSIDList.clear();
			for (WifiConfiguration wifiItem : wifiList) {
				wifiSSIDList.add(wifiItem.SSID.replaceAll("^\"|\"$", ""));
			}
		}
		if (wifiSSIDList.size() == 0) {
			wifiSSIDList.add("No network in range. Is Wifi on?");
			return;
		}
		ArrayAdapter adapter = new ArrayAdapter(WiFiPicker.this, android.R.layout.simple_list_item_1, android.R.id.text1, wifiSSIDList);
		setListAdapter(adapter);
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

	@Override
	protected void onDestroy() {
		super.onDestroy();
/*
		unbindService(wifiWatchdogServiceConnection);
*/
	}
}
