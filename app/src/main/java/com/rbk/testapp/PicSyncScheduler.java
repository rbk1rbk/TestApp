package com.rbk.testapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
	static private boolean isconnected = false;
	static private boolean isChargingCondition = false;
	static private boolean isCharging = false;
	static private boolean chargerEventReceiverRegistered = false;
	static private boolean changeInMediaFolders = false;
	static private boolean isChangeInMediaFoldersRegistered = false;
	static private int nextWakeUpTime = 0;
	int callBack = 0;

	private final Context myContext = this;

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
			String filePath;
			filePath = getImagePath(uri);
			evaluateTheNeedOfSync();
		}

		private String getImagePath(Uri uri) {
			Cursor cursor = getContentResolver().query(uri, null, null, null, null);
			cursor.moveToFirst();
			String document_id = cursor.getString(0);
			document_id = document_id.substring(document_id.lastIndexOf(":") + 1);
			cursor.close();

			cursor = getContentResolver().query(
					android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					null, MediaStore.Images.Media._ID + " = ? ", new String[]{document_id}, null);
			cursor.moveToFirst();
			String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
			cursor.close();

			return path;
		}

		;
	};

	SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
					if (TextUtils.equals(key, "pref_switch_charging_condition")) {
						handleChargingConditions();
					}
					if (TextUtils.equals(key, "pref_homewifissid")) {
						wifiChangeReceiver.onReceive(myContext, new Intent("android.net.conn.CONNECTIVITY_CHANGE"));
					}
					if (TextUtils.equals(key, "pref_when2sync")) {
						handleSyncCondititions();
					}
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
		int connType;
		WifiManager wifiManager = null;

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
			Log.d("PicSyncScheduler", "wifiChangeReceiver onReceive called");
			if (cm == null)
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
						onHomeWifi = true;
						Log.d("PicSyncScheduler", "Home WiFi detected");
					} else {
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
		if (onHomeWifi && isCharging && changeInMediaFolders) {
			Log.d("PicSyncScheduler", "We should start sync");
			Intent PicSyncIntent = new Intent(this, PicSync.class);
			PicSyncIntent.setAction(PicSync.ACTION_START_SYNC);
			startService(PicSyncIntent);
			changeInMediaFolders = false;
		} else
			Log.d("PicSyncScheduler", "We should NOT start sync. If it is running, stop it.");
	}


	public PicSyncScheduler() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not supported");
	}

	private void handleSyncCondititions() {
		if (!TextUtils.equals(settings.getString("pref_when2sync", ""), "Manual only")) {
			if (!wifiChangeReceiverRegistered) {
				registerReceiver(wifiChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
				wifiChangeReceiverRegistered = true;
				Log.d("PicSyncScheduler", "wifiChangeReceiver registered");
			}
			handleChargingConditions();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		settings = PreferenceManager.getDefaultSharedPreferences(this);
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
	}

	private boolean isChargingConditionRequired() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(myContext);
		return settings.getBoolean("pref_switch_charging_condition", false);
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

	private void handleChargingConditions() {
		isChargingCondition = isChargingConditionRequired();
		if (isChargingCondition && !chargerEventReceiverRegistered) {
			Log.d("PicSyncScheduler", "Charging events listeners registered");
			registerChargerEventReceiver();
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
