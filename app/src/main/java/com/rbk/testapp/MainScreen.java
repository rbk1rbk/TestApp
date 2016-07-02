package com.rbk.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class MainScreen extends AppCompatActivity {
    static TextView TestBox1;
    static TextView twPicSyncState;
    private static boolean alreadyRunning=false;



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

        /*
        finish();
        System.exit(0);
        return;
*/
        Log.i("MainScreen","onCreate finished");
    }
    protected void onResume() {
        Log.i("MainScreen","onResume called");
        super.onResume();
        registerReceiver(MainScreenReceiver, new IntentFilter(PicSync.NOTIFICATION));
        Intent PicSyncIntent = new Intent(this,PicSync.class);
        PicSyncIntent.setAction(PicSync.ACTION_GET_STATE);
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
