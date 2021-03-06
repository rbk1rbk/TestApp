package com.rbk.testapp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

import static android.app.AlarmManager.INTERVAL_HALF_HOUR;
import static android.app.AlarmManager.RTC_WAKEUP;
import static java.lang.Thread.sleep;

public class PicSync extends IntentService{
	private static final int mId=1;
	public static final String NOTIFICATION = "com.rbk.testapp.MainScreen.receiver";
//	public static final String INTENT_PARENT = "IntentParent";
	static final String ACTION_ADD_MEDIA_FOLDERS_TO_SETTINGS = "PicSync.addMediaFolders";
	static final String ACTION_DEL_MEDIA_FOLDERS_W_PICS = "PicSync.delMediaFolder";
	static final String INTENT_PARAM_LOCAL_FOLDER = "PicSync.localFolder";
	static final String ACTION_GET_STORAGE_PATHS = "PicSync.getAllExternalStoragePaths";
	static final String ACTION_GET_STORAGE_PATHS_EACCESS = "PicSync.getAllExternalStoragePathsEACCESS";
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
	static int scanCIFSFilesWithSizeIntoDBTotalCount=0;
	static int scanCIFSFilesWithSizeIntoDBTotalCountLastPrint=0;

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
	private static MediaFilesDB MediaFilesDB;
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

