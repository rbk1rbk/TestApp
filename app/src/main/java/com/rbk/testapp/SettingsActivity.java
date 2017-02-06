package com.rbk.testapp;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import java.util.Date;
import java.util.List;

public class SettingsActivity extends AppCompatPreferenceActivity {
	@Override
	public void startActivity(Intent intent) {
		String classShortName = intent.getComponent().getShortClassName();
		if (classShortName.equals(".CIFSbrowser")) {
			intent.putExtra("path", "smb://");
			super.startActivityForResult(intent, 1000);
		} else if (classShortName.equals(".WiFiPicker")) {
			super.startActivityForResult(intent, 1001);
		} else {
			super.startActivity(intent);
		}
	}

	@Override
	protected void onActivityResult(int reqCode, int resCode, Intent data) {
		if ((reqCode == 1000) & (resCode == 0) & (data != null)) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			String oldservername=prefs.getString("prefsSMBSRV","");
			String newservername = data.getStringExtra("servername");
			SharedPreferences.Editor editor = prefs.edit();
			if (!TextUtils.equals(oldservername,newservername)) {
				editor.putBoolean("prefsMACverified", false);
				editor.putString("prefsSMBSRV", newservername);
			}
			editor.putString("prefsSMBURI", data.getStringExtra("path"));
			editor.putString("prefsSMBSHARE", data.getStringExtra("sharename"));
			editor.commit();
			finish();
			startActivity(getIntent());
		} else if ((reqCode == 1001) & (resCode == 0) & (data != null)) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("pref_homewifissid", data.getStringExtra("wifiName"));
			editor.commit();
			finish();
			startActivity(getIntent());
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
	}

	private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();

			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);

				// Set the summary to reflect the new value.
				preference.setSummary(
						index >= 0
								? listPreference.getEntries()[index]
								: null);

/*
			} else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

*/
			} else if (preference instanceof EditTextPreference) {
				if (TextUtils.equals(preference.getKey(), "prefsSMBPWD"))
					preference.setSummary("********");
				else
					preference.setSummary(stringValue);
			} else {
				// For all other preferences, set the summary to the value's
				// simple string representation.
				preference.setSummary(stringValue);
			}
			return true;
		}
	};


	/**
	 * Helper method to determine if the device has an extra-large screen. For
	 * example, 10" tablets are extra-large.
	 */
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout
				& Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 *
	 * @see #sBindPreferenceSummaryToValueListener
	 */
	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

		// Trigger the listener immediately with the preference's
		// current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
				PreferenceManager
						.getDefaultSharedPreferences(preference.getContext())
						.getString(preference.getKey(), ""));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setupActionBar();
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	private void setupActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			// Show the Up button in the action bar.
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		int id = item.getItemId();
		if (id == android.R.id.home) {
			if (!super.onMenuItemSelected(featureId, item)) {
				NavUtils.navigateUpFromSameTask(this);
			}
			return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuItem) {
		ContextMenu.ContextMenuInfo cmi = menuItem.getMenuInfo();
		cmi = menuItem.getMenuInfo();
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onIsMultiPane() {
		return isXLargeTablet(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.pref_headers, target);
	}

	/**
	 * This method stops fragment injection in malicious applications.
	 * Make sure to deny any unknown fragments here.
	 */
	protected boolean isValidFragment(String fragmentName) {
		return PreferenceFragment.class.getName().equals(fragmentName)
				|| PreferenceFragment_Sync.class.getName().equals(fragmentName)
				|| PreferenceFragment_Server.class.getName().equals(fragmentName)
				|| PreferenceFragment_Upload.class.getName().equals(fragmentName)
				|| PreferenceFragment_Notification.class.getName().equals(fragmentName);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class PreferenceFragment_Sync extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_sync);
			setHasOptionsMenu(true);

			bindPreferenceSummaryToValue(findPreference("pref_when2sync"));
			bindPreferenceSummaryToValue(findPreference("pref_homewifissid"));
			bindPreferenceSummaryToValue(findPreference("prefsMAC"));
/*
			CheckBoxPreference checkBoxPreference = new CheckBoxPreference(getActivity());
			checkBoxPreference.setKey("pref_directory_3");
			checkBoxPreference.setTitle("/mnt/sdcard0/DCIM");
*/
/*
            checkBoxPreference.setChecked(false);
*/
/*
            PreferenceScreen targetScreen = (PreferenceScreen) findPreference("pref_local_dirs_screen");
            targetScreen.addPreference(checkBoxPreference);
*/
/*
            bindPreferenceSummaryToValue(findPreference("pref_directory_1"));
            bindPreferenceSummaryToValue(findPreference("pref_directory_2"));
*/
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			int id = item.getItemId();
			if (id == android.R.id.home) {
				startActivity(new Intent(getActivity(), SettingsActivity.class));
				return true;
			}
			return super.onOptionsItemSelected(item);
		}
	}

	public static class PreferenceFragment_Server extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_server);
			setHasOptionsMenu(true);

			bindPreferenceSummaryToValue(findPreference("prefsSMBUSER"));
			bindPreferenceSummaryToValue(findPreference("prefsSMBPWD"));
			bindPreferenceSummaryToValue(findPreference("prefsSMBSRV"));
			bindPreferenceSummaryToValue(findPreference("prefsSMBSHARE"));
			bindPreferenceSummaryToValue(findPreference("prefsSMBURI"));

			final Preference CIFSBrowserPreference = (Preference) findPreference("CIFSBrowserPreference");
