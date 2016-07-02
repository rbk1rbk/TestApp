package com.rbk.testapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

public class MainScreen extends AppCompatActivity {
    private static TextView TestBox1;
    private static boolean alreadyRunning=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);
        Log.i("MainScreen","onCreate called");
        if (alreadyRunning){
            Log.i("MainScreen","Already running, return");
            return;
        }
        alreadyRunning=true;
        TestBox1 = (TextView)findViewById(R.id.TestBox1);

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

    public static void SetState(String state){
        alreadyRunning=false;
        TestBox1.setText(state);
    }
}
