package com.rbk.testapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

public class PicSyncScheduler extends Service {
	static private boolean onUnmeteredNetwork=false;
	static private boolean onHomeWifi=false;
	static private boolean isconnected=false;
	static private boolean isChargingCondition=false;
	static private boolean isCharging=false;
	static private boolean chargerEventReceiverRegistered=false;
	static private boolean changeInMediaFolders=false;
	static private int nextWakeUpTime=0;
	int callBack=0;

	private final Context myContext=this;

	private static SharedPreferences settings;

	/*
	* This class provides a Watchdog type of service, that calls back
	* a sync action when user is on a home network.
	* If user recently connects to the home network, it lets the sync to scan for changes.
	* While on the home network, it waits for a new picture to sync.
	* If the server is not available, and while on home network, and with WoL off or not working,
	* it sets an alarm.
	*
	* */
	private static boolean wifiChangeReceiverRegistered = false;
	private static ConnectivityManager cm;

	SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
					if (TextUtils.equals(key,"pref_switch_charging_condition")) {
						handleChargingConditions();
					}
					if (TextUtils.equals(key,"pref_homewifissid")) {
						wifiChangeReceiver.onReceive(myContext,new Intent("android.net.conn.CONNECTIVITY_CHANGE"));
					}
				}
			};

	private BroadcastReceiver chargerPluggedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			isCharging=true;
			Log.d("PicSyncScheduler","Charger plugged in.");
			evaluateTheNeedOfSync();
		}
	};

	private BroadcastReceiver chargerUnpluggedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			isCharging=false;
			Log.d("PicSyncScheduler","Charger unplugged.");
			evaluateTheNeedOfSync();
		}
	};

	private BroadcastReceiver wifiChangeReceiver = new BroadcastReceiver() {
		int connType;
		WifiManager wifiManager=null;

		public String getCurrentSsid() {
			String ssid;
			if (isconnected && connType == ConnectivityManager.TYPE_WIFI) {
				final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
				if (connectionInfo != null) {
					ssid = connectionInfo.getSSID();
					return ssid.replaceAll("^\"|\"$", "");
				}
			}
			return null;
		}
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("PicSyncScheduler","wifiChangeReceiver onReceive called");
			if (cm==null)
				cm = (ConnectivityManager) myContext.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkInfo = cm.getActiveNetworkInfo();
			if (networkInfo == null) {
				isconnected = false;
			} else {
				isconnected = networkInfo.isConnected();
				connType = networkInfo.getType();
				wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
				if (isconnected && connType == ConnectivityManager.TYPE_WIFI) {
					SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(myContext);
					String prefsWifi = settings.getString("pref_homewifissid", "NoWifiWhatsoever");

					if (getCurrentSsid().equals(prefsWifi)) {
						onHomeWifi=true;
						Log.d("PicSyncScheduler","Home WiFi detected");
					}
					else {
						onHomeWifi = false;
						Log.d("PicSyncScheduler", "Other than home WiFi detected");
					}
					evaluateTheNeedOfSync();
				}
			}
/*
			Intent PicSyncIntent = new Intent(this, PicSync.class);
			if (actionForPicSync == eactionForPicSync.GO)
				PicSyncIntent.setAction(PicSync.ACTION_START_SYNC);
			else
				PicSyncIntent.setAction(PicSync.ACTION_STOP_SYNC);
			startService(PicSyncIntent);

*/
		}
	};

	private void evaluateTheNeedOfSync() {
		if (onHomeWifi && isCharging)
			Log.d("PicSyncScheduler","We should start sync");
		else
			Log.d("PicSyncScheduler","We should NOT start sync. If it is running, stop it.");
	}


	public PicSyncScheduler() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not supported");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.registerOnSharedPreferenceChangeListener(prefChangeListener);
		if (!wifiChangeReceiverRegistered){
			registerReceiver(wifiChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
			wifiChangeReceiverRegistered=true;
			Log.d("PicSyncScheduler","wifiChangeReceiver registered");
		}
		handleChargingConditions();
	}

	private boolean isChargingConditionRequired() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(myContext);
		return settings.getBoolean("pref_switch_charging_condition", false);
	}
	private void registerChargerEventReceiver(){
		if (!chargerEventReceiverRegistered) {
			registerReceiver(chargerPluggedReceiver, new IntentFilter("android.intent.action.ACTION_POWER_CONNECTED"));
			registerReceiver(chargerUnpluggedReceiver, new IntentFilter("android.intent.action.ACTION_POWER_DISCONNECTED"));
			chargerEventReceiverRegistered = true;
		}
	}
	private void unregisterChargerEventReceiver(){
		if (chargerEventReceiverRegistered) {
			unregisterReceiver(chargerPluggedReceiver);
			unregisterReceiver(chargerUnpluggedReceiver);
			chargerEventReceiverRegistered = false;
		}

	}
	private void handleChargingConditions(){
		isChargingCondition = isChargingConditionRequired();
		if (isChargingCondition && !chargerEventReceiverRegistered){
			Log.d("PicSyncScheduler","Charging events listeners registered");
			registerChargerEventReceiver();
		}
		else{
			unregisterChargerEventReceiver();
			isCharging=true;
		}
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (wifiChangeReceiverRegistered) {
			unregisterReceiver(wifiChangeReceiver);
			wifiChangeReceiverRegistered = false;
		}
		if (chargerEventReceiverRegistered){
			unregisterChargerEventReceiver();
		}
		settings.unregisterOnSharedPreferenceChangeListener(prefChangeListener);

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	public void Schedule(){
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			//TODO: Pouzi JobScheduler
		} else {
			//TODO: Pouzi Broadcast
		}

	}
}