/*
			CIFSBrowserPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Context ctx = CIFSBrowserPreference.getContext();
					AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
*/
/*
					final LayoutInflater inflater = ctx.getLayoutInflater();
*//*

					final View dialogView1 = View.inflate(ctx, R.xml.pref_server, null);
					builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}

						;
					});
					builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}
					});
					return true;
				}

				;
			});
*/

		};

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			int id = item.getItemId();
			if (id == android.R.id.home) {
				startActivity(new Intent(getActivity(), SettingsActivity.class));
				return true;
			}
			return super.onOptionsItemSelected(item);
		}

/*		private void showDialogSetLastRun() {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			final LayoutInflater inflater = this.getLayoutInflater();
			final View dialogView1 = View.inflate(MyContext, R.layout.dialog_date_time_picker, null);
			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface _dialog, int id) {
					DatePicker datePicker = (DatePicker) dialogView1.findViewById(R.id.datePicker);
					TimePicker timePicker = (TimePicker) dialogView1.findViewById(R.id.timePicker);

					int y = datePicker.getYear();
					int m = datePicker.getMonth();
					int d = datePicker.getDayOfMonth();
					Calendar calendar = new GregorianCalendar(
							datePicker.getYear(),
							datePicker.getMonth(),
							datePicker.getDayOfMonth(),
							timePicker.getCurrentHour(),
							timePicker.getCurrentMinute());

					Long resultTime;
					resultTime = calendar.getTimeInMillis();
					Intent PicSyncIntent = new Intent(MainScreen.this, PicSync.class);
					PicSyncIntent.setAction(PicSync.ACTION_SET_LAST_IMAGE_TIMESTAMP);
					PicSyncIntent.putExtra("lastCopiedImageDate", resultTime);
					MyContext.startService(PicSyncIntent);

					_dialog.dismiss();
				}
			});

			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
				}
			});
			builder.setView(dialogView1);
			AlertDialog dialog = builder.create();
			DatePicker datePicker = (DatePicker) dialogView1.findViewById(R.id.datePicker);
			TimePicker timePicker = (TimePicker) dialogView1.findViewById(R.id.timePicker);
			Date _date = new Date(localLastCopiedImageTimestamp);
			int y = _date.getYear() + 1900;
			int m = _date.getMonth();
			int d = _date.getDate();
			datePicker.updateDate(y, m, d);
			timePicker.setIs24HourView(true);
			timePicker.setCurrentHour(_date.getHours());
			timePicker.setCurrentMinute(_date.getMinutes());
			dialog.setTitle("Date & Time");
			dialog.show();
		}*/

	}

	/**
	 * This fragment shows notification preferences only. It is used when the
	 * activity is showing a two-pane settings UI.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class PreferenceFragment_Notification extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_notification);
			setHasOptionsMenu(true);
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			int id = item.getItemId();
			if (id == android.R.id.home) {
				startActivity(new Intent(getActivity(), SettingsActivity.class));
				return true;
			}
			return super.onOptionsItemSelected(item);
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class PreferenceFragment_Upload extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_upload);
			setHasOptionsMenu(true);
			ListPreference prefTGTFolderStructure = (ListPreference) findPreference("prefTGTFolderStructure");
			if (prefTGTFolderStructure.getValue().equals("prefTGTFolderStructDate")) {
				findPreference("prefsSubfolderNameFormat").setEnabled(true);
			} else
				findPreference("prefsSubfolderNameFormat").setEnabled(false);

			bindPreferenceSummaryToValue(prefTGTFolderStructure);
			bindPreferenceSummaryToValue(findPreference("prefsSubfolderNameFormat"));
			bindPreferenceSummaryToValue(findPreference("prefTGTRenameOption"));
			bindPreferenceSummaryToValue(findPreference("prefTGTAlreadyExistsTest"));
			bindPreferenceSummaryToValue(findPreference("prefTGTAlreadyExistsRename"));
			bindPreferenceSummaryToValue(findPreference("prefSRCShrinkAfter"));
			bindPreferenceSummaryToValue(findPreference("prefSRCDeleteAfter"));

			prefTGTFolderStructure.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					final String val = newValue.toString();
					if (val.equals("prefTGTFolderStructDate")) {
						findPreference("prefsSubfolderNameFormat").setEnabled(true);
					} else
						findPreference("prefsSubfolderNameFormat").setEnabled(false);
					return true;
				}
			});
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			int id = item.getItemId();
			if (id == android.R.id.home) {
				startActivity(new Intent(getActivity(), SettingsActivity.class));
				return true;
			}
			return super.onOptionsItemSelected(item);
		}

		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			if (key.equals("prefTGTFolderStructure")) {
				if (sharedPreferences.getString("prefTGTFolderStructure", "").equals("prefTGTFolderStructDate")) {
					findPreference("prefsSubfolderNameFormat").setEnabled(true);
				} else
					findPreference("prefsSubfolderNameFormat").setEnabled(false);
			}
		}

	}

	@Override
	protected void onDestroy() {
		// TODO: Toto je blbost, potrebu rescanu sa musi riesit na zaklade zmenenych parametrov
		startService(new Intent(this, PicSync.class)
				.setAction(PicSync.ACTION_SUGGEST_MEDIA_SCAN)
				.putExtra("cmdTimestamp", new Date().getTime())
		);
		super.onDestroy();
	}
}
