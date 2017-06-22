package dt.call.aclient.screens;

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.sqlite.History;
import dt.call.aclient.sqlite.SQLiteDb;
/**
 * Created by Daniel on 07/03/2016
 */
public class HistoryUI extends AppCompatActivity implements View.OnClickListener
{
	private LinearLayout historyLayout;
	private Button moreHistory;
	private ArrayList<History> dbhistory;
	private int i = 0;
	private SimpleDateFormat sameDayFormat = new SimpleDateFormat("HH:mm", Locale.US);
	private SimpleDateFormat diffDayFormat = new SimpleDateFormat("MMMM dd yyy @ HH:mm", Locale.US);

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_history_ui);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		historyLayout = (LinearLayout)findViewById(R.id.history_table);
		moreHistory = (Button)findViewById(R.id.history_more);

		SQLiteDb sqLiteDb = SQLiteDb.getInstance(getApplicationContext());
		dbhistory = sqLiteDb.getCallHistory();
		addEntries();
	}

	@Override
	public void onClick(View v)
	{
		if(v == moreHistory)
		{
			addEntries();
		}
	}

	private void addEntries()
	{
		int max = Math.min(i + 20, dbhistory.size());
		while(i < max)
		{
			//create a new table row for each log
			History history = dbhistory.get(i);
			RelativeLayout entry = (RelativeLayout) View.inflate(this, R.layout.row_history, null);

			//use the caller's nickname if there is one
			TextView who = (TextView)entry.findViewById(R.id.row_history_who);
			if(history.getWho().getNickname() == null || history.getWho().getNickname().equalsIgnoreCase(""))
			{
				who.setText(history.getWho().getName());
			}
			else
			{
				who.setText(history.getWho().getNickname());
			}

			//if the call was made today show only the time. otherwise show time and date (like android dialer)
			//https://stackoverflow.com/questions/6850874/how-to-create-a-java-date-object-of-midnight-today-and-midnight-tomorrow
			TextView when = (TextView)entry.findViewById(R.id.row_history_when);
			Calendar today = new GregorianCalendar();
			today.set(Calendar.HOUR_OF_DAY, 0);
			today.set(Calendar.MINUTE, 0);
			today.set(Calendar.SECOND, 0);
			today.set(Calendar.MILLISECOND, 0);
			if(history.getTimestamp() > today.getTimeInMillis()) //call happened after today @ midnight (happened today)
			{
				when.setText(sameDayFormat.format(history.getTimestamp()));
			}
			else
			{
				String date = diffDayFormat.format(history.getTimestamp());
				when.setText(date);
			}

			//set the call status icon
			ImageView status = (ImageView)entry.findViewById(R.id.row_history_status);
			if(history.getType() == Const.CALLINCOMING)
			{
				status.setBackground(ContextCompat.getDrawable(this, R.drawable.ic_call_received_blue_48dp));
				status.setContentDescription(getString(R.string.history_incoming_accessibility));
			}
			else if(history.getType() == Const.CALLMISSED)
			{
				status.setBackground(ContextCompat.getDrawable(this, R.drawable.ic_call_missed_red_48dp));
				status.setContentDescription(getString(R.string.history_missed_accessibility));
			}
			else if(history.getType() == Const.CALLOUTGOING)
			{
				status.setBackground(ContextCompat.getDrawable(this, R.drawable.ic_call_made_green_48dp));
				status.setContentDescription(getString(R.string.history_outgoing_accessibility));
			}
			historyLayout.addView(entry);
			i++;
		}
	}
}
