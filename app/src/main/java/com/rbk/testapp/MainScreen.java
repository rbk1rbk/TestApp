package com.rbk.testapp;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import static android.os.Environment.getExternalStorageDirectory;
import static com.rbk.testapp.PicSyncScheduler.INTENT_EXTRA_SENDER;

public class MainScreen extends AppCompatActivity {
	private static final int mId=1;
	private static String PACKAGE_NAME;
	private static boolean alreadyRunning = false;
	private static boolean MainScreenReceiverRegistered=false;
	private static int MainScreenReceiverRegisteredCount=0;
	private static String localPicSyncState, localTotalImages, localScannedImages, localUnsyncedImages, localNASConnectivity, localCopyFrom, localCopyTo;
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
	private final Context myContext = this;
	private final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 101;
	TextView twPicSyncState;
	Button button;
	private long localLastCopiedImageTimestamp;


    private BroadcastReceiver MainScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
//			Log.i("MainScreen", "onReceive called with " + action);
			Bundle bundle = intent.getExtras();
			String Message = bundle.getString("Message");
//			Log.i("MainScreen", "onReceive called with " + Message);
            if (bundle != null) {
                if (Message.equals("isNASConnected")){
                    boolean isNASConnected = bundle.getBoolean("isNASConnected");
                    if (isNASConnected)
						localNASConnectivity = "Connected";
					else
						localNASConnectivity = "Not reachable";
					((TextView) findViewById(R.id.twNASConnectivity)).setText(localNASConnectivity);
				}
                if (Message.equals("msgCopyInProgress")){
					localCopyFrom = bundle.getString("srcFile");
					localCopyTo = bundle.getString("tgtFile");
					if (localCopyFrom.equals("none")){
						findViewById(R.id.copyProgressBar).setVisibility(View.INVISIBLE);
					}
					else {
						findViewById(R.id.copyProgressBar).setVisibility(View.VISIBLE);
						((TextView) findViewById(R.id.twCopyFrom)).setText(localCopyFrom);
						((TextView) findViewById(R.id.twCopyTo)).setText(localCopyTo);
					}
				}
				if (Message.equals("msgImagesCounts")) {
					localTotalImages = ((Integer) bundle.getInt("TotalImages")).toString();
					localScannedImages = ((Integer) bundle.getInt("ScannedImages")).toString();
					localUnsyncedImages = ((Integer) bundle.getInt("UnsyncedImages")).toString();
					((TextView) findViewById(R.id.twTotalImages)).setText(localTotalImages);
					((TextView) findViewById(R.id.twScannedImages)).setText(localScannedImages);
					((TextView) findViewById(R.id.twUnsyncedImages)).setText(localUnsyncedImages);
				}
				if (Message.equals("msgState")) {
					localPicSyncState = bundle.getString(PicSync.STATE);
					((TextView) findViewById(R.id.twPicSyncState)).setText(localPicSyncState);
					doNotify();
				}
				if (Message.equals("msgLastCopiedImageTimestamp")) {
					localLastCopiedImageTimestamp = bundle.getLong("lastCopiedImageDate");
					if (localLastCopiedImageTimestamp == 0)
						((TextView) findViewById(R.id.twLastSyncedImage)).setText("Not synced yet");
					else
						((TextView) findViewById(R.id.twLastSyncedImage)).setText(dateFormat.format(new Date(localLastCopiedImageTimestamp)));
				}
			}
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

