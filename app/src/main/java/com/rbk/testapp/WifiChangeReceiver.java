package com.rbk.testapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;

public class WifiChangeReceiver extends BroadcastReceiver {
    public WifiChangeReceiver() {
    }
	WifiWatchdogService wifiWatchdogServiceGW;
	boolean WifiWatchdogServiceBound = false;

	private ServiceConnection wifiWatchdogServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			WifiWatchdogService.LocalBinder binder = (WifiWatchdogService.LocalBinder) service;
			wifiWatchdogServiceGW = binder.getService();
			WifiWatchdogServiceBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			WifiWatchdogServiceBound = false;
		}
	};

    @Override
    public void onReceive(Context context, Intent intent) {
		wifiWatchdogServiceGW.wifiChangeTrigger();
/*
        Intent PicSyncIntent = new Intent(context, PicSync.class);
        String currentssid = getCurrentSsid(context);
        if (currentssid == null) {
            PicSyncIntent.setAction(PicSync.ACTION_STOP_SYNC);
			wifiWatchdogServiceGW.wifiChangeTrigger();
//            Toast.makeText(context, "WifiChangeReceiver: Sync stopped", Toast.LENGTH_SHORT).show();
        } else{
            PicSyncIntent.setAction(PicSync.ACTION_START_SYNC);
//            Toast.makeText(context, "WifiChangeReceiver: Sync initiated", Toast.LENGTH_SHORT).show();
        }
        context.startService(PicSyncIntent);
*/
    }

    public static String getCurrentSsid(Context context) {
        String ssid;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null) {
            return null;
        }

        boolean isconnected =  networkInfo.isConnected();
        int conntype = networkInfo.getType();
        if ( isconnected && conntype == ConnectivityManager.TYPE_WIFI) {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null) {
                ssid = connectionInfo.getSSID();
                return ssid.replaceAll("^\"|\"$","");
            }
        }
        else {
            return null;
        }
        return null;
    }

}
