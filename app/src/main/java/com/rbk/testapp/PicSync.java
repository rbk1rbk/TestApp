package com.rbk.testapp;

import android.Manifest;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

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
    public static final String STATE="State";
    public static final String NOTIFICATION = "com.rbk.testapp.MainScreen.receiver";

    private static String MyState = "Not running";
    private static Context ParentContext;

    static final String ACTION_GET_STATE = "com.rbk.testapp.PicSync.action.GetState";
    static final String ACTION_START_SYNC = "com.rbk.testapp.PicSync.action.Start";
    static final String ACTION_STOP_SYNC = "com.rbk.testapp.PicSync.action.Stop";
    static final String ACTION_START_SYNC_RESTART = "com.rbk.testapp.PicSync.action.Resync";


    // TODO: Rename parameters
    static final String EXTRA_PARAM1 = "com.rbk.testapp.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.rbk.testapp.extra.PARAM2";
    private static Handler h;
    private static String fileurl;
    static int READ_EXTERNAL_STORAGE_PERMISSION_CODE=1;


    private enum ePicSyncState {PIC_SYNC_STATE_STOPPED, PIC_SYNC_STATE_RUNNING, PIC_SYNC_STATE_NO_ACCESS };
    private static ePicSyncState PicSyncState=ePicSyncState.PIC_SYNC_STATE_STOPPED;

    public PicSync() {
        super("PicSync");
    }

    @Override
    public void onCreate(){
        super.onCreate();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            Log.i("PicSync", "No permission to READ_EXTERNAL_STORAGE");
            PicSyncState = ePicSyncState.PIC_SYNC_STATE_NO_ACCESS;
            MyState="No access to external storage";
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void PublishState(String State2Send) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(STATE, State2Send);
        sendBroadcast(intent);
    }

    private void makeToast(final String toastString){
        h = new Handler(this.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {Toast.makeText(PicSync.this,toastString,Toast.LENGTH_LONG).show();}
        });
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
                return;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                Log.i("PicSync", "No permission to READ_EXTERNAL_STORAGE");
                PicSyncState = ePicSyncState.PIC_SYNC_STATE_NO_ACCESS;
                MyState="No access to external storage";
                return;
            }
            if (ACTION_START_SYNC.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionStartSync(param1, param2);
                return;
            }
            if (ACTION_STOP_SYNC.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionStopSync(param1, param2);
                return;
            }
        }
    }
    private File[] listFiles(String path) {
        return null;
    }

    private String[] getStoragePaths(){
        /*
        First, gather all possible "external" storage locations
         */
        final Set<String> storagePathsSet = new HashSet<String>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            File[] externalMediaDirs = this.getExternalMediaDirs();
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            File[] externalMediaDirs = this.getExternalMediaDirs();
        }
        storagePathsSet.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        try {
            storagePathsSet.add(Environment.getExternalStorageDirectory().getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        storagePathsSet.add(System.getenv("EXTERNAL_STORAGE"));
        final String envSecondaryStorages = System.getenv("SECONDARY_STORAGE");
        if (!TextUtils.isEmpty(envSecondaryStorages)) {
            // All Secondary SD-CARDs splited into array
            final String[] rawSecondaryStorages = envSecondaryStorages.split(File.pathSeparator);
            for (String rawSecondaryStorage : rawSecondaryStorages) {
                if (!rawSecondaryStorage.toLowerCase().contains("usb"))
                    storagePathsSet.add(rawSecondaryStorage);
            }
        }

        return null;
    }
    private String[] getStorageLocations() {
        final Pattern DIR_SEPORATOR = Pattern.compile(":");
            // Final set of paths
            final Set<String> rv = new HashSet<String>();

            // Primary physical SD-CARD (not emulated)
            final String rawExternalStorage = System.getenv("EXTERNAL_STORAGE");

            // All Secondary SD-CARDs (all exclude primary) separated by ":"
            final String rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE");

            // Primary emulated SD-CARD
            final String rawEmulatedStorageTarget = System.getenv("EMULATED_STORAGE_TARGET");

            if (TextUtils.isEmpty(rawEmulatedStorageTarget)) {
                // Device has physical external storage; use plain paths.
                if (TextUtils.isEmpty(rawExternalStorage)) {
                    // EXTERNAL_STORAGE undefined; falling back to default.
                    rv.add(Environment.getExternalStorageDirectory().getAbsolutePath());
                } else {
                    rv.add(rawExternalStorage);
                }
            } else {
                // Device has emulated storage; external storage paths should have
                // userId burned into them.
                final String rawUserId;
/*
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    rawUserId = "";
                } else {
*/
                final String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                final String[] folders = DIR_SEPORATOR.split(path);
                final String lastFolder = folders[folders.length - 1];
                boolean isDigit = false;
                try {
                    Integer.valueOf(lastFolder);
                    isDigit = true;
                } catch (NumberFormatException ignored) {
                }
                rawUserId = isDigit ? lastFolder : "";
/*
                }
*/
                // /storage/emulated/0[1,2,...]
                if (TextUtils.isEmpty(rawUserId)) {
                    rv.add(rawEmulatedStorageTarget);
                } else {
                    rv.add(rawEmulatedStorageTarget + File.separator + rawUserId);
                }
            }
            // Add all secondary storages
            if (!TextUtils.isEmpty(rawSecondaryStoragesStr)) {
                // All Secondary SD-CARDs splited into array
                final String[] rawSecondaryStorages = rawSecondaryStoragesStr.split(File.pathSeparator);
                for (String rawSecondaryStorage : rawSecondaryStorages) {
                    if (!rawSecondaryStorage.toLowerCase().contains("usb"))
                        rv.add(rawSecondaryStorage);
                }
            }

        final File[] externaldirs1;
        final File[] externaldirs2;
        final File[] externaldirs3;
        final File externaldir;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean tst1, tst2, tst3;
            File[] path4, path5, path6;
            File path1, path2, path3, path7;

//api22

            path1 = Environment.getExternalStorageDirectory();
            tst1 = Environment.isExternalStorageRemovable(path1);

            path2 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "/");
            tst2 = Environment.isExternalStorageRemovable(path2);

            path3 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            tst3 = Environment.isExternalStorageEmulated(path3);

            path4 = this.getExternalMediaDirs();
            path5 = this.getExternalFilesDirs(Environment.DIRECTORY_DCIM);
            path6 = this.getObbDirs();

            tst1 = Environment.isExternalStorageRemovable(path1);
            //len aby sa neskoncil pri debugingu if
        }
