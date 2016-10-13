package com.rbk.testapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainScreen extends AppCompatActivity {
    static TextView twPicSyncState;
    public static final String prefsSMBPREFS="preferences.smb";
    public static final String prefsSMBUSER="smbuser";
    public static final String prefsSMBPWD="smbpwd";
    public static final String prefsSMBSRV="smbsrv";
    public static final String prefsSMBSHARE="smbshare";
    public static final String prefsPicSyncPREFS="preferences.picsync";
    public static final String prefsLITS="lastImageTimestamp";

    private static boolean alreadyRunning=false;
    private static boolean MainScreenReceiverRegistered=false;
    static Button button;
    private final int READ_EXTERNAL_STORAGE_PERMISSION_CODE=101;


    private BroadcastReceiver MainScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("MainScreen","onReceive called");
			final String action = intent.getAction();
			Bundle bundle = intent.getExtras();
			String Message = bundle.getString("Message");
            if (bundle != null) {
                if (Message.equals("isNASConnected")){
                    boolean isNASConnected = bundle.getBoolean("isNASConnected");
                    TextView twConnectivity = (TextView) findViewById(R.id.twNASConnectivity);
                    if (isNASConnected)
                        twConnectivity.setText("Connected");
                    else
                        twConnectivity.setText("Not available");
                }
                if (Message.equals("msgCopyInProgress")){
                    boolean isNASConnected = bundle.getBoolean("isNASConnected");
                    TextView twCopyFrom = (TextView) findViewById(R.id.twCopyFrom);
                    twCopyFrom.setText(bundle.getString("srcFile"));
                    TextView twCopyTo = (TextView) findViewById(R.id.twCopyTo);
					twCopyTo.setText(bundle.getString("tgtFile"));
                }
				if (Message.equals("State")){
					String string = bundle.getString(PicSync.STATE);
					TextView tw = (TextView) findViewById(R.id.twPicSyncState);
					tw.setText(string);
					Log.i("MainScreen","onReceive got string "+string);
				}
//                    MainScreen.twPicSyncState.setText(string);
//                    Toast.makeText(MainScreen.this, "PicSync says: "+ string, Toast.LENGTH_SHORT).show();
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

        return super.onOptionsItemSelected(item);
    }

    protected void DrawMainScreen(){
/*

        txsmbUser = (EditText) findViewById(R.id.txsmbUser);
        txsmbPWD = (EditText) findViewById(R.id.txsmbPWD);
*/
        button = (Button) findViewById(R.id.btnSaveSMB);
/*

        SharedPreferences settings = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
        txsmbUser.setText(settings.getString(MainScreen.prefsSMBUSER,"guest"));
        txsmbPWD.setText(settings.getString(MainScreen.prefsSMBPWD,"passw0rd"));
*/

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
		Intent PicSyncIntent=new Intent(MainScreen.this,PicSync.class);
		PicSyncIntent.setAction(PicSync.ACTION_GET_NAS_CONNECTION);
		this.startService(PicSyncIntent);

    };

    public void btnSaveOnClickListener(View v) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
        } else
            handlebtnSaveonClick();
    }

    private void handlebtnSaveonClick(){
/*
        SharedPreferences prefs = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MainScreen.prefsSMBUSER,txsmbUser.getText().toString());
        editor.putString(MainScreen.prefsSMBPWD,txsmbPWD.getText().toString());
        editor.commit();
*/
//        this.startService(new Intent(MainScreen.this,PicSync.class).setAction(PicSync.ACTION_START_SYNC));
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

        alreadyRunning = true;
        Intent intent = new Intent(this, WifiWatchdogService.class);
        startService(intent);

        DrawMainScreen();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
        } else {
            Intent PicSyncIntent = new Intent(this, PicSync.class);
            PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
            startService(PicSyncIntent);
        }


        Log.i("MainScreen", "onCreate finished");
    }

    protected void onResume() {
        Log.i("MainScreen","onResume called");
        super.onResume();
        DrawMainScreen();
        registerReceiver(MainScreenReceiver, new IntentFilter(PicSync.NOTIFICATION));
		MainScreenReceiverRegistered = true;
        Intent PicSyncIntent = new Intent(this,PicSync.class);
        PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
        this.startService(PicSyncIntent);
        Log.i("MainScreen", "onResume finished");
    }

    @Override
    protected void onPause() {
        Log.i("MainScreen","onPause called");
        super.onPause();
		if (MainScreenReceiverRegistered) {
			unregisterReceiver(MainScreenReceiver);
			MainScreenReceiverRegistered = false;
		}
	}

	@Override
	protected void onDestroy() {
		Log.i("MainScreen","onDestroy called");
		super.onDestroy();
		if (MainScreenReceiverRegistered) {
			unregisterReceiver(MainScreenReceiver);
			MainScreenReceiverRegistered = false;
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
