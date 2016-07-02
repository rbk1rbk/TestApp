package com.rbk.testapp;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class PicSync extends IntentService {
    public static final String STATE="State";
    public static final String NOTIFICATION = "com.rbk.testapp.MainScreen.receiver";

    private static String MyState="New";
    private static Context ParentContext;
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
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
            Log.i("PicSync","onHandleIntent: "+action);
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

    private void handleActionGetState(String param1, String param2) {
        Log.i("PicSync","handleActionGetState: "+MyState);
        PublishState(MyState);
    }

    private void handleActionStartSync(String param1, String param2) {
        MyState="Sync started";
        Log.i("PicSync","handleActionStartSync: "+MyState);
        PublishState(MyState);
    }

    private void handleActionStopSync(String param1, String param2) {
        MyState="Sync stopped";
        Log.i("PicSync","handleActionStopSync: "+MyState);
        PublishState(MyState);
    }
}
