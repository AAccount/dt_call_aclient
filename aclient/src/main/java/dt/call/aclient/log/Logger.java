package dt.call.aclient.log;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import dt.call.aclient.Const;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 2/23/19.
 */

public class Logger
{
	private static Logger instance = null;
	private String outputFileName;
	private File outputFile;
	private FileOutputStream fileOutputStream;
	public static final int MAX_LOG_SIZE = 50000;
	private long currentLogSize;
	private boolean useable;
	private final LinkedBlockingQueue<LogEntry> backlog;

	private Logger()
	{
		final SharedPreferences sharedPreferences = Vars.applicationContext.getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
		outputFileName = sharedPreferences.getString(Const.PREF_LOGFILE, Const.PREF_LOGFILE_A);
		outputFile = new File(Vars.applicationContext.getFilesDir(), outputFileName);
		currentLogSize = outputFile.length();
		try
		{
			fileOutputStream = new FileOutputStream(outputFile, true);
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
			useable = false;
		}
		useable = true;
		backlog =  new LinkedBlockingQueue<LogEntry>();

		//if the logEntry file is over the maximum size, swap to the other one
		//erase old logEntry contents
		Thread diskRW = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					while(true)
					{
						LogEntry logEntry = backlog.take();

						//if the logEntry file is over the maximum size, swap to the other one
						if(currentLogSize > MAX_LOG_SIZE)
						{
							currentLogSize = 0;
							fileOutputStream.close();
							if(outputFileName.equals(Const.PREF_LOGFILE_A))
							{
								outputFileName = Const.PREF_LOGFILE_B;
							}
							else
							{
								outputFileName = Const.PREF_LOGFILE_A;
							}
							final SharedPreferences sharedPreferences = Vars.applicationContext.getSharedPreferences(Const.PREFSFILE, Context.MODE_PRIVATE);
							final SharedPreferences.Editor editor = sharedPreferences.edit();
							editor.putString(Const.PREF_LOG, outputFileName);
							editor.apply();
							outputFile = new File(Vars.applicationContext.getFilesDir(), outputFileName);
							fileOutputStream = new FileOutputStream(outputFile, false); //erase old logEntry contents
						}

						final byte[] bytes = logEntry.toString().getBytes();
						currentLogSize = currentLogSize + bytes.length;
						fileOutputStream.write(bytes);
						fileOutputStream.flush();
					}
				}
				catch(IOException e)
				{
					e.printStackTrace();
					useable = false;
				}
				catch(InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		});
		diskRW.start();
	}

	public synchronized static Logger getInstance()
	{
		if(instance == null)
		{
			instance = new Logger();
		}
		return instance;
	}

	public synchronized void writeLog(LogEntry logEntry)
	{
		if(!useable)
		{
			return;
		}

		try
		{
			backlog.put(logEntry);
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}
