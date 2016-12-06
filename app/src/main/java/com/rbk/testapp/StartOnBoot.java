package com.rbk.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import static android.content.Context.POWER_SERVICE;
import static com.rbk.testapp.PicSyncScheduler.INTENT_EXTRA_BOOTTIME;
import static com.rbk.testapp.PicSyncScheduler.INTENT_EXTRA_SENDER;
import static com.rbk.testapp.PicSyncScheduler.INTENT_EXTRA_START_TYPE;

public class StartOnBoot extends BroadcastReceiver {
	public StartOnBoot() {
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("StartOnBoot","onReceive");
		PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
		if (powerManager == null){
			Log.d("StartOnBoot","PowerManager not available");
			return;
		}
		PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PicSyncStartOnBoot");
		wakeLock.acquire();
		context.startService(new Intent(context, PicSyncScheduler.class)
				.putExtra(INTENT_EXTRA_START_TYPE,INTENT_EXTRA_BOOTTIME)
				.putExtra(INTENT_EXTRA_SENDER,this.getClass().getSimpleName())
		);
		wakeLock.release();
	}
}
