package com.rbk.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainScreen extends AppCompatActivity {
    static TextView TestBox1;
    static TextView twPicSyncState;
    public static final String prefsSMBPREFS="preferences.smb";
    public static final String prefsSMBUSER="smbuser";
    public static final String prefsSMBPWD="smbpwd";
    public static final String prefsSMBSRV="smbsrv";
    private static boolean alreadyRunning=false;
    static Button button;
    static EditText txsmbUser;
    static EditText txsmbPWD;


    private BroadcastReceiver MainScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("MainScreen","onReceive called");
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String string = bundle.getString(PicSync.STATE);
                    Log.i("MainScreen","onReceive got string "+string);
                    MainScreen.twPicSyncState.setText(string);
                    Toast.makeText(MainScreen.this, "PicSync says: "+ string, Toast.LENGTH_SHORT).show();
            }
        }
    };

    protected void DrawMainScreen(){
        button = (Button) findViewById(R.id.btnSaveSMB);
        txsmbUser = (EditText) findViewById(R.id.txsmbUser);
        txsmbPWD = (EditText) findViewById(R.id.txsmbPWD);

        SharedPreferences settings = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
        txsmbUser.setText(settings.getString(MainScreen.prefsSMBUSER,"guest"));
        txsmbPWD.setText(settings.getString(MainScreen.prefsSMBPWD,"passw0rd"));
    };

    public void btnSaveOnClickListener(View v) {
        handlebtnSaveonClick();
    }

    private void handlebtnSaveonClick(){
//        button.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(MainScreen.prefsSMBUSER,txsmbUser.getText().toString());
                editor.putString(MainScreen.prefsSMBPWD,txsmbPWD.getText().toString());
                editor.commit();
                this.startService(new Intent(MainScreen.this,PicSync.class).setAction(PicSync.ACTION_START_SYNC));

  //          }
  //      });

    };

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);
        Log.i("MainScreen","onCreate called");
        TestBox1 = (TextView)findViewById(R.id.TestBox1);
        twPicSyncState = (TextView)findViewById(R.id.twPicSyncState);
        if (alreadyRunning){
            Log.i("MainScreen","Already running, return");
            return;
        }
        alreadyRunning=true;

//        WifiWatchdogService WWS = new WifiWatchdogService();
        Intent intent = new Intent(this,WifiWatchdogService.class);
        this.startService(intent);

//        PicSync PicSyncService = new PicSync();
        Intent PicSyncIntent = new Intent(this,PicSync.class);
        PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
        this.startService(PicSyncIntent);
            DrawMainScreen();
        Log.i("MainScreen","onCreate finished");

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
    public static void SetState(String state){
        alreadyRunning=false;
        TestBox1.setText(state);
    }
}