	private AlarmManager alarmMgr = null;
	private PendingIntent alarmIntent;


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
	com.rbk.testapp.MediaFilesDB.MediaFilesCallback testCallback;
	private void initlastCopiedImageTimestamp() {
		if (!preferenceInitialized)
			initPreferences();
		if (MediaFilesDB == null) {
			MediaFilesDB = new MediaFilesDB(myContext);
			testCallback=new MediaFilesDB.MediaFilesCallback() {
				@Override
				public void onDBStatisticsChange(String statistic, int data) {
					if (statistic.equals("mediaFilesCountTotal"))
						mediaFilesCountTotal=data;
					if (statistic.equals("mediaFilesCountToSync"))
						mediaFilesCountToSync=data;
					broadcastMediaFilesCount(mediaFilesCountTotal,0,mediaFilesCountToSync);
				}
			};
			MediaFilesDB.setMediaFilesCallback(testCallback);
		}
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
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
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
				scanMediaFilesToSync();
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
				//scanCIFS4SyncedFilesByFilesize();
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
			if (ACTION_GET_STORAGE_PATHS_EACCESS.equals(action)) {
			}
			if (ACTION_GET_STORAGE_PATHS.equals(action)) {
				if (!checkStoragePermission())
					return;
				String[] storagePaths = getAllExternalStoragePaths();
				Intent returnstoragePathsIntent = new Intent("storagePaths");
				returnstoragePathsIntent.putExtra("storagePaths", storagePaths);
				returnstoragePathsIntent.putExtra("cmdTimestamp", cmdTimestamp);
				LocalBroadcastManager.getInstance(this).sendBroadcastSync(returnstoragePathsIntent);
			}
			if (ACTION_GET_STORAGE_PATHS_EACCESS.equals(action)) {
				getAllExternalStoragePaths();
				Intent returnstoragePathsIntent = new Intent(ACTION_GET_STORAGE_PATHS_EACCESS);
				if (storagePathsEACCESS != null) {
					returnstoragePathsIntent.putExtra("storagePathsEACCESS", storagePathsEACCESS);
					returnstoragePathsIntent.putExtra("cmdTimestamp", cmdTimestamp);
				}
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


	private String[] storagePathsEACCESS=null;
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
				} catch (IOException|java.lang.NullPointerException e) {
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
		SortedSet<String> storagePathsEACCESSTree = new TreeSet<String>();

		storagePathsEACCESS=null;
		storagePathsSetVerified.addAll(storagePathsSet);
		File testFile=null;
		for (String storagePath2Check : storagePathsSet){
			try {
				Log.d(LOG_TAG,"Checking path "+storagePath2Check);
				//search for a file, don't create one
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
				if (e.toString().contains("EACCES") && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)){
					storagePathsEACCESSTree.add(storagePath2Check);
				}
				if ((testFile.exists()))
					testFile.delete();
			}
		}
		if (storagePathsEACCESSTree.size()>0)
			storagePathsEACCESS=storagePathsEACCESSTree.toArray(new String[storagePathsEACCESSTree.size()]);
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

	private boolean isFileInConfiguredLocation(String fileNameFull){
		Set<String> prefFolderList  = settings.getStringSet("prefFolderList",null);
		if (prefFolderList == null)
			return false;
		for (String mediaPath : prefFolderList){
			if (fileNameFull.startsWith(mediaPath))
				return true;
		}
		return false;
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
				//TODO: overit, ci sa skenovany subor nachadza pod konfigurovanou cestou
				String srcFileNameFullCanonical = new File(srcFile).getCanonicalPath().toString();
				if (isFileInConfiguredLocation(srcFileNameFullCanonical) ){
					// && !MediaFilesDB.containsSrcFile(srcFile) => insertSrcFile si robi tuto kontrolu
					if (MediaFilesDB.insertSrcFile(srcFileNameFullCanonical))
						mediaFilesScanned++;
				}
				cursor.moveToNext();
			}
			cursor.close();
			MediaFilesDB.getDBStatistics();
			broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
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
		String fileName = null;
		Long fileSize = 0L;
		for (SmbFile entry : fileList) {
			boolean isDirectory = false;
			try {
				isDirectory = entry.isDirectory();
				fileSize = entry.length();
			} catch (SmbException e) {
				e.printStackTrace();
				return 0;
			}
			if (isDirectory) {
				count = scanCIFSFilesWithSizeIntoDB(entry.getCanonicalPath());
			} else {
				fileName = entry.toString();
				dbRemoteFiles = new RemoteFilesDB(myContext);
				dbRemoteFiles.addFile(fileName, fileSize);
				count++;
			}
		}
		scanCIFSFilesWithSizeIntoDBTotalCount+=count;
		if (scanCIFSFilesWithSizeIntoDBTotalCount - scanCIFSFilesWithSizeIntoDBTotalCountLastPrint > 1000) {
			scanCIFSFilesWithSizeIntoDBTotalCountLastPrint = scanCIFSFilesWithSizeIntoDBTotalCount;
			Log.d("scanCIFSFilesWithSize", "Found " + scanCIFSFilesWithSizeIntoDBTotalCount + " files");
			Log.d("listNASFilesWithSize", "Found " + fileName + " of size " + fileSize);
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

	private void scanCIFS4SyncedFilesByMetadata() {
		final String LOG_TAG = "scanCIFSByMeta";
		if (!NASService.checkConnection()) {
			makeToast("NAS connection not available");
			return;
		}

		PowerManager.WakeLock wakeLock = null;
		WifiManager.WifiLock wifiLock = null;
		if (prefEnabledWakeLockCopy) {
			PowerManager powerManager = (PowerManager) myContext.getSystemService(POWER_SERVICE);
			WifiManager wifiManager = (WifiManager) myContext.getSystemService(WIFI_SERVICE);
			if (powerManager == null || wifiManager == null) {
				Log.d(LOG_TAG, "PowerManager or WifiManager not available");
			} else {
				wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DoSyncFromDB");
				wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "DoSyncFromDB");
				wakeLock.acquire();
				wifiLock.acquire();
				Log.d(LOG_TAG, "wake and WiFi lock acquired");
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
					if (powerManager.isPowerSaveMode())
						Log.d(LOG_TAG, "Super saver mode enabled, may work incorrectly.");
				}
			}
		}

		if (tgtNASPath == null) {
			tgtNASPath = settings.getString("prefsSMBURI", null);
			if (tgtNASPath.endsWith("/"))
				tgtNASPath = tgtNASPath.substring(0, tgtNASPath.lastIndexOf("/"));
		}
		broadcastCurrTask(stateCurrTaskDescription = "Updating local fingerprints");
		MediaFilesDB.updateMetadataHashOfAllFiles();

/*
		broadcastCurrTask(stateCurrTaskDescription = "Cleaning remote file DB");
		dbRemoteFiles = new RemoteFilesDB(myContext);
		int oldSize = dbRemoteFiles.size();
		Cursor cAllRemoteFiles = dbRemoteFiles.getAllFiles();
		if (cAllRemoteFiles != null) {
			cAllRemoteFiles.moveToFirst();
			int colRemoteFileName = cAllRemoteFiles.getColumnIndex(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE);
			while (!cAllRemoteFiles.isAfterLast()) {
				String remoteFileNameFull = cAllRemoteFiles.getString(colRemoteFileName);
				SmbFile remoteFile = null;
				try {
					remoteFile = new SmbFile(remoteFileNameFull, auth);
					if (!remoteFile.exists()) {
						dbRemoteFiles.delFile(remoteFileNameFull);
						Log.d(LOG_TAG, "removing " + remoteFileNameFull);
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (SmbException e) {
					e.printStackTrace();
				}
				cAllRemoteFiles.moveToNext();
			}
			cAllRemoteFiles.close();

		}
		Log.d(LOG_TAG, "old remote file DB size " + oldSize);
		Log.d(LOG_TAG, "new remote file DB size " + dbRemoteFiles.size());
*/

		broadcastCurrTask(stateCurrTaskDescription = "Scanning remote files");
		int remoteFilesFound = 0;
		scanCIFSFilesWithSizeIntoDBTotalCount = 0;
//		if (dbRemoteFiles == null)
		scanCIFSFilesWithSizeIntoDB(tgtNASPath);
		remoteFilesFound = dbRemoteFiles.size();

		Log.d(LOG_TAG, "Found NAS files: " + remoteFilesFound);

		broadcastCurrTask(stateCurrTaskDescription = "Comparing local w/ remote");
//		Cursor cAllLocalFiles = MediaFilesDB.getAllFiles();
		Cursor cAllLocalFiles = MediaFilesDB.getUnsyncedFiles(consistentSyncTimestamp);
		cAllLocalFiles.moveToFirst();
		int colSRCPATH = cAllLocalFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
		int colSRCFILE = cAllLocalFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
		int colSRCSIZE = cAllLocalFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILESIZE);
		int colSRCHASH = cAllLocalFiles.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_FINGERPRINT);
		while (!cAllLocalFiles.isAfterLast()) {
			Long fileSizeLocal = cAllLocalFiles.getLong(colSRCSIZE);
			String filePathLocal = cAllLocalFiles.getString(colSRCPATH);
			String fileNameLocal = cAllLocalFiles.getString(colSRCFILE);
			String fileHashLocal = cAllLocalFiles.getString(colSRCHASH);
			String fileNameFullLocal = filePathLocal + File.separator + fileNameLocal;

			Log.d(LOG_TAG, "Searching for remote copy of " + fileNameFullLocal + " / " + fileSizeLocal);
			String fileTypeLocal = Utils.getFileType(fileNameFullLocal);
			if (fileTypeLocal == null) {
				cAllLocalFiles.moveToNext();
				continue;
			}
			Cursor cSimilarRemoteFiles;
			if (fileTypeLocal.equals(Constants.FILE_TYPE_PICTURE)) {
				cSimilarRemoteFiles = dbRemoteFiles.getFilesBySize(fileSizeLocal, 1 * 1024L);
			} else if (fileTypeLocal.equals(Constants.FILE_TYPE_VIDEO))
				cSimilarRemoteFiles = dbRemoteFiles.getFilesBySize(fileSizeLocal, 0L);
			else {
				cAllLocalFiles.moveToNext();
				continue;
			}
			cSimilarRemoteFiles.moveToFirst();
			int colRemoteFileName = 0;
//			int colRemoteFileSize=0;
			int colRemoteFileHash = 0;
			String fileHashRemote = "";
			String remoteFileNameFull;
			if (!cSimilarRemoteFiles.isAfterLast()) {
				colRemoteFileName = cSimilarRemoteFiles.getColumnIndex(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILE);
//				colRemoteFileSize=cSimilarRemoteFiles.getColumnIndex(Constants.RemoteFilesDBEntry.COLUMN_NAME_FILESIZE);
				colRemoteFileHash = cSimilarRemoteFiles.getColumnIndex(Constants.RemoteFilesDBEntry.COLUMN_NAME_FINGERPRINT);

				while (!cSimilarRemoteFiles.isAfterLast() && fileHashLocal != null && !fileHashLocal.equals(fileHashRemote)) {
					fileHashRemote = cSimilarRemoteFiles.getString(colRemoteFileHash);
					remoteFileNameFull = cSimilarRemoteFiles.getString(colRemoteFileName);
					if (fileHashRemote == null || fileHashRemote.equals("")) {
						String fileTypeRemote = Utils.getFileType(remoteFileNameFull);
						if (fileTypeRemote == null) {
							cSimilarRemoteFiles.moveToNext();
							continue;
						}
						if (fileTypeRemote.equals(Constants.FILE_TYPE_PICTURE)) {
							String localEXIFFileCache = copyEXIFHeaderLocally(remoteFileNameFull);
							if (localEXIFFileCache == null) {
								Log.d("copyEXIFHeader", "removing " + remoteFileNameFull);
								dbRemoteFiles.delFile(remoteFileNameFull);
								cSimilarRemoteFiles.moveToNext();
								continue;
							}
							fileHashRemote = Utils.makeEXIFHash(localEXIFFileCache);
							new File(localEXIFFileCache).delete();
						} else if (fileTypeRemote.equals(Constants.FILE_TYPE_VIDEO))
							fileHashRemote = Utils.makeFileFingerprint(remoteFileNameFull, auth);
						else {
							cSimilarRemoteFiles.moveToNext();
							continue;
						}
						dbRemoteFiles.updateFileFingerprint(remoteFileNameFull, fileHashRemote);
					}
					if (!fileHashLocal.equals(fileHashRemote)) {
						cSimilarRemoteFiles.moveToNext();
					}
				}
			}
			if (fileHashLocal.equals(fileHashRemote)) {
				String fileNameFullRemote = cSimilarRemoteFiles.getString(colRemoteFileName);
				Log.d(LOG_TAG, "Identical files: " + fileNameFullLocal + " and " + fileNameFullRemote);
				MediaFilesDB.updateTgtFileName(filePathLocal, fileNameLocal, fileNameFullRemote);
				broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, --mediaFilesCountToSync);
			}
			cAllLocalFiles.moveToNext();
			cSimilarRemoteFiles.close();
		}
		cAllLocalFiles.close();
		dbRemoteFiles.close();
		MediaFilesDB.getDBStatistics();
		broadcastMediaFilesCount(mediaFilesCountTotal, mediaFilesScanned, mediaFilesCountToSync);
		broadcastCurrTask(stateCurrTaskDescription = "Idle");

		if (wakeLock != null) {
			Log.d("DoSyncFromDB", "wakeLock released");
			wakeLock.release();
		}
		if (wifiLock != null) {
			Log.d("DoSyncFromDB", "wifiLock released");
			wifiLock.release();
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
			localFileName=Environment.getExternalStorageDirectory().getCanonicalPath()+File.separator+"exif.jpg";
			localFile=new FileOutputStream(localFileName);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		try {
			NASService.openConnection();
//			Log.i("PicSync", "Opening file: " + remoteFileName);
			sfile = new SmbFile(remoteFileName, auth);
			in = new SmbFileInputStream(sfile);
		} catch (IOException e) {
//			e.printStackTrace();
			Log.i("PicSync", "File NOT opened " + e.getMessage());
			return null;
		}

		int bytes2copy = 5 * 16 *1024; //o nieco viac ako 64k
		int iosize=16*1024;
		byte[] buffer = new byte[iosize];
		int bytes_read, bytes_total=0;
		try {
			while ((bytes_read = in.read(buffer,0,iosize)) > 0 && bytes_total < bytes2copy) {
				bytes_total+=bytes_read;
				localFile.write(buffer,0,bytes_read);
			}
			localFile.close();
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
			//if this file is missing, consider copying as OK
			rc = true;
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
		if (!preferenceInitialized)
			initPreferences();
		Cursor cToSync = MediaFilesDB.getUnsyncedFiles(settings.getLong("consistentSyncTimestamp",0));
		if (cToSync == null)
			return;
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
		cToSync.moveToFirst();
		int colSRCPATH = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_PATH);
		int colSRCFILE = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_FILE);
		int colSRCTS = cToSync.getColumnIndex(Constants.MediaFilesDBEntry.COLUMN_NAME_SRC_TS);

		while (!cToSync.isAfterLast() && PicSyncState == ePicSyncState.PIC_SYNC_STATE_SYNCING) {
			String srcFilePath = cToSync.getString(colSRCPATH);
			String srcFileName = cToSync.getString(colSRCFILE);
			String srcFileNameFull = srcFilePath + "/" + srcFileName;

			if (! new File(srcFileNameFull).exists()){
				//If the file doesn't exist anymore
				Log.d("File not found",srcFileNameFull);
				MediaFilesDB.removeSrcFile(srcFileNameFull);
				cToSync.moveToNext();
				continue;
			}

			Log.d("DoSyncFromDB copy from",srcFileNameFull);
			long srcFileTimestamp = cToSync.getLong(colSRCTS);
			String tgtFileNameFull = constructNASPath(srcFilePath, srcFileName, srcFileTimestamp);
			Log.d("DoSyncFromDB copy to  ",tgtFileNameFull);
			if (tgtFileNameFull == null) {
				break;
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
	private void setupAlarm(){

		alarmMgr = (AlarmManager)myContext.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(myContext, PicSync.class)
				.setAction(PicSync.ACTION_STOP_SYNC)
				.putExtra("cmdTimestamp", new Date().getTime());
		alarmIntent = PendingIntent.getBroadcast(myContext, 0, intent, 0);
		alarmMgr.setInexactRepeating(RTC_WAKEUP,new Date().getTime(),INTERVAL_HALF_HOUR,alarmIntent);
		alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,10000,alarmIntent);
	}
	private void cancelAlarm(){
		if (alarmMgr==null)
			return;
		alarmMgr.cancel(PendingIntent.getBroadcast(myContext, 0, new Intent(myContext, PicSync.class),0));
	}
	private void handleActionStartSync(String flags) {
		final String LOG_TAG="handleActionStartSync";
		boolean doit = false;
		Log.d(LOG_TAG,"beginning");
		if (tgtNASPath == null || tgtNASPath.length()==0) {
			tgtNASPath = settings.getString("prefsSMBURI", null);
			if (tgtNASPath == null) {
				broadcastCurrTask(stateCurrTaskDescription = "SMB is not configured yet.");
				Log.d(LOG_TAG, stateCurrTaskDescription);
				return;
			}
		}
		stateCopyPaused=settings.getBoolean("statePicSyncCopyPaused", true);
		if (stateCopyPaused) {
			makeToast("Syncing is paused");
			return;
		}

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
				setupAlarm();
			}
			broadcastConnectionStatus();
			if (stateNASConnected) {
				cancelAlarm();
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

/*
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
*/

	private void doNotify(){
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

	private enum ePicSyncState {PIC_SYNC_STATE_STOPPED, PIC_SYNC_STATE_SYNCING, PIC_SYNC_STATE_SCANNING, PIC_SYNC_STATE_NO_ACCESS}

	/*
		************************************************************************************************
	*/

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
			WifiManager wifiManager = (WifiManager) MyContext.getApplicationContext().getSystemService(WIFI_SERVICE);
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
				domainsFile = new SmbFile(smbshareurl, auth);
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
