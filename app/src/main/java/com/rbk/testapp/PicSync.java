package com.rbk.testapp;

import android.Manifest;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

import static com.rbk.testapp.PicSync.NASService.checkConnection;
import static java.lang.Thread.sleep;

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
    private final Context MyContext = this;
	private final String timestampFileName = "timestampFile";
	private static File timestampFile;
	private static IBinder myBinder;

    static final String ACTION_GET_STATE = "com.rbk.testapp.PicSync.action.GetState";
    static final String ACTION_START_SYNC = "com.rbk.testapp.PicSync.action.Start";
    static final String ACTION_STOP_SYNC = "com.rbk.testapp.PicSync.action.Stop";
    static final String ACTION_START_SYNC_RESTART = "com.rbk.testapp.PicSync.action.Resync";
	static final String ACTION_BROWSE_CIFS = "com.rbk.testapp.PicSync.action.BrowseCIFS";
	static final String ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS = "com.rbk.testapp.PicSync.action.addMediaFolders";
	static final String ACTION_GET_STORAGE_PATHS = "com.rbk.testapp.PicSync.action.getStoragePaths";
	static final String ACTION_GET_NAS_CONNECTION = "com.rbk.testapp.PicSync.action.getNASConnection";
	static final String ACTION_SET_LAST_IMAGE_TIMESTAMP = "com.rbk.testapp.PicSync.action.setLastImageTimestamp";


    static String smblocalhostname = "testovacimobil";
    static String picSyncLogFile = "testapp."+smblocalhostname+".log";

    static String smbservername=null;
    static String smbuser=null;
    static String smbpasswd=null;
    static String smbshare=null;
    static String smbshareurl=null;
	static NtlmPasswordAuthentication auth = null;
	static boolean authenticated = false;
	static boolean isNASConnected = false;

	static final int cifsAllowedBrowsables = SmbFile.TYPE_WORKGROUP | SmbFile.TYPE_SERVER | SmbFile.TYPE_SHARE | SmbFile.TYPE_FILESYSTEM;
	static final int cifsAllowedBrowsablesForUp = SmbFile.TYPE_SERVER | SmbFile.TYPE_SHARE | SmbFile.TYPE_FILESYSTEM;
	private List values;
	Date lastCopiedImageTimestamp;

    // TODO: Rename parameters
    static final String EXTRA_PARAM1 = "com.rbk.testapp.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.rbk.testapp.extra.PARAM2";
    private static Handler h;
    private static String fileurl;
    static int READ_EXTERNAL_STORAGE_PERMISSION_CODE=1;
	private static SharedPreferences settings;
	private static boolean SharedPreferencesChanged=true;


	private static Integer mediaFilesCountTotal = 0;
	private static Integer mediaFilesScanned = 0;
	private static Integer mediaFilesCountToSync = 0;
	private static MediaFilesDBclass MediaFilesDB;

	private enum ePicSyncState {PIC_SYNC_STATE_STOPPED, PIC_SYNC_STATE_RUNNING, PIC_SYNC_STATE_NO_ACCESS, PIC_SYNC_STATE_STOP}

	;
	private static ePicSyncState PicSyncState=ePicSyncState.PIC_SYNC_STATE_STOPPED;

	private final NASService nasService = new NASService(MyContext);
    public PicSync() {
        super("PicSync");
/*
		nasService = new NASService();
*/
    }

	SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
					if (key.contains("smb") || key.contains("SMB")) {
						SharedPreferencesChanged=true;
					}
				}
			};
    @Override
	public void onCreate() {
		super.onCreate();
		Log.i("PicSync", "onCreate");
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
			Log.i("PicSync", "No permission to READ_EXTERNAL_STORAGE");
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_NO_ACCESS;
			MyState = "No access to external storage";
		}
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.registerOnSharedPreferenceChangeListener(prefChangeListener);
		Intent intent = new Intent(this, PicSyncScheduler.class);
		timestampFile = new File(getFilesDir(), timestampFileName);
		try {
			if (!timestampFile.exists()) {
				timestampFile.createNewFile();
				timestampFile.setLastModified(0);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		lastCopiedImageTimestamp = new Date(timestampFile.lastModified());
		MediaFilesDB = new MediaFilesDBclass(MyContext);
		broadcastLastCopiedImageTimestamp();
		startService(intent);
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		Log.i("PicSync", "onStartCommand");
		myBinder = new LocalBinder();
		return START_STICKY;
	}

	public class LocalBinder extends Binder {
		PicSync getService() {
			return PicSync.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return myBinder;
	}

	SmbFileFilter browseCIFSFilter = new SmbFileFilter() {
		@Override
		public boolean accept(SmbFile file) {
			int filetype = 0;
			try {
				filetype = file.getType();
			} catch (SmbException e) {
				e.printStackTrace();
				if (e.toString().contains("Logon failure")) {
					authenticated = false;
					isNASConnected = false;
				}
				return false;
			}
			String filepath = file.getPath();
			String filename = file.getName();
			if (filename.endsWith("/"))
				filename = TextUtils.substring(filename, 0, TextUtils.lastIndexOf(filename, '/'));
			int tmp = filetype & cifsAllowedBrowsables;
			if ((filetype & cifsAllowedBrowsables) == 0)
				return false;
			if (filename.startsWith(".") || filename.contains("$"))
				return false;
			return true;
		}
	};

	public void BrowseCIFS(String path) {
		if (values == null)
			values = new ArrayList();
		else
			values.clear();
		if (!path.endsWith(File.separator))
			path = path + File.separator;
		SmbFile dir;
		SmbFile[] list;
		String servername = "";
		String sharename = "";
		int smbtype = 0;
		boolean canReadDir = true;
		try {
			NASService.openConnection();
			//Check, whether it is not a smb://server/../ format
			String pathToCheckWithoutDoubleDots = path.replaceAll("\\.\\.\\/$", "");
			if (path.endsWith(".." + File.separator) && (new SmbFile(pathToCheckWithoutDoubleDots, auth).getType() & SmbFile.TYPE_SERVER) == SmbFile.TYPE_SERVER)
				path = new SmbFile(pathToCheckWithoutDoubleDots, auth).getParent();
			if (TextUtils.equals(path.toLowerCase(), "smb://../"))
				path = "smb://";
			path = new SmbFile(path).getCanonicalPath();
			dir = new SmbFile(path, auth);
/*
			canReadDir = dir.canRead();
			if (!canReadDir) {
				return;
			}
*/
			servername = dir.getServer();
			sharename = dir.getShare();
			list = dir.listFiles(browseCIFSFilter);
			if (list != null) {
				for (SmbFile file : list) {
/*
					SmbFile file2verify;
					if (new SmbFile(path,auth).getType() == SmbFile.TYPE_WORKGROUP)
						file2verify = new SmbFile("smb://"+file+File.separator, auth);
					else
						file2verify = new SmbFile(path + file+File.separator, auth);
*/
					smbtype = file.getType();
/*
					boolean isDirectory = file2verify.isDirectory();
					boolean isHidden = file2verify.isHidden();
					boolean isItRealDirectory = false;
					if (isDirectory)
						isItRealDirectory = isRealDirectory(file2verify.getCanonicalPath());
*/
/*
					if ((!file.startsWith(".")) && (!file.startsWith("$")) && (!file.endsWith("$")) && ((smbtype == SmbFile.TYPE_WORKGROUP) || (smbtype == SmbFile.TYPE_SERVER) || (smbtype == SmbFile.TYPE_SHARE) || (smbtype == SmbFile.TYPE_FILESYSTEM)))
*/
					values.add(file.getName());
				}
				int tmp = smbtype & cifsAllowedBrowsablesForUp;
				if ((smbtype & cifsAllowedBrowsablesForUp) != 0)
					values.add("..");
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (SmbException e) {
			e.printStackTrace();
		}
		broadcastConnectionStatus();
		Collections.sort(values);
		Intent returnCIFSListIntent = new Intent("CIFSList");
		returnCIFSListIntent.putExtra("cifsList", (String[]) values.toArray(new String[values.size()]));
		returnCIFSListIntent.putExtra("smbCanonicalPath", path);
		returnCIFSListIntent.putExtra("smbType", smbtype);
		returnCIFSListIntent.putExtra("servername", servername);
		returnCIFSListIntent.putExtra("sharename", sharename);
		LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnCIFSListIntent);
	}

	private void broadcastState(String State2Send) {
		Intent intent = new Intent(NOTIFICATION);
		intent.putExtra("Message", "msgState");
		intent.putExtra(STATE, State2Send);
		sendBroadcast(intent);
	}

	private void broadcastLastCopiedImageTimestamp() {
		Intent intent = new Intent(NOTIFICATION);
		intent.putExtra("Message", "msgLastCopiedImageTimestamp");
		intent.putExtra("lastCopiedImageTimestamp", lastCopiedImageTimestamp.getTime());

        sendBroadcast(intent);
    }
	private void broadcastConnectionStatus() {
		Intent updateIntent = new Intent(NOTIFICATION);
		updateIntent.putExtra("Message", "isNASConnected");
		updateIntent.putExtra("isNASConnected", isNASConnected);
		sendBroadcast(updateIntent);
	}
	private void broadcastCopyInProgress(String srcFile, String tgtFile) {
		Intent updateIntent = new Intent(NOTIFICATION);
		updateIntent.putExtra("Message", "msgCopyInProgress");
		updateIntent.putExtra("srcFile", srcFile);
		updateIntent.putExtra("tgtFile", tgtFile);
		sendBroadcast(updateIntent);
	}

	private void broadcastMediaFilesCount(Integer total, Integer toscan, Integer tosync) {
		Intent updateIntent = new Intent(NOTIFICATION);
		updateIntent.putExtra("Message", "msgImagesCounts");
		updateIntent.putExtra("TotalImages", total);
		updateIntent.putExtra("ScannedImages", toscan);
		updateIntent.putExtra("UnsyncedImages", tosync);
		sendBroadcast(updateIntent);
	}

	private void makeToast(final String toastString){
        h = new Handler(MyContext.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {Toast.makeText(MyContext,toastString,Toast.LENGTH_LONG).show();}
        });
    }
    @Override
    protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			final String action = intent.getAction();
            Log.i("PicSync","onHandleIntent: "+action);

			if (ACTION_BROWSE_CIFS.equals(action)) {
				final String path = intent.getStringExtra("path");
				BrowseCIFS(path);
				return;
			}
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
				WoL();
                handleActionStartSync(param1, param2);
                return;
            }
            if (ACTION_STOP_SYNC.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionStopSync(param1, param2);
                return;
            }
			if (ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS.equals(action)) {
				addMediaFoldersToSettings();
				return;
			}
			if (ACTION_GET_STORAGE_PATHS.equals(action)) {
				String[] storagePaths = getStoragePaths();
				Intent returnstoragePathsIntent = new Intent("storagePaths");
				returnstoragePathsIntent.putExtra("storagePaths", storagePaths);
				LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnstoragePathsIntent);
				return;
			}
			if (ACTION_GET_NAS_CONNECTION.equals(action)){
				checkConnection();
				broadcastConnectionStatus();
			}
			if (ACTION_SET_LAST_IMAGE_TIMESTAMP.equals(action)) {
				long timestamp = intent.getLongExtra("lastCopiedImageTimestamp", 0);
				if (timestamp != 0) {
					saveLastCopiedImageTimestamp(timestamp);
					broadcastLastCopiedImageTimestamp();
				}
			}
		}
    }
	private void addMediaFoldersToSettings() {
		String[] MediaFolderList = getMediaPaths(getStoragePaths());
		Intent returnMediaFoldersListIntent = new Intent("getMediaFoldersList");
		returnMediaFoldersListIntent.putExtra("MediaFoldersList", MediaFolderList);
		LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnMediaFoldersListIntent);
	}
    private String[] getStoragePaths(){
        String canonicalPath, aPath, absolutePath;
        /*
        First, gather all possible "external" storage locations
         */
		/*final*/
		SortedSet<String> storagePathsSet = new TreeSet<String>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            File[] externalMediaDirs = this.getExternalMediaDirs();
            for (File externalMediaDir : externalMediaDirs){
                try {
                    canonicalPath = externalMediaDir.getCanonicalPath();
                    int needle = TextUtils.indexOf(canonicalPath,"/Android",0);
                    canonicalPath = TextUtils.substring(canonicalPath,0,needle);
                    storagePathsSet.add(canonicalPath);
                } catch (IOException e) {
                    Log.i("getStoragePaths","externalMediaDir.getAbsolutePath()",e);
                    e.printStackTrace();
                }
            }
        }

        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        if (!Environment.isExternalStorageEmulated()) {
            try {
                canonicalPath = externalStorageDirectory.getCanonicalPath();
                storagePathsSet.add(canonicalPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String envExternalStorage=System.getenv("EXTERNAL_STORAGE");
        try {
            canonicalPath=new File(envExternalStorage).getCanonicalPath();
            storagePathsSet.add(canonicalPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*final */
		String envSecondaryStorages = System.getenv("SECONDARY_STORAGE");
		if (!TextUtils.isEmpty(envSecondaryStorages)) {
            // All Secondary SD-CARDs splited into array
			/*final*/
			String[] rawSecondaryStorages = envSecondaryStorages.split(File.pathSeparator);
            for (String rawSecondaryStorage : rawSecondaryStorages) {
                if (!rawSecondaryStorage.toLowerCase().contains("usb"))
                    try {
                        canonicalPath=new File(rawSecondaryStorage).getCanonicalPath();
                        storagePathsSet.add(canonicalPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }
        return storagePathsSet.toArray(new String[storagePathsSet.size()]);
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
        return rv.toArray(new String[rv.size()]);
    }

	final FilenameFilter pictureFileFilterWithTimestamp = new FilenameFilter() {
		@Override
        public boolean accept(File dir, String pathname) {
            String dirname = dir.getAbsolutePath().toLowerCase();
            String lowercase = pathname.toLowerCase();
            String fullpath=dirname+"/"+lowercase;
			File f = new File(dir.getAbsolutePath() + "/" + pathname);
			boolean isItDirectory = f.isDirectory();
			if (!isItDirectory) {
				if (f.lastModified() <= lastCopiedImageTimestamp.getTime()) {
					return false;
				}
			} else
				return true;
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
			if (lowercase.endsWith("mp4"))
				return true;
			if (lowercase.endsWith("mpg"))
				return true;
			if (lowercase.endsWith("avi"))
				return true;
			return false;
		}
	};

	final FilenameFilter pictureFileFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String pathname) {
			String dirname = dir.getAbsolutePath().toLowerCase();
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
            if (lowercase.endsWith("mp4"))
                return true;
            if (lowercase.endsWith("mpg"))
                return true;
            if (lowercase.endsWith("avi"))
                return true;
			return new File(dir.getAbsolutePath() + "/" + pathname).isDirectory();
		}
    };

    final FilenameFilter pictureFolderFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String pathname) {
            String dirname = dir.getAbsolutePath().toLowerCase();
            String lowercase = pathname.toLowerCase();
            String fullpath=dirname+"/"+lowercase;
            File fullpathFile=new File(dir.getAbsolutePath()+"/"+pathname);
            if (!fullpathFile.isDirectory())
                return false;
            if (fullpath.startsWith("."))
                return false;
            if (fullpath.contains("images") || fullpath.contains("dcim") || fullpath.contains("pictures") || fullpath.contains("video"))
                return true;
            else
                return false;
        }
    };

	String[] listPictures(String dir, String params) {
		Set<String> listOfFiles = new HashSet<String>();
		File[] filelist;
		if ((params != null) && (params.equals("TimeStampCondition")))
			filelist = new File(dir).listFiles(pictureFileFilterWithTimestamp);
		else
			filelist = new File(dir).listFiles(pictureFileFilter);

        String[] filesToAddTolistOfFiles;
        for (File entry : filelist ) {
            if (entry.isDirectory()) {
				filesToAddTolistOfFiles = listPictures(entry.getAbsolutePath(), params);
				Collections.addAll(listOfFiles, filesToAddTolistOfFiles);
            }
            else {
                listOfFiles.add(entry.toString());
            }
        }
        return listOfFiles.toArray(new String[listOfFiles.size()]);
    }

	String[] listPictures(String dir) {
		return listPictures(dir, "");
	}

	private String[] getMediaPaths(String storagePath){
        Set<String> mediaPathsSet = new HashSet<String>();
        File[] filelist = new File(storagePath).listFiles(pictureFolderFilter);
        if (filelist == null)
            return null;
        for (File entry : filelist) {
            if (entry.isDirectory()){
                getMediaPaths(entry.getAbsolutePath());
            }
            mediaPathsSet.add(entry.getAbsolutePath());
        }
        return mediaPathsSet.toArray(new String[mediaPathsSet.size()]);
    }
    private String[] getMediaPaths(String[] storagePaths){
		Set<String> mediaPathsSet = new HashSet<String>();
/*
		SortedSet<String> mediaPathsSet = new TreeSet<String>();
*/
		for (String storagePath : storagePaths) {
			String[] mediaPaths=getMediaPaths(storagePath);
			if (mediaPaths!=null)
            	Collections.addAll(mediaPathsSet, mediaPaths);
        }
        return mediaPathsSet.toArray(new String[mediaPathsSet.size()]);
    }
	private Set<String> listMediaFilesToSync() {
		broadcastState("Scanning media");
		mediaFilesCountTotal = 0;
		mediaFilesScanned = 0;
		mediaFilesCountToSync = 0;
		SmbFile tgtMediaFile;
		Set<String> mediaPaths = settings.getStringSet("prefFolderList", null);
		if (mediaPaths == null) {
			broadcastCopyInProgress("none", "none");
			return null;
		}
		Set<String> fileListToSync = new HashSet<String>();

		for (String mediaPath : mediaPaths) {
			String[] mediaFiles = listPictures(mediaPath);
			mediaFilesCountTotal += mediaFiles.length;
		}
		broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);

		String tgtPath = settings.getString("prefsSMBURI", null);
		for (String mediaPath : mediaPaths) {
/*
			String[] mediaFiles = listPictures(mediaPath, "TimeStampCondition");
*/
			String[] mediaFiles = listPictures(mediaPath);
			for (String srcMediaFileFull : mediaFiles) {
				if (!isNASConnected)
					try {
						NASService.openConnection();
					} catch (MalformedURLException e) {
						e.printStackTrace();
						return null;
					}
				if ((PicSyncState != ePicSyncState.PIC_SYNC_STATE_RUNNING) || (!isNASConnected))
					return null;
				File mediaFile = new File(srcMediaFileFull);
				String srcMediaFileName = mediaFile.getName();
				String tgtMediaFileNameFull = tgtPath + "/" + srcMediaFileName;
				Log.d("DoSync", srcMediaFileFull);
				mediaFilesScanned++;
				try {
					boolean tgtFileExists = false;
					tgtMediaFile = new SmbFile(tgtMediaFileNameFull, auth);
					try {
						tgtFileExists = tgtMediaFile.exists();
					} catch (SmbException e) {
						e.printStackTrace();
						if (e.toString().contains("Logon failure")) {
							authenticated = false;
							isNASConnected = false;
							tgtFileExists = true;
						}
					} finally {
						if (!tgtFileExists)
							fileListToSync.add(srcMediaFileFull);
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
					PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
					broadcastState("NAS error");
					return null;
				}
				mediaFilesCountToSync = fileListToSync.size();
				broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
			}
		}
		/*TODO
		Sort by timestamp!!!
		 */
		return fileListToSync;
	}

	private void getMediaFilesToSync() {
		broadcastState("Scanning media");
		mediaFilesCountTotal = 0;
		mediaFilesScanned = 0;
		mediaFilesCountToSync = 0;
		SmbFile tgtMediaFile;
		Set<String> mediaPaths = settings.getStringSet("prefFolderList", null);
		SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
		if (mediaPaths == null) {
			broadcastCopyInProgress("none", "none");
			return;
		}

		String tgtPath = settings.getString("prefsSMBURI", null);
		long newRowId;
		for (String mediaPath : mediaPaths) {
			String[] mediaFiles = listPictures(mediaPath);
			for (String srcMediaFileNameFull : mediaFiles) {
				File mediaFile = new File(srcMediaFileNameFull);
/*
				String srcMediaFileName = srcMediaFileNameFull.substring(srcMediaFileNameFull.lastIndexOf('/') + 1);
*/
				String srcMediaFileName = mediaFile.getName();
				String tgtMediaFileNameFull = tgtPath + "/" + srcMediaFileName;
				Long lastModified = mediaFile.lastModified();
				ContentValues mediaFilePair = new ContentValues();
				mediaFilePair.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC, srcMediaFileNameFull);
				mediaFilePair.put(Constants.MediaFilesDBEntry.COLUMN_NAME_TGT, tgtMediaFileNameFull);
				mediaFilePair.put(Constants.MediaFilesDBEntry.COLUMN_NAME_TS, lastModified);
				mediaFilePair.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SYNC, false);
				mediaFilesScanned++;
				newRowId = db.insert(Constants.MediaFilesDBEntry.TABLE_NAME, null, mediaFilePair);
			}
			Cursor c = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME, null);
			c.moveToFirst();
			mediaFilesCountTotal = c.getInt(0);
			c.close();
			Cursor cToSync = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
					+ "where " + Constants.MediaFilesDBEntry.COLUMN_NAME_SYNC + "==0", null);
			cToSync.moveToFirst();
			mediaFilesCountToSync = cToSync.getInt(0);
			cToSync.close();
			Cursor cHighestSyncedTimestamp = db.rawQuery("select max("
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_TS + ")"
							+ " from "
							+ Constants.MediaFilesDBEntry.TABLE_NAME
							+ " where "
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SYNC + "==1"
					, null);
			cHighestSyncedTimestamp.moveToFirst();
			long highestSyncedTimestamp = cHighestSyncedTimestamp.getLong(0);
			if (highestSyncedTimestamp == 0)
				saveLastCopiedImageTimestamp(1);
			else
				saveLastCopiedImageTimestamp(highestSyncedTimestamp);
			cHighestSyncedTimestamp.close();
			broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
		}
		/*TODO
		Sort by timestamp!!!
		 */
	}
	private boolean copyFileToNAS(String src, String tgt) {
		try {
			sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	public void StopSync() {
		PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOP;
		Log.i("PicSync", "StopSync()");
	}
	public void DoSync() {
		Log.i("PicSync", "Sync initiaged");
		getMediaFilesToSync();
		Set<String> mediaFilesToSync = listMediaFilesToSync();
		if (mediaFilesToSync != null) {
			for (String srcMediaFileFull : mediaFilesToSync) {
				if (!isNASConnected)
					try {
						NASService.openConnection();
					} catch (MalformedURLException e) {
						e.printStackTrace();
						return;
					}
				if ((PicSyncState != ePicSyncState.PIC_SYNC_STATE_RUNNING) || (!isNASConnected))
					return;
				File mediaFile = new File(srcMediaFileFull);
				String srcMediaFileName = mediaFile.getName();
				String tgtPath = settings.getString("prefsSMBURI", null);
				String tgtMediaFileNameFull = tgtPath + "/" + srcMediaFileName;
				if (copyFileToNAS(srcMediaFileFull, tgtMediaFileNameFull)) {
					mediaFilesCountToSync--;
					broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
					broadcastCopyInProgress(srcMediaFileFull, tgtMediaFileNameFull);
					long srcFileLastModified = new File(srcMediaFileFull).lastModified();
					if (srcFileLastModified > lastCopiedImageTimestamp.getTime()) {
						saveLastCopiedImageTimestamp(srcFileLastModified);
						broadcastLastCopiedImageTimestamp();
					}
				}
			}
		}
		broadcastState("Sync finished");
	}

    private void handleActionGetState(String param1, String param2) {
        Log.i("PicSync","handleActionGetState: "+MyState);
		broadcastState(MyState);
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
/*			HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
			thread.start();
*/
			DoSync();
			MyState = "PicSync has ran";
            Log.i("PicSync", "handleActionStartSync: " + MyState);
			broadcastState(MyState);
		}
    }

    private void handleActionStopSync(String param1, String param2) {
        if (PicSyncState == ePicSyncState.PIC_SYNC_STATE_RUNNING) {
            PicSyncState=ePicSyncState.PIC_SYNC_STATE_STOPPED;

            StopSync();

            MyState = "PicSync not running";
            Log.i("PicSync", "handleActionStopSync: " + MyState);
			broadcastState(MyState);
		}
    }

    private void saveLastCopiedImageTimestamp(Date timestamp){
/*
		if (lastCopiedImageTimestamp.getTime() > timestamp.getTime())
			return;
*/
		lastCopiedImageTimestamp=timestamp;
		timestampFile.setLastModified(lastCopiedImageTimestamp.getTime());
	}
    private void saveLastCopiedImageTimestamp(){
        saveLastCopiedImageTimestamp(new Date());
    }

	private void saveLastCopiedImageTimestamp(long timestamp) {
		saveLastCopiedImageTimestamp(new Date(timestamp));
	}

	private void writeLogFile(String message){
        SmbFile smbLogFile;
        SmbFileOutputStream smbLogFileOutputStream;
        try{
			NASService.openConnection();
            smbLogFile=new SmbFile(smbshareurl+"/"+picSyncLogFile,auth);
//            smbLogFile.setAttributes();
            smbLogFileOutputStream= (SmbFileOutputStream) smbLogFile.getOutputStream();
			broadcastConnectionStatus();
		}catch (IOException e){
            e.printStackTrace();
			broadcastConnectionStatus();
			return;
        }
/*
        try {
            smbLogFile.createNewFile();
        } catch (SmbException e) {
            e.printStackTrace();
        }
*/
        try {
            smbLogFileOutputStream.write(Long.valueOf(lastCopiedImageTimestamp.getTime()).toString().getBytes());
            smbLogFileOutputStream.close();
        }catch (IOException e){
            e.printStackTrace();
        }
		broadcastConnectionStatus();
	}
    public void readTestFile() {
        SmbFile sfile;
        SmbFileInputStream in;
        try {
			NASService.openConnection();
            fileurl="smb://"+smbservername+"/testexport/somefile.txt";
            Log.i("PicSync","Opening file: "+fileurl);
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
		} catch (MalformedURLException | SmbException | UnknownHostException e) {
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
        }catch (IOException e){
            e.printStackTrace();
        }
    }
	public static final int PORT = 9;

	public void WoL() {

		String ipStr = "192.168.0.255";
		String macStr = "1c:6f:65:90:a8:f9";

		try {
			byte[] macBytes = getMacBytes(macStr);
			byte[] bytes = new byte[6 + 16 * macBytes.length];
			for (int i = 0; i < 6; i++) {
				bytes[i] = (byte) 0xff;
			}
			for (int i = 6; i < bytes.length; i += macBytes.length) {
				System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
			}

			InetAddress address = InetAddress.getByName(ipStr);
			DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
			DatagramSocket socket = new DatagramSocket();
			socket.send(packet);
			socket.close();

			Log.d("WoL", "Wake-on-LAN packet sent.");
		} catch (Exception e) {
			Log.d("WoL", "Failed to send Wake-on-LAN packet:" + e);
		}

	}

	private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
		byte[] bytes = new byte[6];
		String[] hex = macStr.split("(\\:|\\-)");
		if (hex.length != 6) {
			throw new IllegalArgumentException("Invalid MAC address.");
		}
		try {
			for (int i = 0; i < 6; i++) {
				bytes[i] = (byte) Integer.parseInt(hex[i], 16);
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid hex digit in MAC address.");
		}
		return bytes;
	}

	/*
		************************************************************************************************
	*/
	private static class MediaFilesDBclass extends SQLiteOpenHelper {
		public static final int DATABASE_VERSION = 1;
		public static final String DATABASE_NAME = "PicSync.db";

		public MediaFilesDBclass(Context ctx) {
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		}

		public void onCreate(SQLiteDatabase db) {

			final String TABLE_CREATE = "create table " + Constants.MediaFilesDBEntry.TABLE_NAME + "("
					+ Constants.MediaFilesDBEntry.TABLE_NAME + "_ID INTEGER PRIMARY KEY, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_TS + " INTEGER, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SYNC + " INTEGER, "
					+ "CONSTRAINT srcFile_unique UNIQUE (" + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC + ")"
					+ ")";
			db.execSQL(TABLE_CREATE);

		}

		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("backup data");
			db.execSQL("delete");
			onCreate(db);
			db.execSQL("restore data");
		}

		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			onUpgrade(db, oldVersion, newVersion);
		}
	}
