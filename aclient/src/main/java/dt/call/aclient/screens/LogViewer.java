package dt.call.aclient.screens;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Vars;
import dt.call.aclient.sqlite.SQLiteDb;
import dt.call.aclient.sqlite.DBLog;

public class LogViewer extends AppCompatActivity implements View.OnClickListener
{
	private Button clear;
	private CheckBox enable;
	private LinearLayout logTable;
	private SQLiteDb sqliteDb;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log_viewer);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		clear = (Button)findViewById(R.id.log_viewer_clear);
		clear.setOnClickListener(this);
		clear.setTag(null);
		enable = (CheckBox)findViewById(R.id.log_viewer_enable);
		enable.setOnClickListener(this);
		logTable = (LinearLayout)findViewById(R.id.log_viewer_scroller_table);
		sqliteDb = sqliteDb.getInstance(this);

		if(Vars.SHOUDLOG)
		{
			enable.setChecked(true);
		}

		ArrayList<DBLog> logs;
		logs = sqliteDb.getLogs();

		//put all logs into the table
		for(DBLog log : logs)
		{
			//create a new table row for each log
			LinearLayout logRow = (LinearLayout) View.inflate(this, R.layout.row_log_viewer, null);
			TextView ts = (TextView)logRow.findViewById(R.id.row_log_viewer_timestamp);
			ts.setText(log.getHumanReadableTimestampShort());
			Button tag = (Button)logRow.findViewById(R.id.row_log_viewer_tag);
			tag.setText(log.getTag());
			tag.setOnClickListener(this);
			tag.setTag(log);

			logTable.addView(logRow);
		}
	}

	@Override
	public void onClick(View v)
	{
		if(v == enable)
		{
			if(enable.isChecked())
			{
				Vars.SHOUDLOG = true;
			}
			else
			{
				Vars.SHOUDLOG = false;
			}
			SharedPreferences prefs = getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
			SharedPreferences.Editor ed = prefs.edit();
			ed.putBoolean(Const.PREF_LOG, Vars.SHOUDLOG);
			ed.apply();
		}

		if(v == clear)
		{
			sqliteDb.clearLogs();
			logTable.removeAllViews();
		}

		//each log entry's message is clickable
		if(v.getTag() != null)
		{
			Intent seeDetails = new Intent(LogViewer.this, LogDetails.class);
			seeDetails.putExtra(Const.EXTRA_LOG, (DBLog)v.getTag());
			startActivity(seeDetails);
		}
	}
}
