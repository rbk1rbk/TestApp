package com.rbk.testapp;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

/**
 * Created by biel on 9.2.2017.
 */

public class AnyFileInputStream {
	private static String fileType;
	private static SmbFileInputStream sfis;
	private static FileInputStream fis;

	AnyFileInputStream(String filePath, NtlmPasswordAuthentication auth) {
		fileType = "SMB";
		try {
			sfis = new SmbFileInputStream(new SmbFile(filePath, auth));
		} catch (SmbException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	AnyFileInputStream(String filePath) {
		fileType = "local";
		try {
			fis = new FileInputStream(filePath);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public int read(byte[] b, int off, int len) throws IOException {
		if (fileType.equals("SMB")) {
			return sfis.read(b, off, len);
		} else
			return fis.read(b, off, len);
	}
	FileDescriptor getFD() throws IOException {
		return fis.getFD();
	}
}
