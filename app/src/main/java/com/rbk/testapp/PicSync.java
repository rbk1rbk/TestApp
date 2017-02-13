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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
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
import java.io.FileOutputStream;
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

import static com.rbk.testapp.Constants.cksumMaxBytes;
import static java.lang.Thread.sleep;

public class PicSync extends IntentService{
	private static final int mId=1;
	public static final String NOTIFICATION = "com.rbk.testapp.MainScreen.receiver";
//	public static final String INTENT_PARENT = "IntentParent";
	static final String ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS = "PicSync.addMediaFolders";
	static final String ACTION_DEL_MEDIA_FOLDERS_W_PICS = "PicSync.delMediaFolder";
	static final String INTENT_PARAM_LOCAL_FOLDER = "PicSync.localFolder";
	static final String ACTION_GET_STORAGE_PATHS = "PicSync.getAllExternalStoragePaths";
	static final String ACTION_GET_NAS_CONNECTION = "PicSync.getNASConnection";
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
	static boolean prefWoLAllowed = false;
	static boolean cksumEnabled = false;
	private String cksumGlobalVariable = null;

	static boolean DEBUGdryRun = false;
	static boolean preferenceInitialized = false;
	static NtlmPasswordAuthentication auth = null;

	static String prefTGTFolderStructure, prefsSubfolderNameFormat, prefTGTRenameOption, prefTGTAlreadyExistsTest, prefTGTAlreadyExistsRename;
	static boolean prefEnabledWakeLockCopy;
	static boolean prefCreatePerAlbumFolder;
//	static Date lastCopiedImageDate;
	static long consistentSyncTimestamp;
//	private static File timestampFile;
	private static IBinder myBinder;
	private static boolean prefMACverified = false;
	private static Handler h;
	private static String fileurl;
	private static SharedPreferences settings;
	private static boolean SharedPreferencesChanged = true;
	private static Integer mediaFilesCountTotal = -1;
	private static Integer mediaFilesScanned = -1;
	private static Integer mediaFilesCountToSync = -1;
	private static PicSync.MediaFilesDB MediaFilesDB;
	private static volatile ePicSyncState PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
	private boolean constructNASPathErrorFileExist=false;
//	private static PicSyncScheduler picSyncSchedulerService;
//	private static boolean servicePicSyncSchedulerServiceBound = false;

	static final String ACTION_START_SYNC = "PicSync.Start";
	static final String ACTION_START_SYNC_FLAG = "PicSync.Start.Restart";
	static final String ACTION_START_SYNC_RESTART = "PicSync.Resync";
	static volatile long lastStartSyncIntentTimestamp=0;
	static final String ACTION_STOP_SYNC = "PicSync.Stop";
	static volatile long lastStopSyncIntentTimestamp=0;
	static volatile long lastStartStopSyncIntentTimestamp=0;

	static final String ACTION_BROWSE_CIFS = "PicSync.BrowseCIFS";
	static volatile long lastStartBrowseCIFSIntentTimestamp=0;

	static volatile long lastStopBrowseCIFSIntentTimestamp=0;

	static final String ACTION_SUGGEST_MEDIA_SCAN = "PicSync.suggestMediaScan";
	static final String ACTION_RESCAN_NAS_FILES = "PicSync.suggestRescan";
	static volatile long lastMediaScanIntentTimestamp=0;
	private static List<NASFileMetadataStruct> listOfNASFiles;

	static final String ACTION_COPY_PAUSE_CHANGED = "PicSync.CopyPauseChanged";
	static volatile long lastCopyPauseChangedIntentTimestamp=0;
	static volatile boolean stateCopyPaused = false;

	public static final String PICSYNC_CURRTASK = "PicSync.CurTask";
	public static final String ACTION_GET_CURRTASK = "PicSync.GetCurrTask";
	static volatile String stateCurrTaskDescription = "Idle";

	static volatile boolean stateNASConnected = false;
	static volatile boolean stateNASauthenticated = false;
//	static volatile boolean stateHomeWifiConnected = false;

	private final Context myContext = this;
	private final String timestampFileName = "timestampFile";
	private final NASService nasService = new NASService(myContext);

	private List listCifsBrowser;
	private long lastSuggestMediaScanTimestamp=0;

