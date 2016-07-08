package com.rbk.testapp;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class PicSync extends IntentService {
	public static final String STATE = "State";
	public static final String NOTIFICATION = "com.rbk.testapp.MainScreen.receiver";

	private static String MyState = "New";
	private static Context ParentContext;

	static final String ACTION_GET_STATE = "com.rbk.testapp.action.PicSync.GetState";
	static final String ACTION_START_SYNC = "com.rbk.testapp.action.PicSync.Start";
	static final String ACTION_STOP_SYNC = "com.rbk.testapp.action.PicSync.Stop";

	// TODO: Rename parameters
	private static final String EXTRA_PARAM1 = "com.rbk.testapp.extra.PARAM1";
	private static final String EXTRA_PARAM2 = "com.rbk.testapp.extra.PARAM2";

	public PicSync() {
		super("PicSync");
	}


	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}

	/**
	 * Starts this service to perform action Foo with the given parameters. If
	 * the service is already performing a task this action will be queued.
	 *
	 * @see IntentService
	 */
/*
	// TODO: Customize helper method
    public static void startActionFoo(Context context, String param1, String param2) {
        Intent intent = new Intent(context, PicSync.class);
        intent.setAction(ACTION_FOO);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }
*/

	/**
	 * Starts this service to perform action Baz with the given parameters. If
	 * the service is already performing a task this action will be queued.
	 *
	 * @see IntentService
	 */
    /*
    // TODO: Customize helper method
    public static void startActionBaz(Context context, String param1, String param2) {
        Intent intent = new Intent(context, PicSync.class);
        intent.setAction(ACTION_BAZ);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }
*/
	private void PublishState(String State2Send) {
		Intent intent = new Intent(NOTIFICATION);
		intent.putExtra(STATE, State2Send);
		sendBroadcast(intent);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			final String action = intent.getAction();
			Log.i("PicSync", "onHandleIntent: " + action);
			if (ACTION_GET_STATE.equals(action)) {
				final String param1 = intent.getStringExtra(EXTRA_PARAM1);
				final String param2 = intent.getStringExtra(EXTRA_PARAM2);
				handleActionGetState(param1, param2);
			}
			if (ACTION_START_SYNC.equals(action)) {
				final String param1 = intent.getStringExtra(EXTRA_PARAM1);
				final String param2 = intent.getStringExtra(EXTRA_PARAM2);
				handleActionStartSync(param1, param2);
			}
			if (ACTION_STOP_SYNC.equals(action)) {
				final String param1 = intent.getStringExtra(EXTRA_PARAM1);
				final String param2 = intent.getStringExtra(EXTRA_PARAM2);
				handleActionStopSync(param1, param2);
			}
		}
	}

	public void DoSync() {
		Log.i("PicSync", "DoSync started");
		Log.i("PicSync", "Sync started");
		MyState = "Sync started";
		PublishState(MyState);
        /*
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
    */
		readTestFile();
		Log.i("PicSync", "Sync finished");
		MyState = "Sync finished";
		PublishState(MyState);
		Log.i("PicSync", "DoSync finished");
	}

	private void handleActionGetState(String param1, String param2) {
		Log.i("PicSync", "handleActionGetState: " + MyState);
		PublishState(MyState);
	}

	private void handleActionStartSync(String param1, String param2) {
		MyState = "Sync doable";
		// TODO Prerobit na novy thread?
		DoSync();
		Log.i("PicSync", "handleActionStartSync: " + MyState);
		PublishState(MyState);
	}

	private void handleActionStopSync(String param1, String param2) {
		MyState = "Sync not doable";
		Log.i("PicSync", "handleActionStopSync: " + MyState);
		PublishState(MyState);
	}

	public void readTestFile() {
		SharedPreferences settings = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
		String smbservername = settings.getString(MainScreen.prefsSMBSRV, "192.168.0.1");
		String smbuser = settings.getString(MainScreen.prefsSMBUSER, "");
		String smbpasswd = settings.getString(MainScreen.prefsSMBPWD, "PASSWORD");
		Log.i("PicSync", "Settings retrieved: " + smbuser + ":" + smbpasswd + "@" + smbservername);


		Log.i("PicSync", "readTestFile: start");
		jcifs.Config.setProperty("jcifs.netbios.wins", smbservername);
		NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, smbuser, smbpasswd);
		SmbFile[] domains = null;
		try {
			domains = (new SmbFile("smb://NET01/", auth)).listFiles();
		} catch (SmbException e1) {
			e1.printStackTrace();
			return;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			Log.i("PicSync", "Domain NOT listed" + e.getMessage());
			return;
		}
		SmbFile sfile;
		SmbFileInputStream in;
		try {
			String fileurl = "smb://" + smbservername + "/testexport/somefile.txt";
			Log.i("PicSync", "Opening file: " + fileurl);
//            in = new SmbFileInputStream(new SmbFile("smb://192.168.0.1/testexport/somefile.txt"),auth);
			sfile = new SmbFile(fileurl, auth);
			Log.i("PicSync", "File opened");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			Log.i("PicSync", "File NOT opened " + e.getMessage());
			return;
		}
		try {
			in = new SmbFileInputStream(sfile);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			Log.i("PicSync", "File NOT opened " + e.getMessage());
			return;
		} catch (SmbException e) {
			e.printStackTrace();
			Log.i("PicSync", "File NOT opened " + e.getMessage());
			return;
		} catch (UnknownHostException e) {
			e.printStackTrace();
			Log.i("PicSync", "File NOT opened " + e.getMessage());
			return;
		}
		byte[] b = new byte[8192];
		int n;
		try {
			while ((n = in.read(b)) > 0) {
				System.out.write(b, 0, n);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String ping(String url) {
		String str = "";
		try {
			Process process = Runtime.getRuntime().exec("/system/bin/ping -c 8 " + url);
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			int i;
			char[] buffer = new char[4096];
			StringBuffer output = new StringBuffer();
			while ((i = reader.read(buffer)) > 0)
				output.append(buffer, 0, i);
			reader.close();

			// body.append(output.toString()+"\n");
			str = output.toString();
			// Log.d(TAG, str);
		} catch (IOException e) {
			// body.append("Error\n");
			e.printStackTrace();
		}
		return str;
	}
}