   @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent myIntent = new Intent(this, SettingsActivity.class);
            this.startActivity(myIntent);
            return true;
        }

        if (id == R.id.action_rescan) {
			Intent PicSyncIntent = new Intent(myContext, PicSync.class);
			PicSyncIntent.setAction(PicSync.ACTION_SUGGEST_RESCAN);
            this.startService(PicSyncIntent);
            return true;
        }

	   if (id == R.id.action_set_last_run) {
		   showDialogSetLastRun();
		   return true;
	   }
	   if (id == R.id.action_export) {
		   boolean res = false;
		   String resMessage = "All settings but password was exported to ";
		   ObjectOutputStream output = null;
		   SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		   String smbpasswd = settings.getString(getResources().getString(R.string.pref_cifs_password), "");
		   settings.edit()
				   .putString(getString(R.string.pref_cifs_password), "")
				   .apply();
		   File exportFile = new File(getExternalStorageDirectory(), "picsync_preferences.xml");
		   try {
			   FileOutputStream exportFileWriter = new FileOutputStream(exportFile);
			   output = new ObjectOutputStream(exportFileWriter);
			   output.writeObject(settings.getAll());
			   res = true;
		   } catch (IOException e) {
			   e.printStackTrace();
			   resMessage = "Problem exporting settings: " + e.getMessage();
		   } finally {
			   settings.edit()
					   .putString(getString(R.string.pref_cifs_password), smbpasswd)
					   .apply();
			   AlertDialog.Builder aDialog = new AlertDialog.Builder(myContext);
			   aDialog.setMessage(resMessage + exportFile.getAbsolutePath());
			   aDialog.setCancelable(true);
			   aDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int id) {
					   dialog.dismiss();
				   }
			   });
			   aDialog.show();
			   try {
				   if (output != null) {
					   output.flush();
					   output.close();
				   }
			   } catch (IOException ex) {
				   ex.printStackTrace();
			   }
		   }
		   return res;
	   }
	   if (id == R.id.action_import) {
		   boolean res = false;
		   ObjectInputStream input = null;
		   String resMessage = "Settings imported. Remember to set CIFS password.";
		   try {
			   String envExternalStorage = System.getenv("EXTERNAL_STORAGE");
			   String canonicalPath = new File(envExternalStorage).getCanonicalPath();
			   File exportFile = new File(canonicalPath, "picsync_preferences.xml");
			   FileInputStream exportFileReader = new FileInputStream(exportFile);
			   input = new ObjectInputStream(exportFileReader);
			   SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(this).edit();
			   prefEdit.clear();
			   Map<String, ?> entries = (Map<String, ?>) input.readObject();
			   for (Map.Entry<String, ?> entry : entries.entrySet()) {
				   Object v = entry.getValue();
				   String key = entry.getKey();

				   if (v instanceof Boolean)
					   prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
				   else if (v instanceof Float)
					   prefEdit.putFloat(key, ((Float) v).floatValue());
				   else if (v instanceof Integer)
					   prefEdit.putInt(key, ((Integer) v).intValue());
				   else if (v instanceof Long)
					   prefEdit.putLong(key, ((Long) v).longValue());
				   else if (v instanceof String)
					   prefEdit.putString(key, ((String) v));
			   }
			   prefEdit.commit();
			   res = true;
		   } catch (IOException | ClassNotFoundException e) {
			   e.printStackTrace();
			   resMessage = "Problem exporting settings: " + e.getMessage();
		   } finally {
			   AlertDialog.Builder aDialog = new AlertDialog.Builder(myContext);
			   aDialog.setMessage(resMessage);
			   aDialog.setCancelable(true);
			   aDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int id) {
					   dialog.dismiss();
				   }
			   });
			   aDialog.show();
			   try {
				   if (input != null) {
					   input.close();
				   }
			   } catch (IOException ex) {
				   ex.printStackTrace();
			   }
		   }
		   return res;
	   }
	   return super.onOptionsItemSelected(item);
   }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);
		PACKAGE_NAME = getApplicationContext().getPackageName();
		Log.i("MainScreen", "onCreate called");
		twPicSyncState = (TextView) findViewById(R.id.twPicSyncState);
        if (alreadyRunning) {
            Log.i("MainScreen", "Already running, return");
            return;
        }

		localPicSyncState = (String) ((TextView) findViewById(R.id.twPicSyncState)).getText();
		localTotalImages = (String) ((TextView) findViewById(R.id.twTotalImages)).getText();
		localUnsyncedImages = (String) ((TextView) findViewById(R.id.twUnsyncedImages)).getText();
		localNASConnectivity = (String) ((TextView) findViewById(R.id.twNASConnectivity)).getText();
		localCopyFrom = (String) ((TextView) findViewById(R.id.twCopyFrom)).getText();
		localCopyTo = (String) ((TextView) findViewById(R.id.twCopyTo)).getText();

        alreadyRunning = true;
		localCopyFrom="none";
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
        }
		Log.i("MainScreen", "onCreate finished");
    }
	protected void onStop() {
		Log.i("MainScreen", "onStop called");
		super.onStop();
	}
	protected void onStart() {
		Log.i("MainScreen", "onStart called");
		super.onStart();
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		boolean servicePicSyncSchedulerRunning = false;
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (PicSyncScheduler.class.getName().equals(service.service.getClassName()))
				servicePicSyncSchedulerRunning = true;
		}
		if (!servicePicSyncSchedulerRunning){
			Intent intent = new Intent(this, PicSyncScheduler.class);
			intent.putExtra(INTENT_EXTRA_SENDER,this.getClass().getSimpleName());
			startService(intent);
		}
	}

	@Override
	protected void onRestart() {
		Log.i("MainScreen", "onRestart called");
		super.onRestart();
	}

	@Override
	public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
		Log.i("MainScreen", "onSaveInstanceState called");
		super.onSaveInstanceState(outState, outPersistentState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Log.i("MainScreen", "onRestoreInstanceState called");
		super.onRestoreInstanceState(savedInstanceState);
	}
	protected void onResume() {
        Log.i("MainScreen","onResume called");
        super.onResume();
        DrawMainScreen();
		doNotify();
		if (MainScreenReceiverRegisteredCount==0) {
			registerReceiver(MainScreenReceiver, new IntentFilter(PicSync.NOTIFICATION));
			Log.i("MainScreen", "Registering a receiver");
			MainScreenReceiverRegisteredCount++;
		}
        Intent PicSyncIntent = new Intent(this,PicSync.class);
        PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
        this.startService(PicSyncIntent);
        Log.i("MainScreen", "onResume finished");
    }

    @Override
    protected void onPause() {
        Log.i("MainScreen","onPause called");
        super.onPause();
/*
		if (MainScreenReceiverRegistered) {
			unregisterReceiver(MainScreenReceiver);
			MainScreenReceiverRegistered = false;
		}
*/
	}

	@Override
	protected void onDestroy() {
		Log.i("MainScreen","onDestroy called");
		super.onDestroy();
		while (MainScreenReceiverRegisteredCount>0) {
			unregisterReceiver(MainScreenReceiver);
			MainScreenReceiverRegisteredCount--;
		}
	}

	@Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case READ_EXTERNAL_STORAGE_PERMISSION_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent PicSyncIntent = new Intent(this, PicSync.class);
                    PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
                    this.startService(PicSyncIntent);
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

        }
    }
	private void showDialogSetLastRun() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final LayoutInflater inflater = this.getLayoutInflater();
		final View dialogView1 = View.inflate(myContext, R.layout.dialog_date_time_picker, null);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface _dialog, int id) {
				DatePicker datePicker = (DatePicker) dialogView1.findViewById(R.id.datePicker);
				TimePicker timePicker = (TimePicker) dialogView1.findViewById(R.id.timePicker);

				int y = datePicker.getYear();
				int m = datePicker.getMonth();
				int d = datePicker.getDayOfMonth();
				Calendar calendar = new GregorianCalendar(
						datePicker.getYear(),
						datePicker.getMonth(),
						datePicker.getDayOfMonth(),
						timePicker.getCurrentHour(),
						timePicker.getCurrentMinute());

				Long resultTime;
				resultTime = calendar.getTimeInMillis();
				Intent PicSyncIntent = new Intent(MainScreen.this, PicSync.class);
				PicSyncIntent.setAction(PicSync.ACTION_SET_LAST_IMAGE_TIMESTAMP);
				PicSyncIntent.putExtra("lastCopiedImageDate", resultTime);
				myContext.startService(PicSyncIntent);
				myContext.startService(new Intent(MainScreen.this, PicSync.class)
											   .setAction(PicSync.ACTION_SUGGEST_MEDIA_SCAN));

				_dialog.dismiss();
			}
		});

		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		builder.setView(dialogView1);
		AlertDialog dialog = builder.create();
		DatePicker datePicker = (DatePicker) dialogView1.findViewById(R.id.datePicker);
		TimePicker timePicker = (TimePicker) dialogView1.findViewById(R.id.timePicker);
		Date _date = new Date(localLastCopiedImageTimestamp);
		int y = _date.getYear() + 1900;
		int m = _date.getMonth();
		int d = _date.getDate();
		datePicker.updateDate(y, m, d);
		timePicker.setIs24HourView(true);
		timePicker.setCurrentHour(_date.getHours());
		timePicker.setCurrentMinute(_date.getMinutes());
		dialog.setTitle("Date & Time");
		dialog.show();
	}
	protected void DrawMainScreen(){
		button = (Button) findViewById(R.id.btnSaveSMB);

		((TextView) findViewById(R.id.twPicSyncState)).setText(localPicSyncState);
		((TextView) findViewById(R.id.twTotalImages)).setText(localTotalImages);
		((TextView) findViewById(R.id.twScannedImages)).setText(localScannedImages);
		((TextView) findViewById(R.id.twUnsyncedImages)).setText(localUnsyncedImages);
		((TextView) findViewById(R.id.twNASConnectivity)).setText(localNASConnectivity);
		((TextView) findViewById(R.id.twLastSyncedImage)).setText(dateFormat.format(new Date(localLastCopiedImageTimestamp)));
		if (localCopyFrom.equals("none")){
			findViewById(R.id.copyProgressBar).setVisibility(View.INVISIBLE);
		}
		else {
			findViewById(R.id.copyProgressBar).setVisibility(View.VISIBLE);
			((TextView) findViewById(R.id.twCopyFrom)).setText(localCopyFrom);
			((TextView) findViewById(R.id.twCopyTo)).setText(localCopyTo);
		}

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		Intent PicSyncIntent=new Intent(MainScreen.this,PicSync.class);
		PicSyncIntent.setAction(PicSync.ACTION_GET_NAS_CONNECTION);
		this.startService(PicSyncIntent);

	};

	public void btnStopSyncListener(View v) {
		Intent PicSyncIntent = new Intent(MainScreen.this, PicSync.class);
		PicSyncIntent.setAction(PicSync.ACTION_STOP_SYNC);
		PicSyncIntent.putExtra("cmdTimestamp",new Date().getTime());
		this.startService(PicSyncIntent);
	}

	public void btnSaveOnClickListener(View v) {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
		} else
			handlebtnSaveonClick();
	}

	private void handlebtnSaveonClick(){
		Intent PicSyncIntent=new Intent(MainScreen.this,PicSync.class);
		PicSyncIntent.setAction(PicSync.ACTION_START_SYNC);
		PicSyncIntent.putExtra(PicSync.ACTION_START_SYNC_FLAG,PicSync.ACTION_START_SYNC_RESTART);
		PicSyncIntent.putExtra("cmdTimestamp",new Date().getTime());
		this.startService(PicSyncIntent);
	}
	private void doNotify(){
		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
						.setSmallIcon(R.drawable.ic_sync_black_24dp)
						.setContentTitle("PicSync")
						.setContentText(localPicSyncState);
		Intent notifyIntent = new Intent(this, MainScreen.class);
		PendingIntent notifyPendingIntent =
				PendingIntent.getActivity(
						this,
						0,
						notifyIntent,
						PendingIntent.FLAG_UPDATE_CURRENT
				);

		mBuilder.setContentIntent(notifyPendingIntent);
		NotificationManager mNotificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(mId, mBuilder.build());

	}
}
/*
	ComponentName receiver = new ComponentName(context, SampleBootReceiver.class);
	PackageManager pm = context.getPackageManager();

	pm.setComponentEnabledSetting(receiver,
		PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
		PackageManager.DONT_KILL_APP);
*/