	class NASFileMetadataStruct{
		String fileNameFull;
		Long fileSize;
		Long fileEXIFHash;
		NASFileMetadataStruct(String fileNameFull){
			this.fileNameFull = fileNameFull;
			this.fileSize= 0L;
			this.fileEXIFHash=0L;
		}
		NASFileMetadataStruct(String fileNameFull, Long fileSize){
			this.fileNameFull = fileNameFull;
			this.fileSize= fileSize;
			this.fileEXIFHash=0L;
		}
	}
	SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
					if (key.contains("smb") || key.contains("SMB") || key.contains("cifs") || key.contains("CIFS")) {
						SharedPreferencesChanged = true;
						tgtNASPath=null;
					}
				}
			};

	final FilenameFilter pictureFileFilterWithTimestamp = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String pathname) {
			String dirname = dir.getAbsolutePath().toLowerCase();
			String lowercase = pathname.toLowerCase();
//			String fullpath = dirname + "/" + lowercase;
			File f = new File(dir.getAbsolutePath() + "/" + pathname);
			if (lowercase.startsWith("."))
				return false;
			boolean isItDirectory = f.isDirectory();
			if (!isItDirectory) {
				if (f.lastModified() <= consistentSyncTimestamp) {
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
			return lowercase.endsWith("avi");
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
			return fullpath.contains("images") || fullpath.contains("dcim") || fullpath.contains("pictures") || fullpath.contains("video");
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
					stateNASauthenticated = false;
					stateNASConnected = false;
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
	private final Runnable DoSyncInSeparateThread = new Runnable() {
		@Override
		public void run() {
			DoSyncFromDB();
		}
	};
	public PicSync() {
		super("PicSync");
	}

	private String constructNASPath(String srcFilePath, String srcFileName, long srcFileTimestamp) {
		constructNASPathErrorFileExist=false;
		String tgtFileNameFull;
		if (tgtNASPath == null || tgtNASPath.length()==0) {
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
					tgtFileName = tgtFileNameBase + "." + dateFormat.format(new Date()) + srcFileExtDot;
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


	@Override
	public void onCreate() {
		super.onCreate();
		Log.i("PicSync", "onCreate");
	}

	private void getAllSettings() {
		prefTGTFolderStructure = settings.getString("prefTGTFolderStructure", getString(R.string.prefTGTFolderStructDefault));
		prefsSubfolderNameFormat = settings.getString("prefsSubfolderNameFormat", getString(R.string.prefsSubfolderNameFormatDefault));
		prefTGTRenameOption = settings.getString("prefTGTRenameOption", getString(R.string.prefTGTRenameOptionDefault));
		prefTGTAlreadyExistsTest = settings.getString("prefTGTAlreadyExistsTest", getString(R.string.prefTGTAlreadyExistsTestDefault));
		prefTGTAlreadyExistsRename = settings.getString("prefTGTAlreadyExistsRename", getString(R.string.prefTGTAlreadyExistsRenameDefault));
		prefCreatePerAlbumFolder = settings.getBoolean("prefCreatePerAlbumFolder", false);
		stateCopyPaused = settings.getBoolean("statePicSyncCopyPaused", true);

		prefWoLAllowed = settings.getBoolean("pref_switch_WOL", false);
		prefEnabledWakeLockCopy = true;
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null)
			return START_NOT_STICKY;
		final String action = intent.getAction();
		Log.i("PicSync", "onStartCommand: " + action);

		long cmdTimestamp=intent.getLongExtra("cmdTimestamp",0);
		if (cmdTimestamp == 0)
			cmdTimestamp=new Date().getTime();

		if (ACTION_START_SYNC.equals(action)) {
			if (lastStartStopSyncIntentTimestamp < cmdTimestamp)
				lastStartStopSyncIntentTimestamp = cmdTimestamp;
		}

		if (ACTION_STOP_SYNC.equals(action)) {
			if (lastStartStopSyncIntentTimestamp < cmdTimestamp)
				lastStartStopSyncIntentTimestamp = cmdTimestamp;
			if (lastStartSyncIntentTimestamp < cmdTimestamp)
				lastStopSyncIntentTimestamp = cmdTimestamp;
			if (PicSyncState == ePicSyncState.PIC_SYNC_STATE_SYNCING) {
				PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
				broadcastCurrTask(stateCurrTaskDescription = "Sync stopped");
				doNotify();
			}
		}
		if (ACTION_COPY_PAUSE_CHANGED.equals(action)) {
			lastCopyPauseChangedIntentTimestamp=cmdTimestamp;
			stateCopyPaused=settings.getBoolean("statePicSyncCopyPaused", true);
		}
		super.onStartCommand(intent, flags, startId);
		if (ACTION_SUGGEST_MEDIA_SCAN.equals(action)) {
			lastSuggestMediaScanTimestamp=cmdTimestamp;
		}
/*
		if (!servicePicSyncSchedulerServiceBound)
			bindService(new Intent(this,PicSyncScheduler.class),connectionPicSyncSchedulerService, Context.BIND_AUTO_CREATE);
*/
		return START_STICKY;
	}
	private void initPreferences() {
/*
		if (preferenceInitialized) => treba doplnit zmenu premennej, ak doslo k zmene
			return;
		preferenceInitialized = true;
*/
		PreferenceManager.setDefaultValues(myContext, R.xml.pref_upload, false);
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.registerOnSharedPreferenceChangeListener(prefChangeListener);
		getAllSettings();
	}
	private void saveConsistentSyncTimestamp(long timestamp) {
		if (timestamp <= 0)
			return;
		SharedPreferences.Editor e = settings.edit();
		e.putLong("consistentSyncTimestamp",timestamp);
		e.apply();
		consistentSyncTimestamp = timestamp;
		broadcastLastCopiedImageTimestamp();
	}
	private void initlastCopiedImageTimestamp() {
		if (!preferenceInitialized)
			initPreferences();
		if (MediaFilesDB == null)
			MediaFilesDB = new MediaFilesDB(myContext);
		consistentSyncTimestamp=settings.getLong("consistentSyncTimestamp",0);
		if (consistentSyncTimestamp <= 0)
			saveConsistentSyncTimestamp(MediaFilesDB.getConsistentSyncTimestamp());
/*
		timestampFile = new File(getFilesDir(), timestampFileName);
		if (MediaFilesDB == null)
			MediaFilesDB = new MediaFilesDB(myContext);
		//TODO: Prejdi vsetky obrazky od najstarsieho po najnovsi a ako lastCopiedImageDate
		//TODO: daj cas posledneho sync suboru v serii
			if (!timestampFile.exists()) {
				try {
					timestampFile.createNewFile();
					timestampFile.setLastModified(MediaFilesDB.getConsistentSyncTimestamp());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		lastCopiedImageDate = new Date(timestampFile.lastModified());
		consistentSyncTimestamp = lastCopiedImageDate.getTime();
*/


		if (!prefMACverified)
			prefMACverified = settings.getBoolean("prefMACverified", false);
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
/*
		if (MediaFilesDB.getReadableDatabase() != null)
			MediaFilesDB.close();
*/
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
		if (listCifsBrowser == null)
			listCifsBrowser = new ArrayList();
		else
			listCifsBrowser.clear();
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
					listCifsBrowser.add(file.getName());
				}
				int tmp = smbtype & cifsAllowedBrowsablesForUp;
				if ((smbtype & cifsAllowedBrowsablesForUp) != 0)
					listCifsBrowser.add("..");
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			exception = true;
		} catch (SmbException e) {
			exception = true;
			e.printStackTrace();
			if (e.toString().contains("Logon failure"))
				makeToast("Check username and password settings");
/*
			else
				makeToast("PicSync: connectivity issue: " + e.getMessage());
*/

		}
		broadcastConnectionStatus();
		Collections.sort(listCifsBrowser);
		Intent returnCIFSListIntent = new Intent("CIFSList");
		returnCIFSListIntent.putExtra("cifsList", (String[]) listCifsBrowser.toArray(new String[listCifsBrowser.size()]));
		returnCIFSListIntent.putExtra("smbCanonicalPath", path);
		returnCIFSListIntent.putExtra("servername", servername);
		returnCIFSListIntent.putExtra("smbType", smbtype);
		returnCIFSListIntent.putExtra("sharename", sharename);
		returnCIFSListIntent.putExtra("exception", exception);
		returnCIFSListIntent.putExtra("selectable", (smbtype & cifsAllowedToSelect));
		LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnCIFSListIntent);
	}

	private void broadcastCurrTask(String currTask) {
		Intent intent = new Intent(NOTIFICATION);
		intent.putExtra("Message", PICSYNC_CURRTASK);
		intent.putExtra(PICSYNC_CURRTASK, currTask);
		sendBroadcast(intent);
		updateNotificationIcon();
	}

	private void broadcastLastCopiedImageTimestamp() {
		Intent intent = new Intent(NOTIFICATION);
		intent.putExtra("Message", "msgLastCopiedImageTimestamp");
		intent.putExtra("lastCopiedImageDate", consistentSyncTimestamp);

		sendBroadcast(intent);
	}

	private void broadcastConnectionStatus() {
		Intent updateIntent = new Intent(NOTIFICATION);
		updateIntent.putExtra("Message", "stateNASConnected");
		updateIntent.putExtra("stateNASConnected", stateNASConnected);
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
	private void updateNotificationIcon(){
		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
						.setSmallIcon(R.drawable.ic_sync_black_24dp)
						.setContentTitle("PicSync")
						.setContentText(stateCurrTaskDescription);
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

	private void makeToast(final String toastString) {
		h = new Handler(myContext.getMainLooper());
		h.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(myContext, toastString, Toast.LENGTH_LONG).show();
			}
		});
	}
	private boolean checkStoragePermission(){
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
			Log.i("PicSync", "No permission to READ_EXTERNAL_STORAGE");
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_NO_ACCESS;
			broadcastCurrTask(stateCurrTaskDescription = "No access to external storage");
			doNotify();
			return false;
		}
		return true;
	}