/*
	************************************************************************************************
*/
static class NASService {
	private static Context MyContext;
	NASService(Context ctx) {
		MyContext = ctx;

/*
		if (settings == null)
			settings = PreferenceManager.getDefaultSharedPreferences(MyContext);
*/
	}

	public static boolean checkConnection(){
		isNASConnected = true;
		try {
			(new SmbFile("smb://"+smbservername+"/", auth)).list();
		} catch (SmbException | MalformedURLException e) {
			e.printStackTrace();
			isNASConnected = false;
			authenticated = false;
		}
		return isNASConnected;
	}
	public static void openConnection() throws MalformedURLException {
		if ((authenticated) && (!SharedPreferencesChanged))
			return;

		smbservername = settings.getString(MyContext.getString(R.string.pref_cifs_server), "");
		smbuser = settings.getString(MyContext.getString(R.string.pref_cifs_user), "");
		smbpasswd = settings.getString(MyContext.getString(R.string.pref_cifs_password), "guest");
		smbshare = settings.getString(MyContext.getString(R.string.pref_cifs_share), "");

		if (smbshare.equals(""))
			smbshareurl = "smb://" + smbservername + "/";
		else
			smbshareurl = "smb://" + smbservername + "/" + smbshare + "/";

		jcifs.Config.setProperty("jcifs.netbios.wins", smbservername);
		String exceptionString;
		if (((auth == null) || SharedPreferencesChanged) && (!smbuser.isEmpty()))
			auth = new NtlmPasswordAuthentication(null, smbuser, smbpasswd);
		SharedPreferencesChanged = false;
		isNASConnected = true;
		SmbFile[] domains = null;
		SmbFile domainsFile = null;
		try {
			domainsFile = new SmbFile("smb:///", auth);
		}catch (MalformedURLException e) {
			isNASConnected = false;
			throw e;
		};
		try{
			domains = domainsFile.listFiles();
			authenticated = true;
		} catch (SmbException e) {
			isNASConnected = false;
			e.printStackTrace();
			if (e.toString().contains("Logon failure")) {
				makeToast("Check username and password settings");
			} else
				makeToast("PicSync: connectivity issue: " + e.getMessage());
			authenticated = false;
			return;
		}
	}

	/*
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

	*/
	public static void WoL() {

		String ipStr = "192.168.0.255";
		String macStr = "1c:6f:65:90:a8:f9";

		try {
			byte[] macBytes = getMacBytes(macStr);
			byte[] bytes = new byte[6 + 16 * macBytes.length];
			for (int i = 0; i < 6; i++) {
				bytes[i] = (byte) 0xff;
			}
			for (int i = 6; i < bytes.length; i += macBytes.length) {
				System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
			}

			InetAddress address = InetAddress.getByName(ipStr);
			DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
			DatagramSocket socket = new DatagramSocket();
			socket.send(packet);
			socket.close();

			Log.d("WoL", "Wake-on-LAN packet sent.");
		} catch (Exception e) {
			Log.d("WoL", "Failed to send Wake-on-LAN packet:" + e);
		}
	}
	private static void makeToast(final String toastString){
		h = new Handler(MyContext.getMainLooper());
		h.post(new Runnable() {
			@Override
			public void run() {Toast.makeText(MyContext,toastString,Toast.LENGTH_LONG).show();}
		});
	}
}
}
