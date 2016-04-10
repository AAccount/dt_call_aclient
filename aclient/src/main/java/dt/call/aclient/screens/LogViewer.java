package dt.call.aclient.screens;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;

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
		logTable = (LinearLayout)findViewById(R.id.log_viewer_scroller_table);
		db = new DB(this);

		ArrayList<DBLog> logs;
		logs = db.getLogs();

		//put all logs into the table
		for(DBLog log : logs)
		{
			//create a new table row for each log
			LinearLayout logRow = (LinearLayout) View.inflate(this, R.layout.row_log_viewer, null);
			logRow.setTag(log);
			TextView ts = (TextView)logRow.findViewById(R.id.row_log_viewer_timestamp);
			ts.setText(log.getHumanReadableTimestampShort());
			TextView tag = (TextView)logRow.findViewById(R.id.row_log_viewer_tag);
			tag.setText(log.getTag());
			TextView message = (TextView)logRow.findViewById(R.id.row_log_viewer_message);
			message.setText(log.getMessage());

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
	}
}