/*
	private ServiceConnection connectionPicSyncSchedulerService = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			PicSyncScheduler.LocalBinder binder = (PicSyncScheduler.LocalBinder) service;
			picSyncSchedulerService = binder.getService();
			servicePicSyncSchedulerServiceBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			servicePicSyncSchedulerServiceBound = false;
		}
	};

*/
	@Override
	protected void onHandleIntent(Intent intent) {
		final String LOG_TAG="onHandleIntent";
		if (intent != null) {
			final String action = intent.getAction();
			final long cmdTimestamp=intent.getLongExtra("cmdTimestamp",0);
			Log.d(LOG_TAG, "onHandleIntent: " + action);

/*
			if (TextUtils.equals("PicSyncSchedulerNotification", action)) {
				initlastCopiedImageTimestamp();
				Boolean wifion = intent.getBooleanExtra("WifiOn", false);
				stateHomeWifiConnected = wifion;
				Log.d(LOG_TAG, "PicSyncSchedulerNotification: " + wifion);
			}
*/
			if (ACTION_DEL_MEDIA_FOLDERS_W_PICS.equals(action)) {
				final String srcMediaFilePath = intent.getStringExtra(INTENT_PARAM_LOCAL_FOLDER);
				MediaFilesDB.removeLocalPathWithUnsyncedPics(srcMediaFilePath);
				MediaFilesDB.getDBStatistics();
				broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
				return;
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
			if (ACTION_GET_CURRTASK.equals(action)) {
				handleActionGetCurrTask();
				return;
			}
			if (ACTION_START_SYNC.equals(action)) {
				//Verify, it is the last of intermediate start/stop commands
				if (cmdTimestamp < lastStartStopSyncIntentTimestamp)
					return;
				if (PicSyncState == ePicSyncState.PIC_SYNC_STATE_SYNCING)
					return;
				initPreferences();
				stateCopyPaused=settings.getBoolean("statePicSyncCopyPaused", true);
				if (stateCopyPaused) {
					makeToast("Syncing is paused");
					return;
				}
				if (!checkStoragePermission())
					return;
				if (!NASService.isHomeWifiConnected()) {
					makeToast("Wifi is not connected");
					return;
				}
				initlastCopiedImageTimestamp();
				final String flags = intent.getStringExtra(ACTION_START_SYNC_FLAG);
				if (mediaFilesCountToSync == -1)
					MediaFilesDB.getDBStatistics();
				if (mediaFilesCountToSync > 0)
					handleActionStartSync(flags);
				if (PicSyncState == ePicSyncState.PIC_SYNC_STATE_SYNCING) {
					//sync finished gracefully
					scanMediaFilesToSync();
					if (mediaFilesCountToSync > 0)
						handleActionStartSync(flags);
				}
				PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
				broadcastCurrTask(stateCurrTaskDescription="Idle");
				return;
			}
			if (ACTION_RESCAN_NAS_FILES.equals(action)) {
				scanCIFS4SyncedFilesByFilesize();
				scanCIFS4SyncedFilesByMetadata();
				listOfNASFiles=null;
				return;
			}
			if (ACTION_SUGGEST_MEDIA_SCAN.equals(action)) {
				if (cmdTimestamp < lastSuggestMediaScanTimestamp) {
					Log.d(LOG_TAG,"Old invocation. This cmdTimestamp:" + cmdTimestamp
					+ "\nMost recent timestamp: " + lastSuggestMediaScanTimestamp);
					return;
				}
				if (PicSyncState == ePicSyncState.PIC_SYNC_STATE_SYNCING)
					return;
				if (!checkStoragePermission())
					return;
				initlastCopiedImageTimestamp();
				final String uri = intent.getStringExtra("uri");
				if (uri != null)
					scanMediaFilesFromUri(uri);
				else
					scanMediaFilesToSync();
				if (mediaFilesCountToSync > 0 && NASService.isHomeWifiConnected()) {
					handleActionStartSync("");
				}
				return;
			}
			if (ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS.equals(action)) {
				addMediaFoldersToSettings();
				return;
			}
			if (ACTION_GET_STORAGE_PATHS.equals(action)) {
				if (!checkStoragePermission())
					return;
				String[] storagePaths = getAllExternalStoragePaths();
				Intent returnstoragePathsIntent = new Intent("storagePaths");
				returnstoragePathsIntent.putExtra("storagePaths", storagePaths);
				returnstoragePathsIntent.putExtra("cmdTimestamp", cmdTimestamp);
				LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnstoragePathsIntent);
				return;
			}
			if (ACTION_GET_NAS_CONNECTION.equals(action)) {
				broadcastConnectionStatus();
			}
			if (ACTION_SET_LAST_IMAGE_TIMESTAMP.equals(action)) {
				initlastCopiedImageTimestamp();
				long timestamp = intent.getLongExtra("lastCopiedImageDate", 0);
				if (timestamp != 0) {
					saveConsistentSyncTimestamp(timestamp);
					broadcastLastCopiedImageTimestamp();
				}
			}
		}
	}

	private void addMediaFoldersToSettings() {
		String[] MediaFolderList = getMediaPaths(getAllExternalStoragePaths());
		Intent returnMediaFoldersListIntent = new Intent("getMediaFoldersList");
		returnMediaFoldersListIntent.putExtra("MediaFoldersList", MediaFolderList);
		LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnMediaFoldersListIntent);
	}

	private String[] getAllExternalStoragePaths() {
		final String LOG_TAG="getAllExtStgPaths";
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
					Log.d(LOG_TAG, "externalMediaDir.getAbsolutePath()", e);
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
//remove duplicate paths e.g. /storage/emulated/0

		SortedSet<String> storagePathsSetVerified = new TreeSet<String>();
		storagePathsSetVerified.addAll(storagePathsSet);
		File testFile=null;
		for (String storagePath2Check : storagePathsSet){
			try {
				Log.d(LOG_TAG,"Checking path "+storagePath2Check);
				String testFileName = storagePath2Check+"/storagetestfile";
				testFile = new File(testFileName);
				testFile.createNewFile();
				for (String storagePathCheckForNewFile : storagePathsSet){
					if (TextUtils.equals(storagePathCheckForNewFile,storagePath2Check))
						continue;
					if (new File(storagePathCheckForNewFile+"/storagetestfile").exists()) {
						if (storagePath2Check.endsWith("/0"))
							storagePathsSetVerified.remove(storagePath2Check);
						else
							storagePathsSetVerified.remove(storagePathCheckForNewFile);
					}
				}
				testFile.delete();
			} catch (IOException e) {
				e.printStackTrace();
				if ((testFile.exists()))
					testFile.delete();
			}
		}
		return storagePathsSetVerified.toArray(new String[storagePathsSetVerified.size()]);
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

		//Filelist je null, ak dir neexistuje
		if (filelist == null)
			return null;
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
			broadcastCurrTask("Scanning media");
		else
			return null;
		for (String mediaPath : mediaPaths) {
/*
			String[] mediaFiles = listPictures(mediaPath, "TimeStampCondition");
*/
			String[] mediaFiles = listPictures(mediaPath);
			for (String srcMediaFileFull : mediaFiles) {
				if (!stateNASConnected)
					try {
						NASService.openConnection();
					} catch (IOException e) {
						e.printStackTrace();
						return null;
					}
				if ((PicSyncState != ePicSyncState.PIC_SYNC_STATE_SYNCING) || (!stateNASConnected))
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
							stateNASauthenticated = false;
							stateNASConnected = false;
							tgtFileExists = true;
						}
					} finally {
						if (!tgtFileExists)
							fileListToSync.add(srcMediaFileFull);
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
					PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
					broadcastCurrTask("NAS error");
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
		broadcastCurrTask("Scanning finished.");
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
			cursor = getContentResolver().query(uri, null, "datetaken > " + consistentSyncTimestamp, null, "datetaken desc");
			if (cursor == null)
				return;
			cursor.moveToFirst();
			colidData = cursor.getColumnIndex("_data");
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_SCANNING;
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
		PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
	}

	private String[] listNASFiles(String smbPath){
		Set<String> listOfNASFiles = new HashSet<String>();
		SmbFile[] fileList=null;
		try {
			fileList = new SmbFile(smbPath).listFiles();
		} catch (MalformedURLException|SmbException e) {
			e.printStackTrace();
			return null;
		}
		//fileList je null, ak dir neexistuje
		if (fileList == null)
			return null;

		String[] filesToAddTolistOfFiles;
		for (SmbFile entry : fileList) {
			try {
				if (entry.isDirectory()) {
					filesToAddTolistOfFiles = listNASFiles(entry.getCanonicalPath());
					Collections.addAll(listOfNASFiles, filesToAddTolistOfFiles);
				} else {
					String filename=entry.toString();
					Long filesize=entry.length();
					listOfNASFiles.add(filename+","+filesize);
				}
			} catch (SmbException e) {
				e.printStackTrace();
			}
		}
		return listOfNASFiles.toArray(new String[listOfNASFiles.size()]);
	}
	SmbFileFilter mediaCIFSFilter = new SmbFileFilter() {
		@Override
		public boolean accept(SmbFile file) {
			String lowercase = file.toString().toLowerCase();

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
			try {
				return file.isDirectory();
			} catch (SmbException e) {
				e.printStackTrace();
				return false;
			}
		}
	};
	private RemoteFilesDB dbRemoteFiles = null;
	int scanCIFSFilesWithSizeIntoDB(String smbPath){
		SmbFile[] fileList=null;
		try {
			if (!smbPath.endsWith(File.separator))
				smbPath = smbPath + File.separator;
			fileList = new SmbFile(smbPath,auth).listFiles(mediaCIFSFilter);
		} catch (MalformedURLException | SmbException e) {
			e.printStackTrace();
			return 0;
		}
		//fileList je null, ak dir neexistuje
		if (fileList == null)
			return 0;

		int count=0;
		for (SmbFile entry : fileList) {
			try {
				if (entry.isDirectory()) {
					count+=scanCIFSFilesWithSizeIntoDB(entry.getCanonicalPath());
				} else {
					Long fileSize=entry.length();
					String fileName=entry.toString();
//					if (dbRemoteFiles == null)
						dbRemoteFiles = new RemoteFilesDB(myContext);
					dbRemoteFiles.addFile(fileName,fileSize);
					count++;
					Log.d("listNASFilesWithSize","Found "+fileName+" of size "+fileSize);
				}
			} catch (SmbException e) {
				e.printStackTrace();
			}
		}
		return count;
	};
/*	private List<NASFileMetadataStruct> listNASFilesWithSize(String smbPath){
		List<NASFileMetadataStruct> NASFilesList = new ArrayList<NASFileMetadataStruct>();

		SmbFile[] fileList=null;
		try {
			if (!smbPath.endsWith(File.separator))
				smbPath = smbPath + File.separator;
			fileList = new SmbFile(smbPath,auth).listFiles(mediaCIFSFilter);
		} catch (MalformedURLException | SmbException e) {
			e.printStackTrace();
			return null;
		}
		//fileList je null, ak dir neexistuje
		if (fileList == null)
			return null;

		List<NASFileMetadataStruct> filesToAddTolistOfFiles;
		for (SmbFile entry : fileList) {
			try {
				if (entry.isDirectory()) {
					filesToAddTolistOfFiles = listNASFilesWithSize(entry.getCanonicalPath());
					for (int index=filesToAddTolistOfFiles.size()-1; index >=0; index--){
						NASFilesList.add(filesToAddTolistOfFiles.get(index));
					}
				} else {
					Long fileSize=entry.length();
					String fileName=entry.toString();
					NASFilesList.add(new NASFileMetadataStruct(fileName,fileSize));
					Log.d("listNASFilesWithSize","Found "+fileName+" of size "+fileSize);
				}
			} catch (SmbException e) {
				e.printStackTrace();
			}
		}
		return NASFilesList;
	}*/

	private void scanCIFS4SyncedFilesByMetadata(){
		final String LOG_TAG="scanCIFSByFilesize";
		if (!NASService.checkConnection()) {
			makeToast("NAS connection not available");
			return;
		}
		if (tgtNASPath == null) {
			tgtNASPath = settings.getString("prefsSMBURI", null);
			if (tgtNASPath.endsWith("/"))
				tgtNASPath = tgtNASPath.substring(0, tgtNASPath.lastIndexOf("/"));
		}
		broadcastCurrTask(stateCurrTaskDescription="Updating local fingerprints");
		MediaFilesDB.updateMetadataHashOfAllFiles();

		broadcastCurrTask(stateCurrTaskDescription="Scanning remote files");
		int remoteFilesFound=0;
		if (dbRemoteFiles == null)
			remoteFilesFound=scanCIFSFilesWithSizeIntoDB(tgtNASPath);
		else
			remoteFilesFound=dbRemoteFiles.size();
		if (dbRemoteFiles == null)
			return;
		Log.d(LOG_TAG,"Found NAS files: "+remoteFilesFound);

		broadcastCurrTask(stateCurrTaskDescription="Comparing local w/ remote");
		Cursor cAllFiles = MediaFilesDB.getAllFiles();
		cAllFiles.moveToFirst();
		int colSRCPATH = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
		int colSRCFILE = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
		int colSRCSIZE = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE);
		int colSRCHASH = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT);
		while (!cAllFiles.isAfterLast()){
			Long fileSizeLocal = cAllFiles.getLong(colSRCSIZE);
			String filePathLocal=cAllFiles.getString(colSRCPATH);
			String fileNameLocal=cAllFiles.getString(colSRCFILE);
			String fileHashLocal=cAllFiles.getString(colSRCHASH);
			String fileNameFullLocal = filePathLocal + File.separator + fileNameLocal;
			Log.d(LOG_TAG, "Searching for remote copy of " + fileNameFullLocal + " / " + fileSizeLocal);
			Cursor cSimilarRemoteFiles = dbRemoteFiles.getFilesBySize(fileSizeLocal,10*1024L);
			cSimilarRemoteFiles.moveToFirst();
			int colRemoteFileName=0;
//			int colRemoteFileSize=0;
			int colRemoteFileHash=0;
			String fileHashRemote="";
			String remoteFileNameFull;
			if (!cSimilarRemoteFiles.isAfterLast()){
				colRemoteFileName=cSimilarRemoteFiles.getColumnIndex(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE);
//				colRemoteFileSize=cSimilarRemoteFiles.getColumnIndex(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE);
				colRemoteFileHash=cSimilarRemoteFiles.getColumnIndex(Constants.RemoteFilesDBEntry.COLUMN_NAME_FINGERPRINT);
				fileHashRemote=cSimilarRemoteFiles.getString(colRemoteFileHash);
				remoteFileNameFull=cSimilarRemoteFiles.getString(colRemoteFileName);
				if (fileHashRemote.equals("")){
					String remoteFileFingerprint="";
					String fileType = Utils.getFileType(remoteFileNameFull);
					if (fileType == null)
						return;
					if ( fileType.equals(Constants.FILE_TYPE_PICTURE)) {
						String localEXIFFileCache = copyEXIFHeaderLocally(remoteFileNameFull);
						if (localEXIFFileCache ==null)
							return;
						remoteFileFingerprint = Utils.makeEXIFHash(localEXIFFileCache);
						new File(localEXIFFileCache).delete();
					}
					else if ( fileType.equals(Constants.FILE_TYPE_VIDEO))
						remoteFileFingerprint = Utils.makeFileFingerprint(remoteFileNameFull,auth);
					dbRemoteFiles.updateFileFingerprint(remoteFileNameFull,remoteFileFingerprint);
				}
			}

			while (!cSimilarRemoteFiles.isAfterLast() && fileHashLocal.equals(fileHashRemote)){

				cSimilarRemoteFiles.moveToNext();
				fileHashRemote=cSimilarRemoteFiles.getString(colRemoteFileHash);
			}
			if (fileHashLocal.equals(fileHashRemote)) {
				String fileNameFullRemote=cSimilarRemoteFiles.getString(colRemoteFileName);
				Log.d(LOG_TAG, "Identical files: " + fileNameFullLocal + " and " + fileNameFullRemote);
				MediaFilesDB.updateTgtFileName(filePathLocal, fileNameLocal, fileNameFullRemote);
			}
			cAllFiles.moveToNext();
		}
		MediaFilesDB.getDBStatistics();
		broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
		broadcastCurrTask(stateCurrTaskDescription="Idle");

	}
	private void scanCIFS4SyncedFilesByFilesize(){
		final String LOG_TAG="scanCIFSByFilesize";
		if (!NASService.checkConnection()) {
			makeToast("NAS connection not available");
			return;
		}
		if (tgtNASPath == null) {
			tgtNASPath = settings.getString("prefsSMBURI", null);
			if (tgtNASPath.endsWith("/"))
				tgtNASPath = tgtNASPath.substring(0, tgtNASPath.lastIndexOf("/"));
		}
		scanMediaFilesToSync();
		broadcastCurrTask(stateCurrTaskDescription="Updating local hashes");
		MediaFilesDB.updateFileHashOfAllFiles();

		broadcastCurrTask(stateCurrTaskDescription="Scanning remote files");
		int remoteFilesFound=0;
		if (dbRemoteFiles == null)
			remoteFilesFound=scanCIFSFilesWithSizeIntoDB(tgtNASPath);
		else
			remoteFilesFound=dbRemoteFiles.size();
		if (dbRemoteFiles == null)
			return;
		Log.d(LOG_TAG,"Found NAS files: "+remoteFilesFound);

		broadcastCurrTask(stateCurrTaskDescription="Comparing repositories");
		Cursor cAllFiles = MediaFilesDB.getAllFiles();
		cAllFiles.moveToFirst();
		int colSRCPATH = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
		int colSRCFILE = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
		int colSRCSIZE = cAllFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE);
		while (!cAllFiles.isAfterLast()){
			Long fileSizeLocal = cAllFiles.getLong(colSRCSIZE);
			String filePathLocal=cAllFiles.getString(colSRCPATH);
			String fileNameLocal=cAllFiles.getString(colSRCFILE);
			String fileNameFullLocal = filePathLocal + File.separator + fileNameLocal;
			Log.d(LOG_TAG, "Searching for remote copy of " + fileNameFullLocal + " / " + fileSizeLocal);
			int index=0;
			long fileSizeNAS=-1L;
			//TODO: skontrolovat ten for cyklus, neskonci pri prvom najdenom NAS subore s danou velkostou?
			for (fileSizeNAS = listOfNASFiles.get(index).fileSize; index < remoteFilesFound && fileSizeLocal != (fileSizeNAS = listOfNASFiles.get(index).fileSize); index++);
/*
				fileSizeNAS = listOfNASFiles.get(index).fileSize;
				if (fileSizeLocal == fileSizeNAS)
					break;
			}
*/
			if (fileSizeLocal == fileSizeNAS) {
				String fileNameFullRemote = listOfNASFiles.get(index).fileNameFull;
				Log.d(LOG_TAG, "Identical files: " + fileNameFullLocal + " and " + fileNameFullRemote);
				MediaFilesDB.updateTgtFileName(filePathLocal,fileNameLocal,fileNameFullRemote);
			}
			else
				MediaFilesDB.updateTgtFileName(filePathLocal,fileNameLocal,null);
			cAllFiles.moveToNext();
		}
