package com.rbk.testapp;

import android.media.ExifInterface;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

import static android.content.ContentValues.TAG;
import static android.media.ExifInterface.TAG_DATETIME;
import static android.media.ExifInterface.TAG_EXPOSURE_TIME;
import static android.media.ExifInterface.TAG_FOCAL_LENGTH;
import static android.media.ExifInterface.TAG_IMAGE_LENGTH;
import static android.media.ExifInterface.TAG_IMAGE_WIDTH;
import static android.media.ExifInterface.TAG_ISO;
import static android.media.ExifInterface.TAG_ISO_SPEED_RATINGS;
import static android.media.ExifInterface.TAG_MODEL;
import static com.rbk.testapp.Constants.cksumMaxBytes;

/**
 * Created by biel on 7.2.2017.
 */

public class Utils {
	static MessageDigest digestMD5;
	private static void initializeMD5(){
		if (digestMD5!=null) {
			digestMD5.reset();
			return;
		}
		try {
			digestMD5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "Exception while getting digest", e);
			digestMD5=null;
		}
	}
	public static String makeMP4HashSmall(String srcMediaFileNameFull){
		final String LOG_TAG="mp4MetaHash";
/*
		MediaMetadataRetriever metaRetreiver;
		metaRetreiver = new MediaMetadataRetriever();
		metaRetreiver.setDataSource(srcMediaFileNameFull);
		metaRetreiver.setDataSource(srcMediaFileNameFull);
		String metadataKeyDate = metaRetreiver.extractMetadata(METADATA_KEY_DATE);
		String metadataDuration = metaRetreiver.extractMetadata(METADATA_KEY_DURATION);
		return metadataKeyDate+";"+metadataDuration;
*/
		FileInputStream srcFileStream = null;
		try {
			srcFileStream = new FileInputStream(srcMediaFileNameFull);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return  null;
		}

		int  read_curr = 0;
		long read_total = 0;
		final byte[] buffer = new byte[cksumMaxBytes];
		byte[] md5sumBytes;
		String md5sum = new String();
		initializeMD5();
		if (digestMD5 == null)
			return null;
		try {
			while (((read_curr = srcFileStream.read(buffer, 0, buffer.length)) > 0) && (read_total<cksumMaxBytes)) {
				read_total += read_curr;
				digestMD5.update(buffer, 0, read_curr);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		md5sumBytes = digestMD5.digest();
		for (int i = 0; i < md5sumBytes.length; i++)
			md5sum += Integer.toString((md5sumBytes[i] & 0xff) + 0x100, 16).substring(1);
		return md5sum;
	}
	public static String makeFileFingerprint(String srcFile, NtlmPasswordAuthentication auth){
		AnyFileInputStream anyFIS= new AnyFileInputStream(srcFile,auth);
		return makeFileFingerprint(anyFIS);
	}
	public static String makeFileFingerprint(String srcFile){
		AnyFileInputStream anyFIS= new AnyFileInputStream(srcFile);
		return makeFileFingerprint(anyFIS);
	}
	public static String makeFileFingerprint(AnyFileInputStream anyFIS){
		int  read_curr = 0;
		long read_total = 0;
		final byte[] buffer = new byte[cksumMaxBytes];
		byte[] md5sumBytes;
		String md5sum = new String();
		initializeMD5();
		if (digestMD5 == null)
			return null;
		try {
			while (((read_curr = anyFIS.read(buffer, 0, buffer.length)) > 0) && (read_total<cksumMaxBytes)) {
				read_total += read_curr;
				digestMD5.update(buffer, 0, read_curr);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		md5sumBytes = digestMD5.digest();
		for (int i = 0; i < md5sumBytes.length; i++)
			md5sum += Integer.toString((md5sumBytes[i] & 0xff) + 0x100, 16).substring(1);
		return md5sum;

	}
	public static String makeMP4HashSmall(String srcFile, NtlmPasswordAuthentication auth){
		SmbFileInputStream srcFileStream;
		try {
			srcFileStream = new SmbFileInputStream(new SmbFile(srcFile, auth));
		} catch (UnknownHostException | SmbException | MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
		int  read_curr = 0;
		long read_total = 0;
		final byte[] buffer = new byte[cksumMaxBytes];
		byte[] md5sumBytes;
		String md5sum = new String();
		initializeMD5();
		if (digestMD5 == null)
			return null;
		try {
			while (((read_curr = srcFileStream.read(buffer, 0, buffer.length)) > 0) && (read_total<cksumMaxBytes)) {
				read_total += read_curr;
				digestMD5.update(buffer, 0, read_curr);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		md5sumBytes = digestMD5.digest();
		for (int i = 0; i < md5sumBytes.length; i++)
			md5sum += Integer.toString((md5sumBytes[i] & 0xff) + 0x100, 16).substring(1);
		return md5sum;
	}
	static String makeEXIFHash(String srcMediaFileNameFull){
		ExifInterface exifInterface;
		String exifDateTimeTaken;
		String exifModel;
		Integer exifISO,exifImageLength,exifImageWidth;
		boolean fileIsRemote=false;
		boolean err=false;
		String exifHash=null;
		FileInputStream fi=null;

		Log.d("makeExif","file is "+srcMediaFileNameFull);
		try {
			exifInterface = new ExifInterface(srcMediaFileNameFull);
			exifDateTimeTaken=exifInterface.getAttribute(TAG_DATETIME);
			if (Build.VERSION.SDK_INT < 24)
				exifISO=exifInterface.getAttributeInt(TAG_ISO,0);
			else
				exifISO=exifInterface.getAttributeInt(TAG_ISO_SPEED_RATINGS,0);
			exifImageLength=exifInterface.getAttributeInt(TAG_IMAGE_LENGTH,0);
			exifImageWidth=exifInterface.getAttributeInt(TAG_IMAGE_WIDTH,0);
			exifModel=exifInterface.getAttribute(TAG_MODEL);
			Double exifFocal=exifInterface.getAttributeDouble(TAG_FOCAL_LENGTH,0);
			Double exifExposureTime=exifInterface.getAttributeDouble(TAG_EXPOSURE_TIME,0);
			exifHash =  exifModel+";"+exifDateTimeTaken+";"+exifISO.toString()+";"+exifImageLength.toString()+";"+exifImageWidth.toString()+";"+exifExposureTime.toString();
/*
				MessageDigest digestMD5=null;
				digestMD5 = MessageDigest.getInstance("MD5");
				digestMD5.update(exifHash.getBytes(),0,exifHash.length());
				byte [] md5sumBytes;
				String md5sum = new String();
				md5sumBytes = digestMD5.digest();
				for (int i=0; i < md5sumBytes.length; i++)
					md5sum += Integer.toString( ( md5sumBytes[i] & 0xff ) + 0x100, 16).substring( 1 );
				Log.d(TAG,"md5sum of exifHash" + md5sum);
				return md5sum;
*/
		} catch (IOException e) {
			e.printStackTrace();
			Log.d("makeExif","file is "+srcMediaFileNameFull);
		} catch (StackOverflowError e) {
			System.err.println("ExifInterface crashed!");
		}
		return exifHash;
	}

	public static String getFileType(String fileNameFull) {
		String suffix = fileNameFull.substring(fileNameFull.lastIndexOf(".")).toLowerCase();
		if (suffix.endsWith("jpg")
					|| suffix.endsWith("jpeg")
/*					|| suffix.endsWith("png")
					|| suffix.endsWith("raw")*/
				)
			return Constants.FILE_TYPE_PICTURE;
		if (suffix.endsWith("mpg")
					|| suffix.endsWith("mp4")
					|| suffix.endsWith("avi")
				)
			return Constants.FILE_TYPE_VIDEO;
		else return null;
	}
	public static void cpFile(String src, String tgt) throws IOException {
		final byte[] buffer = new byte[8 * 1024];
		FileInputStream fIn = null;
		File exportFile = null;
		FileOutputStream fOut = null;
		boolean exceptionThrown = false;
		IOException x = null;
		try {
			fIn = new FileInputStream(src);
			exportFile = new File(tgt);
			fOut = new FileOutputStream(exportFile);
			exportFile.createNewFile();
			int read = 0;
			while ((read = fIn.read(buffer, 0, buffer.length)) > 0) {
				fOut.write(buffer, 0, read);
			}
		} catch (IOException e) {
			e.printStackTrace();
			x=e;
			exceptionThrown = true;
		} finally {
			if (fIn != null)
				try {
					fIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

			if (fOut != null)
				try {
					fOut.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		if (x != null){
			throw x;
		}
	}
}
