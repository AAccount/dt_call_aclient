package dt.call.aclient.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

import dt.call.aclient.Const;
import dt.call.aclient.log.Logger;
import dt.call.aclient.R;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 3/2/19.
 */

public class LogViewer2 extends AppCompatActivity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener
{
	private RadioGroup logButtons;
	private CheckBox enableLogs;
	private Button refresh;
	private TextView fileSize, dumpArea;
	private static final int LOGA = 1;
	private static final int LOGB = 2;
	private int log = LOGA;
	private String logADump, logBDump;
	private long logASize, logBSize;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log_viewer2);

		logButtons = findViewById(R.id.log_viewer2_log_select);
		logButtons.setOnCheckedChangeListener(this);
		enableLogs = findViewById(R.id.log_viewer2_enable);
		enableLogs.setOnClickListener(this);
		refresh = findViewById(R.id.log_viewer2_refresh);
		refresh.setOnClickListener(this);
		fileSize = findViewById(R.id.log_viewer2_file_size);
		dumpArea = findViewById(R.id.log_viewer2_dump_area);

		if(Vars.SHOUDLOG)
		{
			enableLogs.setChecked(true);
		}

		dumpLog(LOGA);
		dumpLog(LOGB);

		final SharedPreferences sharedPreferences = getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
		final String currentLog = sharedPreferences.getString(Const.PREF_LOGFILE, Const.PREF_LOGFILE_A);
		log = currentLog.equals(Const.PREF_LOGFILE_A) ? LOGA : LOGB;
		if(log == LOGA)
		{
			logButtons.check(R.id.log_viewer2_loga);
		}
		else
		{
			logButtons.check(R.id.log_viewer2_logb);
		}
	}

	@Override
	public void onClick(View v)
	{
		if(v == enableLogs)
		{
			Vars.SHOUDLOG = enableLogs.isChecked();
			SharedPreferences prefs = getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
			SharedPreferences.Editor ed = prefs.edit();
			ed.putBoolean(Const.PREF_LOG, Vars.SHOUDLOG);
			ed.apply();
		}
		else if(v == refresh)
		{
			if(log == LOGA)
			{
				dumpLog(LOGA);
				onCheckedChanged(logButtons, R.id.log_viewer2_loga);
			}
			else if(log == LOGB)
			{
				dumpLog(LOGB);
				onCheckedChanged(logButtons, R.id.log_viewer2_logb);
			}
		}
	}

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId)
	{
		if(group == logButtons)
		{
			switch(checkedId)
			{
				case R.id.log_viewer2_loga:
				{
					log = LOGA;
					dumpArea.setText(logADump);
					fileSize.setText(printFileSize(logASize));
					return;
				}
				case R.id.log_viewer2_logb:
				{
					log = LOGB;
					dumpArea.setText(logBDump);
					fileSize.setText(printFileSize(logBSize));
					return;
				}
			}
		}
	}

	private void dumpLog(int which)
	{
		try
		{
			String fileName;
			if(which == LOGA)
			{
				fileName = Const.PREF_LOGFILE_A;
			}
			else
			{
				fileName = Const.PREF_COMMANDPORT;
			}
			ArrayList<String> lines = new ArrayList<>();
			File logFile = new File(getFilesDir(), fileName);
			if(which == LOGA)
			{
				logASize = logFile.length();
			}
			else if(which == LOGB)
			{
				logBSize = logFile.length();
			}

			BufferedReader bufferedReader = new BufferedReader(new FileReader(logFile));
			String line;
			while((line = bufferedReader.readLine()) != null)
			{
				lines.add(line);
			}
			bufferedReader.close();

			StringBuilder stringBuilder = new StringBuilder();
			for(int i=lines.size()-1; i>0; i--)
			{
				//put the newest log lines at the top
				stringBuilder.append(lines.get(i));
				stringBuilder.append('\n');
			}

			if(which == LOGA)
			{
				logADump = stringBuilder.toString();
			}
			else if (which == LOGB)
			{
				logBDump = stringBuilder.toString();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private String printFileSize(long size)
	{
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder = stringBuilder.append(size).append("/").append(Logger.MAX_LOG_SIZE);
		return stringBuilder.toString();
	}
}
