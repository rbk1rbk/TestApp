package com.rbk.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class WifiChangeReceiver extends BroadcastReceiver {
	public WifiChangeReceiver() {
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO: This method is called when the BroadcastReceiver is receiving
		// an Intent broadcast.
		Intent PicSyncIntent = new Intent(context, PicSync.class);
		String currentssid = getCurrentSsid(context);
		if (currentssid == null) {
			PicSyncIntent.setAction(PicSync.ACTION_STOP_SYNC);
		} else {
			PicSyncIntent.setAction(PicSync.ACTION_START_SYNC);
		}
//        context.sendBroadcast(PicSyncIntent);
		context.startService(PicSyncIntent);
	}

	public static String getCurrentSsid(Context context) {
		String ssid;
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		if (networkInfo == null) {
			return null;
		}

		boolean isconnected = networkInfo.isConnected();
		int conntype = networkInfo.getType();
		if (isconnected && conntype == ConnectivityManager.TYPE_WIFI) {
			final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
			if (connectionInfo != null) {
				ssid = connectionInfo.getSSID();
				return ssid.replaceAll("^\"|\"$", "");
			}
		} else {
			return null;
		}
		return null;
	}
}
