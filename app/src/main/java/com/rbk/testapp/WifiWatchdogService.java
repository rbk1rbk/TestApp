package com.rbk.testapp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class WifiWatchdogService extends Service {
	private static WifiChangeReceiver WCR;
	private static IntentFilter IF;
	private static IBinder myBinder;
	private static ConnectivityManager cm;
	private boolean isconnected;
	private int connType;
	private WifiManager wifiManager;
	enum eactionForPicSync {GO,STOP};
	private eactionForPicSync actionForPicSync=eactionForPicSync.STOP;

	public WifiWatchdogService() {
	}

	@Override
	//The system calls this method when another component, such as an activity,
	//requests that the service be started, by calling startService()
	public int onStartCommand(Intent intent, int flags, int startId) {
//        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
		myBinder = new LocalBinder();
		return START_STICKY;
	}

	@Override
	//The system calls this method when the service is first created,
	// to perform one-time setup procedures (before it calls either onStartCommand() or onBind()).
	// If the service is already running, this method is not called.
	public void onCreate() {
		WCR = new WifiChangeReceiver();
		IF = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
		registerReceiver(WCR, IF);
		Log.i("WifiChangeReceiver", "onCreate: Receiver registered");

		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		Log.i("WifiChangeReceiver", "onCreate: Background thread started");
		cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	public class LocalBinder extends Binder {
		WifiWatchdogService getService() {
			return WifiWatchdogService.this;
		}
	}
	@Override
	public IBinder onBind(Intent intent) {
		return myBinder;
	}
	@Override
	public void onDestroy() {
		unregisterReceiver(WCR);
		Log.i("WifiChangeReceiver", "onDestroy: Receiver unregistered");
		Log.i("WifiChangeReceiver", "onDestroy: Service done");
	}
	public String[] getWifiList(){
		List<String> wifiSSIDList = new ArrayList<String>();

		if ( isconnected && connType == ConnectivityManager.TYPE_WIFI) {
/*
			final WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
*/
			if (!wifiManager.isWifiEnabled())
				return null;
/*
				return new String[]{"Wifi disabled"};
*/
			wifiManager.startScan();
			List<ScanResult> wifiList = wifiManager.getScanResults();
			for (ScanResult wifiItem : wifiList){
				wifiSSIDList.add(wifiItem.SSID);
			}
			if (wifiSSIDList.size() == 0){
				wifiSSIDList.add("No network in range");
			}
		}
		return wifiSSIDList.toArray(new String[wifiSSIDList.size()]);
	}
	public String getCurrentSsid() {
		String ssid;
		if ( isconnected && connType == ConnectivityManager.TYPE_WIFI) {
			final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
			if (connectionInfo != null) {
				ssid = connectionInfo.getSSID();
				return ssid.replaceAll("^\"|\"$","");
			}
		}
		return null;
	}
	public void wifiChangeTrigger(){
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		if (networkInfo == null) {
			isconnected = false;
			return;
		}
		isconnected =  networkInfo.isConnected();
		connType = networkInfo.getType();
		if (wifiManager == null)
			wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
/*
		if (!wifiManager.isWifiEnabled())
		}
*/
		actionForPicSync=eactionForPicSync.STOP;
		if ( isconnected && connType == ConnectivityManager.TYPE_WIFI) {
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			String prefsWifi = settings.getString("pref_homewifissid", "NoWifiWhatsoever");

			if (getCurrentSsid().equals(prefsWifi)) {
				actionForPicSync = eactionForPicSync.GO;
			}
		}

		Intent PicSyncIntent = new Intent(this, PicSync.class);
		if (actionForPicSync == eactionForPicSync.GO)
			PicSyncIntent.setAction(PicSync.ACTION_STOP_SYNC);
		else
			PicSyncIntent.setAction(PicSync.ACTION_START_SYNC);
		startService(PicSyncIntent);
	}
}
