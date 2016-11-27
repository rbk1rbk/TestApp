package com.rbk.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static com.rbk.testapp.PicSyncScheduler.INTENT_EXTRA_BOOTTIME;
import static com.rbk.testapp.PicSyncScheduler.INTENT_EXTRA_SENDER;
import static com.rbk.testapp.PicSyncScheduler.INTENT_EXTRA_START_TYPE;

public class StartOnBoot extends BroadcastReceiver {
	public StartOnBoot() {
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("StartOnBoot: ","onReceive");
		context.startService(new Intent(context, PicSyncScheduler.class)
				.putExtra(INTENT_EXTRA_START_TYPE,INTENT_EXTRA_BOOTTIME)
				.putExtra(INTENT_EXTRA_SENDER,this.getClass().getSimpleName())
		);
	}
}
