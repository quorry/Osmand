package net.osmand.plus.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.osmand.plus.OsmandSettings;
import net.osmand.plus.ProgressDialogImplementation;
import net.osmand.plus.R;
import net.osmand.plus.ResourceManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ContributionVersionActivity extends ListActivity {

	private static ContributionVersionActivityThread thread = new ContributionVersionActivityThread();
	private static final int DOWNLOAD_BUILDS_LIST = 1;
	private static final int INSTALL_BUILD = 2;
	private static final int ACTIVITY_TO_INSTALL = 23;
	
	private static final String URL_TO_RETRIEVE_BUILDS = "http://download.osmand.net/builds.php";
	private static final String URL_GET_BUILD = "http://download.osmand.net/";
	private static final String CONTRIBUTION_INSTALL_APP_DATE = "CONTRIBUTION_INSTALL_APP_DATE";
	
	private ProgressDialog progressDlg;
	private Date currentInstalledDate;
	
	private List<OsmAndBuild> downloadedBuilds = new ArrayList<OsmAndBuild>();
	private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
	private File pathToDownload = new File(Environment.getExternalStorageDirectory(), ResourceManager.APP_DIR + "osmandToInstall.apk");
	private OsmAndBuild currentSelectedBuild = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.download_builds);
		
		String installDate = OsmandSettings.getPrefs(this).getString(CONTRIBUTION_INSTALL_APP_DATE, null);
		if(installDate != null){
			try {
				currentInstalledDate = dateFormat.parse(installDate);
			} catch (ParseException e) {
			}
		}
		
		downloadedBuilds.clear();
		startThreadOperation(DOWNLOAD_BUILDS_LIST, getString(R.string.loading_builds), -1);
	}

	private void startThreadOperation(int operationId, String message, int total) {
		
		progressDlg = new ProgressDialog(this);
		progressDlg.setTitle(getString(R.string.loading));
		progressDlg.setMessage(message);
		if(total != -1){
			progressDlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDlg.setMax(total);
			progressDlg.setProgress(0);
		} else {
			progressDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		}
		progressDlg.show();
//		progressFileDlg.setCancelable(false);
		if(thread.getState() == Thread.State.TERMINATED || thread.getOperationId() != operationId){
			thread = new ContributionVersionActivityThread();
			thread.setOperationId(operationId);
		}
		thread.setActivity(this);
		if(thread.getState() == Thread.State.NEW){
			thread.start();
		}
	}
	
	protected void endThreadOperation(int operationId, Exception e){
		if(progressDlg != null){
			progressDlg.dismiss();
			progressDlg = null;
		}
		if(operationId == DOWNLOAD_BUILDS_LIST){
			if(e != null){
				Toast.makeText(this, getString(R.string.loading_builds_failed) + " : " + e.getMessage(), Toast.LENGTH_LONG).show();
				finish();
			} else {
				setListAdapter(new OsmandBuildsAdapter(downloadedBuilds));
			}
		} else if(operationId == INSTALL_BUILD){
			if(currentSelectedBuild != null){
				Intent intent = new Intent(Intent.ACTION_VIEW);
	            intent.setDataAndType(Uri.fromFile(pathToDownload), "application/vnd.android.package-archive");
	            startActivityForResult(intent, ACTIVITY_TO_INSTALL);
	            //startActivity(intent);
	            //updateLastInstalledBuild(true);
			}
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(ACTIVITY_TO_INSTALL == requestCode && resultCode == RESULT_OK){
			updateLastInstalledBuild(true);
		}
	}

	private void updateLastInstalledBuild(boolean showMessage) {
		if (currentSelectedBuild != null) {
			if (showMessage) {
				Toast.makeText(
						this,
						MessageFormat.format(getString(R.string.build_installed), currentSelectedBuild.tag, dateFormat
								.format(currentSelectedBuild.date)), Toast.LENGTH_LONG).show();
			}
			OsmandSettings.getPrefs(this).edit().putString(CONTRIBUTION_INSTALL_APP_DATE, dateFormat.format(currentSelectedBuild.date))
					.commit();
			currentInstalledDate = currentSelectedBuild.date;
			getListAdapter().notifyDataSetInvalidated();
		}
	}
	
	protected void executeThreadOperation(int operationId) throws Exception {
		if(operationId == DOWNLOAD_BUILDS_LIST){
			URLConnection connection = new URL(URL_TO_RETRIEVE_BUILDS).openConnection();
			XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
			parser.setInput(connection.getInputStream(), "UTF-8");
			int next;
			while((next = parser.next()) != XmlPullParser.END_DOCUMENT) {
				if(next == XmlPullParser.START_TAG && parser.getName().equals("build")) { //$NON-NLS-1$
					if ("osmand".equalsIgnoreCase(parser.getAttributeValue(null, "type"))) {

						String path = parser.getAttributeValue(null, "path"); //$NON-NLS-1$
						String size = parser.getAttributeValue(null, "size"); //$NON-NLS-1$
						String date = parser.getAttributeValue(null, "date"); //$NON-NLS-1$
						String tag = parser.getAttributeValue(null, "tag"); //$NON-NLS-1$
						Date d = null;
						if (date != null) {
							try {
								d = dateFormat.parse(date);
							} catch (ParseException e) {
							}
						}
						OsmAndBuild build = new OsmAndBuild(path, size, d, tag);
						downloadedBuilds.add(build);
					}
				}
			}
		} else if(operationId == INSTALL_BUILD){
			URLConnection connection = new URL(URL_GET_BUILD + currentSelectedBuild.path).openConnection();
			if(pathToDownload.exists()){
				pathToDownload.delete();
			}
			byte[] buffer = new byte[1024];
			InputStream is = connection.getInputStream();
			FileOutputStream fout = new FileOutputStream(pathToDownload);
			try {
				int totalRead = 0;
				int read;
				while((read = is.read(buffer, 0, 1024)) != -1){
					fout.write(buffer, 0, read);
					totalRead += read;
					progressDlg.setProgress(totalRead / 1024);
				}
			} finally {
				fout.close();
				is.close();
			}
		}
		
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		final OsmAndBuild item = (OsmAndBuild) getListAdapter().getItem(position);
		Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(MessageFormat.format(getString(R.string.install_selected_build), item.tag, dateFormat.format(item.date), item.size));
		builder.setPositiveButton(R.string.default_buttons_yes, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				currentSelectedBuild = item;
				int kb = (int) (Double.parseDouble(item.size) * 1024);
				startThreadOperation(INSTALL_BUILD, getString(R.string.downloading_build), kb);
			}
		});
		
		builder.setNegativeButton(R.string.default_buttons_no, null);
		builder.show();
		
	}
	
	@Override
	public OsmandBuildsAdapter getListAdapter() {
		return (OsmandBuildsAdapter) super.getListAdapter();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		thread.setActivity(null);
		if(progressDlg != null){
			progressDlg.dismiss();
			progressDlg = null;
		}
	}
	
	
	protected class OsmandBuildsAdapter extends ArrayAdapter<OsmAndBuild> implements Filterable {
		

		public OsmandBuildsAdapter(List<OsmAndBuild> builds) {
			super(ContributionVersionActivity.this, R.layout.download_build_list_item, builds);
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater inflater = getLayoutInflater();
				v = inflater.inflate(net.osmand.plus.R.layout.download_build_list_item, parent, false);
			}
			final View row = v;
			OsmAndBuild build = getItem(position);
			TextView tagView = (TextView) row.findViewById(R.id.download_tag);
			tagView.setText(build.tag);
			
			TextView description = (TextView) row.findViewById(R.id.download_descr);
			StringBuilder format = new StringBuilder();
			format.append(dateFormat.format(build.date))/*.append(" : ").append(build.size).append(" MB")*/;
			description.setText(format.toString());

			if(currentInstalledDate != null){
				if(currentInstalledDate.after(build.date)){
					tagView.setTextColor(Color.BLUE);
				} else {
					tagView.setTextColor(Color.GREEN);
				}
			} else {
				tagView.setTextColor(Color.WHITE);
			}
			return row;
		}
		
	}
	
	
	private static class ContributionVersionActivityThread extends Thread {
		private ContributionVersionActivity activity;
		private int operationId;
		
		public void setActivity(ContributionVersionActivity activity) {
			this.activity = activity;
		}
		
		public int getOperationId() {
			return operationId;
		}
		
		public void setOperationId(int operationId) {
			this.operationId = operationId;
		}

		@Override
		public void run() {
			Exception ex= null;
			try {
				if(this.activity != null){
					this.activity.executeThreadOperation(operationId);
				}
			} catch (Exception e) {
				ex = e;
			}
			final Exception e = ex;
			if(this.activity != null){
				this.activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						activity.endThreadOperation(operationId, e);
					}
				});
			}
		}
	}
	
	private static class OsmAndBuild {
		public String path;
		public String size;
		public Date date;
		public String tag;
		
		public OsmAndBuild(String path, String size, Date date, String tag) {
			super();
			this.path = path;
			this.size = size;
			this.date = date;
			this.tag = tag;
		}
		
		
	}

}