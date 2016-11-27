package com.rbk.testapp;

import android.Manifest;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import jcifs.UniAddress;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

import static java.lang.Thread.sleep;

public class PicSync extends IntentService {
	private static final int mId=1;
	public static final String STATE = "State";
	public static final String NOTIFICATION = "com.rbk.testapp.MainScreen.receiver";
	public static final String INTENT_PARENT = "IntentParent";
	static final String ACTION_GET_STATE = "PicSync.GetState";
	static final String ACTION_START_SYNC = "PicSync.Start";
	static final String ACTION_START_SYNC_FLAG = "PicSync.Start.Restart";
	static final String ACTION_STOP_SYNC = "PicSync.Stop";
	static final String ACTION_START_SYNC_RESTART = "PicSync.Resync";
	static final String ACTION_BROWSE_CIFS = "PicSync.BrowseCIFS";
	static final String ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS = "PicSync.addMediaFolders";
	static final String ACTION_GET_STORAGE_PATHS = "PicSync.getStoragePaths";
	static final String ACTION_GET_NAS_CONNECTION = "PicSync.getNASConnection";
	static final String ACTION_SUGGEST_MEDIA_SCAN = "PicSync.suggestMediaScan";
	static final String ACTION_SET_LAST_IMAGE_TIMESTAMP = "PicSync.setLastImageTimestamp";
	static final String ACTION_UPDATE_WOL = "PicSync.updateWOL";
	static final int cifsAllowedBrowsables = SmbFile.TYPE_WORKGROUP | SmbFile.TYPE_SERVER | SmbFile.TYPE_SHARE | SmbFile.TYPE_FILESYSTEM;
	static final int cifsAllowedBrowsablesForUp = SmbFile.TYPE_SERVER | SmbFile.TYPE_SHARE | SmbFile.TYPE_FILESYSTEM;
	static final int cifsAllowedToSelect = SmbFile.TYPE_SHARE | SmbFile.TYPE_FILESYSTEM;
	static String smblocalhostname = "testovacimobil";
	static String picSyncLogFile = "testapp." + smblocalhostname + ".log";
	static String smbservername = null;
	static String smbuser = null;
	static String smbpasswd = null;
	static String smbshare = null;
	static String smbshareurl = null;
	static String tgtNASPath = null;
	static boolean isWoLallowed = false;
	static boolean preferenceInitialized = false;
	static NtlmPasswordAuthentication auth = null;
	static boolean authenticated = false;
	static boolean isNASConnected = false;
	static String prefTGTFolderStructure, prefsSubfolderNameFormat, prefTGTRenameOption, prefTGTAlreadyExistsTest, prefTGTAlreadyExistsRename;
	static boolean prefCreatePerAlbumFolder;
	static Date lastCopiedImageTimestamp;
	static long lastScannedImageTimestamp;
	private static String MyState = "Idle";
	private static File timestampFile;
	private static IBinder myBinder;
	private static boolean prefsMACverified = false;
	private static Handler h;
	private static String fileurl;
	private static SharedPreferences settings;
	private static boolean SharedPreferencesChanged = true;
	private static volatile boolean isConnectedToWifi = false;
	private static Integer mediaFilesCountTotal = -1;
	private static Integer mediaFilesScanned = -1;
	private static Integer mediaFilesCountToSync = -1;
	private static MediaFilesDBclass MediaFilesDB;
	private static volatile ePicSyncState PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
	private static boolean constructNASPathErrorFileExist=false;
	final FilenameFilter pictureFileFilterWithTimestamp = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String pathname) {
			String dirname = dir.getAbsolutePath().toLowerCase();
			String lowercase = pathname.toLowerCase();
			String fullpath = dirname + "/" + lowercase;
			File f = new File(dir.getAbsolutePath() + "/" + pathname);
			if (lowercase.startsWith("."))
				return false;
			boolean isItDirectory = f.isDirectory();
			if (!isItDirectory) {
				if (f.lastModified() <= lastScannedImageTimestamp) {
					return false;
				}
			} else
				return true;
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
			String fullpath = dirname + "/" + lowercase;
			File fullpathFile = new File(dir.getAbsolutePath() + "/" + pathname);
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

	private final Context MyContext = this;
	private final String timestampFileName = "timestampFile";
	private final NASService nasService = new NASService(MyContext);

	private final Runnable DoSyncInSeparateThread = new Runnable() {
		@Override
		public void run() {
			DoSyncFromDB();
		}
	};
	SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
					if (key.contains("smb") || key.contains("SMB")) {
						SharedPreferencesChanged = true;
					}
				}
			};
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
			String filepath = file.getParent();
			String filename = file.getName();
			if (filename.endsWith("/"))
				filename = TextUtils.substring(filename, 0, TextUtils.lastIndexOf(filename, '/'));
			int tmp = filetype & cifsAllowedBrowsables;
			if ((filetype & cifsAllowedBrowsables) == 0)
				return false;
			if (filename.startsWith(".") || filename.contains("$"))
				return false;
			boolean isDir = false;
			try {
				isDir = file.isDirectory();
			} catch (SmbException e) {
				e.printStackTrace();
			}
			return isDir;
		}
	};
	private List values;

	public PicSync() {
		super("PicSync");
/*
		nasService = new NASService();
*/
	}

	private static String constructNASPath(String srcFileNameFull, long srcFileTimestamp) {
		String srcFileName = srcFileNameFull.substring(srcFileNameFull.lastIndexOf('/') + 1);
		String srcFilePath = srcFileNameFull.substring(0, srcFileNameFull.lastIndexOf('/'));
		return constructNASPath(srcFilePath, srcFileName, srcFileTimestamp);
	}

	private static String constructNASPath(String srcFilePath, String srcFileName, long srcFileTimestamp) {
		constructNASPathErrorFileExist=false;
		String tgtFileNameFull;
		if (tgtNASPath == null) {
			tgtNASPath = settings.getString("prefsSMBURI", null);
			if (tgtNASPath.endsWith("/"))
				tgtNASPath = tgtNASPath.substring(0, tgtNASPath.lastIndexOf("/"));
		}
		String tgtFilePath = tgtNASPath;
		String srcFileExtDot = srcFileName.substring(srcFileName.lastIndexOf("."));
		String tgtFileName;
		String srcFileNameFull = srcFilePath + "/" + srcFileName;
		if (prefCreatePerAlbumFolder) {
			String albumFolderName = srcFilePath.substring(srcFilePath.lastIndexOf('/') + 1);
			tgtFilePath = tgtFilePath + "/" + albumFolderName;
		}
		if (prefTGTFolderStructure.equalsIgnoreCase("prefTGTFolderStructDate")) {
			SimpleDateFormat dateFormat = new SimpleDateFormat(prefsSubfolderNameFormat);
			String dateSubfolderName = dateFormat.format(new Date(srcFileTimestamp));
			tgtFilePath = tgtFilePath + "/" + dateSubfolderName;
		}
		if (prefTGTRenameOption.equalsIgnoreCase("prefTGTRenameOption_YMD-HMS")) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
			tgtFileName = dateFormat.format(new Date(srcFileTimestamp)) + srcFileExtDot;
		} else
			//prefTGTRenameOption_NoChange
			tgtFileName = srcFileName;
		tgtFileNameFull = tgtFilePath + "/" + tgtFileName;

		if (!NASService.checkConnection())
			return null;
		Integer renameNumber = 1;
		String tgtFileNameBase = tgtFileName.substring(0, tgtFileName.lastIndexOf("."));
		try {
			while (new SmbFile(tgtFileNameFull, auth).exists()) {
				// detect, if it is the same file
				if (prefTGTAlreadyExistsTest.equalsIgnoreCase("prefTGTAlreadyExistsTest_matchName")) {
					constructNASPathErrorFileExist=true;
				} else if (prefTGTAlreadyExistsTest.equalsIgnoreCase("prefTGTAlreadyExistsTest_matchNameSize")) {
					long srcFileTS = new File(srcFileNameFull).length();
					long tgtFileTS = new SmbFile(tgtFileNameFull,auth).length();
/*
					long srcFileTS = new File(srcFileNameFull).lastModified();
					long tgtFileTS = new SmbFile(tgtFileNameFull).lastModified();
*/
					if (srcFileTS == tgtFileTS)
						constructNASPathErrorFileExist=true;
				} else if (prefTGTAlreadyExistsTest.equalsIgnoreCase("prefTGTAlreadyExistsTest_matchNameSizeCksum")) {
					// TODO: Implement checksums
					constructNASPathErrorFileExist=true;
				}
				if (constructNASPathErrorFileExist)
					return tgtFileNameFull;

				// if file exists, but it is not the same not, make a new name
				if (prefTGTAlreadyExistsRename.equalsIgnoreCase("prefTGTAlreadyExistsRename_currdate")) {
					SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
					tgtFileName = tgtFileNameBase + "." + dateFormat.format(new Date(srcFileTimestamp)) + srcFileExtDot;
				} else if (prefTGTAlreadyExistsRename.equalsIgnoreCase("prefTGTAlreadyExistsRename_number")) {
					tgtFileName = tgtFileNameBase + "." + String.format("%02d", renameNumber) + srcFileExtDot;
					renameNumber++;
				}
				tgtFileNameFull = tgtFilePath + "/" + tgtFileName;
			}
		} catch (SmbException | MalformedURLException e) {
			e.getMessage();
			String exceptionString = e.toString();
		}
		return tgtFileNameFull;
	}

	static private void saveLastCopiedImageTimestamp(Date timestamp) {
		if (lastCopiedImageTimestamp.getTime() > timestamp.getTime())
			return;
		lastCopiedImageTimestamp = timestamp;
		timestampFile.setLastModified(lastCopiedImageTimestamp.getTime());
	}

	static private void saveLastCopiedImageTimestamp() {
		saveLastCopiedImageTimestamp(new Date());
	}

	static private void saveLastCopiedImageTimestamp(long timestamp) {
		saveLastCopiedImageTimestamp(new Date(timestamp));
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i("PicSync", "onCreate");
	}

	private void getAllSettings() {
		PreferenceManager.setDefaultValues(MyContext, R.xml.pref_upload, false);
		prefTGTFolderStructure = settings.getString(prefTGTFolderStructure, getString(R.string.prefTGTFolderStructDefault));
		prefsSubfolderNameFormat = settings.getString(prefsSubfolderNameFormat, getString(R.string.prefsSubfolderNameFormatDefault));
		prefTGTRenameOption = settings.getString(prefTGTRenameOption, getString(R.string.prefTGTRenameOptionDefault));
		prefTGTAlreadyExistsTest = settings.getString(prefTGTAlreadyExistsTest, getString(R.string.prefTGTAlreadyExistsTestDefault));
		prefTGTAlreadyExistsRename = settings.getString(prefTGTAlreadyExistsRename, getString(R.string.prefTGTAlreadyExistsRenameDefault));
		prefCreatePerAlbumFolder = settings.getBoolean("prefCreatePerAlbumFolder", false);
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		final String action = intent.getAction();
		Log.i("PicSync", "onStartCommand: " + action);
		super.onStartCommand(intent, flags, startId);
		if (ACTION_STOP_SYNC.equals(action)) {
			if (PicSyncState == ePicSyncState.PIC_SYNC_STATE_RUNNING) {
				PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOP;
				MyState = "Sync stopped";
				broadcastState(MyState);
				doNotify();
			}
		}
		return START_STICKY;
	}
	private void initPreferences() {
		if (preferenceInitialized)
			return;
		preferenceInitialized = true;
		PreferenceManager.setDefaultValues(MyContext, R.xml.pref_upload, false);
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.registerOnSharedPreferenceChangeListener(prefChangeListener);
		getAllSettings();
	}
	private void initlastCopiedImageTimestamp() {
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
		MediaFilesDB.openDBRW();

		if (!preferenceInitialized)
			initPreferences();

		if (!prefsMACverified)
			prefsMACverified = settings.getBoolean("prefsMACverified", false);
	}
	@Override
	public IBinder onBind(Intent intent) {
		if (myBinder == null)
			myBinder = new LocalBinder();
		return myBinder;
	}

	@Override
	public void onDestroy() {
		Log.d("PicSync", "onDestroy");
		MediaFilesDB.close();
		super.onDestroy();
	}

	@Override
	public void onLowMemory() {
		Log.d("PicSync", "onLowMemory");
		MediaFilesDB.close();
		super.onLowMemory();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		Log.d("PicSync", "onTaskRemoved");
		super.onTaskRemoved(rootIntent);
	}

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
		boolean exception = false;
		initPreferences();
		try {
/*
			NASService.openConnection();
*/
			NASService.setAuthentication();
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
			exception = true;
		} catch (SmbException e) {
			exception = true;
			e.printStackTrace();
			if (e.toString().contains("Logon failure"))
				makeToast("Check username and password settings");
			else
				makeToast("PicSync: connectivity issue: " + e.getMessage());

		}
		broadcastConnectionStatus();
		Collections.sort(values);
		Intent returnCIFSListIntent = new Intent("CIFSList");
		returnCIFSListIntent.putExtra("cifsList", (String[]) values.toArray(new String[values.size()]));
		returnCIFSListIntent.putExtra("smbCanonicalPath", path);
		returnCIFSListIntent.putExtra("servername", servername);
		returnCIFSListIntent.putExtra("smbType", smbtype);
		returnCIFSListIntent.putExtra("sharename", sharename);
		returnCIFSListIntent.putExtra("exception", exception);
		returnCIFSListIntent.putExtra("selectable", (smbtype & cifsAllowedToSelect));
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

	private void makeToast(final String toastString) {
		h = new Handler(MyContext.getMainLooper());
		h.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(MyContext, toastString, Toast.LENGTH_LONG).show();
			}
		});
	}

	private boolean checkStoragePermission(){
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
			Log.i("PicSync", "No permission to READ_EXTERNAL_STORAGE");
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_NO_ACCESS;
			MyState = "No access to external storage";
			doNotify();
			return false;
		}
		return true;
	}
	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			final String action = intent.getAction();
			Log.i("PicSync", "onHandleIntent: " + action);

			if (TextUtils.equals("PicSyncSchedulerNotification", action)) {
				initlastCopiedImageTimestamp();
				Boolean wifion = intent.getBooleanExtra("WifiOn", false);
				isConnectedToWifi = wifion;
				Log.i("PicSync", "PicSyncSchedulerNotification: " + wifion);
			}
			if (ACTION_BROWSE_CIFS.equals(action)) {
				final String path = intent.getStringExtra("path");
				BrowseCIFS(path);
				return;
			}
			if (ACTION_UPDATE_WOL.equals(action)) {
				initPreferences();
				NASService.storeWoLInfo(intent.getStringExtra("servername"));
				return;
			}
			if (ACTION_GET_STATE.equals(action)) {
				handleActionGetState();
				return;
			}
			if (ACTION_START_SYNC.equals(action)) {
				if (!checkStoragePermission())
					return;
				if (!isConnectedToWifi) {
					makeToast("Wifi is not connected");
					return;
				}
				initlastCopiedImageTimestamp();
				final String flags = intent.getStringExtra(ACTION_START_SYNC_FLAG);
				if (mediaFilesCountToSync == -1)
					MediaFilesDB.getDBStatistics();
				if (mediaFilesCountToSync > 0)
					handleActionStartSync(flags);
				scanMediaFilesToSync();
				if (mediaFilesCountToSync > 0)
					handleActionStartSync(flags);
				return;
			}
			if (ACTION_SUGGEST_MEDIA_SCAN.equals(action)) {
				if (!checkStoragePermission())
					return;
				initlastCopiedImageTimestamp();
				final String uri = intent.getStringExtra("uri");
				if (uri != null)
					scanMediaFilesFromUri(uri);
				else
					scanMediaFilesToSync();
				if (mediaFilesCountToSync > 0)
					handleActionStartSync("");
				return;
			}
			if (ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS.equals(action)) {
				addMediaFoldersToSettings();
				return;
			}
			if (ACTION_GET_STORAGE_PATHS.equals(action)) {
				if (!checkStoragePermission())
					return;
				String[] storagePaths = getStoragePaths();
				Intent returnstoragePathsIntent = new Intent("storagePaths");
				returnstoragePathsIntent.putExtra("storagePaths", storagePaths);
				LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnstoragePathsIntent);
				return;
			}
			if (ACTION_GET_NAS_CONNECTION.equals(action)) {
				broadcastConnectionStatus();
			}
			if (ACTION_SET_LAST_IMAGE_TIMESTAMP.equals(action)) {
				initlastCopiedImageTimestamp();
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

	private String[] getStoragePaths() {
		String canonicalPath, aPath, absolutePath;
		/*
		First, gather all possible "external" storage locations
         */
		/*final*/
		SortedSet<String> storagePathsSet = new TreeSet<String>();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			File[] externalMediaDirs = this.getExternalMediaDirs();
			for (File externalMediaDir : externalMediaDirs) {
				try {
					canonicalPath = externalMediaDir.getCanonicalPath();
					int needle = TextUtils.indexOf(canonicalPath, "/Android", 0);
					canonicalPath = TextUtils.substring(canonicalPath, 0, needle);
					storagePathsSet.add(canonicalPath);
				} catch (IOException e) {
					Log.i("getStoragePaths", "externalMediaDir.getAbsolutePath()", e);
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
		String envExternalStorage = System.getenv("EXTERNAL_STORAGE");
		try {
			canonicalPath = new File(envExternalStorage).getCanonicalPath();
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
						canonicalPath = new File(rawSecondaryStorage).getCanonicalPath();
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

	String[] listPictures(String dir, String params) {
		Set<String> listOfFiles = new HashSet<String>();
		File[] filelist;
		if ((params != null) && (params.equals("TimeStampCondition")))
			filelist = new File(dir).listFiles(pictureFileFilterWithTimestamp);
		else
			filelist = new File(dir).listFiles(pictureFileFilter);

		String[] filesToAddTolistOfFiles;
		for (File entry : filelist) {
			if (entry.isDirectory()) {
				filesToAddTolistOfFiles = listPictures(entry.getAbsolutePath(), params);
				Collections.addAll(listOfFiles, filesToAddTolistOfFiles);
			} else {
				listOfFiles.add(entry.toString());
			}
		}
		return listOfFiles.toArray(new String[listOfFiles.size()]);
	}

	String[] listPictures(String dir) {
		return listPictures(dir, "");
	}

	private String[] getMediaPaths(String storagePath) {
		Set<String> mediaPathsSet = new HashSet<String>();
		File[] filelist = new File(storagePath).listFiles(pictureFolderFilter);
		if (filelist == null)
			return null;
		for (File entry : filelist) {
			if (entry.isDirectory()) {
				getMediaPaths(entry.getAbsolutePath());
			}
			mediaPathsSet.add(entry.getAbsolutePath());
		}
		return mediaPathsSet.toArray(new String[mediaPathsSet.size()]);
	}

	private String[] getMediaPaths(String[] storagePaths) {
		Set<String> mediaPathsSet = new HashSet<String>();
/*
		SortedSet<String> mediaPathsSet = new TreeSet<String>();
*/
		for (String storagePath : storagePaths) {
			String[] mediaPaths = getMediaPaths(storagePath);
			if (mediaPaths != null)
				Collections.addAll(mediaPathsSet, mediaPaths);
		}
		return mediaPathsSet.toArray(new String[mediaPathsSet.size()]);
	}

	private Set<String> listMediaFilesToSync() {
		SmbFile tgtMediaFile;
		Set<String> mediaPaths = settings.getStringSet("prefFolderList", null);
		if (mediaPaths == null) {
			broadcastCopyInProgress("none", "none");
			return null;
		}
		Set<String> fileListToSync = new HashSet<String>();

/*
		for (String mediaPath : mediaPaths) {
			String[] mediaFiles = listPictures(mediaPath);
			mediaFilesCountTotal += mediaFiles.length;
		}
		mediaFilesCountTotal
		broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
*/

		String tgtPath = settings.getString("prefsSMBURI", null);
		if (mediaPaths.size() > 0)
			broadcastState("Scanning media");
		else
			return null;
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
/*
				mediaFilesCountToSync = fileListToSync.size();
				broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
*/
			}
		}
		/*TODO
		Sort by timestamp!!!
		 */
		broadcastState("Scanning finished.");
		return fileListToSync;
	}

	private void scanMediaFilesFromUri(String uriString) {
		if (uriString == null)
			return;
		final Uri uri = Uri.parse(uriString);
		Cursor cursor;
		int document_id, colidData, colidDocumentId;
		String path = null;
		final Set<String> colNames = new HashSet<String>();
		final Set<String> colValues = new HashSet<String>();
		final Set<String> paths = new HashSet<String>();
		try {
			cursor = getContentResolver().query(uri, null, "datetaken > " + lastCopiedImageTimestamp.getTime(), null, "datetaken desc");
			if (cursor == null)
				return;
			cursor.moveToFirst();
			colidData = cursor.getColumnIndex("_data");

			while (!cursor.isAfterLast()) {
				String srcFile = cursor.getString(colidData);
				if (!MediaFilesDB.containsSrcFile(srcFile))
					paths.add(srcFile);
				cursor.moveToNext();
			}
			cursor.close();
			if (!paths.isEmpty()) {
				for (String srcFileNameFull : paths) {
					String srcFileNameFullCanonical = new File(srcFileNameFull).getCanonicalPath().toString();
					MediaFilesDB.insertSrcFile(srcFileNameFullCanonical);
					mediaFilesScanned++;
				}
				MediaFilesDB.getDBStatistics();
				broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
			}
		} catch (Exception e) {
			Log.d("getImagePath: uri:", uri.toString());
			e.getMessage();
		}
	}

	private int scanMediaFilesToSync() {
		SmbFile tgtMediaFile;
		Set<String> mediaPaths = settings.getStringSet("prefFolderList", null);
		SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
		if (mediaPaths == null) {
			broadcastCopyInProgress("none", "none");
			return 0;
		}

		String tgtPath = settings.getString("prefsSMBURI", null);
		MediaFilesDB.getDBStatistics();
		long newRowId;
		if (mediaPaths.size() > 0)
			broadcastState("Scanning media");
		else {
			broadcastState("No folders configured");
			return 0;
		}
		for (String mediaPath : mediaPaths) {
			String[] mediaFiles = listPictures(mediaPath, "TimeStampCondition");
			MediaFilesDB.insertSrcFile(mediaFiles);
			MediaFilesDB.getDBStatistics();
			broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
		}
		broadcastState("Scanning done");
		return mediaFilesCountToSync;
	};

	private boolean createNASPath(String smbFile) {
		String smbPath = smbFile.substring(0, smbFile.lastIndexOf("/"));
		try {
			new SmbFile(smbPath, auth).mkdirs();
		} catch (MalformedURLException | SmbException e) {
			e.printStackTrace();
			return false;
		}
/*
		String smbPathRelative;
		int smbPathLenght=smbPath.length();
		if (!smbPath.contains(tgtNASPath))
			return false;
		smbPathRelative=smbPath.substring(tgtNASPath.length());
		if (smbPathRelative.startsWith("/"))
			smbPathRelative=smbPathRelative.substring(1);
		String [] smbPathRelativeSegments = smbPathRelative.split("/");
		int segments= smbPathRelativeSegments.length;
		String currPath=tgtNASPath+"/";
		for (int segment=1; segment < segments; segment++){
			currPath=currPath+smbPathRelativeSegments[segment]+"/";
			try {
				SmbFile dir2create = new SmbFile(currPath,auth);
				try {
					dir2create.mkdir();
				} catch (SmbException e) {
					e.printStackTrace();
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return false;
			}
		}

*/
		return true;
	}

	private boolean copyFileToNAS(String src, String tgt, long timestamp) {
		String tgtTMP = tgt + ".picsync";
		boolean rc = true;
		if (!NASService.checkConnection())
			return false;
		FileInputStream srcFileStream = null;
		File srcFile = null;
		SmbFileOutputStream tgtFileStream = null;
		SmbFile tgtFileTMP = null, tgtFile = null;
		final byte[] buffer = new byte[256 * 1024];
//		createNASPath(tgt);
		try {
			broadcastCopyInProgress(src, tgt);
			srcFileStream = new FileInputStream(src);
			tgtFileTMP = new SmbFile(tgtTMP, auth);
			tgtFile = new SmbFile(tgt, auth);
			try {
				tgtFile.createNewFile();
				tgtFile.delete();
			} catch (SmbException e) {
				e.getMessage();
				String exceptionString = e.toString();
				if (exceptionString.contains("path"))
					createNASPath(tgt);
			}
			if (tgtFile == null)
				return true;
			tgtFileStream = new SmbFileOutputStream(tgtFileTMP);
			int read = 0;
			long read_total = 0;
			while (((read = srcFileStream.read(buffer, 0, buffer.length)) > 0) && PicSyncState == ePicSyncState.PIC_SYNC_STATE_RUNNING) {
				read_total += read;
				tgtFileStream.write(buffer, 0, read);
			}
			srcFile = new File(src);
			long srcFileSize = srcFile.length();
			if (srcFileSize != read_total)
				rc = false;
			sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
			rc = false;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			rc = false;
		} catch (SmbException e) {
			e.printStackTrace();
			rc = false;
		} catch (UnknownHostException e) {
			e.printStackTrace();
			rc = false;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			if (e.toString().contains("ENOENT"))
				MediaFilesDB.removeSrcFile(src);
			rc = false;
		} catch (IOException e) {
			e.printStackTrace();
			rc = false;
		} finally {
			try {
				if (srcFileStream != null)
					srcFileStream.close();
				if (tgtFileStream != null)
					tgtFileStream.close();
				if (tgtFileTMP != null) {
					tgtFileTMP.setLastModified(timestamp);
					tgtFileTMP.renameTo(new SmbFile(tgt, auth));
				}
			} catch (IOException e) {
				e.printStackTrace();
				rc = false;
			}
		}
		return rc;
	}

	final public void DoSyncFromDB() {
		Cursor cToSync;
		SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
		if (db == null)
			return;
		try {
			cToSync = db.rawQuery("select "
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH
							+ "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
							+ "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS
							+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
							+ " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
							+ " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
							+ " order by " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " desc "
					, null);
			if (cToSync == null)
				return;
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		cToSync.moveToFirst();
		int colSRCPATH = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
		int colSRCFILE = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
		int colSRCTS = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS);

		while (!cToSync.isAfterLast() && PicSyncState == ePicSyncState.PIC_SYNC_STATE_RUNNING) {
			String srcFilePath = cToSync.getString(colSRCPATH);
			String srcFileName = cToSync.getString(colSRCFILE);
			String srcFileNameFull = srcFilePath + "/" + srcFileName;
			Log.d("DoSyncFromDB",srcFileNameFull);

			long srcFileTimestamp = cToSync.getLong(colSRCTS);
			String tgtFileNameFull = constructNASPath(srcFilePath, srcFileName, srcFileTimestamp);
			if (tgtFileNameFull == null)
				continue;
			if (!constructNASPathErrorFileExist) {
				if (!copyFileToNAS(srcFileNameFull, tgtFileNameFull, srcFileTimestamp)) {
					break;
				}
			}
			else
				broadcastCopyInProgress(srcFileName, "Already there");

			MediaFilesDB.updateTgtFileName(srcFilePath, srcFileName, tgtFileNameFull);
			mediaFilesCountToSync--;
			broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
			cToSync.moveToNext();
		}
		if (!cToSync.isClosed())
			cToSync.close();
		db.close();
		broadcastCopyInProgress("none", "none");
		if ((PicSyncState != ePicSyncState.PIC_SYNC_STATE_RUNNING) && (Looper.getMainLooper().getThread() != Thread.currentThread()))
			stopSelf();
	}

	private void handleActionGetState() {
		Log.i("PicSync", "handleActionGetState: " + MyState);
		initPreferences();
		if (settings.getString("prefsSMBSRV", "EMPTY").equals("EMPTY"))
			broadcastState("Not Configured");
		initlastCopiedImageTimestamp();
		lastCopiedImageTimestamp = new Date(timestampFile.lastModified());
		broadcastLastCopiedImageTimestamp();

		MediaFilesDB = new MediaFilesDBclass(MyContext);
		MediaFilesDB.openDBRW();


		if (settings.getString("prefsSMBSRV", "EMPTY").equals("EMPTY"))
			broadcastState("Not Configured");
		if (!prefsMACverified)
			prefsMACverified = settings.getBoolean("prefsMACverified", false);

		broadcastState(MyState);
		broadcastConnectionStatus();
		broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
	}

	private void handleActionStartSync(String flags) {
		boolean doit = false;
		Log.d("handleActionStartSync","beginning");
		if (PicSyncState != ePicSyncState.PIC_SYNC_STATE_RUNNING)
			doit = true;

		if (flags != null && flags.equals(ACTION_START_SYNC_RESTART))
			doit = true;

		if (doit) {
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_RUNNING;
			Log.d("handleActionStartSync","Do it");

			// TODO Prerobit na novy thread?
			broadcastState(MyState = "Sync initiated");
			doNotify();
/*			HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
			thread.start();
*/
/*
			DoSync();
*/
			broadcastConnectionStatus();
			Log.d("handleActionStartSync","NAS Connected: "+new Boolean(isNASConnected).toString());
			if (!isNASConnected) {
				try {
					NASService.openConnection();
					NASService.waitForConnection(3, 2);
					broadcastConnectionStatus();
				} catch (MalformedURLException e) {
					e.printStackTrace();
					return;
				}
			}
			initPreferences();
			if (!isNASConnected && isWoLallowed && prefsMACverified) {
				Log.d("handleActionStartSync: ","performing WoL");
				NASService.WoL();
				NASService.waitForConnection(10, 2);
				broadcastConnectionStatus();
			}
			if (isNASConnected) {
				broadcastState(MyState = "Copying files");
				doNotify();
				Log.d("handleActionStartSync: ","Starting sync thread");
				DoSyncInSeparateThread.run();
				broadcastState(MyState = "In sync");
			} else {
				broadcastState(MyState = "Not in sync");
			}
			doNotify();
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
		}
	}

	private void writeLogFile(String message) {
		SmbFile smbLogFile;
		SmbFileOutputStream smbLogFileOutputStream;
		try {
			NASService.openConnection();
			smbLogFile = new SmbFile(smbshareurl + "/" + picSyncLogFile, auth);
//            smbLogFile.setAttributes();
			smbLogFileOutputStream = (SmbFileOutputStream) smbLogFile.getOutputStream();
			broadcastConnectionStatus();
		} catch (IOException e) {
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
		} catch (IOException e) {
			e.printStackTrace();
		}
		broadcastConnectionStatus();
	}

	public void readTestFile() {
		SmbFile sfile;
		SmbFileInputStream in;
		try {
			NASService.openConnection();
			fileurl = "smb://" + smbservername + "/testexport/somefile.txt";
			Log.i("PicSync", "Opening file: " + fileurl);
			sfile = new SmbFile(fileurl, auth);
			Log.i("PicSync", "File opened");
			makeToast("PicSync: " + PicSync.fileurl + " opened");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			Log.i("PicSync", "File NOT opened " + e.getMessage());
			makeToast("PicSync: File NOT opened " + e.getMessage());
			return;
		}

		try {
			in = new SmbFileInputStream(sfile);
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void doNotify(){
		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
						.setSmallIcon(R.drawable.ic_notifications_black_24dp)
						.setContentTitle("PicSync")
						.setContentText(MyState);
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

	private enum ePicSyncState {PIC_SYNC_STATE_STOPPED, PIC_SYNC_STATE_RUNNING, PIC_SYNC_STATE_NO_ACCESS, PIC_SYNC_STATE_STOP}

	/*
		************************************************************************************************
	*/
	private static class MediaFilesDBclass extends SQLiteOpenHelper {
		static final int DATABASE_VERSION = 2;
		static final String DATABASE_NAME = "PicSync.db";
		private static boolean dbOpened = false;
		private static SQLiteDatabase db;

		MediaFilesDBclass(Context ctx) {
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		}

		boolean insertSrcFile(String srcMediaFileNameFull) {
			db = getWritableDatabase();
			File mediaFile = new File(srcMediaFileNameFull);
			String srcMediaFileName = mediaFile.getName();
			String srcMediaFilePath = mediaFile.getParent();
			if (containsSrcFile(srcMediaFilePath,srcMediaFileName))//prerobit na rozbitie path+name
				return false;
			Long lastModified = mediaFile.lastModified();
			ContentValues newMediaFile = new ContentValues();
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH, srcMediaFilePath);
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE, srcMediaFileName);
			newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS, lastModified);
			mediaFilesScanned++;
			try {
				db.insert(Constants.MediaFilesDBEntry.TABLE_NAME, null, newMediaFile);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

		void insertSrcFile(String[] srcMediaFileNameFullList) {
			db = getWritableDatabase();
			for (String srcMediaFileNameFull : srcMediaFileNameFullList)
				insertSrcFile(srcMediaFileNameFull);
		}

		void updateTgtFileName(String srcMediaFilePath, String srcMediaFileName, String tgtMediaFileNameFull){
			//update tgtMediaFileName where  srcMediaFilePath + srcMediaFileName
			ContentValues newvalues = new ContentValues();
			newvalues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_TGT, tgtMediaFileNameFull);
			String where = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
					+ " and "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\"";
			db.update(Constants.MediaFilesDBEntry.TABLE_NAME, newvalues, where, null);
		}
		boolean removeSrcFile(String srcMediaFileNameFull) {
			db = getWritableDatabase();
			File mediaFile = new File(srcMediaFileNameFull);
			String srcMediaFilePath = mediaFile.getParent();
			String srcMediaFileName = mediaFile.getName();
			try {
				db.delete(Constants.MediaFilesDBEntry.TABLE_NAME,
						Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
								+ " and "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\""
						, null);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

		boolean containsSrcFile(String srcMediaFileNameFull) {
			File mediaFile = new File(srcMediaFileNameFull);
			String srcMediaFilePath = mediaFile.getParent();
			String srcMediaFileName = mediaFile.getName();
			return containsSrcFile(srcMediaFilePath, srcMediaFileName);
		}
		boolean containsSrcFile(String srcMediaFilePath, String srcMediaFileName) {
			db = getReadableDatabase();
			Cursor cChkFile;
			if (db == null)
				return true;
			try {
				cChkFile = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
								+ " where "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
								+ " and "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\""
						, null);
				cChkFile.moveToFirst();
				int count = cChkFile.getInt(0);
				cChkFile.close();
				return (count > 0);
			} catch (Exception e) {
				e.printStackTrace();
				return true;
			}

		}

		void getDBStatistics() {
			db = getWritableDatabase();
			Cursor c = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME, null);
			c.moveToFirst();
			mediaFilesCountTotal = c.getInt(0);
			c.close();

			Cursor cToSync = db.rawQuery("select count (*) from "
							+ Constants.MediaFilesDBEntry.TABLE_NAME
							+ " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
							+ " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
					, null);
			cToSync.moveToFirst();
			mediaFilesCountToSync = cToSync.getInt(0);
			cToSync.close();

			Cursor cHighestScannedTimestamp = db.rawQuery("select max("
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + ")"
							+ " from "
							+ Constants.MediaFilesDBEntry.TABLE_NAME
					, null);
			cHighestScannedTimestamp.moveToFirst();
			lastScannedImageTimestamp = cHighestScannedTimestamp.getLong(0);
			cHighestScannedTimestamp.close();

			Cursor cHighestSyncedTimestamp = db.rawQuery("select max("
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + ")"
							+ " from "
							+ Constants.MediaFilesDBEntry.TABLE_NAME
							+ " where "
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " != \"\""
							+ " or "
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is not null"
					, null);
			cHighestSyncedTimestamp.moveToFirst();
			long highestSyncedTimestamp = cHighestSyncedTimestamp.getLong(0);


			if (highestSyncedTimestamp == 0)
				saveLastCopiedImageTimestamp(1);
			else
				saveLastCopiedImageTimestamp(highestSyncedTimestamp);
			cHighestSyncedTimestamp.close();
		}

		public void onCreate(SQLiteDatabase db) {

			db.execSQL("drop table if exists " + Constants.MediaFilesDBEntry.TABLE_NAME);
			final String TABLE_CREATE = "create table " + Constants.MediaFilesDBEntry.TABLE_NAME + "("
					+ Constants.MediaFilesDBEntry.TABLE_NAME
					+ "_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_MIN_FILE + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " INTEGER, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " TEXT, "
					+ "CONSTRAINT srcFile_unique UNIQUE (" + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH
					+ "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
					+ "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + ")"
					+ ")";
			db.execSQL(TABLE_CREATE);

		}

		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
/*
			db.execSQL("backup data");
			db.execSQL("delete");
*/
			onCreate(db);
/*
			db.execSQL("restore data");
*/
		}

		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			onUpgrade(db, oldVersion, newVersion);
		}

		@Override
		public void onOpen(SQLiteDatabase db) {
			super.onOpen(db);
			dbOpened = true;
		}

		public void openDBRW() {
			db = getWritableDatabase();
		}

		public void openDBRO() {
			db = getReadableDatabase();
		}

		public void closeDB() {
			close();
		}
	}

	/*
		************************************************************************************************
	*/
	static class NASService {
		static Context MyContext;

		NASService(Context ctx) {
			MyContext = ctx;
/*
			jcifs.Config.setProperty("jcifs.netbios.wins", "127.0.0.1");
		if (settings == null)
			settings = PreferenceManager.getDefaultSharedPreferences(MyContext);
*/
		}

		public static boolean checkConnection() {
			if (!isConnectedToWifi) {
/*
				makeToast("Wifi is not connected");
*/
				return false;
			}
			isNASConnected = true;
			try {
				if (!authenticated)
					openConnection();
				String[] x = (new SmbFile("smb://" + smbservername + "/", auth)).list();
			} catch (SmbException | MalformedURLException e) {
				e.printStackTrace();
				isNASConnected = false;
				authenticated = false;
			}

			if (!prefsMACverified)
				prefsMACverified = settings.getBoolean("prefsMACverified", false);
			if (isNASConnected && !prefsMACverified) {
				storeWoLInfo();
			}

			return isNASConnected;
		}

		public static void setAuthentication() {
			if ((authenticated) && (!SharedPreferencesChanged))
				return;
			smbuser = settings.getString(MyContext.getString(R.string.pref_cifs_user), "");
			smbpasswd = settings.getString(MyContext.getString(R.string.pref_cifs_password), "guest");
			if (((auth == null) || SharedPreferencesChanged)) {
				if (!smbuser.isEmpty() && !smbuser.equals("") && !smbpasswd.isEmpty() && !smbpasswd.equals(""))
					auth = new NtlmPasswordAuthentication(null, smbuser, smbpasswd);
				else
					auth = NtlmPasswordAuthentication.ANONYMOUS;
/*
				if  ((smbuser.isEmpty()) || smbuser.equals(""))
					auth = new NtlmPasswordAuthentication(null, null, null);
				else if  ((smbpasswd.isEmpty()) || smbpasswd.equals(""))
					auth = new NtlmPasswordAuthentication(null, "guest", null);
				else
					auth = new NtlmPasswordAuthentication(null, smbuser, smbpasswd);
*/
			}
			SharedPreferencesChanged = false;
		}

		public static void openConnection() throws MalformedURLException {
			if (!isConnectedToWifi) {
				makeToast("Wifi is not connected");
				return;
			}
			smbservername = settings.getString("prefsSMBSRV", "");
			smbshare = settings.getString(MyContext.getString(R.string.pref_cifs_share), "");

			if (smbshare.isEmpty())
				smbshareurl = "smb://" + smbservername + "/";
			else
				smbshareurl = "smb://" + smbservername + "/" + smbshare + "/";
			setAuthentication();
/*
			if ((authenticated) && (!SharedPreferencesChanged))
				return;

			smbuser = settings.getString(MyContext.getString(R.string.pref_cifs_user), "");
			smbpasswd = settings.getString(MyContext.getString(R.string.pref_cifs_password), "guest");
			jcifs.Config.setProperty("jcifs.netbios.wins", smbservername);
			String exceptionString;
			if (((auth == null) || SharedPreferencesChanged) && (!smbuser.isEmpty()))
				auth = new NtlmPasswordAuthentication(null, smbuser, smbpasswd);
			SharedPreferencesChanged = false;
*/
			isNASConnected = true;
			SmbFile[] domains = null;
			SmbFile domainsFile;
			try {
				domainsFile = new SmbFile("smb:///", auth);
			} catch (MalformedURLException e) {
				isNASConnected = false;
				throw e;
			}
			;
			try {
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

		public static void waitForConnection(int retries, int timeout) {
			int iteration = 1;
			while (iteration++ < retries && !checkConnection()) {
				try {
					Thread.sleep(1000 * (iteration * timeout));
				} catch (InterruptedException e) {
					e.printStackTrace();
					iteration = retries;
				}
			}
		}

		public static void WoL() {
			final int PORT = 9;

			String ipStr = null;
			ipStr = settings.getString("smbserverip", "");
			;
			String macStr = settings.getString("prefsMAC", "");

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

		private static void storeWoLInfo() {
			storeWoLInfo(smbservername);
		}

		;

		private static void storeWoLInfo(String servername) {
			String smbserverip, smbserverbcast;
			try {
				smbserverip = UniAddress.getByName(servername).getHostAddress();
				final Set<String> arp = new HashSet<String>();
				BufferedReader br = new BufferedReader(new FileReader(new File("/proc/net/arp")));
				String smbservermac, line;
				while ((line = br.readLine()) != null)
					if (line.startsWith(smbserverip)) {
						smbservermac = line.split("[[:blank:]]+")[3];
						SharedPreferences.Editor settingsEditor = settings.edit();
						settingsEditor.putString("smbserverip", smbserverip);
						settingsEditor.putString("prefsMAC", smbservermac);
						settingsEditor.putBoolean("prefsMACverified", true);
						prefsMACverified = true;
						settingsEditor.apply();
						break;
					}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		private static void makeToast(final String toastString) {
			h = new Handler(MyContext.getMainLooper());
			h.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(MyContext, toastString, Toast.LENGTH_LONG).show();
				}
			});
		}
	}

	public class LocalBinder extends Binder {
		PicSync getService() {
			return PicSync.this;
		}
	}
}
