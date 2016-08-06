package com.rbk.testapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.EditText;
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
    static Button button;
    static EditText txsmbUser;
    static EditText txsmbPWD;
    private final int READ_EXTERNAL_STORAGE_PERMISSION_CODE=101;


    private BroadcastReceiver MainScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("MainScreen","onReceive called");
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String string = bundle.getString(PicSync.STATE);
                    Log.i("MainScreen","onReceive got string "+string);
                    MainScreen.twPicSyncState.setText(string);
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
        button = (Button) findViewById(R.id.btnSaveSMB);
        txsmbUser = (EditText) findViewById(R.id.txsmbUser);
        txsmbPWD = (EditText) findViewById(R.id.txsmbPWD);

        SharedPreferences settings = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
        txsmbUser.setText(settings.getString(MainScreen.prefsSMBUSER,"guest"));
        txsmbPWD.setText(settings.getString(MainScreen.prefsSMBPWD,"passw0rd"));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

    };

    public void btnSaveOnClickListener(View v) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
        } else
            handlebtnSaveonClick();
    }

    private void handlebtnSaveonClick(){
        SharedPreferences prefs = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MainScreen.prefsSMBUSER,txsmbUser.getText().toString());
        editor.putString(MainScreen.prefsSMBPWD,txsmbPWD.getText().toString());
        editor.commit();
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
        this.startService(intent);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
        } else {
            Intent PicSyncIntent = new Intent(this, PicSync.class);
            PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
            this.startService(PicSyncIntent);
        }
        DrawMainScreen();

        Log.i("MainScreen", "onCreate finished");
    }

    protected void onResume() {
        Log.i("MainScreen","onResume called");
        super.onResume();
        registerReceiver(MainScreenReceiver, new IntentFilter(PicSync.NOTIFICATION));
        Intent PicSyncIntent = new Intent(this,PicSync.class);
        PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
        this.startService(PicSyncIntent);
        DrawMainScreen();
        this.startService(PicSyncIntent);
    }

    @Override
    protected void onPause() {
        Log.i("MainScreen","onPause called");
        super.onPause();
        unregisterReceiver(MainScreenReceiver);
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
