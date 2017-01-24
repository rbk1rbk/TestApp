package com.rbk.testapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
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
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Set;

public class PicSyncScheduler extends Service {
	static final String INTENT_EXTRA_START_TYPE =  "StartType";
	static final String INTENT_EXTRA_BOOTTIME = "boottime";
	static final String INTENT_EXTRA_AFTERBOOT = "afterboot";
	static final String INTENT_EXTRA_PICSYNC = "afterboot";
	static final long AFTERBOOT_DELAY = 30*1000;
	static final String INTENT_EXTRA_SENDER = "intentSender";

	static private boolean perfEnabledBackgroundCopy = true;
	static private WifiManager.WifiLock wifiLock = null;
	static private volatile boolean onHomeWifi = false;
	static private volatile boolean onHomeWifi_Old = false;
	static private boolean isConnected = false;
	static private boolean isConnected_Old = false;
	static private boolean wifiEnabled= false;
	static private boolean isConnectedToWifi_Old = false;
	static private boolean isChargingCondition = false;
	static private boolean isCharging = false;
	static private boolean isCharging_Old = false;
	static private volatile boolean chargerEventReceiverRegistered = false;
	static private boolean changeInMediaFolders = false;
	static private volatile boolean isChangeInMediaFoldersRegistered = false;
	private static volatile boolean wifiChangeReceiverRegistered = false;
	private static ConnectivityManager cm;
	static private int nextWakeUpTime = 0;
	static private boolean askedToRun=false;

	int connType;
	WifiManager wifiManager = null;

	private final Context myContext = this;

	private static SharedPreferences settings;
	private static String prefsWifi;
	private static String prefWhenToSync;

	static volatile String startType = null;
	static volatile String intentSender = null;
	static volatile boolean boottime=false;
	static volatile boolean picSyncStart = false;
	static IBinder binderPicSyncScheduler = null;
	public volatile static boolean picSyncShouldBeRunning=false;


	/*
	* This class provides a Watchdog type of service, that calls back
	* a sync action when user is on a home network.
	* If user recently connects to the home network, it lets the sync to scan for changes.
	* While on the home network, it waits for a new picture to sync.
	* If the server is not available, and while on home network, and with WoL off or not working,
	* it sets an alarm.
	*
	* */

	Handler handler = new Handler();
	private ContentObserver newMediaObserver = new ContentObserver(handler) {

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
/*
						wifiChangeReceiver.onReceive(myContext, new Intent("android.net.conn.CONNECTIVITY_CHANGE"));
*/
					}
					if (TextUtils.equals(key, "pref_when2sync")) {
						handleSyncCondititions();
						handleChargingConditions();
					}
					evaluateTheNeedOfSync();
				}
			};

	private BroadcastReceiver afterbootAlarmReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d("PicSyncScheduler", "afterbootAlarmReceiver: "+action);
			if (action.equals(INTENT_EXTRA_AFTERBOOT)){
				finishServiceInitialization();
			}
		}
	};

	private BroadcastReceiver chargerPluggedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			isCharging_Old = isCharging;
			isCharging = true;
			Log.d("PicSyncScheduler", "Charger plugged in.");
			evaluateTheNeedOfSync();
		}
	};

	private BroadcastReceiver chargerUnpluggedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			isCharging_Old = isCharging;
			isCharging = false;
			Log.d("PicSyncScheduler", "Charger unplugged.");
			evaluateTheNeedOfSync();
		}
	};
