package com.rbk.testapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.opengl.Visibility;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v4.app.ActivityCompat;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class MainScreen extends AppCompatActivity {
    static TextView twPicSyncState;
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
	private final Context MyContext = this;


	private static boolean alreadyRunning = false;
	private static boolean MainScreenReceiverRegistered=false;
	private static int MainScreenReceiverRegisteredCount=0;
    static Button button;
    private final int READ_EXTERNAL_STORAGE_PERMISSION_CODE=101;

	private static String localPicSyncState, localTotalImages, localScannedImages, localUnsyncedImages, localNASConnectivity, localCopyFrom, localCopyTo;
	private long localLastCopiedImageTimestamp;


    private BroadcastReceiver MainScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			Log.i("MainScreen", "onReceive called with " + action);
			Bundle bundle = intent.getExtras();
			String Message = bundle.getString("Message");
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
				}
				if (Message.equals("msgLastCopiedImageTimestamp")) {
					localLastCopiedImageTimestamp = bundle.getLong("lastCopiedImageTimestamp");
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

	private void showDialogSetLastRun() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final LayoutInflater inflater = this.getLayoutInflater();
		final View dialogView1 = View.inflate(MyContext, R.layout.dialog_date_time_picker, null);
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
				PicSyncIntent.putExtra("lastCopiedImageTimestamp", resultTime);
				MyContext.startService(PicSyncIntent);

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

	   if (id == R.id.action_set_last_run) {
		   showDialogSetLastRun();
		   return true;
	   }
	   return super.onOptionsItemSelected(item);
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
        PicSyncIntent.putExtra(PicSync.EXTRA_PARAM1,PicSync.ACTION_START_SYNC_RESTART);
        this.startService(PicSyncIntent);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);
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
/*
        Intent intent = new Intent(this, WifiWatchdogService.class);
        startService(intent);
*/

		localCopyFrom="none";
        DrawMainScreen();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
        }
/*
		else {
            Intent PicSyncIntent = new Intent(this, PicSync.class);
            PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
            startService(PicSyncIntent);
        }
*/


        Log.i("MainScreen", "onCreate finished");
    }
	protected void onStop() {
		Log.i("MainScreen", "onStop called");
		super.onStop();
	}
	protected void onStart() {
		Log.i("MainScreen", "onStart called");
		super.onStart();
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

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
}
