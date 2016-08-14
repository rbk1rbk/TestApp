package com.rbk.testapp;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
public class FolderPicker extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folderPicker);
    }

}
*/

public class FolderPicker extends ListActivity {

    private String path;

    private void dirListToFOLDERListView() {
        List values = new ArrayList();
        File dir = new File(path);
        if (!dir.canRead()) {
            setTitle(getTitle() + " (inaccessible)");
        }
        String[] list = dir.list();
        if (list != null) {
            for (String file : list) {
                File file2verify = new File(path + "/" + file);
                boolean canRead = file2verify.canExecute();
                boolean isDirectory = file2verify.isDirectory();
                boolean isFile = file2verify.isFile();
                if ((!file.startsWith(".")) && (canRead) && (isDirectory)) {
                    values.add(file);
                }
            }
        }

        try {
            if (!TextUtils.equals(new File(path).getCanonicalPath(), new File(path + "/..").getCanonicalPath()))
                values.add("..");
        } catch (IOException e) {
            e.printStackTrace();
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
        setContentView(R.layout.activity_folder_picker);

        // Use the current directory as title
        path = "/";
        if (getIntent().hasExtra("path")) {
            path = getIntent().getStringExtra("path");
        }
        setTitle(path);

        dirListToFOLDERListView();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String filename = (String) getListAdapter().getItem(position);
        if (path.endsWith(File.separator)) {
            filename = path + filename;
        } else {
            filename = path + File.separator + filename;
        }
        if (new File(filename).isDirectory()) {
/*
            Intent intent = new Intent(this, FolderPicker.class);
            intent.putExtra("path", filename);
            startActivity(intent);
*/
            path = filename;
            dirListToFOLDERListView();
        } else {
            Toast.makeText(this, filename + " is not a directory", Toast.LENGTH_LONG).show();
        }
    }

    public void onBtnSelectClick(View v) {
        Intent returnIntent = new Intent();
        returnIntent.putExtra("path", path);
        setResult(0, returnIntent);
        finish();
    }
}