/*

	private BroadcastReceiver wifiChangeReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action == null)
				return;
			Log.d("wifiChangeReceiver", "wifiChangeReceiver action: "+action);
			dumpIntent(intent);
			verifyWifiConnection(intent);
			Log.d("wifiChangeReceiver", "onHomeWifi: " + Boolean.toString(onHomeWifi) + ",onHomeWifi_Old: " + Boolean.toString(onHomeWifi_Old));
			if (onHomeWifi != onHomeWifi_Old || isConnected != isConnected_Old) {
				broadcastWifiState();
				evaluateTheNeedOfSync();
				onHomeWifi_Old=onHomeWifi;
				isConnected_Old=isConnected;
			}
}
	};
*/
	public static void dumpIntent(Intent i){
		final String LOG_TAG="dumpIntent";
		Bundle bundle = i.getExtras();
		if (bundle != null) {
			Set<String> keys = bundle.keySet();
			Iterator<String> it = keys.iterator();
			Log.d(LOG_TAG,"Dumping Intent start");
			while (it.hasNext()) {
				String key = it.next();
				Log.d(LOG_TAG,key + "=" + bundle.get(key));
			}
			Log.d(LOG_TAG,"Dumping Intent end");
		}
	}
	private void broadcastWifiState(){
		Intent PicSyncIntent = new Intent(myContext, PicSync.class);
		PicSyncIntent.setAction("PicSyncSchedulerNotification");
		PicSyncIntent.putExtra("WifiOn",isConnected);
		myContext.startService(PicSyncIntent);
		Log.d("broadcastWifiState", "Sending WifiOn connection="+isConnected);
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
		Log.d("PicSyncScheduler", "Requesting start sync");
		broadcastWifiState();
		startService(new Intent(this, PicSync.class)
				.setAction(PicSync.ACTION_START_SYNC)
				.putExtra("cmdTimestamp", new Date().getTime())
		);
		askedToRun = true;
	}
	private void evaluateTheNeedOfSync() {
		final String LOG_TAG = "evaluateTheNeedOfSync";
		boolean preferencesFullfilled = (onHomeWifi && isConnected && isCharging && !TextUtils.equals(prefWhenToSync, "ManualOnly"));
		if (onHomeWifi == onHomeWifi_Old && isConnected != isConnected_Old && isCharging == isCharging_Old) {
			Log.d(LOG_TAG, "There is no change on monitored conditions. evaluateTheNeedOfSync called redundantly.");
			askedToRun=false;
			return;
		}
		if (!preferencesFullfilled){
			Log.d(LOG_TAG, "We should NOT start sync. If it is running, stop it.");
			startService(new Intent(myContext, PicSync.class)
					.setAction(PicSync.ACTION_STOP_SYNC)
					.putExtra("cmdTimestamp", new Date().getTime())
			);
			askedToRun=false;
			picSyncShouldBeRunning=false;
			return;
		}

		Log.d(LOG_TAG, "We passed all checks, ask to run sync.");
		askToRun();
		picSyncShouldBeRunning=true;
		if (wifiLock!=null && wifiLock.isHeld()) {
			wifiLock.release();
			Log.d(LOG_TAG,"WiFi wakeLock released");
		}
	}

	public PicSyncScheduler() {
	}

	public boolean getRunningRecommendation(){
		return picSyncShouldBeRunning;
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
	private void verifyWifiConnection(Intent intent) {
		String LOG_TAG="verifyWifiConnection";
		if (intent == null)
			return;
		String action = intent.getAction();
		Log.d(LOG_TAG, "Parsing intent with action " + action);
		if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
			Integer wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,-1);
			if (wifiState == WifiManager.WIFI_STATE_ENABLED)
				wifiEnabled = true;
			else
				wifiEnabled = false;
			Log.d(LOG_TAG, "Wifi state is " + wifiState);
		}
		if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")){
			if (cm == null)
				cm = (ConnectivityManager) myContext.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkInfo = cm.getActiveNetworkInfo();
			if (networkInfo == null){
				isConnected = false;
				return;
			}
			if (intent.hasExtra(ConnectivityManager.EXTRA_NETWORK_TYPE)) {
				connType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
			}
			else{
				connType = networkInfo.getType();
			}
			Log.d(LOG_TAG, "Network type is " + networkInfo.getTypeName());
			if (connType == ConnectivityManager.TYPE_WIFI){
				isConnected = networkInfo.isConnected();
				Log.d(LOG_TAG, "isConnected: "+isConnected);
				String extraInfo = intent.getStringExtra(ConnectivityManager.EXTRA_EXTRA_INFO);
				if (extraInfo == null ){
					onHomeWifi = (getCurrentSsid().equals(prefsWifi));
				}else if (extraInfo.contains(prefsWifi)){
					onHomeWifi = true;
					Log.d(LOG_TAG, "Home WiFi detected");
				} else {
					onHomeWifi = false;
					Log.d(LOG_TAG, "Other than home WiFi detected");
				}
			}
		}

		if (wifiEnabled && onHomeWifi && !isConnected && perfEnabledBackgroundCopy)
			enableWifiWakeLock();
	}

	private void enableWifiWakeLock() {
		final String LOG_TAG = "enableWifiWakeLock";
		Log.d(LOG_TAG, "I will setup wifi always on.");
		if (perfEnabledBackgroundCopy){
			WifiManager wifiManager = (WifiManager) myContext.getSystemService(WIFI_SERVICE);
			if (wifiManager == null){
				Log.d(LOG_TAG,"PowerManager or WifiManager not available");
			}
			else {
				wifiLock = wifiManager.createWifiLock("PicSyncScheduer");
				wifiLock.acquire();
				Log.d(LOG_TAG,"WiFi wakeLock acquired");
				isConnected=true;
			}
		}

	}

	@Override
	public IBinder onBind(Intent intent) {
		return binderPicSyncScheduler;
//		throw new UnsupportedOperationException("Not supported");
	}

	private void handleSyncCondititions() {
		if (!TextUtils.equals(prefWhenToSync, "ManualOnly")) {
/*
			if (!wifiChangeReceiverRegistered) {
				registerReceiver(wifiChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
				registerReceiver(wifiChangeReceiver, new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED"));
				wifiChangeReceiverRegistered = true;
				Log.d("PicSyncScheduler", "wifiChangeReceiver registered");
				wifiChangeReceiver.onReceive(myContext,new Intent());
			}
*/
			handleChargingConditions();
		}
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


	private void setupReceivers(){
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
	public class LocalBinder extends Binder {
		PicSyncScheduler getService() {
			return PicSyncScheduler.this;
		}
	}
	private void finishServiceInitialization(){
/*
		verifyWifiConnection(null);
		if (!onHomeWifi)
			broadcastInitialMediaScan();
		broadcastWifiState();
*/
		setupReceivers();
		if (binderPicSyncScheduler == null)
			binderPicSyncScheduler = new LocalBinder();
//		evaluateTheNeedOfSync();
	}
	private void setAfterbootInit() {
		Long afterboot_time = new GregorianCalendar().getTimeInMillis()+AFTERBOOT_DELAY;
		Intent afterbootIntent = new Intent(INTENT_EXTRA_AFTERBOOT);
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmManager.set(AlarmManager.RTC_WAKEUP, afterboot_time, PendingIntent.getBroadcast(this, 1, afterbootIntent, PendingIntent.FLAG_UPDATE_CURRENT));
		Log.d("PicSyncScheduler", "Afterboot tasks scheduled at " + new SimpleDateFormat("HH:mm:ss dd.MM.yyyy").format(new Date(afterboot_time)));
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		getMySettings();
		PowerManager powerManager = (PowerManager) myContext.getSystemService(POWER_SERVICE);
		PowerManager.WakeLock wakeLock = null;
		if (powerManager == null){
			Log.d("StartOnBoot","PowerManager not available");
		}else {
			wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PicSyncScheduler");
			wakeLock.acquire();
		}

		if (intent == null) {
			Log.d("PicSyncScheduler", "Service started with no Intent");
			//start with the latest intent, we were restarted by system
		} else {
			startType = intent.getStringExtra(INTENT_EXTRA_START_TYPE);
			intentSender = intent.getStringExtra(INTENT_EXTRA_SENDER);

//		boolean afterboot;
			if (startType != null) {
				boottime = startType.equals(INTENT_EXTRA_BOOTTIME);
//			afterboot = startType.equals(INTENT_EXTRA_AFTERBOOT);
				picSyncStart = startType.equals(INTENT_EXTRA_PICSYNC);
			}
		}
		if (intentSender == null)
			intentSender="Unknown";
		Log.d("PicSyncScheduler","onStartCommand() " + new Integer(startId).toString() + " from "+intentSender);

		if (boottime) {
			Log.d("PicSyncScheduler", "Service started on device boot");
			setAfterbootInit();
			registerReceiver(afterbootAlarmReceiver, new IntentFilter(INTENT_EXTRA_AFTERBOOT));
		} else if (startId == 1) {
			Log.d("PicSyncScheduler", "Service started by MainScreen");
			finishServiceInitialization();
		}
		if (intentSender.equals("WifiChangeReceiver")){
			dumpIntent(intent);
			verifyWifiConnection(intent);
			Log.d("wifiChangeReceiver", "onHomeWifi: " + Boolean.toString(onHomeWifi) + ",onHomeWifi_Old: " + Boolean.toString(onHomeWifi_Old));
			if (onHomeWifi != onHomeWifi_Old || isConnected != isConnected_Old) {
				broadcastWifiState();
				evaluateTheNeedOfSync();
				onHomeWifi_Old=onHomeWifi;
				isConnected_Old=isConnected;
			}

		} else {
			Log.d("PicSyncScheduler", "Who is calling me now?");
		}
		if (wakeLock != null)
			wakeLock.release();
		return START_REDELIVER_INTENT;
		//START_REDELIVER_INTENT
		//START_STICKY
	}

	public void Schedule() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			//TODO: Pouzi JobScheduler
		} else {
			//TODO: Pouzi Broadcast
		}

	}
	@Override
	public void onCreate() {
		super.onCreate();
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (wifiChangeReceiverRegistered) {
/*
			unregisterReceiver(wifiChangeReceiver);
*/
			wifiChangeReceiverRegistered = false;
		}
		if (chargerEventReceiverRegistered) {
			unregisterChargerEventReceiver();
		}
		if (isChangeInMediaFoldersRegistered)
			this.getContentResolver().unregisterContentObserver(newMediaObserver);
		settings.unregisterOnSharedPreferenceChangeListener(prefChangeListener);

	}
}