/*

		for (int index=0; index < remoteFilesFound; index++){
			Long fileSizeNAS = listOfNASFiles.get(index).fileSize;
			if (MediaFilesDB.containsSrcFileOfSize(fileSizeNAS)) {
//				Log.d(LOG_TAG, "Some local file with size of " + fileSizeNAS + " already exists on NAS as " + listOfNASFiles.get(index).fileNameFull);
				String fileNASName = listOfNASFiles.get(index).fileNameFull;
				String fileHashNAS=makeHashNAS(fileNASName);
				String localFile = MediaFilesDB.getSrcFileOfSizeAndHash(fileSizeNAS, fileHashNAS);
				if (localFile != null)
					Log.d(LOG_TAG, "Identical files: "+ localFile +" and " + fileNASName);
			}
*/
/*
			else
				Log.d(LOG_TAG,"No local file exists with size of "+fileSizeNAS);
*//*

		}

*/
		MediaFilesDB.getDBStatistics();
		broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
		broadcastCurrTask(stateCurrTaskDescription="Idle");
	}
	private int scanMediaFilesToSync() {
		SmbFile tgtMediaFile;
		Set<String> mediaPaths = settings.getStringSet("prefFolderList", null);
		SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
		if (mediaPaths == null) {
			broadcastCopyInProgress("none", "none");
			return 0;
		}

//		String tgtPath = settings.getString("prefsSMBURI", null);
		MediaFilesDB.getDBStatistics();
//		long newRowId;
		if (mediaPaths.size() > 0) {
			broadcastCurrTask("Scanning media");
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_SCANNING;
		}
		else {
			broadcastCurrTask("No folders configured");
			return 0;
		}
		for (String mediaPath : mediaPaths) {
			String[] mediaFiles = listPictures(mediaPath, "TimeStampCondition");
			if (mediaFiles!= null) {
				MediaFilesDB.insertSrcFile(mediaFiles);
				MediaFilesDB.getDBStatistics();
				broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
			}
		}
		broadcastCurrTask("Scanning done");
		return mediaFilesCountToSync;
	}

	private static String copyEXIFHeaderLocally(String remoteFileName){
		SmbFile sfile;
		SmbFileInputStream in;
		FileOutputStream localFile;
		String localFileName=null;
		try {
			localFileName=Environment.getDataDirectory().getCanonicalPath()+File.separator+"exif.jpg";
			localFile=new FileOutputStream(localFileName);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		try {
			NASService.openConnection();
			Log.i("PicSync", "Opening file: " + remoteFileName);
			sfile = new SmbFile(remoteFileName, auth);
			in = new SmbFileInputStream(sfile);
		} catch (IOException e) {
			e.printStackTrace();
			Log.i("PicSync", "File NOT opened " + e.getMessage());
			return null;
		}

		byte[] buffer = new byte[65535];
		int bytes_read;
		try {
			while ((bytes_read = in.read(buffer)) > 0 && bytes_read < 65535) {
				localFile.write(buffer,0,bytes_read);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return localFileName;
	}
	private boolean createNASPath(String smbFile) {
		String smbPath = smbFile.substring(0, smbFile.lastIndexOf("/"));
		try {
			new SmbFile(smbPath, auth).mkdirs();
		} catch (MalformedURLException | SmbException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private boolean copyFileToNAS(String src, String tgt, long timestamp) {
		PowerManager.WakeLock wakeLock=null;
		if (!prefEnabledWakeLockCopy){
			PowerManager powerManager = (PowerManager) myContext.getSystemService(POWER_SERVICE);
			if (powerManager == null){
				Log.d("copyFileToNAS","PowerManager not available");
			}
			else {
				wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "copyFileToNAS");
				wakeLock.acquire();
				Log.d("copyFileToNAS","wakeLock acquired");
			}
		}

		String tgtTMP = tgt + ".picsync";
		boolean rc = true;
		if (!NASService.checkConnection()){
			if (wakeLock != null) {
				Log.d("copyFileToNAS","wakeLock released");
				wakeLock.release();
			}
			return false;
		}
		FileInputStream srcFileStream = null;
		File srcFile = null;
		SmbFileOutputStream tgtFileStreamTMP = null;
		SmbFile tgtFileTMP = null, tgtFile = null;
		final byte[] buffer = new byte[256 * 1024];
		byte [] md5sumBytes;
		String md5sum = new String();

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
					if (!createNASPath(tgt)) {
						if (wakeLock != null){
							Log.d("copyFileToNAS","wakeLock released");
							wakeLock.release();
						}
						return false;
					}
			}
			tgtFileStreamTMP = new SmbFileOutputStream(tgtFileTMP);
/*
			if (cksumEnabled)
				initializeMD5();
*/
			int read = 0;
			long read_total = 0;
			while (((read = srcFileStream.read(buffer, 0, buffer.length)) > 0) && !stateCopyPaused && PicSyncState == ePicSyncState.PIC_SYNC_STATE_SYNCING) {
				read_total += read;
/*
				if (cksumEnabled && read_total < cksumMaxBytes)
					digestMD5.update(buffer, 0, read);
*/
				tgtFileStreamTMP.write(buffer, 0, read);
			}
			if (PicSyncState != ePicSyncState.PIC_SYNC_STATE_SYNCING || stateCopyPaused)
				throw new InterruptedException("InterruptedException");
/*
			if (cksumEnabled) {
				md5sumBytes = digestMD5.digest();
				for (int i=0; i < md5sumBytes.length; i++)
					md5sum += Integer.toString( ( md5sumBytes[i] & 0xff ) + 0x100, 16).substring( 1 );
				Log.d("copyFileToNAS","md5sum " + md5sum);
				cksumGlobalVariable=md5sum;
			}
*/
			srcFile = new File(src);
			long srcFileSize = srcFile.length();
			if (srcFileSize != read_total)
				rc = false;
			sleep(1);
		} catch (MalformedURLException|SmbException|UnknownHostException e) {
			e.printStackTrace();
			rc = false;
		} catch (InterruptedException e){
			e.printStackTrace();
			if (PicSyncState != ePicSyncState.PIC_SYNC_STATE_SYNCING || stateCopyPaused){
				try {
					if (tgtFileStreamTMP != null) {
						tgtFileStreamTMP.close();
						tgtFileStreamTMP = null;
					}
					tgtFileTMP.delete();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				tgtFileTMP=null;
			}

			rc = false;
		}
		catch (FileNotFoundException e) {
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
				if (tgtFileStreamTMP != null)
					tgtFileStreamTMP.close();
				if (tgtFileTMP != null) {
					tgtFileTMP.setLastModified(timestamp);
					tgtFileTMP.renameTo(new SmbFile(tgt, auth));
				}
			} catch (IOException e) {
				e.printStackTrace();
				rc = false;
			}
		}
		if (wakeLock != null) {
			Log.d("copyFileToNAS","wakeLock released");
			wakeLock.release();
		}
		return rc;
	}

	final public void DoSyncFromDB() {
		final String LOG_TAG="DoSyncFromDB";
		PowerManager.WakeLock wakeLock=null;
		WifiManager.WifiLock wifiLock = null;
		if (prefEnabledWakeLockCopy){
			PowerManager powerManager = (PowerManager) myContext.getSystemService(POWER_SERVICE);
			WifiManager wifiManager = (WifiManager) myContext.getSystemService(WIFI_SERVICE);
			if (powerManager == null || wifiManager == null){
				Log.d(LOG_TAG,"PowerManager or WifiManager not available");
			}
			else {
				wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DoSyncFromDB");
				wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF , "DoSyncFromDB");
				wakeLock.acquire();
				wifiLock.acquire();
				Log.d(LOG_TAG,"wake and WiFi lock acquired");
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
					if (powerManager.isPowerSaveMode())
						Log.d(LOG_TAG, "Super saver mode enabled, may work incorrectly.");
				}
			}
		}
		Cursor cToSync = MediaFilesDB.getUnsyncedFiles();
		if (cToSync == null)
			return;
		cToSync.moveToFirst();
		int colSRCPATH = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
		int colSRCFILE = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
		int colSRCTS = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS);

		while (!cToSync.isAfterLast() && PicSyncState == ePicSyncState.PIC_SYNC_STATE_SYNCING) {
			String srcFilePath = cToSync.getString(colSRCPATH);
			String srcFileName = cToSync.getString(colSRCFILE);
			String srcFileNameFull = srcFilePath + "/" + srcFileName;
			Log.d("DoSyncFromDB copy from",srcFileNameFull);

			long srcFileTimestamp = cToSync.getLong(colSRCTS);
			String tgtFileNameFull = constructNASPath(srcFilePath, srcFileName, srcFileTimestamp);
			Log.d("DoSyncFromDB copy to  ",tgtFileNameFull);
			if (tgtFileNameFull == null) {
				return;
			}
			if (!constructNASPathErrorFileExist) {
				broadcastCopyInProgress(srcFileName, tgtFileNameFull);
				if (!DEBUGdryRun) {
					writeRemoteLogFile(new SimpleDateFormat("yyyMMddHHmmss").format(new Date()) + " " + tgtFileNameFull);
					if (!copyFileToNAS(srcFileNameFull, tgtFileNameFull, srcFileTimestamp)) {
						writeRemoteLogFile(" ERR\n");
						break;
					} else
						writeRemoteLogFile(" OK\n");
				}
			} else
				broadcastCopyInProgress(srcFileName, "Already there");

			if (!DEBUGdryRun) {
				if (cksumEnabled)
					MediaFilesDB.updateTgtFileName(srcFilePath, srcFileName, tgtFileNameFull,null);
				else
					MediaFilesDB.updateTgtFileName(srcFilePath, srcFileName, tgtFileNameFull,cksumGlobalVariable);
			}
			mediaFilesCountToSync--;
			broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
			saveConsistentSyncTimestamp(srcFileTimestamp);
			cToSync.moveToNext();
		}
		if (!cToSync.isClosed())
			cToSync.close();
		broadcastCopyInProgress("none", "none");
		if (wakeLock != null) {
			Log.d("DoSyncFromDB","wakeLock released");
			wakeLock.release();
		}
		if (wifiLock != null) {
			Log.d("DoSyncFromDB","wifiLock released");
			wifiLock.release();
		}
		if ((PicSyncState != ePicSyncState.PIC_SYNC_STATE_SYNCING) && (Looper.getMainLooper().getThread() != Thread.currentThread())) {
			Log.d("DoSyncFromDB","Stopping sync thread");
			stopSelf();
		}
		MediaFilesDB.getUnsyncedFilesCloseDB();
	}

	private void handleActionGetCurrTask() {
		Log.i("PicSync", "handleActionGetCurrTask: " + stateCurrTaskDescription);
		initPreferences();
		if (settings.getString("prefsSMBSRV", "EMPTY").equals("EMPTY"))
			broadcastCurrTask("Not Configured");
		initlastCopiedImageTimestamp();
		broadcastLastCopiedImageTimestamp();

		if (settings.getString("prefsSMBSRV", "EMPTY").equals("EMPTY"))
			broadcastCurrTask("Not Configured");
		if (!prefMACverified)
			prefMACverified = settings.getBoolean("prefMACverified", false);

		broadcastCurrTask(stateCurrTaskDescription);
		NASService.checkConnection();
		broadcastConnectionStatus();
		MediaFilesDB.getDBStatistics();
		broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
	}

	private void handleActionStartSync(String flags) {
		final String LOG_TAG="handleActionStartSync";
		boolean doit = false;
		Log.d(LOG_TAG,"beginning");
		if (PicSyncState != ePicSyncState.PIC_SYNC_STATE_SYNCING)
			doit = true;

		if (flags != null && flags.equals(ACTION_START_SYNC_RESTART))
			doit = true;

		if (doit) {
			Log.d(LOG_TAG,"Do it");

			broadcastCurrTask(stateCurrTaskDescription = "Sync initiated");
			doNotify();
			broadcastConnectionStatus();
			Log.d(LOG_TAG,"NAS Connected: "+new Boolean(stateNASConnected).toString());
			if (!stateNASConnected) {
				try {
					NASService.openConnection();
					NASService.waitForConnection(3, 2);
					broadcastConnectionStatus();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			initPreferences();
			if (!stateNASConnected && prefWoLAllowed && prefMACverified) {
				Log.d(LOG_TAG,"performing WoL");
				broadcastCurrTask(stateCurrTaskDescription = "Sending WoL packet");
				NASService.WoL();
				NASService.waitForConnection(10, 2);
			}
			if (!stateNASConnected){
				Log.d(LOG_TAG,"prefWoLAllowed: "+new Boolean(prefWoLAllowed).toString());
				Log.d(LOG_TAG,"prefMACverified: "+new Boolean(prefMACverified).toString());
			}
			broadcastConnectionStatus();
			if (stateNASConnected) {
				PicSyncState = ePicSyncState.PIC_SYNC_STATE_SYNCING;
				broadcastCurrTask(stateCurrTaskDescription = "Copying files");
				doNotify();
				Log.d(LOG_TAG,"Starting sync thread");
				DoSyncInSeparateThread.run();
			} else {
				Log.d(LOG_TAG,"NAS Connected: "+new Boolean(stateNASConnected).toString());
				broadcastCurrTask(stateCurrTaskDescription = "Not in sync");
			}
			doNotify();
			PicSyncState = ePicSyncState.PIC_SYNC_STATE_STOPPED;
		}
	}

	private void writeRemoteLogFile(String message) {
		SmbFile smbLogFile;
		SmbFileOutputStream smbLogFileOutputStream;
		try {
/*
			NASService.openConnection();
*/
			smbLogFile = new SmbFile(smbshareurl + "/" + picSyncLogFile, auth);
			smbLogFileOutputStream = new SmbFileOutputStream(smbLogFile, true);
			broadcastConnectionStatus();
		} catch (IOException e) {
			e.printStackTrace();
			broadcastConnectionStatus();
			return;
		}
		try {
/*
			smbLogFileOutputStream.write(Long.valueOf(lastCopiedImageDate.getTime()).toString().getBytes());
*/
			smbLogFileOutputStream.write(message.getBytes());
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
		} catch (IOException e) {
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
						.setContentText(stateCurrTaskDescription);
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

	private enum ePicSyncState {PIC_SYNC_STATE_STOPPED, PIC_SYNC_STATE_SYNCING, PIC_SYNC_STATE_SCANNING, PIC_SYNC_STATE_NO_ACCESS}

	/*
		************************************************************************************************
	*/
	private static class MediaFilesDB extends SQLiteOpenHelper {
		static final int DATABASE_VERSION = 9;
		static final String DATABASE_NAME = "PicSync.db";
		private static volatile int dbOpened = 0;
		private static SQLiteDatabase db;

		MediaFilesDB(Context ctx) {
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		}
		public void updateFileHashOfAllFiles(){
			db = getReadableDatabase();
			Cursor cChkFile;
			if (db == null)
				return;
			int count = 1;

			try {
				cChkFile = db.rawQuery("select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
								+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
								+ " where "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5 + "=\"" + 0 + "\""
								+ " or "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5 + " is null "
								+ " or "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH + "!=\"" + cksumMaxBytes + "\""
								+ " or "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH + " is null "

						, null);
				cChkFile.moveToFirst();
				while (!cChkFile.isAfterLast()) {
					int colSRCPATH = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
					int colSRCFILE = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
					String fileNameFull = cChkFile.getString(colSRCPATH) + File.separator + cChkFile.getString(colSRCFILE);
					String md5sum = Utils.makeFileFingerprint(fileNameFull);
					ContentValues newHashValues = new ContentValues();
					newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5, md5sum);
					newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH, cksumMaxBytes);
					String where = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" +  cChkFile.getString(colSRCPATH) + "\""
							+ " and "
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + cChkFile.getString(colSRCFILE) + "\"";
					db.update(Constants.MediaFilesDBEntry.TABLE_NAME, newHashValues, where, null);
					Log.d("updateFileHash", fileNameFull + " md5sum: " + md5sum);
					cChkFile.moveToNext();
				}
				cChkFile.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				if (--dbOpened == 0)
					db.close();
			}
		}
		public void updateMetadataHashOfFile(String filePath, String fileName, String fileMetadataHash){
			String fileNameFull = filePath + File.separator + fileName;
			ContentValues newHashValues = new ContentValues();
			newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT, fileMetadataHash);
			newHashValues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE, Constants.MediaFilesDBEntry_FINGERPRINT_TYPE);
			String where = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" +  filePath + "\""
								   + " and "
								   + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + fileName + "\"";
			db.update(Constants.MediaFilesDBEntry.TABLE_NAME, newHashValues, where, null);
			Log.d("updateFileHash", fileNameFull + " exifHash: " + fileMetadataHash);
		}
		public void updateMetadataHashOfAllFiles(){
			db = getReadableDatabase();
			Cursor cChkFile;
			if (db == null)
				return;
			int count = 1;

			try {
				cChkFile = db.rawQuery("select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
								+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
								+ " where "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT + "=\"" + 0 + "\""
								+ " or "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT + " is null "
								+ " or "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE + "!=\"" + Constants.MediaFilesDBEntry_FINGERPRINT_TYPE + "\""
						, null);
				cChkFile.moveToFirst();
				while (!cChkFile.isAfterLast()) {
					int colSRCPATH = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
					int colSRCFILE = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
					String filePath = cChkFile.getString(colSRCPATH);
					String fileName = cChkFile.getString(colSRCFILE);
					String fileNameFull = filePath + File.separator + fileName;
					String fileType = Utils.getFileType(fileNameFull);
					String fileMetadataHash = null;
					if ( fileType.equals(Constants.FILE_TYPE_PICTURE))
						fileMetadataHash = Utils.makeEXIFHash(fileNameFull);
					else if ( fileType.equals(Constants.FILE_TYPE_VIDEO))
						fileMetadataHash = Utils.makeFileFingerprint(fileNameFull);
					updateMetadataHashOfFile(filePath, fileName, fileMetadataHash);
					cChkFile.moveToNext();
				}
				cChkFile.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				if (--dbOpened == 0)
					db.close();
			}
		}
		void insertSrcFile(String srcMediaFileNameFull) {
			final String TAG="String";
			db = getWritableDatabase();
			File mediaFile = new File(srcMediaFileNameFull);
			String srcMediaFileName = mediaFile.getName();
			String srcMediaFilePath = mediaFile.getParent();
			Long srcMediaFileSize = mediaFile.length();
			ContentValues newMediaFile=null;
			if (!containsSrcFile(srcMediaFilePath,srcMediaFileName)) {
				Long lastModified = mediaFile.lastModified();
				newMediaFile = new ContentValues();
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH, srcMediaFilePath);
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE, srcMediaFileName);
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS, lastModified);
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS, lastModified);
/*
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT, exifHash);
*/
				newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE, srcMediaFileSize);
				if (cksumEnabled){
					newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5, Utils.makeFileFingerprint(srcMediaFileNameFull));
					newMediaFile.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH, cksumMaxBytes);
				}
				mediaFilesScanned++;
				try {
					db.insert(Constants.MediaFilesDBEntry.TABLE_NAME, null, newMediaFile);
				} catch (Exception e) {
					e.printStackTrace();
					Log.d("insertSrcFile","dbOpened: "+dbOpened);
				}
			}
			if (--dbOpened == 0)
				db.close();
		}

		void insertSrcFile(String[] srcMediaFileNameFullList) {
			for (String srcMediaFileNameFull : srcMediaFileNameFullList)
				insertSrcFile(srcMediaFileNameFull);
		}

		void updateTgtFileName(String srcMediaFilePath, String srcMediaFileName, String tgtMediaFileNameFull){
			updateTgtFileName(srcMediaFilePath, srcMediaFileName, tgtMediaFileNameFull,null);
		}
		void updateTgtFileName(String srcMediaFilePath, String srcMediaFileName, String tgtMediaFileNameFull, String md5sum){
			//update tgtMediaFileName where  srcMediaFilePath + srcMediaFileName
			ContentValues newvalues = new ContentValues();
			newvalues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_TGT, tgtMediaFileNameFull);
			if (md5sum != null) {
				newvalues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5, md5sum);
				newvalues.put(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH, cksumMaxBytes);
			}
			String where = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
					+ " and "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\"";
			db.update(Constants.MediaFilesDBEntry.TABLE_NAME, newvalues, where, null);
		}
		void removeLocalPathWithUnsyncedPics(String srcMediaFilePath) {
			db = getWritableDatabase();
			String WHERE = Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + " like \"" + srcMediaFilePath + "%\""
					+ " and ("
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
					+ " or "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null )";

			try {
				Cursor c = db.rawQuery("select count(*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
								+ " where "
						+ WHERE
						, null);
				c.moveToFirst();
				Integer picsToRemove = c.getInt(0);
				c.close();
				Log.d("removePath","Removing "+ picsToRemove+" pictures from path "+srcMediaFilePath);
				db.delete(Constants.MediaFilesDBEntry.TABLE_NAME, WHERE, null);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (--dbOpened == 0)
				db.close();

		}
		boolean removeSrcFile(String srcMediaFileNameFull) {
			db = getWritableDatabase();
			boolean rc=true;
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
				rc = false;
			}
			if (--dbOpened == 0)
				db.close();
			return rc;
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
			int count = 1;
			try {
				cChkFile = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
								+ " where "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + "=\"" + srcMediaFilePath + "\""
								+ " and "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + "=\"" + srcMediaFileName + "\""
						, null);
				cChkFile.moveToFirst();
				count = cChkFile.getInt(0);
				cChkFile.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				if (--dbOpened == 0)
					db.close();
			}
			return (count > 0);
		}
		boolean containsSrcFileOfSize(Long fileSize){
			db = getReadableDatabase();
			Cursor cChkFile;
			if (db == null)
				return true;
			int count = 1;
			try {
				cChkFile = db.rawQuery("select count (*) from " + Constants.MediaFilesDBEntry.TABLE_NAME
								+ " where "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE + "=\"" + fileSize + "\""
						, null);
				cChkFile.moveToFirst();
				count = cChkFile.getInt(0);
				cChkFile.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				if (--dbOpened == 0)
					db.close();
			}
			return (count > 0);
		}
		String getSrcFileOfSizeAndHash(Long fileSize, String fileHash){
			db = getReadableDatabase();
			Cursor cChkFile;
			if (db == null)
				return null;
			int count = 1;
			String fileNameFull=new String();
			try {
				cChkFile = db.rawQuery("select * from " + Constants.MediaFilesDBEntry.TABLE_NAME
								+ " where "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE + "=\"" + fileSize + "\""
								+ " and "
								+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5 + "=\"" + fileHash + "\""
						, null);
				cChkFile.moveToFirst();
				if (cChkFile.isAfterLast())
					return null;
				int colSRCPATH = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
				int colSRCFILE = cChkFile.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
				fileNameFull = cChkFile.getString(colSRCPATH)+File.separator+cChkFile.getString(colSRCFILE);
				cChkFile.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				if (--dbOpened == 0)
					db.close();
			}
			return fileNameFull;
		}
		Cursor getUnsyncedFiles() {
			db = getWritableDatabase();
			Cursor cToSync;
			SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
			if (db == null)
				return null;
			try {
				cToSync = db.rawQuery("select "
											  + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH
											  + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE
											  + "," + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS
											  + " from " + Constants.MediaFilesDBEntry.TABLE_NAME
											  + " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
											  + " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
											  + " order by " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " asc "
						, null);
				if (cToSync == null)
					return null;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return cToSync;
		}
		Cursor getAllFiles() {
			db = getWritableDatabase();
			Cursor cAllFiles;
//			SQLiteDatabase db = MediaFilesDB.getWritableDatabase();
			if (db == null)
				return null;
			try {
				cAllFiles = db.rawQuery("select * "
											  + " from " + Constants.MediaFilesDBEntry.TABLE_NAME
/*
											  + " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
											  + " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
											  + " order by " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " desc "
*/
						, null);
				if (cAllFiles == null)
					return null;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return cAllFiles;
		}
		void getUnsyncedFilesCloseDB() {
			if (--dbOpened == 0)
				db.close();
		}
	/*
	* Function return timestamp of the most recent synced pic
	* where all older pictures are also synced
	* */
		Long getConsistentSyncTimestamp() {
			db = getWritableDatabase();
			String QUERY = "select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS
					+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
					+ " where ( " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " = \"\""
					+ " or " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is null "
					+ " ) and "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " > 0 "
					+ " order by " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " asc"
					+ " limit 1";
			Cursor cOldestUnsyncedImage = db.rawQuery(QUERY, null);
			if (cOldestUnsyncedImage == null)
				return 0L;
			cOldestUnsyncedImage.moveToFirst();
			if (cOldestUnsyncedImage.isAfterLast())
				return 0L;
			Long oldestUnsyncedImage = cOldestUnsyncedImage.getLong(0);
			cOldestUnsyncedImage.close();

			QUERY = "select " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS
					+ " from " + Constants.MediaFilesDBEntry.TABLE_NAME
					+ " where " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + "  != \"\" "
					+ " and " + Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " is not null "
					+ " and " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " > 0 "
					+ " and " + Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " <  " + oldestUnsyncedImage
					+ " order by timestamp desc limit 1";
			Cursor cConsistentSyncTimestamp = db.rawQuery(QUERY, null);
			if (cConsistentSyncTimestamp == null)
				return 0L;
			cConsistentSyncTimestamp.moveToFirst();
			if (cConsistentSyncTimestamp.isAfterLast())
				return oldestUnsyncedImage-1;
			Long ConsistentSyncTimestamp = cConsistentSyncTimestamp.getLong(0);
			cConsistentSyncTimestamp.close();
			if (--dbOpened == 0)
				db.close();
			return ConsistentSyncTimestamp;
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

/*
			Cursor cHighestScannedTimestamp = db.rawQuery("select max("
							+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + ")"
							+ " from "
							+ Constants.MediaFilesDBEntry.TABLE_NAME
					, null);
			cHighestScannedTimestamp.moveToFirst();
*/
/*
			lastScannedImageTimestamp = cHighestScannedTimestamp.getLong(0);
*//*

			cHighestScannedTimestamp.close();

*/
			if (--dbOpened == 0)
				db.close();
		}

		public void onCreate(SQLiteDatabase db) {
			Log.d("SQL","Creating db "+DATABASE_NAME);
			db.execSQL("drop table if exists " + Constants.MediaFilesDBEntry.TABLE_NAME);
			final String TABLE_CREATE = "create table " + Constants.MediaFilesDBEntry.TABLE_NAME + "("
					+ Constants.MediaFilesDBEntry.TABLE_NAME
					+ "_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5 + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_MD5_LENGTH + " INTEGER, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_MIN_FILE + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS + " INTEGER, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_TGT + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT + " TEXT, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT_TYPE + " INTEGER, "
					+ Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE + " INTEGER, "
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
			synchronized (this) {
				dbOpened++;
			}
			Log.d("onOpen "+db.toString(),Integer.toString(dbOpened));
		}

		public void openDBRW() {
			synchronized (this) {
				db = getWritableDatabase();
				dbOpened++;
			}
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
	private static class NASService {
		static Context MyContext;

		NASService(Context ctx) {
			MyContext = ctx;
/*
			jcifs.Config.setProperty("jcifs.netbios.wins", "127.0.0.1");
		if (settings == null)
			settings = PreferenceManager.getDefaultSharedPreferences(myContext);
*/
		}
		public static boolean isHomeWifiConnected() {
			boolean onHomeWifi, isConnected;
			int connType;
			WifiManager wifiManager = (WifiManager) MyContext.getSystemService(WIFI_SERVICE);
			ConnectivityManager cm = (ConnectivityManager) MyContext.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkInfo = cm.getActiveNetworkInfo();
			if (networkInfo == null)
				return false;
			connType = networkInfo.getType();
			if (connType == ConnectivityManager.TYPE_WIFI) {
				isConnected = networkInfo.isConnected();
				if (isConnected) {
					final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
					if (connectionInfo != null) {
						String ssid = connectionInfo.getSSID();
						settings = PreferenceManager.getDefaultSharedPreferences(MyContext);
						String prefsWifi = settings.getString("pref_homewifissid", "NoWifiWhatsoever");
						return ssid.replaceAll("^\"|\"$", "").equals(prefsWifi);
					}
				}
			}
			return false;
		}

		public static boolean checkConnection() {
			if (!isHomeWifiConnected()) {
//				makeToast("Wifi is not connected");
				return false;
			}
			stateNASConnected = true;
			try {
				if (!stateNASauthenticated)
					openConnection();
				String[] x = (new SmbFile("smb://" + smbservername + "/", auth)).list();
			} catch (IOException e) {
				e.printStackTrace();
				stateNASConnected = false;
				stateNASauthenticated = false;
			}

			if (!prefMACverified){
				prefMACverified = settings.getBoolean("prefMACverified", false);
				if (stateNASConnected)
					storeWoLInfo();
			}

			return stateNASConnected;
		}

		public static void setAuthentication() {
			if ((stateNASauthenticated) && (!SharedPreferencesChanged))
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

		public static void openConnection() throws IOException {
			if (!isHomeWifiConnected()) {
				return;
			}
			smbservername = settings.getString("prefsSMBSRV", "");
			smbshare = settings.getString(MyContext.getString(R.string.pref_cifs_share), "");

			if (smbshare.isEmpty())
				smbshareurl = "smb://" + smbservername + "/";
			else
				smbshareurl = "smb://" + smbservername + "/" + smbshare + "/";
			setAuthentication();
			stateNASConnected = true;
			SmbFile[] domains = null;
			SmbFile domainsFile;
			try {
				domainsFile = new SmbFile("smb:///", auth);
			} catch (MalformedURLException e) {
				stateNASConnected = false;
				throw e;
			}
			try {
				domains = domainsFile.listFiles();
				stateNASauthenticated = true;
			} catch (SmbException e) {
				stateNASConnected = false;
				if (e.toString().contains("Logon failure")) {
					makeToast("Check username and password settings");
				}
				else if (e.toString().contains("Failed to connect to server"))
					Log.d("openConnection",e.getMessage());
				else
					e.printStackTrace();
				stateNASauthenticated = false;
				throw new IOException();
			}
		}

		public static void waitForConnection(int retries, int timeout) {
			int iteration = 1;
			boolean connected;
			while (iteration++ < retries && !(connected = checkConnection())) {
				Log.d("waitForConnection","Waiting "+iteration+". time");
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
//			ipStr = settings.getString("smbserverip", "");
			ipStr="255.255.255.255";
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
//				socket.setBroadcast(true);
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
						settingsEditor.putBoolean("prefMACverified", true);
						prefMACverified = true;
						settingsEditor.apply();
						break;
					}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public static void makeToast(final String toastString) {
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
