package com.rbk.testapp;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

public class WifiWatchdogService extends Service {
	private static WifiChangeReceiver WCR;
	private static IntentFilter IF;

	public WifiWatchdogService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	//The system calls this method when another component, such as an activity,
	//requests that the service be started, by calling startService()
	public int onStartCommand(Intent intent, int flags, int startId) {
//        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
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
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(WCR);
		Log.i("WifiChangeReceiver", "onDestroy: Receiver unregistered");
		Log.i("WifiChangeReceiver", "onDestroy: Service done");
	}
}