package com.rbk.testapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public class WifiChangeReceiver extends BroadcastReceiver {
	private static boolean queueTrigger=false;
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
			if (queueTrigger)
				wifiWatchdogServiceGW.wifiChangeTrigger();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			WifiWatchdogServiceBound = false;
		}
	};

    @Override
    public void onReceive(Context context, Intent intent) {
		if (!WifiWatchdogServiceBound) {
			queueTrigger = true;
			Intent wifiWatchdogServiceIntent = new Intent(context, WifiWatchdogService.class);
			context.bindService(wifiWatchdogServiceIntent, wifiWatchdogServiceConnection, Context.BIND_AUTO_CREATE);
		}
		else{
			queueTrigger=false;
			wifiWatchdogServiceGW.wifiChangeTrigger();
		}
    }
}
