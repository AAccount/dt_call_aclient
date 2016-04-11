package dt.call.aclient.screens;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.sqlite.DB;
import dt.call.aclient.sqlite.DBLog;

public class LogViewer extends AppCompatActivity implements View.OnClickListener
{
	private Button clear;
	private LinearLayout logTable;
	private DB db;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log_viewer);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		clear = (Button)findViewById(R.id.log_viewer_clear);
		clear.setOnClickListener(this);
		clear.setTag(null);
		logTable = (LinearLayout)findViewById(R.id.log_viewer_scroller_table);
		db = new DB(this);

		ArrayList<DBLog> logs;
		logs = db.getLogs();

		//put all logs into the table
		for(DBLog log : logs)
		{
			//create a new table row for each log
			LinearLayout logRow = (LinearLayout) View.inflate(this, R.layout.row_log_viewer, null);
			TextView ts = (TextView)logRow.findViewById(R.id.row_log_viewer_timestamp);
			ts.setText(log.getHumanReadableTimestampShort());
			TextView tag = (TextView)logRow.findViewById(R.id.row_log_viewer_tag);
			tag.setText(log.getTag());
			Button message = (Button)logRow.findViewById(R.id.row_log_viewer_message);
			message.setOnClickListener(this);
			message.setText(log.getMessage());
			message.setTag(log);

			logTable.addView(logRow);
		}
	}

	@Override
	public void onClick(View v)
	{
		if(v == clear)
		{
			db.clearLogs();
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