/*
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            makeToast("Media not mounted");
            return;
        }
        else{
            makeToast("Media mounted");
        }
*/
        return rv.toArray(new String[rv.size()]);
    }
    private String[] getMediaLocations() {
    final Set<String> mediaLocationsSet = new HashSet<String>();
/*
    String[] storages = getStorageLocations();
    for (String storagePath : storages) {
        String[] subdirs = {"DCIM", "Pictures", "Camera", "camera"};
        for (String subdir : subdirs) {
            String dir2verify = storagePath + "/" + subdir;
            if (new File(dir2verify).isDirectory())
                mediaLocationsSet.add(dir2verify);
        }
    }
*/
        mediaLocationsSet.add("/sdcard/DCIM");
        mediaLocationsSet.add("/storage/0D63-F320/DCIM/100ANDRO");
        mediaLocationsSet.add("/storage/0D63-F320/DCIM/100_CFV5");
        mediaLocationsSet.add("/storage/0D63-F320/DCIM/Camera");
        mediaLocationsSet.add("/storage/0D63-F320/DCIM/Video");
    return mediaLocationsSet.toArray(new String[mediaLocationsSet.size()]);
}
    final FilenameFilter pictureFileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String pathname) {
            String lowercase = pathname.toLowerCase();
            if (lowercase.startsWith("."))
                return false;
            if (lowercase.endsWith("jpg"))
                return true;
            if (lowercase.endsWith("jpeg"))
                return true;
            if (lowercase.endsWith("png"))
                return true;
            if (lowercase.endsWith("raw"))
                return true;
            return new File(dir.getAbsolutePath()+"/"+pathname).isDirectory();
        }
    };
    String[] listPictures(String dir){
        Set<String> listOfFiles = new HashSet<String>();

        File[] filelist = new File(dir).listFiles(pictureFileFilter);
        String[] filesToAddTolistOfFiles;
        for (File entry : filelist ) {
            if (entry.isDirectory()) {
                filesToAddTolistOfFiles = listPictures(entry.getAbsolutePath());
                for (String fileToAddTolistOfFiles : filesToAddTolistOfFiles) {
                    listOfFiles.add(fileToAddTolistOfFiles);
                }
            }
            else {
                listOfFiles.add(entry.toString());
            }
        }

        return listOfFiles.toArray(new String[listOfFiles.size()]);
    }
    private void getListOfFilesToSync(){
/*
        String[] mediaDirs = getMediaLocations();
*/
        int numOfFiles = 0;
/*
        for (String mediaDir : mediaDirs){
            String[] mediaFiles=listPictures(mediaDir);
            numOfFiles+=mediaFiles.length;
*/
/*
            File path=new File(mediaDir);
            File[] filelist = path.listFiles(pictureFileFilter);
            numOfFiles+=filelist.length;
*/
/*
        }
*/
/*

        try {
//            Process process = Runtime.getRuntime().exec("find /storage/0D63-F320/ -iname '*.jpg' -o -iname '*.mp4'");
//            Process process = Runtime.getRuntime().exec(new String[]{"find","."});
//            Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh -c find \"storage/0D63-F320/\""});
            Process process;
//            process = Runtime.getRuntime().exec(new String[]{"/system/bin/echo 'find / -name DCIM' | /system/bin/sh"});
            process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh","-c","/system/bin/find / -name DCIM"});
            try {
//                process.wait(5000);
                process.getInputStream();
                process.waitFor();
            }catch (java.lang.InterruptedException e){
                    e.printStackTrace();
            }
            Integer exitVal = process.exitValue();
            Log.i("find retval ",exitVal.toString());
            if (exitVal == 0) {
                BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String thisLine = null;
                while ((thisLine = in.readLine()) != null) {
                    Log.i("picture found", thisLine);
                }
            }
    } catch (java.io.IOException e) {
        e.printStackTrace();
    }
        makeToast("Found " + numOfFiles + " files in "+ " media dirs.");
*/
    }
    public void StopSync() {
        Log.i("PicSync", "StopSync()");
    }

    public void DoSync(){
        Log.i("PicSync","Sync started");
        /*
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
    */
        getStoragePaths();
//        getStorageLocations();
//        getListOfFilesToSync();
//        makeToast("PicSync: Sync started");
        PublishState("Sync in progress");

        readTestFile();
        Log.i("PicSync","Sync finished");
        PublishState("Sync finished");
//        makeToast("PicSync: Sync finished");
    }

    private void handleActionGetState(String param1, String param2) {
        Log.i("PicSync","handleActionGetState: "+MyState);
        PublishState(MyState);
    }

    private void handleActionStartSync(String param1, String param2) {
        boolean doit=false;
        if (PicSyncState != ePicSyncState.PIC_SYNC_STATE_RUNNING)
            doit=true;

        if (param1 != null && param1.equals(ACTION_START_SYNC_RESTART))
            doit=true;

        if (doit){
            PicSyncState=ePicSyncState.PIC_SYNC_STATE_RUNNING;

            // TODO Prerobit na novy thread?
            MyState = "PicSync running";
            DoSync();
            MyState = "PicSync has ran";

            Log.i("PicSync", "handleActionStartSync: " + MyState);
            PublishState(MyState);
        }
    }

    private void handleActionStopSync(String param1, String param2) {
        if (PicSyncState == ePicSyncState.PIC_SYNC_STATE_RUNNING) {
            PicSyncState=ePicSyncState.PIC_SYNC_STATE_STOPPED;

            StopSync();

            MyState = "PicSync not running";
            Log.i("PicSync", "handleActionStopSync: " + MyState);
            PublishState(MyState);
        }
    }

    public void readTestFile() {
        SharedPreferences settings = getSharedPreferences(MainScreen.prefsSMBPREFS, 0);
        String smbservername=settings.getString(MainScreen.prefsSMBSRV,"192.168.0.1");
        String smbuser=settings.getString(MainScreen.prefsSMBUSER,"");
        String smbpasswd=settings.getString(MainScreen.prefsSMBPWD,"PASSWORD");
        Log.i("PicSync","Settings retrieved: "+smbuser+":"+smbpasswd+"@"+smbservername);


        Log.i("PicSync","readTestFile: start");
//        jcifs.Config.setProperty("jcifs.netbios.wins", "192.168.0.1");
        jcifs.Config.setProperty("jcifs.netbios.wins", smbservername);
//        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null,null, null);
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null,smbuser, smbpasswd);
        SmbFile[] domains = null;
        try {
                domains = (new SmbFile("smb://NET01/",auth)).listFiles();
            } catch (SmbException e1) {
                e1.printStackTrace();
                makeToast("PicSync: connectivity issue: " + e1.getMessage());

            return;
            } catch(MalformedURLException e) {
                e.printStackTrace();
                Log.i("PicSync", "Domain NOT listed" + e.getMessage());
                return;
        }
        SmbFile sfile;
        SmbFileInputStream in;
        try {
            fileurl="smb://"+smbservername+"/testexport/somefile.txt";
            Log.i("PicSync","Opening file: "+fileurl);
//            in = new SmbFileInputStream(new SmbFile("smb://192.168.0.1/testexport/somefile.txt"),auth);
            sfile = new SmbFile(fileurl,auth);
            Log.i("PicSync","File opened");
            makeToast("PicSync: " + PicSync.fileurl + " opened");
        }catch(MalformedURLException e) {
            e.printStackTrace();
            Log.i("PicSync", "File NOT opened " + e.getMessage());
            makeToast("PicSync: File NOT opened " + e.getMessage());
            return;
        }
        try {
            in=new SmbFileInputStream(sfile);
        }catch(MalformedURLException e) {
            e.printStackTrace();
            Log.i("PicSync", "File NOT opened " + e.getMessage());
            return;
        }catch(SmbException e) {
            e.printStackTrace();
            Log.i("PicSync","File NOT opened " + e.getMessage());
            return;
        }catch(UnknownHostException e){
            e.printStackTrace();
            Log.i("PicSync","File NOT opened " + e.getMessage());
            return;
        }
        byte[] b = new byte[8192];
        int n;
        try {
            while ((n = in.read(b)) > 0) {
                System.out.write(b, 0, n);
            }
        }catch (IOException e){
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
