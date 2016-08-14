package com.rbk.testapp;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class CIFSbrowser extends ListActivity {

    private String currDir;
    private String smbuser, smbpasswd, smbservername, smbshare;
    NtlmPasswordAuthentication auth = null;

    private boolean isRealDirectory(String file) {
        boolean returnvalue = true;
        try {
            if (!file.endsWith(File.separator))
                file = file + File.separator;
            new SmbFile(file, auth).list();
        } catch (SmbException | MalformedURLException e) {
            returnvalue = false;
        }
        return returnvalue;
    }

    private void dirListToCIFSListView() {
        List values = new ArrayList();
        SmbFile dir;
        String[] list;
        boolean canReadDir = true;
        try {
            currDir = new SmbFile(currDir).getCanonicalPath();
            dir = new SmbFile(currDir, auth);
            canReadDir = dir.canRead();
            if (!canReadDir) {
                setTitle(getTitle() + " (inaccessible)");
                return;
            }
            list = dir.list();
            if (list != null) {
                for (String file : list) {
                    if (!currDir.endsWith(File.separator))
                        currDir = currDir + File.separator;
                    SmbFile file2verify = new SmbFile(currDir + file, auth);
                    boolean canRead = file2verify.canRead();
                    boolean isDirectory = file2verify.isDirectory();
                    boolean isHidden = file2verify.isHidden();
                    boolean isItRealDirectory = false;
                    if (isDirectory)
                        isItRealDirectory = isRealDirectory(file2verify.getCanonicalPath());
                    if ((!file.startsWith(".")) && (isItRealDirectory) && (!file.startsWith("$")) && (!file.endsWith("$")) && (canRead) && (isDirectory) && (!isHidden)) {
                        values.add(file);
                    }
                }
                String currCanonicalPath = new SmbFile(currDir, "./").getCanonicalPath();
                String upCanonicalPath = new SmbFile(currDir, "../").getCanonicalPath() + File.separator;
                if (!TextUtils.equals(currCanonicalPath, upCanonicalPath))
                    values.add("..");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            finish();
        } catch (SmbException e) {
            e.printStackTrace();
            values.add("..");
        }
        Collections.sort(values);

        // Put the data into the list
        ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, values);
        setListAdapter(adapter);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cifsbrowser);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        smbuser = settings.getString("prefsSMBUSER", "");
        smbpasswd = settings.getString("prefsSMBPWD", "");
        smbservername = settings.getString("prefsSMBSRV", "");

        jcifs.Config.registerSmbURLHandler();
        jcifs.Config.setProperty("jcifs.netbios.hostname", "Android");
        jcifs.Config.setProperty("jcifs.netbios.wins", "Android");
        auth = new NtlmPasswordAuthentication(null, smbuser, smbpasswd);

        smbshare = settings.getString("prefsSMBSHARE", "");
        if (smbshare.startsWith("smb://"))
            currDir = smbshare;
        else
            currDir = "smb://" + smbservername + "/";

/*
        if (getIntent().hasExtra("currDir")) {
            currDir = getIntent().getStringExtra("currDir");
        }
*/
        setTitle(currDir);

        dirListToCIFSListView();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String filename = (String) getListAdapter().getItem(position);
        if (currDir.endsWith(File.separator)) {
            filename = currDir + filename;
        } else {
            filename = currDir + File.separator + filename;
        }
        boolean isItDirectory = false;
        try {
            SmbFile sFile = new SmbFile(filename, auth);
            isItDirectory = sFile.isDirectory();
        } catch (SmbException | MalformedURLException e) {
            e.printStackTrace();
        }
        if (isItDirectory) {
            if (!filename.endsWith(File.separator))
                currDir = filename + File.separator;
            else
                currDir = filename;
            dirListToCIFSListView();
/*
        } else {
            Toast.makeText(this, filename + " is not a directory", Toast.LENGTH_LONG).show();
*/
        }
    }

    public void onBtnSelectClick(View v) {
        Intent returnIntent = new Intent();
        try {
            returnIntent.putExtra("path", new SmbFile(currDir).getCanonicalPath());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        setResult(0, returnIntent);
        finish();
    }
}
