package com.rbk.testapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

public class PicSyncScheduler extends Service {
	static private boolean onUnmeteredNetwork = false;
	static private boolean onHomeWifi = false;
	static private boolean isConnected = false;
	static private boolean isConnectedToWifi = false;
	static private boolean isChargingCondition = false;
	static private boolean isCharging = false;
	static private boolean chargerEventReceiverRegistered = false;
	static private boolean changeInMediaFolders = false;
	static private boolean isChangeInMediaFoldersRegistered = false;
	static private int nextWakeUpTime = 0;
	static private boolean askedToRun=false;
	int callBack = 0;

	int connType;
	WifiManager wifiManager = null;

	private final Context myContext = this;

	private static SharedPreferences settings;
	private static String prefsWifi;
	private static String prefWhenToSync;

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

	Handler handler = new Handler();
	private ContentObserver newMediaObserver = new ContentObserver(handler) {
		/*		public newMediaObserver(Handler handler) {
					super(handler);
				}*/
		@Override
		public void onChange(boolean selfChange) {
			this.onChange(selfChange, null);
		}

		@Override
		public void onChange(boolean selfChange, Uri uri) {
			changeInMediaFolders = true;
			Intent PicSyncIntent = new Intent(myContext, PicSync.class);
			PicSyncIntent.setAction(PicSync.ACTION_SUGGEST_MEDIA_SCAN);
			PicSyncIntent.putExtra("uri",uri.toString());
			myContext.startService(PicSyncIntent);
			evaluateTheNeedOfSync();
		}
	};

	SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
					getMySettings();
					if (TextUtils.equals(key, "pref_switch_charging_condition")) {
						handleChargingConditions();
					}
					if (TextUtils.equals(key, "pref_homewifissid")) {
						wifiChangeReceiver.onReceive(myContext, new Intent("android.net.conn.CONNECTIVITY_CHANGE"));
					}
					if (TextUtils.equals(key, "pref_when2sync")) {
						handleSyncCondititions();
						handleChargingConditions();
					}
					evaluateTheNeedOfSync();
				}
			};

	private BroadcastReceiver chargerPluggedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			isCharging = true;
			Log.d("PicSyncScheduler", "Charger plugged in.");
			evaluateTheNeedOfSync();
		}
	};

	private BroadcastReceiver chargerUnpluggedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			isCharging = false;
			Log.d("PicSyncScheduler", "Charger unplugged.");
			evaluateTheNeedOfSync();
		}
	};

	private BroadcastReceiver wifiChangeReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("PicSyncScheduler", "wifiChangeReceiver onReceive called");
			checkWifiState();
			broadcastWifiState();
			evaluateTheNeedOfSync();
}
	};

	private void broadcastWifiState(){
		Intent PicSyncIntent = new Intent(myContext, PicSync.class);
		PicSyncIntent.setAction("PicSyncSchedulerNotification");
		PicSyncIntent.putExtra("WifiOn",isConnectedToWifi);
		myContext.startService(PicSyncIntent);
	}
	private void getMySettings(){
		if (settings == null)
			settings = PreferenceManager.getDefaultSharedPreferences(myContext);
		prefsWifi = settings.getString("pref_homewifissid", "NoWifiWhatsoever");
		prefWhenToSync = settings.getString("pref_when2sync", "ManualOnly");

	}
	private void broadcastInitialMediaScan(){
		startService(new Intent(this, PicSync.class).setAction(PicSync.ACTION_SUGGEST_MEDIA_SCAN));
	}
	private void askToRun(){
		Log.d("PicSyncScheduler", "We should start sync");
		broadcastWifiState();
		startService(new Intent(this, PicSync.class).setAction(PicSync.ACTION_START_SYNC));
		askedToRun = true;
	}
	private void evaluateTheNeedOfSync() {
		boolean preferencesFullfilled = (onHomeWifi && isCharging && !TextUtils.equals(prefWhenToSync, "ManualOnly"));
		if (changeInMediaFolders && preferencesFullfilled) {
			askToRun();
			changeInMediaFolders = false;
		}
		if (!askedToRun && preferencesFullfilled)
			askToRun();
		if (askedToRun && !preferencesFullfilled){
			Log.d("PicSyncScheduler", "We should NOT start sync. If it is running, stop it.");
			startService(new Intent(myContext, PicSync.class)
					.setAction(PicSync.ACTION_STOP_SYNC)
			);
			askedToRun=false;
		}
	}

	public PicSyncScheduler() {
	}

	public String getCurrentSsid() {
		String ssid;
		if (isConnected && connType == ConnectivityManager.TYPE_WIFI) {
			final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
			if (connectionInfo != null) {
				ssid = connectionInfo.getSSID();
				return ssid.replaceAll("^\"|\"$", "");
			}
		}
		return null;
	}

	public void checkWifiState() {
		if (cm == null)
			cm = (ConnectivityManager) myContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		if (networkInfo == null) {
			isConnected = false;
		} else {
			isConnected = networkInfo.isConnected();
			connType = networkInfo.getType();
			wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			if (isConnected && connType == ConnectivityManager.TYPE_WIFI) {
				isConnectedToWifi = true;
				if (getCurrentSsid().equals(prefsWifi)) {
					onHomeWifi = true;
					Log.d("PicSyncScheduler", "Home WiFi detected");
				} else {
					onHomeWifi = false;
					Log.d("PicSyncScheduler", "Other than home WiFi detected");
				}
				evaluateTheNeedOfSync();
			} else
				isConnectedToWifi = false;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not supported");
	}

	private void handleSyncCondititions() {
		if (!TextUtils.equals(prefWhenToSync, "ManualOnly")) {
			if (!wifiChangeReceiverRegistered) {
				registerReceiver(wifiChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
				wifiChangeReceiverRegistered = true;
				Log.d("PicSyncScheduler", "wifiChangeReceiver registered");
				wifiChangeReceiver.onReceive(myContext,new Intent());
			}
			handleChargingConditions();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		getMySettings();

		settings.registerOnSharedPreferenceChangeListener(prefChangeListener);
		if (!isChangeInMediaFoldersRegistered) {
			this.getContentResolver().
											 registerContentObserver(
													 MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
													 true,
													 newMediaObserver);
			this.getContentResolver().
											 registerContentObserver(
													 MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
													 true,
													 newMediaObserver);
			isChangeInMediaFoldersRegistered = true;
		}
		handleSyncCondititions();
		//Force initial rescan
		changeInMediaFolders = true;
		evaluateTheNeedOfSync();
		if (!askedToRun)
			broadcastInitialMediaScan();
	}
	private void registerChargerEventReceiver() {
		if (!chargerEventReceiverRegistered) {
			registerReceiver(chargerPluggedReceiver, new IntentFilter("android.intent.action.ACTION_POWER_CONNECTED"));
			registerReceiver(chargerUnpluggedReceiver, new IntentFilter("android.intent.action.ACTION_POWER_DISCONNECTED"));
			chargerEventReceiverRegistered = true;
		}
	}

	private void unregisterChargerEventReceiver() {
		if (chargerEventReceiverRegistered) {
			unregisterReceiver(chargerPluggedReceiver);
			unregisterReceiver(chargerUnpluggedReceiver);
			chargerEventReceiverRegistered = false;
		}

	}
	private boolean getChargingState(){
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = myContext.registerReceiver(null, ifilter);
		int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		return (status == BatteryManager.BATTERY_STATUS_CHARGING);
	}
	private void handleChargingConditions() {
		isChargingCondition = (prefWhenToSync == "AtHomeAndCharging");
		if (isChargingCondition) {
			if (!chargerEventReceiverRegistered) {
				Log.d("PicSyncScheduler", "Charging events listeners registered");
				registerChargerEventReceiver();
				isCharging=getChargingState();
			}
		} else {
			unregisterChargerEventReceiver();
			isCharging = true;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (wifiChangeReceiverRegistered) {
			unregisterReceiver(wifiChangeReceiver);
			wifiChangeReceiverRegistered = false;
		}
		if (chargerEventReceiverRegistered) {
			unregisterChargerEventReceiver();
		}
		if (isChangeInMediaFoldersRegistered)
			this.getContentResolver().unregisterContentObserver(newMediaObserver);
		settings.unregisterOnSharedPreferenceChangeListener(prefChangeListener);

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	public void Schedule() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			//TODO: Pouzi JobScheduler
		} else {
			//TODO: Pouzi Broadcast
		}

	}
}
